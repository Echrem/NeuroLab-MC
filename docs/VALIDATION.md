# 0.5.21 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

- Latest source validation: 123 Java tests passed, 0 skipped or failed; all 15 Forge GameTests passed. The added coverage checks bounded flight acceleration and restoration of the player's perspective after mob-view exit. Forge's update check timed out in this environment but did not affect the test result.
- Installable JAR: `build/libs/neurolab-mc-1.21.1-0.5.21.jar`; SHA-256: `da821831cb40a08f9e107c985ef1bcb254dd0af941dc29019cd372cce5ce91c6`.
- `git diff --check` passed. No interactive client was available; visual GUI review remains a separate in-game check.

# 0.5.20 development validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

- `./gradlew --no-daemon test build`: passed; 120 JUnit tests, zero skipped or failed. This includes progressive player-threat association, attacker-identity reset, per-block positive/negative outcome learning, NBT persistence, bounded memory, and packet round-trip coverage.
- `./gradlew --no-daemon runGameTestServer`: all 15 required Forge GameTests passed, including three real player-damage events establishing the UUID-specific aversive cue.
- Installable JAR: `build/libs/neurolab-1.21.1-0.5.20.jar`; SHA-256: `e7169d2c75f4ec546a004dddd273d51859b3d8eaa95516163d3e4ca1c539f615`.
- `git diff --check` passed. No interactive Minecraft client was available, so PC display layout and in-world steering still require a real client/gameplay check.

# 0.5.19 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

- Java unit tests: 117 passed, 0 failed, 0 skipped. Python analyzer tests: 8 passed. Forge GameTests: 14/14 passed, including selected-subject termination cleanup, probe precedence on horse/villager targets, telemetry trial boundaries, and non-lethal empty needs. `git diff --check` passed.
- Installable JAR: `build/libs/neurolab-1.21.1-0.5.19.jar`; `META-INF/mods.toml` reports mod version 0.5.19. SHA-256: `5ee7687537c0f51ced76aba7c8c7b228275cfe0a5bc96e65327b598cb90bcb22`.
- The new anatomy/rotation/activity renderers compiled and server-side assets loaded, but no interactive Minecraft screenshot was available in this environment. Visual appearance should be checked in-game; tests do not establish rendered quality.

# 0.5.18 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

- Java unit tests: 114 passed; Python analyzer tests: 8 passed; Forge GameTests: 13/13 passed.
- Installable JAR: `build/libs/neurolab-1.21.1-0.5.18.jar`; `META-INF/mods.toml` reports mod version 0.5.18. SHA-256: `9b93c706aba250daab118cf72d7dbc8540832feb39fa367bceaf8db3c2ec457c`.
- No interactive Minecraft screenshot was available; renderer and GUI appearance were not visually verified.

# 0.5.16 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

- `compileJava` passed with the expanded client HUD and derived hunger/thirst telemetry labels.
- Hunger is computed as `1 - satiety`; thirst is `1 - hydration`. Natural-drive code contributes sensory pain/reward signals and contains no health reduction or death action for either need.
- No tests were run for this UI-only revision. Interactive HUD appearance still needs a screenshot/client check.

# 0.5.15 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

- `./gradlew test build runGameTestServer --no-daemon --quiet` passed: 110 JUnit tests and all 11 required Forge GameTests, with no skipped or failed tests.
- The layout test checks that a 480-pixel-high PC screen gives the Connectome preview at least 180 pixels while retaining at least 120 pixels for plots. The preview now opens the explorer on click.
- Packaged JAR: `neurolab-1.21.1-0.5.15.jar`, SHA-256 `c018b94f6ae4e823c7184f2341dabc381cc12ad7dd2cdf880ef0cecd2d94171d`.
- `git diff --check` passed.

No interactive Minecraft client screenshot or mouse-click validation was possible here. These checks do not prove that the user's particular Connectome visual defect is gone; obtain a screenshot and GUI scale if it persists.

# 0.5.14 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

## Checks completed

