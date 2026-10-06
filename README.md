# Baby Ender Dragon

A rideable **Baby Ender Dragon** mod for **Minecraft 26.2** (Fabric · Java 25).

Summon a baby ender dragon, right-click to mount, and fly it — W/A/D to steer, Space/Shift to climb and dive, with the rider welded to the dragon's back through banks, dives, and yaw wraps.

## Status — Phase 1: foundation entity

- Ridable dragon entity (`babyenderdragon:foundation_entity`) with the vanilla dragon model at baby scale
- Server-authoritative flight: W/A/D + Space/Shift, dive/climb speed trade, banking ≤ 35°, pitch ≤ 60°
- Rider seating: a single `DragonFrame` source of truth for the model frame — the rider is welded to the seat and rotates with the dragon's pitch/roll about its attachment point
- **Verified by code**: an in-client automated test harness (`-Dbabyenderdragon.selfcheck=true`) drives 8 attitude scenarios (straight / bank left / bank right / dive / climb / combined / bank sweep / yaw wrap) through the real render path and checks the seat weld (max hip error `0.00000` blocks) and rotation agreement (max `0.0000°`) — with negative controls that fail as designed. See `SEAT_SELFCHECK_RESULT.txt`.

## Build & run

```
./gradlew build       # build the mod
./gradlew runClient   # launch a dev client
```

Requires **Java 25**. Toolchain pinned in `gradle.properties` / `build.gradle`: Fabric Loader 0.19.5, Loom 1.18.2, Fabric API 0.161.0+26.2.

Self-check harness (debug-only; property off = zero behavior change):

```
JAVA_TOOL_OPTIONS="-Dbabyenderdragon.selfcheck=true" java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClientGameTest --no-daemon
```

Negative controls: add `-Dbabyenderdragon.selfcheck.mutate=feet|signs`.

## Roadmap

Hatching from a dragon egg · taming (golden dandelion) · ownership · two-player riding · chorus fruit healing · purple fire breath.

---

Minecraft is a trademark of Mojang AB. This project is not affiliated with or endorsed by Mojang or Microsoft. No Minecraft code or assets are distributed in this repository.
