package com.babyenderdragon.selfcheck;

import com.babyenderdragon.FoundationEntity;
import com.babyenderdragon.SeatSelfCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Unattended verification of the rider seat weld + attitude in the REAL render path.
 *
 * <p>Requires {@code -Dbabyenderdragon.selfcheck=true} (the render hooks are inert otherwise).
 * Run with: {@code gradlew runClientGameTest --no-daemon} (with JAVA_TOOL_OPTIONS carrying the
 * property). Negative controls: {@code -Dbabyenderdragon.selfcheck.mutate=feet|signs}.
 *
 * <p>Scenarios S1-S8 (straight, bank right, bank left, dive, climb, combined, bank sweep, yaw
 * wrap): settle 5 ticks, then collect >= 10 rendered frames each (the loop collects ~15).
 * PASS rules: samples >= 10; max hipError < 0.02 blocks; max rotError < 0.5 degrees; S4 rider
 * forward-vertical negative and matching; S5 positive and matching; S2/S3 up-axis on the same
 * side as the dragon's.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RiderSeatAttitudeClientTest implements FabricClientGameTest {
	private static final String ENTITY_ID = "babyenderdragon:foundation_entity";
	private static final String TAG = "babyenderdragon_selfcheck";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!SeatSelfCheck.ENABLED) {
			throw new AssertionError("SeatSelfCheck is not enabled - run with "
					+ "-Dbabyenderdragon.selfcheck=true (e.g. via JAVA_TOOL_OPTIONS)");
		}

		try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(true).createServer();
				var connection = server.connect()) {
			// Third person back - without it the avatar is not drawn and there are no samples.
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

			server.runCommand("kill @e[tag=" + TAG + "]");
			server.runCommand("summon " + ENTITY_ID + " 0 80 0 {Tags:[\"" + TAG + "\"]}");
			context.waitTicks(10);
			server.runCommand("ride @a[limit=1] mount @e[type=" + ENTITY_ID + ",tag=" + TAG
					+ ",limit=1,sort=nearest]");
			context.waitTicks(8);
			boolean mounted = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).isPassenger());
			if (!mounted) {
				throw new AssertionError("player did not mount the dragon");
			}

			List<SeatSelfCheck.Stats> results = new ArrayList<>();
			List<String> shots = new ArrayList<>();

			results.add(scenario(context, server, "s1_straight", 0, 0, 0, shots));
			results.add(scenario(context, server, "s2_bank_right", 0, 0, 35, shots));
			results.add(scenario(context, server, "s3_bank_left", 0, 0, -35, shots));
			results.add(scenario(context, server, "s4_dive", 0, 60, 0, shots));
			results.add(scenario(context, server, "s5_climb", 0, -60, 0, shots));
			results.add(scenario(context, server, "s6_combined", 137, 40, 35, shots));
			results.add(sweep(context, server, "s7_bank_sweep", 0, 0, 0, 0, 35, 20, shots));
			results.add(sweep(context, server, "s8_yaw_wrap", 170, 190, 0, 25, 25, 20, shots));

			evaluateAndReport(results, shots);
		}
	}

	// ---- scenario drivers ----------------------------------------------------------------

	private static SeatSelfCheck.Stats scenario(ClientGameTestContext context,
			TestDedicatedServerContext server, String name, float yaw, float pitch, float bank,
			List<String> shots) {
		setAttitude(context, server, yaw, pitch, bank);
		context.waitTicks(5); // settle
		SeatSelfCheck.beginScenario(name);
		int waited = 0;
		while (SeatSelfCheck.sampleCount() < 15 && waited < 120) {
			context.waitTicks(5);
			waited += 5;
		}
		SeatSelfCheck.Stats stats = SeatSelfCheck.endScenario();
		shots.add(context.takeScreenshot("seat_" + name).toAbsolutePath().toString());
		return stats;
	}

	private static SeatSelfCheck.Stats sweep(ClientGameTestContext context,
			TestDedicatedServerContext server, String name, float yaw0, float yaw1, float pitch,
			float bank0, float bank1, int ticks, List<String> shots) {
		setAttitude(context, server, yaw0, pitch, bank0);
		context.waitTicks(5); // settle
		SeatSelfCheck.beginScenario(name);
		for (int t = 1; t <= ticks; t++) {
			float f = (float) t / (float) ticks;
			setAttitude(context, server, yaw0 + (yaw1 - yaw0) * f, pitch, bank0 + (bank1 - bank0) * f);
			context.waitTick();
		}
		int waited = 0;
		while (SeatSelfCheck.sampleCount() < 15 && waited < 60) {
			context.waitTicks(5);
			waited += 5;
		}
		SeatSelfCheck.Stats stats = SeatSelfCheck.endScenario();
		shots.add(context.takeScreenshot("seat_" + name).toAbsolutePath().toString());
		return stats;
	}

	private static void setAttitude(ClientGameTestContext context, TestDedicatedServerContext server,
			float yaw, float pitch, float bank) {
		// The ridden dragon is CLIENT-driven (vanilla ridden-mount sync path), so the client copy
		// is the one the controller and the renderer read; mirror to the server copy as well so
		// the server-side seat (positionRider) agrees.
		//
		// The rider's body yaw follows the PLAYER'S LOOK (vanilla passenger behaviour), so align
		// the look with the dragon's yaw per scenario - the same invariant real flight has (the
		// dragon chases the rider's aim). This isolates the seating attitude from look freedom.
		context.runOnClient(mc -> {
			FoundationEntity dragon = findClientDragon(mc);
			if (dragon != null) {
				dragon.debugSetAttitude(yaw, pitch, bank);
			}
			if (mc.player != null) {
				mc.player.setYRot(yaw);
				mc.player.yRotO = yaw;
				mc.player.setYHeadRot(yaw);
				mc.player.yHeadRotO = yaw;
				mc.player.setYBodyRot(yaw);
				mc.player.yBodyRotO = yaw;
			}
		});
		server.runOnServer(s -> {
			FoundationEntity dragon = findServerDragon(s);
			if (dragon != null) {
				dragon.debugSetAttitude(yaw, pitch, bank);
			}
			ServerPlayer player = s.getPlayerList().getPlayers().get(0);
			player.setYRot(yaw);
			player.yRotO = yaw;
			player.setYHeadRot(yaw);
			player.yHeadRotO = yaw;
			player.setYBodyRot(yaw);
			player.yBodyRotO = yaw;
		});
	}

	private static FoundationEntity findClientDragon(Minecraft mc) {
		if (mc.level == null) {
			return null;
		}
		// NOTE: no tag check here - entity tags are server-side data and are NOT synced to the
		// client. The client copy is the only dragon in this test's world, so instanceof is the
		// correct (and only workable) match.
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof FoundationEntity dragon) {
				return dragon;
			}
		}
		return null;
	}

	private static FoundationEntity findServerDragon(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null) {
			return null;
		}
		for (Entity e : level.getAllEntities()) {
			if (e instanceof FoundationEntity dragon && e.entityTags().contains(TAG)) {
				return dragon;
			}
		}
		return null;
	}

	// ---- evaluation + report -------------------------------------------------------------

	private static void evaluateAndReport(List<SeatSelfCheck.Stats> results, List<String> shots) {
		StringBuilder report = new StringBuilder();
		report.append("SEAT SELF-CHECK RESULTS\n");
		report.append("run mode: ")
				.append(SeatSelfCheck.MUTATE.isEmpty() ? "normal" : "mutate=" + SeatSelfCheck.MUTATE)
				.append("\n\n");
		report.append(String.format("%-15s %8s %11s %10s %11s %10s %10s  %s%n",
				"scenario", "samples", "maxHipErr", "maxRotErr", "maxCamDelta", "riderFwdY",
				"dragonFwdY", "verdict"));

		List<String> failures = new ArrayList<>();
		for (SeatSelfCheck.Stats st : results) {
			List<String> why = new ArrayList<>();
			if (st.samples < 10) {
				why.add("samples<10");
			}
			if (!(st.maxHipError < 0.02)) {
				why.add("hipError>=0.02");
			}
			if (!(st.maxRotError < 0.5)) {
				why.add("rotError>=0.5");
			}
			switch (st.scenario) {
				case "s4_dive" -> {
					if (!(st.avgDragonFwdY < -0.5)) {
						why.add("dragon-not-posed");
					}
					if (!(st.allFwdNegative && st.maxFwdMatch < 0.02)) {
						why.add("dive-fwd");
					}
				}
				case "s5_climb" -> {
					if (!(st.avgDragonFwdY > 0.5)) {
						why.add("dragon-not-posed");
					}
					if (!(st.allFwdPositive && st.maxFwdMatch < 0.02)) {
						why.add("climb-fwd");
					}
				}
				case "s2_bank_right", "s3_bank_left" -> {
					if (!(Math.abs(st.avgDragonUpX) > 0.1)) {
						why.add("dragon-not-posed");
					}
					if (!(st.upSameSide && st.maxUpMatch < 0.02)) {
						why.add("up-axis");
					}
				}
				default -> {
				}
			}
			String verdict = why.isEmpty() ? "PASS" : "FAIL(" + String.join(",", why) + ")";
			if (!why.isEmpty()) {
				failures.add(st.scenario + " -> " + verdict);
			}
			report.append(String.format("%-15s %8d %11.5f %10.4f %11.5f %10.4f %10.4f  %s%n",
					st.scenario, st.samples, st.maxHipError, st.maxRotError, st.maxCamDelta,
					st.avgRiderFwdY, st.avgDragonFwdY, verdict));
		}
		report.append("\nfirst-sample diagnostics (rider vs dragon):\n");
		for (SeatSelfCheck.Stats st : results) {
			report.append(String.format("  %-15s rider[%s] bodyYaw=%.1f | dragon[%s] yaw=%.1f%n",
					st.scenario, st.firstRiderEuler, st.firstRiderBodyYaw,
					st.firstDragonEuler, st.firstDragonYaw));
		}
		report.append("\nscreenshots:\n");
		for (String p : shots) {
			report.append("  ").append(p).append("\n");
		}

		System.out.println(report);
		try {
			// The gametest client runs with user.dir = <project>/build/run, so ../../.. is the
			// workspace root (research/.../).
			Path out = Path.of("../../..", "SEAT_SELFCHECK_RESULT.txt").toAbsolutePath().normalize();
			Files.writeString(out, report.toString());
			System.out.println("SEAT_SELFCHECK_FILE=" + out);
		} catch (Exception e) {
			System.out.println("SEAT_SELFCHECK_FILE_WRITE_FAILED=" + e);
		}

		if (!failures.isEmpty()) {
			throw new AssertionError("SEAT SELF-CHECK FAILURES: " + failures);
		}
		System.out.println("SEAT_SELFCHECK_RESULT=PASS");
	}
}
