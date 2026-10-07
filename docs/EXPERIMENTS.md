# Recording and reviewing an experiment

Keep Minecraft 1.21.1, Forge 52.1.16, Java 21, the mod version, and the dataset fixed during a comparison. Record the world seed, `config/neurolab-common.toml`, entity type, mode, stimulus strength/duration, and whether the mob carries a learned imprint. Run the simulation on the lab PC; use the other PC as the LAN spectator/capture client.

## One-mob protocol

1. Start a backed-up test world. Spawn a Neuro Fly and wait for `/neurolab status` to report the dataset and attachment. The same command reports log state, queued records, and dropped records.
2. Clear test pulses and select a condition:

   ```mcfunction
   /neurolab unstimulate @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
   /neurolab mode @e[type=neurolab:neuro_fly,limit=1,sort=nearest] neural
   /neurolab inspect @e[type=neurolab:neuro_fly,limit=1,sort=nearest]
   ```

3. Start a labeled interval and apply one input:

   ```mcfunction
   /neurolab trial @e[type=neurolab:neuro_fly,limit=1,sort=nearest] left-eye-01 400
   /neurolab stimulate @e[type=neurolab:neuro_fly,limit=1,sort=nearest] left_eye 0.8 100
   ```

4. Wait for expiry or call `/neurolab endtrial <mob>`. Repeat with clearly different labels and conditions. Natural input continues during test pulses. Starting a trial or changing mode does not reset neural or learned state, so this protocol segments one continuous run; it does not establish matched independent trials.
5. Close the world normally so the log receives its end marker. Keep the raw `<world>/neurolab/events.jsonl` with the video and configuration.

## Experiment chair and monitor run

1. Place the Neuro Experiment Chair near one mob and right-click it once. An unattached mob receives the connectome in assisted mode; the chair records its UUID and starts a `neuro-stand` trial. Sneak-right-click clears the dock and ends the trial.
2. The chair does not inject sensory pulses. Use the Neuro Monitor to select its docked subject, inspect the vision and output traces, and deliver a bounded input pulse; stimulus probes are also available for direct sensory tests.
3. Ctrl+N shows input, neural and body output traces; the top-right HUD follows the nearest tracked subject. The monitor and dashboard report simulated neural activity, not biological electrode measurements.

No part of the chair models electrode impedance or measures voltage, current, or tissue activity.

## Handheld sensory probes and field-trial marker

The Tools & Utilities tab contains visual, tactile, olfactory, acoustic, gustatory, and looming probes. Right-click a brain-attached mob to apply that channel's 0.75 test cue for 40 game ticks (two seconds); sneak-right-click applies 0.25. This is a model input pulse, not a physical stimulus calibration. In particular, the acoustic cue is mapped to the dataset's sound/mechanosensory representation and does not imply that a fly ear is present in the connectome model. These inputs do not directly issue motor commands or reset neural/learning state.

Use the Trial Recorder on an attached mob while the session log is active to mark a one-minute `field-<gameTick>` interval. Sneak-right-click ends it early. The JSONL records trial boundary events and the associated subject telemetry. For controlled comparisons, prefer `/neurolab trial <mob> <label> <ticks>` so condition labels and durations are explicit; neither method resets the subject's neural or learned state.

## Offline graphs and CSV

Python 3.10 or newer is sufficient; no packages, account, server, or internet connection are required:

```bash
python3 tools/analyze_telemetry.py /path/to/world/neurolab/events.jsonl --output /path/to/report
```

Open `report.html` in a browser. It contains SVG plots for spikes, synaptic events, real-time factor, body forward command, pain, reward, measured X/Z movement, and altitude. `summary.csv` groups data by run, entity UUID, dimension, mode, trial label, and trial start tick. The charts retain the last 600 samples per group; averages cover the complete input. Use a fresh output directory per analysis, because output files are replaced when the command is rerun.

In-world, a nearby tracked mob can show a small hologram above its head. Dim nodes and links are a sparse sample from the bundled Male CNS anatomical soma positions and directed connectome edges. Bright points mark a bounded sample of IDs that fired in that mob's latest telemetry packet; their color reflects the current reward-versus-pain signal. This is a visualization aid, not a full brain reconstruction or a causal explanation of movement.

To preview the report layout without claiming experiment results:

```bash
python3 tools/analyze_telemetry.py examples/synthetic-telemetry.jsonl --output build/research-demo
```

The bundled example is explicitly synthetic. It tests the reporting pipeline, not a biological hypothesis.

## Reading the numbers

- `neuralTick` identifies a 50 ms simulated update. `gameTick` identifies Minecraft world time. They can advance at different rates; the charts use world ticks, not wall-clock seconds.
- Spike and synaptic-event values are counts in the latest sampled neural update. The server reports every two game ticks, so updates may be skipped or repeated. Means exclude consecutive repeated neural ticks when available; neither these samples nor their sums are complete spike counts for a trial.
- `bodyForward` is a requested movement command. Schema 6 also records server-side world position and displacement. The report plots the measured X/Z trajectory and altitude, and summarizes horizontal displacement, vertical displacement, and blocks per game-second. Intervals over 200 game ticks are excluded from displacement summaries; `observe` may still show movement caused by vanilla AI.
- `reflex` labels decoder assistance, not a complete causal decomposition of every downstream body rule.
- A missing `session_end` can mean a live recording, crash, or truncated copy. Positive `droppedRecords` means an incomplete recording. A log failure is also visible through `/neurolab status`.
- Legacy logs without run IDs or neural ticks generate warnings. Keep separate sessions in separate files. Malformed JSON and reversed ticks fail with a source line number rather than silently discarding evidence.
- Trial groups are summaries, not independent statistical replicates. Keep the raw data and avoid claiming learning or biological fidelity from a single attractive trace.

## Validation commands

```bash
./gradlew clean test build
./gradlew runGameTestServer
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

GameTests use an isolated development world under `build/gametest`. They check a villager's assisted-mode movement/AI transition and Neuro Fly auto-attachment, persistence, and actual displacement. They do not establish long-duration stability, visual renderer correctness, LAN synchronization, or 16-brain performance. Those require separate live play tests.
