# Changelog

## 0.5.21 — NeuroLab-MC public repository release

- Smooth assisted flight acceleration and collision rerouting, restore the player's selected camera perspective after leaving a mob view, and resolve PC termination requests by stable entity UUID.
- Repack the installable 0.5.21 JAR after these fixes; Java tests and Forge GameTests pass.
- Standardize the project and installable artifact name as **NeuroLab-MC**, retaining the `neurolab` Minecraft registry namespace for world compatibility.
- Publish the complete 0.5 line as one current release in the new repository; add a system architecture illustration and release/build guidance.
- Run Java tests, offline analysis tests, and Forge GameTests on pushes, pull requests, version tags, and manual dispatch; package only the installable mod JAR for tagged builds.
- Include the progressive player-threat and block-outcome learning introduced in 0.5.20.

Earlier version entries below document the development history carried forward from the former `neurolab` repository.

## 0.5.20 — Outcome associations for players and blocks

- Accumulate an aversive cue for the individual damaging player, with steering activation determined by learned cue strength rather than a fixed hit count.
- Learn block-specific pain from environmental damage, bias routes away from blocks with negative outcomes, and associate observed grounded health recovery with the support block.
- Persist cue memories per mob, surface player and strongest block associations in Connectome inspection, and clarify reflex versus learned behavior in the Behavior Map.
- Keep health/block attribution explicitly correlational; add regression coverage for gradual learning, identity reset, and bounded block-outcome memory.

## 0.5.19 — More expressive Neuro Fly flight and safer PC subject controls

- Replace infrequent fixed-turn flight jolts with deterministic per-fly saccade turns, layered heading drift, small speed bouts, and bounded vertical flutter. The texture is only added in assisted flight, yields to threat/shelter behavior, and remains separate from connectome motor output.
- Add a two-step **KILL SUBJECT** action to the PC dashboard for the selected tracked mob. The server validates the monitor, target distance, liveness, and active attachment; confirmation ends the trial, closes its brain worker, restores AI state, and then removes only that target.
- Isolate client histories on dimension changes and entity-UUID reuse so recycled numeric entity IDs cannot blend two subjects' traces.
- Add regression checks for bounded deterministic flight motion, subject termination cleanup, dimension history reset, and entity-ID reuse.

## 0.5.18 — Sensory experiment kit and connectome activity detail

- Add acoustic, gustatory, and looming probe items alongside vision, touch, and odor. Normal use injects a bounded 0.75 cue for two seconds; sneak-use lowers it to 0.25. Inputs are labeled channel proxies, not biological electrode measurements.
- Add a handheld trial recorder that marks and closes one-minute field intervals in the active JSONL telemetry log; the monitor, probe, chair, and command flows remain available.
- Expand the connectome preview's sampled soma and edge detail, preserve unknown synapse polarity rather than guessing its sign, and expose neuron class, transmitter, polarity, connectivity, and soma coordinates in the explorer.
- Add independent brain rotation/zoom and a separate anatomy-versus-learned-edge view; improve the head hologram's spatial sample and activity overlay.
- Extend movement telemetry analysis with bounded, gap-aware trajectories, altitude, displacement, and time-weighted speed summaries. Preserve legacy log compatibility.
- Include Forge GameTests for trial-file boundaries, six probe channels on horse/villager targets, and non-lethal depleted needs.

## 0.5.17 — Connectome activity hologram and measurable movement

- Render a compact connectome hologram above nearby tracked mobs, using sampled Male CNS soma coordinates and real directed edges; highlight sampled firing neurons using live activity and reward/pain state.
- Expand NeuroView to show world position, client-side motion estimate, grounded/airborne state, and unclipped hunger/thirst, reward, and pain gauges.
- Record server-observed entity coordinates and valid displacement intervals in schema 6 telemetry. Add measured X/Z trajectories, altitude plots, and horizontal/vertical displacement and speed summaries to offline experiment reports.
- Keep display geometry bounded and load the graph asynchronously. The hologram is a sparse visualization, not a complete anatomical rendering or proof that connectome activity caused each movement.
- Keep hunger and thirst visible as non-lethal needs; neither reduces health nor kills mobs.

## 0.5.16 — Expanded NeuroView HUD and visible non-lethal needs