- `./gradlew test build runGameTestServer --no-daemon --quiet` passed: 109 JUnit tests, zero skipped or failed; all 11 required Forge GameTests passed.
- GameTests cover attachment, no-AI and gravity behavior, the bee and villager controller handoff, and cactus contact on an attached zombie. Unit tests cover behavior categories, cue memory, synaptic eligibility, and packet encoding.
- `git diff --check` passed. The installable JAR reports version 0.5.14; SHA-256: `aa832587d05123f8331863d9ce91ff8c79153e8fe1a8a57e9466e6940975d7cf`.

Installable artifact: `neurolab-1.21.1-0.5.14.jar`

## Not verified in this environment

- No interactive Forge client or dedicated-server multiplayer session was run. The PC behavior-map layout, synapse selection, and imprint button still need a visual check in-game.
- The accelerated fly GameTest checks attachment, flight height, and collision but does not prove ground landing or long-run weather behavior. Reward-driven pursuit, flight navigation, and 10–20-mob performance need reproducible gameplay trials.

# 0.5.12 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

## Checks completed

- `./gradlew clean build` passed with 99 JUnit tests; all 9 required Forge GameTests passed in a separate `runGameTestServer` run.
- The installable JAR reports mod version 0.5.12 and has SHA-256 `e225a48cafe445ae99b4b39bc9bc275913ca416dce90b03a544cc56b8f8dccd3`.
- Layout tests verify at least 90 GUI pixels of chart height on a 240-pixel-tall details screen and at least 60 pixels in a 320-pixel overview. Plot reduction tests verify that a short peak remains visible across 72,000 samples.
- `git diff --check` passed.

Installable artifact: `neurolab-1.21.1-0.5.12.jar`

## Not verified in this environment

- No interactive Forge client rendered the dashboard or PC console; the new subject-details flow, scrolling, text clipping, and hover/click placement still need a real-client visual check.
- No long 10–20-mob telemetry load test was run. The plotted history remains bounded at 72,000 scalar samples per mob; frame time and memory use depend on the client hardware and enabled plots.

# 0.5.9 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

## Checks completed

- `./gradlew clean test build runGameTestServer`: passed; 85 JUnit tests and all 8 required Forge GameTests passed, with zero skipped.
- Forge GameTests cover Neuro Fly ground landing, wall perching, rain exposure, roof-seeking, and collision-free shelter occupancy alongside existing attachment and chair behavior checks.
- Unit tests cover rain-safe wall clinging, shelter steering/braking, and controlled descent.
- `git diff --check` passed. Installable JAR metadata reports 0.5.9; SHA-256: `3f7796fc468dfa467a925edfdf8778a47e55d27fa3cc871a2f5fe5e5d7101a56`.

Installable artifact: `neurolab-1.21.1-0.5.9.jar`

## Not verified in this environment

- No interactive Minecraft client was available, so the revised activity labels and 24,000-point Connectome default still need a visual/performance check in the user's Forge client.
- No multi-mob load test was run; high-detail connectome drawing, telemetry throughput, and 10–20-subject real-time performance remain hardware-dependent.

# 0.5.8 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

## Checks completed

- `./gradlew --no-daemon clean test build runGameTestServer`: passed; 79 JUnit tests, zero failures, zero errors, zero skipped.
- Forge GameTest server loaded the clean 0.5.8 metadata and passed all 7 required tests.
- Wire-format test encoded and decoded the 25,600-ID sample under 100 KB; async JSONL test retained all 25,600 IDs and the new drive/learning metrics.
- Natural-drive tests cover separate mob state, cooldown/anti-loop behavior, reattachment memory, water/hydration, rain-stress pain, and exploration rewards.
- `git diff --check` passed. Installable JAR metadata reports 0.5.8.

Installable artifact: `neurolab-1.21.1-0.5.8.jar`
SHA-256: `26b4d6906891bbebf61df11bed4ba50cfa4a45c4e9d5a35a6f3606717a495902`

## Not verified in this environment

- No interactive Minecraft client was available, so the revised top-right NeuroView HUD still needs a visual check in the user's Forge client.
- A 10–20-mob live performance run was not performed. Sampling is capped at 25,600 IDs per observation, but CPU scheduling, packet throughput, disk throughput, and real-time factor remain hardware-dependent; `/neurolab status` exposes dropped log records.

# 0.5.7 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

## Checks completed