- Expand the in-world mob-camera HUD with a spike history graph, active-neuron and edge-visit counters, neural/body motor commands, sensory inputs, feedback, and learning revision.
- Show hunger and thirst as separate labeled bars. Hunger is derived from the existing per-mob satiety meter; both needs feed sensory/reward behavior without reducing health or causing death.
- Update the dashboard readouts and plots to show hunger/thirst values.

## 0.5.15 — Larger PC connectome preview

- Give the active Neuro Monitor dashboard's 3D brain preview more height when GUI space permits, while reserving room for plots.
- Make the brain preview itself open the full Connectome explorer on click, in addition to the toolbar button.
- Add responsive layout regression coverage for medium and tall Minecraft GUI sizes.

## 0.5.14 — Behavior layers and inspectable learning

- Group experiment behaviors in a PC **Behavior Map** as automatic reflexes, assisted movement, or learned responses. Neuro Fly rain shelter remains automatic.
- Gate assisted flower attraction on a positive flower encounter, and add bounded cue preferences and recent blocked-route memory. Attached bees no longer inherit vanilla flower-seeking; attached zombies and villagers no longer inherit immediate vanilla chase/flee goals.
- Register cactus side contact and other hazardous blocks as aversive feedback, including the relevant contact cell for local avoidance.
- Restrict reward-eligible synapses to signed directed connectome edges with simulated pre-before-post spiking; expose bounded per-edge changes in the Connectome inspector and keep named brain imprints accessible from the PC.
- Add unit and Forge GameTests for behavior categories, cue memory, synaptic eligibility, attachment handoff, and cactus contact. Interactive client rendering and long-run behavior remain unverified.

## 0.5.13 — Learned threat response and named mind imprints

- Remove the hand-coded zombie looming/flee cue: contact is a sparse positive reinforcement event, so any learned change comes from the connectome's active-edge eligibility traces rather than a preloaded zombie-chase rule.
- Exclude zombies from flower reward/flower targeting; every attached mob has a shared thirst drive, local water seeking, and a small cooldown-limited reward on reaching water.
- Retain learned hazard responses per subject: fire, lava, and damaging contact remain negative reinforcement and are saved with that mob's learned edge state.
- Suspend villagers' vanilla zombie-avoidance AI while attached in assisted mode; their previous AI state is restored on detach or observe mode.
- Add a monitor-local library for naming, saving, and applying up to 16 learned-synapse imprints to attached subjects. The shared biological connectome is not copied or modified.
- Sweep Neuro Fly movement against collision boxes and test nearby steering directions before moving, avoiding direct wall penetration; suppress automatic fall damage for the research fly.
- Emit a small, cooldown-limited negative reinforcement pulse when sustained forward intent makes no movement progress.
- Reduce continuous rain pain, and make feedback markers larger with a short, edge-triggered display window.
- Raise firing-neuron ID sampling to 65,536 and migrate legacy/default 25,600 settings while preserving other custom values.
- Keep Connectome explorer controls and anatomy within compact GUI viewports.

## 0.5.12 — Subject details and readable PC plots

- Add a full-width, scrollable subject details page from both the placed monitor and Ctrl+N dashboard; retain the mob roster, preview, and 3D brain on the overview.
- Fit only as many overview plots as the current GUI height permits, and start in details view on very short screens.
- Reflow the placed monitor's compact view so charts and readouts do not cover its action buttons; paginate expanded plot settings.
- Add time-series plots for outgoing edge visits, sound, pain, reward, satiety, hydration, and modified edges. Clarify that these are simulated/game-derived signals, not measured synaptic currents.
- Render up to 72,000 samples per subject with pixel-column min/max envelopes on the PC console, preserving brief peaks without drawing every historical sample each frame.
- Add regression checks for short-screen plot space and transient-preserving history reduction.

## 0.5.11 — Visible connectome, flight correction, and feedback

- Restore a sampled 3D Male CNS with anatomical synapse links on both the main dashboard and full Connectome explorer; sampled active neurons remain visually distinct from anatomical connections.
- Register the NeuroView HUD above the hotbar in its actual Forge HUD stack, and re-enable it when the camera enters a mob.
- Increase each mob's client-side scalar telemetry history from 720 to 72,000 samples; each visible plot now summarizes the complete retained interval.
- Measure flight height from the nearby floor and available headroom instead of the top of a roof; steer floor landings along a curved approach with body orientation following the path.
- Render a green `+` for reward and a red `-` for pain above tracked brain-attached mobs while the corresponding telemetry is active.
- Add regression coverage for roof-constrained flight, the landing curve, camera-entry HUD recovery, bounded connectome edge sampling, and expanded telemetry retention.