- `./gradlew test build runGameTestServer`: passed; 67 JUnit tests and all 7 Forge GameTests passed.
- Offline telemetry analysis: 5 Python tests passed.
- English/Turkish locale JSON and `git diff --check`: passed.
- Connectome projection tests verify shared-axis scale, aspect preservation, and safe handling of invalid/degenerate bounds.
- Connectome selected-edge rendering now traverses the full screen-space segment; interactive client visuals still need a desktop check.

## Not verified in this environment

No interactive Minecraft client was available, so the Connectome's visual proportions and long-edge appearance have not been confirmed in-game. GameTests cover server behavior and do not exercise client rendering.

# 0.5.1 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

## Checks completed

- `./gradlew test build`: passed; 53 JUnit tests, zero failures, zero errors, zero skipped.
- Forge GameTest server: both required tests passed. The Neuro Fly test asserts its live one-block collision box, vertical displacement, continued survival, automatic attachment, and collision-free flight; the villager test checks assisted movement and AI restoration.
- English/Turkish locale files and all three new item models parse successfully.
- Release JAR contains the three sensory probes and their item models.
- `git diff --check`: passed.

## Not verified in this environment

No interactive desktop client was available, so item appearance and use in a player's hand still need an in-client check. The GameTest verifies server-side entity motion and hitbox behavior, not client rendering.

# 0.5.0 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

## Checks completed

- `./gradlew test build`: passed after the gameplay, stand, and HUD changes.
- Forge GameTest server: both registered tests passed, including attached villager movement/AI restoration and Neuro Fly auto-attachment/movement.
- Escape-jump gate unit tests cover held escape signals and grounded/airborne/cooldown transitions.
- English/Turkish locale and stand model JSON parse successfully; `git diff --check` passes.

## Not verified in this environment

No interactive desktop client was available, so the HUD layer, brain schematic rendering, and stand appearance still need visual confirmation in a Forge 1.21.1 client. GameTests verify server behavior, not client rendering or the visual result of entering mob view.

# 0.4.9 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

## Checks completed

- `./gradlew --no-daemon clean test build runGameTestServer`: passed.
- JUnit: 48 tests, zero failures, zero errors, zero skipped.
- Forge GameTest server: both registered tests passed.
- English and Turkish locale files parse as JSON.
- `git diff --check`: passed.
- Installable JAR metadata and version were checked.

Installable artifact: `neurolab-1.21.1-0.4.9.jar`
SHA-256: `34f39c4314863f3af86d8e8b15f35efafdc8456f32ae0f2e30cd0168c283754b`

## Not verified in this environment

The Minecraft client could not be opened for visual testing here. The new screen preview, button hit targets, camera switch, and ESC close still need confirmation in the user's desktop Forge client. The entity hitbox remains unchanged; one-block sizing applies to the console's visual reference frame.

# 0.4.7 validation record

Target: Minecraft 1.21.1, Forge 52.1.16, Java 21.

## Checks completed

- `./gradlew --no-daemon clean test build runGameTestServer`: passed.
- JUnit: 48 tests, zero failures, zero errors, zero skipped.
- Forge GameTest server: both registered tests passed. They check ordinary-mob assisted movement and AI restoration, plus Neuro Fly auto-attachment, persistence, and position change.
- Offline analysis tool: 5 Python tests passed.
- All packaged NeuroLab JSON resources parsed successfully.
- Release JAR metadata and bundled connectome were inspected. The artifact contains the Petri Dish blockstate/model and client HUD/hologram classes.
- `git diff --check`: passed.

Installable artifact: `neurolab-1.21.1-0.4.7.jar`
SHA-256: `4c6edc763f809695427edd14cdc080ef15983e4e512f78a6d850f618a385c797`

## Not verified in this environment

The client launch was attempted with an isolated game directory under Xvfb. Forge's early display failed at `glfwInit` (`Can't open display :109`) before the mod initialized. Therefore the top-right HUD, rotating brain wireframe, Petri Dish appearance, and client resource loading still need visual confirmation in a desktop Forge client. No dedicated-server/LAN or long-duration play test was run. The server-side GameTests do not cover these visual checks.

The Neuro Experiment Chair only stores a subject association. Test input is delivered separately; no hardware electrode, voltage/current, or tissue measurement is modeled.