## 0.5.10 — Responsive monitor dashboard header

- Reflow the analysis dashboard's top controls on compact screens so Connectome and other actions no longer overlap the title or HUD.
- Add layout regression coverage across narrow, standard, and wide GUI sizes, including screens with and without an attached subject.

## 0.5.9 — More reliable flight, perching, and activity readouts

- Guide Neuro Fly through reachable, collision-free cover during rain; shelter steering overrides rain-stress escape noise without overriding immediate threats, and braking now stops only after the fly reaches the refuge.
- Keep wall perching disabled while exposed to rain and cancel an existing cling when weather or danger makes it unsafe.
- Use a damped landing controller for floor contact, shorten repeated idle-perch delays, and expose cruise, landing, wall-rest, and shelter state in `/neurolab inspect`.
- Label the 720-point chart history separately from spike/activity counts and sampled firing-neuron IDs; open the Connectome explorer at a higher-detail 24,000-point preset, with lower and higher choices still available.
- Add a rainy-world Forge GameTest for finding and occupying a roof shelter, plus regression coverage for the ground landing, wall perch, and shelter guidance controls.

## 0.5.8 — Per-subject natural drives and high-detail camera telemetry

- Raise the default active-neuron ID sample cap from 256 to 25,600 per 50 ms neural snapshot (100×); retain bounded configuration and document the network/log cost for 10–20 attached subjects.
- Migrate an unversioned existing config from the old default of 256 once, while preserving non-default configured limits.
- Keep only the latest full firing-ID array on clients; historical plot samples retain scalar metrics to prevent high-detail sampling from multiplying client memory use.
- Move large JSONL sample serialization to the writer thread and bound queued records to 128, preserving the dropped-record counter rather than allowing high-detail samples to exhaust server memory.
- Add independent per-mob satiety, hydration, rain-stress, and exploration state. Nearby flowers and tree blocks, water contact, and new 4×4 exploration cells provide bounded, cooldown-limited rewards; exposed rain and depleted needs provide pain input.
- Persist each subject's satiety, hydration, and rain stress with its entity data.
- Broaden the assisted looming cue for hostile mobs and players, and inspect threats out to eight blocks while retaining short-range, closing-only cues for passive mobs.
- Record active outgoing synapses before reward/pain arrives so recent activity can be reinforced or suppressed by the bounded eligibility-trace learning rule.
- Add a dedicated camera-subject telemetry stream so the NeuroView HUD continues receiving the selected mob's statistics, and show movement, firing, reward/pain, needs, learned edges, and real-time factor without opening a screen.
- Bump the main telemetry protocol and JSONL observation schema; add unit coverage for drive cooldowns, rain stress, water, exploration, delayed learning, expanded samples, and the mob-view HUD.

## 0.5.7 — Connectome anatomy and edge rendering

- Draw complete selected-neuron connection lines instead of truncating them after 96 pixels.
- Preserve relative X/Y/Z anatomical proportions with a shared projection scale.
- Invalidate projected points when changing filters, and avoid selecting neurons outside the visible viewport.
- Explain empty spatial filters directly in the viewer.
- Add projection normalization regression tests.

## 0.5.6 — Mob-camera HUD and grounded neural movement

- Preserve falling velocity for assisted land mobs and apply bounded gravity when neural control disables vanilla AI, preventing attached subjects from hovering.
- Render the Neuro View instrument in the top-right only while viewing an implanted mob, and only after its telemetry arrives.
- Render the Connectome explorer with an opaque background so pause-menu blur cannot obscure its controls or anatomy.
- Add Forge GameTests for gravity under assisted and neural control, plus client tests for HUD visibility conditions.

## 0.5.5 — Live connectome activity and expanded experiment controls

- Added configurable telemetry cadence and bounded active-neuron ID samples for richer analysis on capable systems.
- Restored Connectome access from the lab computer dashboard; active sampled neuron IDs are highlighted in the anatomical view, with rendering density up to 48,000 points.
- Added a direct VIEW MOB action, dashboard wheel/page scrolling, and return navigation from the Connectome explorer.
- Added JSONL schema 4 active-neuron and sound-level samples; bumped the telemetry wire protocol.
- Made assisted mob motion consistent while observing through the mob camera.
- Extended the petri-dish chair procedure to six seconds with a slower articulated robotic arm, visual arc/spark cues, and impact sound.
- Corrected Neuro Fly's unbounded climb, strengthened sensory-guided steering, and added sound/vibration sampling to the annotated mechanosensory projection.
- Enforced the assisted-flight altitude ceiling against the applied vertical movement step, including residual momentum.
- Added occasional floor landings and wall perching with position/orientation snap, synchronized folded-wing/leg pose, and startle release.

## 0.5.4 — Mob implant interactions and chair procedure

Minecraft 1.21.1 / Forge 52.1.16 / Java 21. Client and server should use the same mod version.

- Route Neuro Mind Imprinter use through a high-priority entity interaction handler so horse, villager, and other mob-specific right-click behaviors do not swallow the attachment action.
- Turn the experiment chair into a timed procedure: pull the nearest mob from within six blocks, restrain it for three seconds, animate a mechanical arm, then restore its previous neural control mode and AI state.
- Restore a subject if the chair is cleared or broken during the procedure.
- Add a chair pull-and-release Forge GameTest and make the GameTest verification independent of a hard-coded test count.
- Move the Neuro Monitor entry point to the main analysis dashboard, remove electrode/pulse controls, expand telemetry history, and show a waiting state in the Neuro View HUD while telemetry arrives.
- Prevent neural escape signals alone from triggering open-ground jumps for land mobs.

## 0.5.3 — Connectome rendering and lab interaction fixes

Minecraft 1.21.1 / Forge 52.1.16 / Java 21. Client and server should use the same mod version.

- Bound connectome rendering to its selected point budget, cache projections between frames, reduce connection-line work, and fix the empty "all neurons" filter.
- Make I a registered HUD keybind; show a compact neural activity instrument only while viewing a brain-attached mob.
- Make the Neuro Monitor graph default to live neuron activity, with vision and sensory/motor views available from the graph selector.
- Remove electrode cycling and sensory pulses from the experiment chair; keep it as a subject dock and move measurements/input to the monitor and probes.
- Add throttled forward-obstacle jumping for terrestrial mobs while preserving the single-jump latch.
- Verify sheep collision dimensions and vertical position across ticks, alongside the villager and Neuro Fly GameTests.

## 0.5.2 — Dashboard readability and subject selection

Minecraft 1.21.1 / Forge 52.1.16 / Java 21. Client and server should use the same mod version.

- Replace the translucent analysis backdrop with an opaque instrument canvas to prevent background blur from washing out panels.
- Add a scrollable dashboard roster with bounded click selection and regression tests for long lists.
- Add a request-scoped roster of every active attached mob in the player's current dimension, with latest telemetry and server-side rate limiting.
- Add a centered 3D subject preview, eye-ray input indicators, and an actual shortest directed visual-receptor-to-motor path from the bundled MaleCNS graph; the path is anatomical, not a live activation trace. Press V to switch to the selected nearby mob's camera.
- Split Neuro View into compact eye-input and neural-output HUD panels; use a single combined layout at narrow GUI widths.
- Document why the current global reward/pain weight adjustment remains exploratory rather than a biologically validated learning mechanism.

## 0.5.1 — Experiment chair and monitor targeting

Minecraft 1.21.1 / Forge 52.1.16 / Java 21. Client and server should use the same mod version.

- Replace the experiment pedestal with a direction-aware, padded neural procedure chair and overhead electrode frame; keep the existing block and entity registry IDs.
- Keep the chair's outline selectable while limiting physical collision to its base so attached mobs can still move normally.
- Remove the floating brain schematic and labels above mobs.
- Bind the monitor display and subject preview strictly to the selected subject; remove the fallback that could show an unrelated mob's telemetry.
- Update the monitor's idle display and remove the stale version label.
- Apply Neuro Fly's vertical lift in the same server-side movement call so entity AI cannot overwrite it before motion occurs; verify vertical displacement and the live one-block collision box in Forge GameTests.
- Add visual, tactile, and olfactory stimulus probes. Right-click a brain-attached mob to send a bounded 0.75 pulse for 40 ticks through its sensory input path.

## 0.5.0 — Brain attachment and steadier mob control

Minecraft 1.21.1 / Forge 52.1.16 / Java 21. Client and server should use the same mod version.

- Replace the two-step mind-imprint tool interaction with direct right-click connectome attachment; retain its registry ID for existing worlds.
- Replace the Petri Dish appearance and naming with a raised experiment stand, retaining its saved block/entity IDs for compatibility.
- Prevent sustained escape activity from repeatedly triggering jumps; non-flying mobs jump only on a fresh escape edge with a cooldown.
- Bound assisted horizontal velocity and stop adding neural movement while the mob is the player's camera, preventing view mode from accelerating the subject.
- Keep ordinary mobs' vanilla navigation and AI active in assisted mode.
- Redraw the overhead brain as a readable bilateral connection schematic, label it with the actual mob name, and avoid presenting it as anatomical synapse coordinates.
- Add an in-dashboard HUD toggle as a fallback to the I key and update English/Turkish instructions.
- Size the Neuro Fly to an exact one-block hitbox and center its model origin vertically and horizontally in that volume.
- Correct GameTest connectome-load timing and add jump-gate regression tests.

## 0.4.9 — Clearer monitor controls and subject view

Minecraft 1.21.1 / Forge 52.1.16 / Java 21. Client and server should use the same mod version.

- Replace cramped abbreviations with larger, two-row Turkish/English action buttons and on-screen instructions for every control.
- Make ESC, Backspace, and the CLOSE button reliably leave the non-pausing console.
- Remove duplicate handling of the HUD key so its configured key mapping toggles once and reports the new state.
- Add a centered 3D subject preview with a one-block reference frame and a MOB VIEW action that switches to the subject camera; V returns to the player.
- Recenter the Neuro Fly model origin so the body sits inside its one-block-wide wing reference instead of appearing shifted.
- Keep display normalization visual-only; the entity hitbox and gameplay size are unchanged.

## 0.4.8 — Instrumented monitor console

Minecraft 1.21.1 / Forge 52.1.16 / Java 21. Client and server should use the same mod version.

- Open a real lab console by right-clicking the Neuro Monitor block.
- Bind a nearby mob or reuse the subject already linked to a nearby Petri Dish; persist the subject UUID and selected electrode with the monitor and synchronize its display state to clients.
- Add live eye, touch, and motor-forward plots with spikes, active neurons, synaptic events, sensory values, motor outputs, real-time factor, and control mode.
- Add server-validated controls for six sensory electrodes, bounded pulses, assisted/neural/observe mode changes, trial ending, and safe detach.
- Keep the GUI non-pausing; all experiment actions run through validated serverbound packets.
- Update the in-game English and Turkish guide and install/use documentation.

## 0.4.7 — Research reliability

Minecraft 1.21.1 / Forge 52.1.16 / Java 21. Client and server should use the same mod version.

- Fix sparse neuron scheduling: independent per-substep sets retain entries during growth and prevent duplicate integration. Keep refractory neurons scheduled and ignore sensory triggers during their refractory interval.
- Include `oDN1` in the population accumulator already used by the forward decoder.
- Transfer pending mind copies atomically and clear previous eligibility traces when replacing an imprint.
- Mark Neuro Fly entities persistent so ordinary distance-based despawning cannot remove research subjects.
- Keep vanilla AI and pathfinding active for ordinary mobs in assisted mode; add bounded neural steering without replacing their movement tick. Neural-only remains the explicit full-control condition.
- Add an interactive Petri Dish with a saved subject UUID, six rotating visual/touch/odor/taste electrode contacts, 0.55-strength pulses, and a named JSONL trial.
- Show the live top-right telemetry HUD for a nearby tracked brain, and render a rotating full-bright 3D wireframe brain with signal flow above the mob.
- Pin ForgeGradle and put development classes/resources in one mod root; verify GameTest completion rather than relying only on the launcher exit code.
- Add versioned telemetry sessions, neural tick IDs, and synaptic-event counts. Flush small recordings while the world runs, isolate queues between worlds, and expose logging failures/drop counts in `/neurolab status`.
- Add an offline HTML/SVG and CSV analysis tool with separate trial/condition groups, repeated-snapshot handling, and incomplete-log warnings.
- Add sparse-scheduling, learning, trial-boundary, logging, report, and Forge world regression checks.
- Document research references, model limits, and a repeatable recording protocol. Publish only the installable mod JAR as a release asset.

Simulation outputs can change because the integration bug was fixed. Treat earlier recordings as a different model revision. Automated tests do not replace a client/LAN play test or prove biological fidelity.
