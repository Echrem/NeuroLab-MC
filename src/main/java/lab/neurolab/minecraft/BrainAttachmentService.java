package lab.neurolab.minecraft;

import lab.neurolab.brain.BrainWorker;
import lab.neurolab.brain.ConnectomeData;
import lab.neurolab.brain.FlyBrain;
import lab.neurolab.brain.EmbodimentDecoder;
import lab.neurolab.brain.FlyNavigation;
import lab.neurolab.brain.FlyFlightStyle;
import lab.neurolab.brain.ExperienceNavigation;
import lab.neurolab.brain.PlayerThreatMemory;
import lab.neurolab.brain.BlockOutcomeMemory;
import lab.neurolab.entity.NeuroFlyEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import lab.neurolab.brain.ControlMode;
import lab.neurolab.brain.StimulusSchedule;
import lab.neurolab.brain.StimulusSchedule.Sense;
import lab.neurolab.brain.TrialSession;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Server-thread-owned bindings between Minecraft mobs and independent neural workers. */
public final class BrainAttachmentService {
    private static final String LEARNED_SYNAPSES_TAG = "NeuroLabLearnedSynapses";
    private static final String SATIETY_TAG = "NeuroLabSatiety";
    private static final String HYDRATION_TAG = "NeuroLabHydration";
    private static final String RAIN_STRESS_TAG = "NeuroLabRainStress";
    private static final String REWARDED_FLOWERS_TAG = "NeuroLabRewardedFlowers";
    private static final String REWARDED_TREES_TAG = "NeuroLabRewardedTrees";
    private static final String EXPLORED_CELLS_TAG = "NeuroLabExploredCells";
    private static final String LEARNED_CUES_TAG = "NeuroLabLearnedCues";
    private static final String THREAT_PLAYER_TAG = "NeuroLabThreatPlayer";
    private static final String THREAT_HITS_TAG = "NeuroLabThreatHits";
    private static final String BLOCK_OUTCOMES_TAG = "NeuroLabBlockOutcomes";
    private static final Map<UUID, Attachment> ATTACHED = new ConcurrentHashMap<>();
    private static final Map<UUID, Mob> AUTO_ATTACH_PENDING = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedDeque<SoundPulse> SOUND_PULSES = new ConcurrentLinkedDeque<>();
    private static volatile CompletableFuture<ConnectomeData> data;
    private static volatile CompletableFuture<java.util.Optional<lab.neurolab.brain.StructuralPathway.Path>> pathway;

    static { startup(); }

    private BrainAttachmentService() {}

    public static synchronized void startup() {
        if (data == null || data.isCompletedExceptionally()) {
            data = CompletableFuture.supplyAsync(() -> {
                try { return ConnectomeData.readBundled(); }
                catch (IOException e) { throw new IllegalStateException("Could not load male-cns connectome", e); }
            });
        }
    }

    public static boolean attach(Mob mob) {
        if (attachFailure(mob) != null) return false;
        BrainWorker worker = new BrainWorker(data.join(), mob.getPersistentData().getLongArray(LEARNED_SYNAPSES_TAG));
        Attachment state = new Attachment(mob, worker, mob.isNoAi());
        mob.setNoAi(noAiFor(mob, state));
        if (mob.isNoAi()) mob.getNavigation().stop();
        ATTACHED.put(mob.getUUID(), state);
        return true;
    }

    public static String attachFailure(Mob mob) {
        if (!(mob.level() instanceof ServerLevel)) return "Mob must be in a server-side world.";
        if (ATTACHED.containsKey(mob.getUUID())) return "This mob already has a NeuroLab brain attached.";
        if (!data.isDone()) return "Connectome is still loading; try again in a moment.";
        if (data.isCompletedExceptionally()) return "Connectome failed to load; check latest.log for the NeuroLab error.";
        return null;
    }

    public static boolean isAttached(Mob mob) { return ATTACHED.containsKey(mob.getUUID()); }

    /** Records an applied player damage event, not merely an attempted swing. */
    public static void recordPlayerAttack(Mob subject, Player attacker) {
        if (!(subject.level() instanceof ServerLevel) || attacker == null) return;
        var persistent = subject.getPersistentData();
        UUID previous = persistent.hasUUID(THREAT_PLAYER_TAG) ? persistent.getUUID(THREAT_PLAYER_TAG) : null;
        PlayerThreatMemory memory = new PlayerThreatMemory(previous, persistent.getInt(THREAT_HITS_TAG));
        int hits = memory.recordHit(attacker.getUUID());
        persistent.putUUID(THREAT_PLAYER_TAG, attacker.getUUID());
        persistent.putInt(THREAT_HITS_TAG, hits);
        Attachment attachment = ATTACHED.get(subject.getUUID());
        if (attachment != null) {
            if (previous != null && !previous.equals(attacker.getUUID()))
                attachment.navigation.clear(ExperienceNavigation.Cue.PLAYER);
            attachment.playerThreat = memory;
            attachment.navigation.observe(ExperienceNavigation.Cue.PLAYER, 0, 0.55f);
            persistState(attachment);
        }
    }

    public static void recordEnvironmentalDamage(Mob subject) {
        if (!(subject.level() instanceof ServerLevel level)) return;
        Attachment attachment = ATTACHED.get(subject.getUUID());
        if (attachment == null) return;
        BlockPos contact = contactHazard(subject, level);
        if (contact == null) return;
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(contact).getBlock());
        attachment.blockOutcomes.observe(key, 0, 0.7f);
        persistState(attachment);
    }

    public static void recordSound(Level level, Vec3 position, float volume, float pitch) {
        if (level.isClientSide || !Float.isFinite(volume) || volume < 0.03f) return;
        SOUND_PULSES.addLast(new SoundPulse(level.dimension().location(), position,
                Math.min(4, volume), Math.max(0.5f, Math.min(2, pitch)), level.getGameTime()));
        while (SOUND_PULSES.size() > 512) SOUND_PULSES.pollFirst();
    }

    public static void updateTelemetry(Mob mob, BrainTelemetryPayload sample) {
        Attachment attachment = ATTACHED.get(mob.getUUID());
        if (attachment != null) attachment.latestTelemetry = sample;
    }

    public static java.util.List<BrainRosterPayload.Entry> roster(ServerLevel level) {
        return ATTACHED.values().stream()
                .filter(attachment -> !attachment.mob.isRemoved() && attachment.mob.level() == level)
                .sorted(java.util.Comparator.comparingInt(attachment -> attachment.mob.getId()))
                .limit(BrainRosterPayload.MAX_ENTRIES)
                .map(attachment -> {
                    Mob mob = attachment.mob;
                    return new BrainRosterPayload.Entry(mob.getId(), mob.getName().getString(),
                            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(),
                            mob.getX(), mob.getY(), mob.getZ(), attachment.latestTelemetry);
                }).toList();
    }

    public static BrainRosterPayload rosterPayload(ServerLevel level) {
        CompletableFuture<java.util.Optional<lab.neurolab.brain.StructuralPathway.Path>> pathFuture = pathwayFuture();
        java.util.List<lab.neurolab.brain.StructuralPathway.Step> path = pathFuture.isDone()
                && !pathFuture.isCompletedExceptionally() ? pathFuture.join().map(
                        lab.neurolab.brain.StructuralPathway.Path::steps).orElse(java.util.List.of())
                : java.util.List.of();
        return new BrainRosterPayload(roster(level), path);
    }

    private static synchronized CompletableFuture<java.util.Optional<lab.neurolab.brain.StructuralPathway.Path>>
    pathwayFuture() {
        if (pathway == null) pathway = data.thenApplyAsync(
                lab.neurolab.brain.StructuralPathway::shortestVisualToMotor);
        return pathway;
    }

    public static long[] copyMindSnapshot(Mob mob) {
        Attachment attachment = ATTACHED.get(mob.getUUID());
        return attachment == null
                ? mob.getPersistentData().getLongArray(LEARNED_SYNAPSES_TAG).clone()
                : attachment.worker.learnedSynapses();
    }

    public static SynapseInspectionPayload inspectSynapses(Mob mob, int neuronId) {
        Attachment attachment = ATTACHED.get(mob.getUUID());
        if (attachment == null || data == null || !data.isDone() || data.isCompletedExceptionally()) return null;
        ConnectomeData graph = data.join();
        if (neuronId < -1 || neuronId >= graph.neurons()) return null;
        long[] learned = attachment.worker.learnedSynapses();
        java.util.List<SynapseInspectionPayload.Edge> rows = new java.util.ArrayList<>();
        int start = neuronId < 0 ? 0 : graph.rowOffsets[neuronId];
        int end = neuronId < 0 ? graph.connections() : graph.rowOffsets[neuronId + 1];
        for (long state : learned) {
            int edge = (int) (state >>> 32);
            if (edge < start || edge >= end) continue;
            float change = Float.intBitsToFloat((int) state);
            if (!Float.isFinite(change) || edge < 0 || edge >= graph.connections()) continue;
            int source = neuronId >= 0 ? neuronId : sourceForEdge(graph.rowOffsets, edge);
            rows.add(new SynapseInspectionPayload.Edge(edge, source, graph.targets[edge],
                    Short.toUnsignedInt(graph.connectionWeights[edge]), graph.transmitterSigns[source], change));
        }
        rows.sort(java.util.Comparator.comparingDouble(
                (SynapseInspectionPayload.Edge edge) -> Math.abs(edge.change())).reversed());
        var strongestBlockCue = attachment.blockOutcomes.strongest();
        return new SynapseInspectionPayload(mob.getId(), neuronId, attachment.worker.learningRevision(),
                learned.length, attachment.navigation.preference(ExperienceNavigation.Cue.FLOWER),
                attachment.navigation.preference(ExperienceNavigation.Cue.WATER),
                attachment.navigation.preference(ExperienceNavigation.Cue.FIRE),
                attachment.navigation.preference(ExperienceNavigation.Cue.ZOMBIE),
                attachment.navigation.preference(ExperienceNavigation.Cue.VILLAGER),
                attachment.playerThreat.damagingHits(),
                attachment.navigation.preference(ExperienceNavigation.Cue.PLAYER),
                strongestBlockCue == null ? "" : strongestBlockCue.getKey(),
                strongestBlockCue == null ? 0 : strongestBlockCue.getValue(),
                attachment.navigation.rememberedBlockedCells(), rows);
    }

    private static int sourceForEdge(int[] rows, int edge) {
        int low = 0, high = rows.length - 2;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (rows[middle] > edge) high = middle - 1;
            else if (rows[middle + 1] <= edge) low = middle + 1;
            else return middle;
        }
        throw new IllegalArgumentException("Edge outside connectome");
    }

    /** Apply a portable learned-synapse imprint and attach the shared connectome when needed. */
    public static String applyMindCopy(Mob mob, long[] state) {
        if (state.length > 8192) return "Mind imprint exceeds the supported sparse-synapse limit.";
        if (!(mob.level() instanceof ServerLevel)) return "Choose a mob in a server-side world.";
        Attachment attachment = ATTACHED.get(mob.getUUID());
        if (attachment != null) {
            attachment.worker.copyLearnedSynapses(state);
            mob.getPersistentData().putLongArray(LEARNED_SYNAPSES_TAG, state.clone());
            return null;
        }
        String failure = attachFailure(mob);
        if (failure != null) return failure;
        if (!attach(mob)) return "Could not attach the copied connectome to this mob.";
        mob.getPersistentData().putLongArray(LEARNED_SYNAPSES_TAG, state.clone());
        Attachment attached = ATTACHED.get(mob.getUUID());
        if (attached != null) attached.worker.copyLearnedSynapses(state);
        return null;
    }

    public static String stimulate(Mob mob, String senseName, float strength, int durationTicks) {
        Attachment attachment = ATTACHED.get(mob.getUUID());
        if (attachment == null) return "Attach a brain to this mob before stimulating it.";
        if (!Float.isFinite(strength) || strength < 0 || strength > 1)
            return "Stimulus strength must be between 0 and 1.";
        if (durationTicks < 1 || durationTicks > 1200)
            return "Stimulus duration must be between 1 and 1200 ticks.";
        Sense sense;
        try { sense = Sense.valueOf(senseName.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) {
            return "Unknown sense. Use eye, left_eye, right_eye, looming, touch, odor, taste, or sound.";
        }
        attachment.stimuli.start(sense, strength, durationTicks, mob.level().getGameTime());
        TelemetryLog.stimulusEvent(mob, attachment.mode, sense.name().toLowerCase(Locale.ROOT),
                strength, durationTicks);
        return null;
    }

    public static void queueAutoAttach(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof NeuroFlyEntity fly && fly.level() instanceof ServerLevel)
            AUTO_ATTACH_PENDING.putIfAbsent(fly.getUUID(), fly);
    }

    public static boolean detach(Mob mob) {
        AUTO_ATTACH_PENDING.remove(mob.getUUID());
        Attachment a = ATTACHED.remove(mob.getUUID());
        if (a == null) return false;
        if (a.perchedTicks > 0) mob.stopRiding();
        if (mob instanceof NeuroFlyEntity fly) fly.setPerched(false);
        persistState(a);
        a.worker.close();
        TrialSession.Snapshot trial = a.trial.end();
        if (trial != null) TelemetryLog.trialEvent("trial_end", mob, a.mode, trial, mob.level().getGameTime());
        mob.setNoAi(a.originalNoAi);
        return true;
    }

    public static boolean killSubject(Mob mob) {
        if (mob == null || !mob.isAlive()) return false;
        endTrial(mob);
        detach(mob);
        mob.kill();
        return true;
    }

    public static void tick() {
        if (data.isDone() && !data.isCompletedExceptionally()) {
            for (var entry : AUTO_ATTACH_PENDING.entrySet()) {
                Mob fly = entry.getValue();
                if (fly.isRemoved() || attach(fly) || ATTACHED.containsKey(entry.getKey()))
                    AUTO_ATTACH_PENDING.remove(entry.getKey(), fly);
            }
        }
        for (Attachment a : ATTACHED.values()) {
            Mob mob = a.mob;
            if (mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
                persistState(a);
                mob.setNoAi(a.originalNoAi);
                a.worker.close(); ATTACHED.remove(mob.getUUID(), a); continue;
            }
            if (a.worker.failure() != null) {
                java.util.logging.Logger.getLogger("neurolab").log(java.util.logging.Level.SEVERE,
                        "Brain failed for entity " + mob.getId() + "; restoring its AI", a.worker.failure());
                detach(mob);
                continue;
            }
            WorldSample world = sample(mob, level, a);
            FlyBrain.Drive senses = a.stimuli.apply(world.senses(), level.getGameTime());
            a.worker.setFiringNeuronSampleLimit(NeuroFlyConfig.ACTIVE_NEURON_SAMPLE_LIMIT.get());
            a.worker.accept(senses);
            FlyBrain.Snapshot neural = a.worker.snapshot();
            EmbodimentDecoder.Command body = EmbodimentDecoder.decode(neural, senses, mob.tickCount,
                    mob.getUUID().getLeastSignificantBits(), a.mode, world.cue(),
                    !(mob instanceof net.minecraft.world.entity.npc.Villager && world.zombieNearby()
                            && a.navigation.preference(ExperienceNavigation.Cue.ZOMBIE) >= -0.04f));
            a.lastWorldCue = world.cue();
            a.lastBodyCommand = body;
            long gameTick = level.getGameTime();
            if (gameTick % 200 == 0) persistState(a);
            TrialSession.Snapshot endedTrial = a.trial.expire(gameTick);
            if (endedTrial != null) TelemetryLog.trialEvent("trial_end", mob, a.mode,
                    endedTrial, endedTrial.endsAtTick());
            if (mob.tickCount % NeuroFlyConfig.TELEMETRY_INTERVAL_TICKS.get() == 0) {
                TrialSession.Snapshot trial = a.trial.current(gameTick);
                NeuroLabMod.sendTelemetry(mob, neural, senses, body,
                        a.stimuli.activeCount(gameTick) > 0, a.mode, trial,
                        a.naturalDrive.satiety(), a.naturalDrive.hydration(),
                        a.worker.learnedSynapseCount(), a.worker.learningRevision());
            }
            if (!a.mode.controlsBody()) {
                if (a.perchedTicks > 0) {
                    mob.stopRiding();
                    a.perchedTicks = 0;
                }
                continue;
            }
            boolean neuroFly = mob instanceof NeuroFlyEntity;
            BlockPos eyeBlock = BlockPos.containing(mob.getEyePosition());
            boolean exposedToRain = level.isRainingAt(eyeBlock) && level.canSeeSky(eyeBlock);
            boolean threatening = senses.looming() > 0.28f || senses.pain() > 0.28f;
            boolean shelterPriority = neuroFly && a.mode == ControlMode.ASSISTED && world.rainSeeking()
                    && world.shelterTarget() != null && world.cue().threatStrength() <= 0.05
                    && mob.hurtTime == 0 && !mob.isOnFire() && !mob.isInLava();
            if (neuroFly && a.mode != ControlMode.ASSISTED) {
                a.wallClingTicks = 0;
                a.surfacePerchTicks = 0;
                a.landingApproachTicks = 0;
                a.landingOrigin = a.landingTarget = null;
                a.wallAnchor = a.surfacePerchAnchor = null;
                ((NeuroFlyEntity) mob).setPerched(false);
            }
            if (neuroFly && a.perchedTicks > 0) {
                if (a.mode != ControlMode.ASSISTED || threatening || exposedToRain
                        || mob.getVehicle() == null || !mob.getVehicle().isAlive()) {
                    mob.stopRiding();
                    a.perchedTicks = 0;
                    a.perchCooldownUntil = gameTick + 100;
                    mob.setDeltaMovement(0, Math.max(0.12, mob.getDeltaMovement().y), 0);
                } else {
                    a.perchedTicks--;
                    if (a.perchedTicks == 0) {
                        mob.stopRiding();
                        a.perchCooldownUntil = gameTick + 900;
                        mob.setDeltaMovement(0, 0.16, 0);
                    }
                    continue;
                }
            }
            if (neuroFly && a.surfacePerchTicks > 0) {
                if (threatening || world.senses().sound() > 0.65f || exposedToRain) {
                    a.surfacePerchTicks = 0;
                    a.surfacePerchAnchor = null;
                    ((NeuroFlyEntity) mob).setPerched(false);
                } else {
                    mob.setPos(a.surfacePerchAnchor);
                    mob.setDeltaMovement(Vec3.ZERO);
                    ((NeuroFlyEntity) mob).setPerched(true);
                    if (--a.surfacePerchTicks == 0) {
                        a.surfacePerchAnchor = null;
                        ((NeuroFlyEntity) mob).setPerched(false);
                        a.nextSurfacePerchTick = gameTick + 200;
                    }
                    continue;
                }
            }
            if (neuroFly && a.mode == ControlMode.ASSISTED && world.hostDistance() <= 1.25
                    && FlyNavigation.mayPerch(NeuroFlyConfig.PERCH_ON_ANIMALS_AND_VILLAGERS.get(),
                            threatening, exposedToRain, gameTick < a.perchCooldownUntil, world.hostDistance())
                    && gameTick >= a.nextPerchOpportunityTick && world.perchHost() != null
                    && mob.startRiding(world.perchHost(), true)) {
                a.landingApproachTicks = 0;
                a.landingOrigin = a.landingTarget = null;
                a.perchedTicks = NeuroFlyConfig.PERCH_TICKS.get();
                a.nextPerchOpportunityTick = gameTick + 900
                        + Math.floorMod((int) (mob.getUUID().getMostSignificantBits() ^ gameTick), 900);
                continue;
            }
            if (neuroFly && a.wallClingTicks > 0) {
                if (!FlyNavigation.mayClingToWall(threatening || world.senses().sound() > 0.65f,
                        exposedToRain)) {
                    a.wallClingTicks = 0;
                    a.wallAnchor = null;
                    ((NeuroFlyEntity) mob).setPerched(false);
                } else {
                    mob.setPos(a.wallAnchor);
                    mob.setYRot(a.wallYaw);
                    mob.yBodyRot = a.wallYaw;
                    mob.setDeltaMovement(Vec3.ZERO);
                    ((NeuroFlyEntity) mob).setPerched(true);
                    if (--a.wallClingTicks == 0) {
                        a.wallAnchor = null;
                        ((NeuroFlyEntity) mob).setPerched(false);
                        a.wallClingCooldownUntil = gameTick + 100;
                    }
                    continue;
                }
            }
            if (neuroFly && a.mode == ControlMode.ASSISTED && a.wallClingTicks == 0
                    && a.wallClingCooldownUntil <= gameTick && senses.looming() < 0.25f
                    && FlyNavigation.mayClingToWall(senses.pain() >= 0.2f, exposedToRain)
                    && beginWallPerch((NeuroFlyEntity) mob, level, a, gameTick)) {
                continue;
            }
            double turn = body.turn();
            if (neuroFly && a.mode == ControlMode.ASSISTED && body.escape() && !shelterPriority) {
                turn = Math.max(-1, Math.min(1, turn + world.cue().threatTurn() * 0.8));
            } else if (shelterPriority) {
                turn = FlyNavigation.shelterTurn(world.cue().targetTurn(),
                        world.cue().avoidanceTurn(), world.cue().obstacle());
            }
            FlyFlightStyle.Motion flightStyle = neuroFly && a.mode == ControlMode.ASSISTED
                    ? FlyFlightStyle.sample(mob.getUUID().getLeastSignificantBits(), gameTick)
                    : new FlyFlightStyle.Motion(0, 1, 0);
            if (neuroFly && a.mode == ControlMode.ASSISTED && !body.escape() && !shelterPriority) {
                double cueWeight = Math.max(0.18, 1.0 - world.cue().targetStrength() * 0.82);
                turn = Math.max(-1, Math.min(1, turn + flightStyle.turn() * cueWeight));
            }
            if (!neuroFly && a.mode == ControlMode.ASSISTED) {
                mob.setYRot(mob.getYRot() + (float) (turn * 2.5));
                Vec3 current = mob.getDeltaMovement();
                var movementAttribute = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
                double movementSpeed = mob.getAttribute(movementAttribute) == null
                        ? 0 : Math.max(0, mob.getAttributeValue(movementAttribute));
                double yaw = Math.toRadians(mob.getYRot());
                double nudge = body.forward() * movementSpeed
                        * (mob instanceof net.minecraft.world.entity.npc.Villager ? 0.35 : 0.08);
                Vec3 horizontal = new Vec3(current.x - Math.sin(yaw) * nudge, 0,
                        current.z + Math.cos(yaw) * nudge);
                double maxSpeed = movementSpeed * 1.15;
                if (horizontal.horizontalDistance() > maxSpeed && maxSpeed > 0)
                    horizontal = horizontal.normalize().scale(maxSpeed);
                double jump = 0;
                if (a.jumpGate.shouldJump(body.escape() && mob.horizontalCollision, mob.horizontalCollision,
                        body.forward(), mob.onGround(), gameTick)) {
                    var jumpAttribute = net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH;
                    jump = (mob.getAttribute(jumpAttribute) == null ? 0.42
                            : mob.getAttributeValue(jumpAttribute)) * 0.8;
                }
                // Do not clamp negative Y velocity to zero: vanilla gravity must remain active for land mobs.
                double vertical = jump > 0 ? Math.max(current.y, jump) : current.y;
                if (mob.isNoAi()) {
                    moveWithoutVanillaAi(mob, a, horizontal.x, vertical, horizontal.z);
                } else {
                    mob.setDeltaMovement(horizontal.x, vertical, horizontal.z);
                    mob.hasImpulse = true;
                }
                continue;
            }
            mob.setYRot(mob.getYRot() + (float) (turn * (neuroFly ? 14 : 6)));
            mob.yBodyRot = mob.getYRot();
            Vec3 velocity = mob.getDeltaMovement();
            var flyingAttribute = net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED;
            boolean canFly = mob instanceof net.minecraft.world.entity.FlyingMob
                    || mob instanceof NeuroFlyEntity
                    || mob instanceof net.minecraft.world.entity.animal.Bee
                    || mob instanceof net.minecraft.world.entity.monster.Ghast
                    || mob instanceof net.minecraft.world.entity.monster.Phantom
                    || mob.getAttribute(flyingAttribute) != null;
            double movement = mob.getAttributeValue(canFly && mob.getAttribute(flyingAttribute) != null
                    ? flyingAttribute : net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            double throttle = Math.max(0, Math.min(1, body.forward()));
            if (neuroFly && a.mode == ControlMode.ASSISTED) {
                if (shelterPriority) throttle = FlyNavigation.shelterThrottle(0.48,
                        world.cue().targetDistance(), world.cue().targetStrength());
                else if (body.escape()) throttle = 1.0;
                else if (world.cue().targetStrength() <= 0.05)
                    throttle = Math.max(throttle, NeuroFlyConfig.CRUISE_THROTTLE.get());
                if (!shelterPriority && !body.escape()) throttle *= flightStyle.speedScale();
            }
            double forward = throttle * Math.max(0, movement);
            double newYaw = Math.toRadians(mob.getYRot());
            double vx = -Math.sin(newYaw) * forward;
            double vz = Math.cos(newYaw) * forward;
            double vy = velocity.y;
            double neuroFlyGroundY = 0;
            double neuroFlyOverheadY = Double.POSITIVE_INFINITY;
            if (canFly) {
                double maxVerticalSpeed = Math.max(0, movement * (neuroFly ? 1.0 : 0.75));
                if (neuroFly && a.mode == ControlMode.ASSISTED)
                    maxVerticalSpeed = Math.min(maxVerticalSpeed, 0.24);
                double liftCommand = Math.max(0, Math.min(1, body.lift()));
                if (mob instanceof NeuroFlyEntity && a.mode == ControlMode.ASSISTED) {
                    double floor = nearbyFloorY(level, mob, mob.getX(), mob.getZ());
                    double ground = Double.isFinite(floor) ? floor
                            : mob.getY() - NeuroFlyConfig.CRUISE_ALTITUDE.get();
                    double overhead = nearbyCeilingY(level, mob);
                    neuroFlyGroundY = ground;
                    neuroFlyOverheadY = overhead;
                    if (a.landingApproachTicks == 0 && gameTick >= a.nextSurfacePerchTick
                            && Double.isFinite(floor) && !threatening && !exposedToRain) {
                        beginGroundLanding((NeuroFlyEntity) mob, level, a, gameTick, floor);
                    }
                    if (a.landingApproachTicks > 0) {
                        Vec3 landing = a.landingTarget;
                        if (threatening || exposedToRain || landing == null) {
                            a.landingApproachTicks = 0;
                            a.landingOrigin = a.landingTarget = null;
                            a.nextSurfacePerchTick = gameTick + 20;
                        } else {
                            Vec3 shift = landing.subtract(mob.position());
                            if (mob.getY() <= landing.y + 0.10 && shift.horizontalDistanceSqr() <= 0.04
                                    && level.noCollision(mob, mob.getBoundingBox().move(shift))) {
                                mob.setPos(landing);
                                mob.setDeltaMovement(Vec3.ZERO);
                                a.landingApproachTicks = 0;
                                a.landingOrigin = a.landingTarget = null;
                                a.surfacePerchTicks = 200;
                                a.surfacePerchAnchor = landing;
                                ((NeuroFlyEntity) mob).setPerched(true);
                                continue;
                            }
                            double progress = 1.0 - a.landingApproachTicks / 80.0;
                            double eased = FlyNavigation.landingEase(progress);
                            Vec3 from = a.landingOrigin;
                            Vec3 horizontalPath = new Vec3(landing.x - from.x, 0, landing.z - from.z);
                            Vec3 sideways = new Vec3(-horizontalPath.z, 0, horizontalPath.x).normalize();
                            double arc = FlyNavigation.landingSideOffset(progress) * a.landingTurnSign;
                            double desiredX = from.x + (landing.x - from.x) * eased + sideways.x * arc;
                            double desiredZ = from.z + (landing.z - from.z) * eased + sideways.z * arc;
                            double dx = desiredX - mob.getX(), dz = desiredZ - mob.getZ();
                            double distance = Math.hypot(dx, dz);
                            double step = Math.min(0.14, movement * 0.7);
                            if (distance > step && step > 0) { dx *= step / distance; dz *= step / distance; }
                            vx = dx;
                            vz = dz;
                            if (distance > 0.01) {
                                float desiredYaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
                                float yawStep = net.minecraft.util.Mth.clamp(
                                        net.minecraft.util.Mth.wrapDegrees(desiredYaw - mob.getYRot()), -8, 8);
                                mob.setYRot(mob.getYRot() + yawStep);
                                mob.yBodyRot = mob.getYRot();
                            }
                            double desiredY = from.y + (landing.y - from.y) * eased;
                            liftCommand = FlyNavigation.landingLiftCommand(mob.getY(), desiredY, velocity.y);
                            if (--a.landingApproachTicks == 0) {
                                a.landingOrigin = a.landingTarget = null;
                                a.nextSurfacePerchTick = gameTick + 40;
                            }
                        }
                    } else {
                        double clearanceAgl = Double.isFinite(overhead)
                                ? Math.max(0, overhead - ground - mob.getBbHeight() - 0.12)
                                : NeuroFlyConfig.CRUISE_ALTITUDE.get();
                        liftCommand = FlyNavigation.altitudeCommand(mob.getY(), ground,
                                Math.min(NeuroFlyConfig.CRUISE_ALTITUDE.get(), clearanceAgl),
                                NeuroFlyConfig.ALTITUDE_CEILING.get(), body.lift());
                    }
                    if (a.landingApproachTicks == 0 && world.rainSeeking() && world.shelterTarget() != null) {
                        liftCommand = FlyNavigation.shelterLiftCommand(mob.getY(),
                                world.shelterTarget().getY(), velocity.y);
                    }
                } else if (mob instanceof net.minecraft.world.entity.animal.Bee
                        && a.mode == ControlMode.ASSISTED) {
                    double floor = nearbyFloorY(level, mob, mob.getX(), mob.getZ());
                    if (Double.isFinite(floor))
                        liftCommand = FlyNavigation.altitudeCommand(mob.getY(), floor, 1.8, 3.5, body.lift());
                }
                if (neuroFly && a.mode == ControlMode.ASSISTED && !shelterPriority
                        && a.landingApproachTicks == 0 && !body.escape()) {
                    liftCommand = Math.max(-0.45, Math.min(0.45, liftCommand + flightStyle.lift()));
                }
                double lift = liftCommand * maxVerticalSpeed;
                vy = Math.max(-maxVerticalSpeed, Math.min(maxVerticalSpeed, velocity.y * 0.8 + lift));
                if (neuroFly && a.mode == ControlMode.ASSISTED) {
                    vy = FlyNavigation.capAscentAtCeiling(mob.getY(), neuroFlyGroundY,
                            NeuroFlyConfig.ALTITUDE_CEILING.get(), vy);
                    if (Double.isFinite(neuroFlyOverheadY) && vy > 0)
                        vy = Math.min(vy, Math.max(0, neuroFlyOverheadY - mob.getBoundingBox().maxY - 0.06));
                }
            } else if (a.jumpGate.shouldJump(body.escape() && mob.horizontalCollision, mob.horizontalCollision,
                    body.forward(), mob.onGround(), gameTick)) {
                var jumpAttribute = net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH;
                double jumpStrength = mob.getAttribute(jumpAttribute) == null ? 0.42
                        : mob.getAttributeValue(jumpAttribute);
                vy = Math.max(velocity.y, jumpStrength);
            }
            if (neuroFly) {
                // This service runs after the entity tick; apply lift in the same movement call or
                // the next AI tick can replace the queued vertical velocity before it is consumed.
                Vec3 positionBeforeMove = mob.position();
                if (a.mode == ControlMode.ASSISTED) {
                    double maxAcceleration = Math.max(0.025, movement * 0.20);
                    vx = FlyNavigation.approachVelocity(a.flightVelocityX, vx, maxAcceleration);
                    vz = FlyNavigation.approachVelocity(a.flightVelocityZ, vz, maxAcceleration);
                }
                Vec3 requestedMotion = new Vec3(vx, vy, vz);
                Vec3 safeMotion = safeNeuroFlyMotion(level, mob, vx, vy, vz);
                boolean collisionReroute = safeMotion.subtract(requestedMotion).horizontalDistanceSqr() > 1.0e-4;
                if (collisionReroute && safeMotion.horizontalDistanceSqr() > 1.0e-6) {
                    float pathYaw = (float) Math.toDegrees(Math.atan2(-safeMotion.x, safeMotion.z));
                    float yawError = net.minecraft.util.Mth.wrapDegrees(pathYaw - mob.getYRot());
                    mob.setYRot(mob.getYRot() + net.minecraft.util.Mth.clamp(yawError, -10, 10));
                    mob.yBodyRot = mob.getYRot();
                }
                a.flightVelocityX = safeMotion.x;
                a.flightVelocityZ = safeMotion.z;
                mob.move(net.minecraft.world.entity.MoverType.SELF, safeMotion);
                recordBlockedMovement(a, gameTick, body.forward(), requestedMotion,
                        positionBeforeMove, mob.position());
                mob.setDeltaMovement(Vec3.ZERO);
            } else if (mob.isNoAi()) {
                moveWithoutVanillaAi(mob, a, vx, vy, vz);
            } else {
                mob.setDeltaMovement(0, vy, 0);
                mob.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(vx, 0, vz));
            }
            if (neuroFly && a.mode == ControlMode.ASSISTED && mob.horizontalCollision
                    && senses.looming() < 0.25f && senses.pain() < 0.2f
                    && FlyNavigation.mayClingToWall(false, exposedToRain)
                    && a.wallClingCooldownUntil <= gameTick && a.wallClingTicks == 0) {
                beginWallPerch((NeuroFlyEntity) mob, level, a, gameTick);
            }
            mob.hasImpulse = true;
        }
    }

    private static double nearbyFloorY(ServerLevel level, Mob mob, double x, double z) {
        int blockX = net.minecraft.util.Mth.floor(x);
        int blockZ = net.minecraft.util.Mth.floor(z);
        int top = net.minecraft.util.Mth.floor(mob.getY() - 0.05);
        int bottom = Math.max(level.getMinBuildHeight(), top - 24);
        for (int y = top; y >= bottom; y--) {
            BlockPos block = new BlockPos(blockX, y, blockZ);
            if (!level.hasChunkAt(block)) break;
            var shape = level.getBlockState(block).getCollisionShape(level, block);
            if (shape.isEmpty()) continue;
            double floor = y + shape.max(net.minecraft.core.Direction.Axis.Y);
            if (floor <= mob.getY() + 0.05) return floor;
        }
        return Double.NaN;
    }

    private static double nearbyCeilingY(ServerLevel level, Mob mob) {
        Vec3 start = new Vec3(mob.getX(), mob.getBoundingBox().maxY + 0.015, mob.getZ());
        Vec3 end = start.add(0, NeuroFlyConfig.ALTITUDE_CEILING.get() + mob.getBbHeight() + 1, 0);
        HitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : Double.POSITIVE_INFINITY;
    }

    private static void beginGroundLanding(NeuroFlyEntity fly, ServerLevel level, Attachment attachment,
                                           long gameTick, double ground) {
        attachment.nextSurfacePerchTick = gameTick + 40;
        double yaw = Math.toRadians(fly.getYRot());
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        int side = (fly.getUUID().getLeastSignificantBits() & 1L) == 0 ? 1 : -1;
        double targetX = fly.getX() + forwardX * 0.8 - forwardZ * side * 0.35;
        double targetZ = fly.getZ() + forwardZ * 0.8 + forwardX * side * 0.35;
        double targetY = nearbyFloorY(level, fly, targetX, targetZ);
        if (!Double.isFinite(targetY) || Math.abs(targetY - ground) > 0.8
                || targetY > fly.getY() + 0.05) return;
        Vec3 target = new Vec3(targetX, targetY, targetZ);
        if (!level.noCollision(fly, fly.getBoundingBox().move(target.subtract(fly.position())))) return;
        attachment.landingOrigin = fly.position();
        attachment.landingTarget = target;
        attachment.landingTurnSign = side;
        attachment.landingApproachTicks = 80;
        attachment.nextSurfacePerchTick = gameTick + 80;
    }

    private static void moveWithoutVanillaAi(Mob mob, Attachment attachment,
                                              double vx, double requestedY, double vz) {
        double vertical = mob instanceof net.minecraft.world.entity.animal.Bee ? requestedY
                : Math.abs(requestedY) < 1.0e-6
                ? attachment.manualVerticalVelocity
                : Math.max(attachment.manualVerticalVelocity, requestedY);
        if (mob.onGround() && vertical < 0) vertical = 0;
        if (!mob.isNoGravity() && !(mob instanceof net.minecraft.world.entity.animal.Bee)) vertical -= 0.08;
        vertical *= 0.98;
        mob.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(vx, vertical, vz));
        if (mob.verticalCollision && vertical < 0) vertical = 0;
        attachment.manualVerticalVelocity = vertical;
        mob.setDeltaMovement(Vec3.ZERO);
        mob.hasImpulse = true;
    }

    private static WorldSample sample(Mob mob, ServerLevel level, Attachment attachment) {
        Vec3 eye = mob.getEyePosition();
        double yaw = Math.toRadians(mob.getYRot());
        float leftEye = sampleEye(level, mob, eye, yaw - Math.toRadians(28));
        float light = sampleEye(level, mob, eye, yaw);
        float rightEye = sampleEye(level, mob, eye, yaw + Math.toRadians(28));
        float looming = 0;
        boolean zombieContact = false;
        boolean zombieNearby = false;
        boolean villagerContact = false;
        Entity nearbyVillager = null;
        double threatTurn = (mob.getUUID().getLeastSignificantBits() & 1L) == 0 ? 1 : -1;
        double threatStrength = 0;
        double nearestItem = Double.POSITIVE_INFINITY;
        Entity target = null;
        double targetDistance = Double.POSITIVE_INFINITY;
        for (Entity nearby : level.getEntities(mob, mob.getBoundingBox().inflate(10), e -> e != mob && e.isAlive())) {
            double distance = nearby.distanceTo(mob);
            if (nearby instanceof ItemEntity item && !item.getItem().isEmpty()) {
                nearestItem = Math.min(nearestItem, distance);
                if (distance < targetDistance) { target = nearby; targetDistance = distance; }
            }
            if (nearby instanceof ItemEntity) continue;
            if (nearby instanceof Player player && attachment.playerThreat.hasLearnedThreat(
                    attachment.navigation.preference(ExperienceNavigation.Cue.PLAYER))
                    && player.getUUID().equals(attachment.playerThreat.aggressor()) && distance <= 12) {
                double relativeYaw = net.minecraft.util.Mth.wrapDegrees((float) Math.toDegrees(
                        Math.atan2(-(player.getX() - mob.getX()), player.getZ() - mob.getZ())) - mob.getYRot());
                double strength = Math.min(1.0, 0.5 + Math.abs(
                        attachment.navigation.preference(ExperienceNavigation.Cue.PLAYER)) * 2.0);
                looming = Math.max(looming, (float) strength);
                threatTurn = Math.abs(relativeYaw) < 8
                        ? ((mob.getUUID().getLeastSignificantBits() & 1L) == 0 ? 1 : -1)
                        : (relativeYaw > 0 ? -1 : 1);
                threatStrength = Math.max(threatStrength, strength);
            }
            if (nearby instanceof Zombie) {
                zombieNearby = true;
                if (mob.getBoundingBox().inflate(0.12).intersects(nearby.getBoundingBox())) zombieContact = true;
                if (mob instanceof net.minecraft.world.entity.npc.Villager
                        && attachment.navigation.preference(ExperienceNavigation.Cue.ZOMBIE) < -0.04f) {
                    double relativeYaw = net.minecraft.util.Mth.wrapDegrees((float) Math.toDegrees(
                            Math.atan2(-(nearby.getX() - mob.getX()), nearby.getZ() - mob.getZ())) - mob.getYRot());
                    looming = Math.max(looming, (float) FlyNavigation.loomingStrength(distance, true, true));
                    threatTurn = relativeYaw > 0 ? -1 : 1;
                    threatStrength = Math.max(threatStrength, looming);
                }
                continue;
            }
            if (mob instanceof Zombie && nearby instanceof net.minecraft.world.entity.npc.Villager) {
                if (nearbyVillager == null || distance < mob.distanceTo(nearbyVillager)) nearbyVillager = nearby;
                if (mob.getBoundingBox().inflate(0.12).intersects(nearby.getBoundingBox())) villagerContact = true;
                continue;
            }
            Vec3 relative = nearby.getBoundingBox().getCenter().subtract(eye);
            double centerDistance = relative.length();
            if (centerDistance < 8.0) {
                boolean closing = nearby.getDeltaMovement().subtract(mob.getDeltaMovement()).dot(relative) < 0;
                boolean dangerous = nearby instanceof net.minecraft.world.entity.monster.Monster
                        || nearby instanceof Player;
                double intensity = FlyNavigation.loomingStrength(centerDistance, closing, dangerous);
                if (intensity <= 0) continue;
                looming = Math.max(looming, (float) intensity);
                if (intensity > threatStrength) {
                    double relativeYaw = net.minecraft.util.Mth.wrapDegrees((float) Math.toDegrees(
                            Math.atan2(-relative.x, relative.z)) - mob.getYRot());
                    threatTurn = Math.abs(relativeYaw) < 8
                            ? ((mob.getUUID().getLeastSignificantBits() & 1L) == 0 ? 1 : -1)
                            : (relativeYaw > 0 ? -1 : 1);
                    threatStrength = intensity;
                }
            }
        }
        float tactile = mob.horizontalCollision || mob.verticalCollision || mob.hurtTime > 0 ? 1 : 0;
        long gameTick = level.getGameTime();
        if (attachment.lastPlantSearchTick == Long.MIN_VALUE
                || gameTick - attachment.lastPlantSearchTick >= 40) {
            PlantTargets targets = findNearbyPlants(mob, level, NeuroFlyConfig.FLOWER_RADIUS.get());
            attachment.flowerTarget = mob instanceof Zombie ? null : targets.flower();
            attachment.treeTarget = targets.tree();
            attachment.lastPlantSearchTick = gameTick;
        }
        BlockPos flower = attachment.flowerTarget;
        double flowerDistance = flower == null ? Double.POSITIVE_INFINITY
                : mob.position().distanceTo(Vec3.atCenterOf(flower));
        BlockPos tree = attachment.treeTarget;
        double treeDistance = tree == null ? Double.POSITIVE_INFINITY
                : mob.position().distanceTo(Vec3.atCenterOf(tree));
        float odor = 0;
        double scentDistance = Math.min(nearestItem,
                mob instanceof NeuroFlyEntity || mob instanceof net.minecraft.world.entity.animal.Bee
                        ? flowerDistance : Double.POSITIVE_INFINITY);
        if (Double.isFinite(scentDistance)) odor = (float) Math.max(0, Math.min(1, 1 - scentDistance / 10));
        net.minecraft.world.level.block.state.BlockState underfoot = level.getBlockState(mob.blockPosition());
        float taste = underfoot.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK)
                || underfoot.is(net.minecraft.world.level.block.Blocks.CAKE)
                || underfoot.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                || underfoot.is(BlockTags.FLOWERS) || flowerDistance < 1.5 ? 1 : 0;
        float health = mob.getHealth();
        float maxHealth = Math.max(1, mob.getMaxHealth());
        float measuredRecovery = Math.max(0, health - attachment.lastHealth) / maxHealth;
        boolean zombieAttack = mob.hurtTime > 0 && mob.getLastHurtByMob() instanceof Zombie;
        float pain = mob.hurtTime > 0 || mob.isOnFire() || mob.isInLava() ? 1 : 0;
        pain = Math.max(pain, Math.min(1, Math.max(0, attachment.lastHealth - health) / maxHealth));
        float reward = Math.min(1, Math.max(0, health - attachment.lastHealth) / maxHealth);
        attachment.lastHealth = health;
        BlockPos eyeBlock = BlockPos.containing(eye);
        boolean exposedToRain = level.isRainingAt(eyeBlock) && level.canSeeSky(eyeBlock);
        BlockPos position = mob.blockPosition();
        long explorationCell = explorationCell(position);
        NaturalDriveState.Snapshot drives = attachment.naturalDrive.update(gameTick, exposedToRain,
                mob.isInWater(), flowerDistance, flower == null ? 0 : flower.asLong(),
                treeDistance, tree == null ? 0 : tree.asLong(), explorationCell);
        reward = Math.max(reward, drives.reward());
        pain = Math.max(pain, drives.pain());
        if (gameTick <= attachment.blockedPainUntil) pain = Math.max(pain, 0.14f);
        if (mob instanceof net.minecraft.world.entity.npc.Villager && (zombieContact || zombieAttack))
            attachment.navigation.observe(ExperienceNavigation.Cue.ZOMBIE, 0, zombieAttack ? 1 : 0.25f);
        if (mob instanceof Zombie && villagerContact && gameTick >= attachment.nextZombieContactRewardTick) {
            reward = Math.max(reward, 0.18f);
            attachment.navigation.observe(ExperienceNavigation.Cue.VILLAGER, 0.18f, 0);
            attachment.nextZombieContactRewardTick = gameTick + 100;
        }
        BlockPos contactHazard = contactHazard(mob, level);
        boolean harmfulGround = contactHazard != null;
        if (harmfulGround || mob.fallDistance > 3) pain = Math.max(pain, 0.65f);
        if (contactHazard != null) {
            looming = Math.max(looming, 0.7f);
            attachment.navigation.markBlocked(contactHazard.asLong(), gameTick);
            Vec3 awayFrom = Vec3.atCenterOf(contactHazard).subtract(mob.position());
            double relativeYaw = net.minecraft.util.Mth.wrapDegrees((float) Math.toDegrees(
                    Math.atan2(-awayFrom.x, awayFrom.z)) - mob.getYRot());
            threatTurn = relativeYaw > 0 ? -1 : 1;
            threatStrength = Math.max(threatStrength, 0.8);
        }
        if (measuredRecovery > 0 && mob.onGround() && contactHazard == null) {
            BlockPos support = mob.blockPosition().below();
            var supportState = level.getBlockState(support);
            if (!supportState.isAir()) attachment.blockOutcomes.observe(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(supportState.getBlock()),
                    0.65f, 0);
        }
        if (looming > 0.75f) pain = Math.max(pain, looming * 0.25f);
        if (Double.isFinite(nearestItem) && Double.isFinite(attachment.lastItemDistance)) {
            double progress = Math.max(0, attachment.lastItemDistance - nearestItem);
            reward = Math.max(reward, (float) Math.min(0.25, progress * 0.2));
        }
        if (flowerDistance < 1.5 && drives.flowerReward() > 0)
            attachment.navigation.observe(ExperienceNavigation.Cue.FLOWER, drives.flowerReward(), pain);
        if (treeDistance < 1.5 && drives.treeReward() > 0)
            attachment.navigation.observe(ExperienceNavigation.Cue.TREE, drives.treeReward(), pain);
        if (mob.isInWater() && drives.waterReward() > 0)
            attachment.navigation.observe(ExperienceNavigation.Cue.WATER, drives.waterReward(), pain);
        if (mob.isOnFire() || mob.isInLava() || harmfulGround)
            attachment.navigation.observe(ExperienceNavigation.Cue.FIRE, 0, pain);
        attachment.lastItemDistance = nearestItem;
        SoundSample sound = soundAt(mob, level, gameTick);
        FlyBrain.Drive senses = new FlyBrain.Drive(light, leftEye, rightEye, looming, tactile, odor, taste,
                pain, reward, (float) sound.strength());

        Vec3 targetPosition = target == null ? null : target.position();
        if (flower != null && attachment.navigation.attractedTo(ExperienceNavigation.Cue.FLOWER)
                && flowerDistance * (1 - Math.max(0, attachment.navigation.preference(ExperienceNavigation.Cue.FLOWER)) * 0.3)
                < targetDistance) {
            targetPosition = Vec3.atCenterOf(flower);
            targetDistance = flowerDistance;
        }
        if (attachment.lastWaterSearchTick == Long.MIN_VALUE || gameTick - attachment.lastWaterSearchTick >= 40) {
            attachment.waterTarget = findNearestWater(mob, level, NeuroFlyConfig.FLOWER_RADIUS.get());
            attachment.lastWaterSearchTick = gameTick;
        }
        BlockPos water = attachment.waterTarget;
        double waterDistance = water == null ? Double.POSITIVE_INFINITY
                : mob.position().distanceTo(Vec3.atCenterOf(water));
        if (water != null && drives.hydration() < 0.65f
                && (drives.hydration() < 0.40f || waterDistance < targetDistance)) {
            targetPosition = Vec3.atCenterOf(water);
            targetDistance = waterDistance;
        }
        if (mob instanceof Zombie && nearbyVillager != null
                && attachment.navigation.attractedTo(ExperienceNavigation.Cue.VILLAGER)) {
            targetPosition = nearbyVillager.position();
            targetDistance = mob.position().distanceTo(targetPosition);
        }

        boolean weatherActive = level.isRaining();
        boolean rainSeeking = NeuroFlyConfig.SEEK_SHELTER_IN_RAIN.get() && mob instanceof NeuroFlyEntity
                && weatherActive && (level.isRainingAt(BlockPos.containing(eye))
                && level.canSeeSky(BlockPos.containing(eye)) || attachment.shelterTarget != null);
        if (!weatherActive || !NeuroFlyConfig.SEEK_SHELTER_IN_RAIN.get()
                || !(mob instanceof NeuroFlyEntity)) {
            attachment.shelterTarget = null;
            attachment.lastShelterSearchTick = Long.MIN_VALUE;
        }
        if (rainSeeking) {
            if (attachment.lastShelterSearchTick == Long.MIN_VALUE || gameTick - attachment.lastShelterSearchTick
                    >= NeuroFlyConfig.RAIN_SCAN_INTERVAL.get()) {
                attachment.shelterTarget = findNearestShelter(mob, level);
                attachment.lastShelterSearchTick = gameTick;
            }
            if (attachment.shelterTarget != null) {
                targetPosition = Vec3.atCenterOf(attachment.shelterTarget);
                targetDistance = mob.position().distanceTo(targetPosition);
            } else {
                targetPosition = null;
                targetDistance = Double.POSITIVE_INFINITY;
            }
        }

        if (mob instanceof NeuroFlyEntity && NeuroFlyConfig.PERCH_ON_ANIMALS_AND_VILLAGERS.get()
                && NeuroFlyConfig.HOST_RADIUS.get() > 0
                && (attachment.lastHostSearchTick == Long.MIN_VALUE || gameTick - attachment.lastHostSearchTick >= 20)) {
            attachment.perchHostTarget = findPerchHost(mob, level, NeuroFlyConfig.HOST_RADIUS.get());
            attachment.lastHostSearchTick = gameTick;
        }
        Entity perchHost = attachment.perchHostTarget;
        double hostDistance = perchHost == null ? Double.POSITIVE_INFINITY
                : mob.position().distanceTo(perchPosition(perchHost));
        if (!rainSeeking && perchHost != null && hostDistance < targetDistance) {
            targetPosition = perchPosition(perchHost);
            targetDistance = hostDistance;
        }

        EmbodimentDecoder.WorldCue cue = EmbodimentDecoder.WorldCue.NONE;
        if (targetPosition != null) {
            Vec3 towardTarget = targetPosition.subtract(mob.position());
            double targetYaw = Math.toDegrees(Math.atan2(-towardTarget.x, towardTarget.z));
            double relativeYaw = net.minecraft.util.Mth.wrapDegrees((float) targetYaw - mob.getYRot());
            double strength = rainSeeking && attachment.shelterTarget != null
                    ? FlyNavigation.shelterGuidanceStrength(targetDistance)
                    : Math.max(0, Math.min(1, (10 - targetDistance) / 6));
            double targetTurn = Math.max(-1, Math.min(1, relativeYaw / 75));
            cue = new EmbodimentDecoder.WorldCue(targetTurn, strength,
                    Math.min(16, targetDistance), 0, 0);
        }
        FlyNavigation.LightMode lightMode = FlyNavigation.LightMode.valueOf(
                NeuroFlyConfig.LIGHT_MODE.get().name());
        double lightTurn = mob instanceof NeuroFlyEntity
                ? FlyNavigation.phototaxisTurn(leftEye, rightEye, light, lightMode,
                        NeuroFlyConfig.LIGHT_STEERING.get()) : 0;
        cue = new EmbodimentDecoder.WorldCue(cue.targetTurn(), cue.targetStrength(), cue.targetDistance(),
                cue.obstacle(), cue.avoidanceTurn(), cue.threatTurn(), cue.threatStrength(), lightTurn,
                NeuroFlyConfig.LIGHT_STEERING.get());

        double leftClear = blockClearance(level, mob, eye, yaw - Math.toRadians(38), 4.5);
        double rightClear = blockClearance(level, mob, eye, yaw + Math.toRadians(38), 4.5);
        double frontClear = blockClearance(level, mob, eye, yaw, 4.5);
        double obstacle = Math.max(0, Math.min(1, (3.5 - frontClear) / 3.0));
        double learnedAhead = learnedBlockRisk(mob, level, attachment.blockOutcomes, 2.0, 0);
        obstacle = Math.max(obstacle, learnedAhead);
        if (obstacle > cue.obstacle()) {
            double avoidance = Math.max(-1, Math.min(1, (rightClear - leftClear) / 3.0));
            if (attachment.navigation.isBlocked(
                    projectedRouteCell(mob, 1.5, -1.2), gameTick)) avoidance = Math.max(avoidance, 0.8);
            if (attachment.navigation.isBlocked(
                    projectedRouteCell(mob, 1.5, 1.2), gameTick)) avoidance = Math.min(avoidance, -0.8);
            if (Math.abs(avoidance) < 0.08)
                avoidance = (mob.getUUID().getLeastSignificantBits() & 1L) == 0 ? -0.75 : 0.75;
            cue = new EmbodimentDecoder.WorldCue(cue.targetTurn(), cue.targetStrength(),
                    cue.targetDistance(), obstacle, avoidance, cue.threatTurn(), cue.threatStrength(),
                    cue.lightTurn(), cue.lightStrength());
        }
        if (learnedAhead > 0) {
            double leftRisk = learnedBlockRisk(mob, level, attachment.blockOutcomes, 2.0, -1.4);
            double rightRisk = learnedBlockRisk(mob, level, attachment.blockOutcomes, 2.0, 1.4);
            double steerAway = Math.max(-1, Math.min(1, (leftRisk - rightRisk) * 2));
            if (Math.abs(steerAway) < 0.1) steerAway = threatTurn;
            cue = new EmbodimentDecoder.WorldCue(cue.targetTurn(), cue.targetStrength(),
                    cue.targetDistance(), obstacle, steerAway, cue.threatTurn(), cue.threatStrength(),
                    cue.lightTurn(), cue.lightStrength());
        }
        if (looming > 0.25f) {
            cue = new EmbodimentDecoder.WorldCue(cue.targetTurn(), cue.targetStrength(), cue.targetDistance(),
                    cue.obstacle(), cue.avoidanceTurn(), threatTurn, threatStrength,
                    cue.lightTurn(), cue.lightStrength());
        }
        cue = new EmbodimentDecoder.WorldCue(cue.targetTurn(), cue.targetStrength(), cue.targetDistance(),
                cue.obstacle(), cue.avoidanceTurn(), cue.threatTurn(), cue.threatStrength(), cue.lightTurn(),
                cue.lightStrength(), sound.turnAway(), sound.strength());
        return new WorldSample(senses, cue, perchHost, hostDistance, rainSeeking,
                attachment.shelterTarget, zombieNearby);
    }

    private static SoundSample soundAt(Mob mob, ServerLevel level, long gameTick) {
        double loudest = 0, weightedPan = 0, panWeight = 0;
        double yaw = Math.toRadians(mob.getYRot());
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        for (SoundPulse pulse : SOUND_PULSES) {
            long age = gameTick - pulse.tick();
            if (age > 20 || age < 0) {
                SOUND_PULSES.remove(pulse);
                continue;
            }
            if (!pulse.dimension().equals(level.dimension().location())) continue;
            Vec3 offset = pulse.position().subtract(mob.getEyePosition());
            double distance = offset.length();
            double radius = Math.max(2, pulse.volume() * 16);
            if (distance >= radius) continue;
            double strength = Math.min(1, pulse.volume()) * (1 - distance / radius)
                    * Math.exp(-age / 8.0) * (0.75 + pulse.pitch() * 0.125);
            if (strength <= 0.025) continue;
            double pan = distance < 0.01 ? 0 : offset.normalize().dot(right);
            weightedPan += pan * strength;
            panWeight += strength;
            loudest = Math.max(loudest, strength);
        }
        if (loudest < 0.08) return SoundSample.NONE;
        double pan = panWeight == 0 ? 0 : weightedPan / panWeight;
        double turnAway = Math.abs(pan) < 0.08
                ? ((mob.getUUID().getLeastSignificantBits() & 1L) == 0 ? -1 : 1)
                : -Math.signum(pan);
        return new SoundSample(turnAway, Math.min(1, loudest));
    }

    private static PlantTargets findNearbyPlants(Mob mob, ServerLevel level, int radius) {
        BlockPos origin = mob.blockPosition();
        BlockPos bestFlower = null;
        BlockPos bestTree = null;
        double bestFlowerDistance = (double) radius * radius;
        double bestTreeDistance = (double) radius * radius;
        for (int y = -2; y <= 3; y++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            BlockPos candidate = origin.offset(x, y, z);
            if (!level.hasChunkAt(candidate)) continue;
            var state = level.getBlockState(candidate);
            double distance = mob.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5,
                    candidate.getZ() + 0.5);
            if (state.is(BlockTags.FLOWERS) && distance < bestFlowerDistance) {
                bestFlower = candidate.immutable();
                bestFlowerDistance = distance;
            }
            if ((state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) && distance < bestTreeDistance) {
                bestTree = candidate.immutable();
                bestTreeDistance = distance;
            }
        }
        return new PlantTargets(bestFlower, bestTree);
    }

    private static BlockPos findNearestWater(Mob mob, ServerLevel level, int radius) {
        BlockPos origin = mob.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = (double) radius * radius;
        for (int y = -3; y <= 3; y++) for (int x = -radius; x <= radius; x++)
            for (int z = -radius; z <= radius; z++) {
                BlockPos candidate = origin.offset(x, y, z);
                if (!level.hasChunkAt(candidate) || !level.getFluidState(candidate).is(FluidTags.WATER)) continue;
                double distance = mob.distanceToSqr(candidate.getX() + 0.5,
                        candidate.getY() + 0.5, candidate.getZ() + 0.5);
                if (distance < nearestDistance) {
                    nearest = candidate.immutable();
                    nearestDistance = distance;
                }
            }
        return nearest;
    }

    private static long explorationCell(BlockPos position) {
        return ((long) (position.getX() >> 2) << 32) | ((position.getZ() >> 2) & 0xffff_ffffL);
    }

    private static BlockPos contactHazard(Mob mob, ServerLevel level) {
        var bounds = mob.getBoundingBox().inflate(0.10);
        BlockPos low = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos high = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        for (BlockPos candidate : BlockPos.betweenClosed(low, high)) {
            if (!level.hasChunkAt(candidate)) continue;
            var block = level.getBlockState(candidate);
            if (block.is(net.minecraft.world.level.block.Blocks.CACTUS)
                    || block.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    || block.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                    || block.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                    || block.is(net.minecraft.world.level.block.Blocks.FIRE)
                    || block.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                    || level.getFluidState(candidate).is(FluidTags.LAVA)) return candidate.immutable();
        }
        return null;
    }

    private static long projectedRouteCell(Mob mob, double forward, double side) {
        double yaw = Math.toRadians(mob.getYRot());
        int x = net.minecraft.util.Mth.floor(mob.getX() - Math.sin(yaw) * forward + Math.cos(yaw) * side);
        int z = net.minecraft.util.Mth.floor(mob.getZ() + Math.cos(yaw) * forward + Math.sin(yaw) * side);
        return new BlockPos(x, mob.blockPosition().getY(), z).asLong();
    }

    private static double learnedBlockRisk(Mob mob, ServerLevel level, BlockOutcomeMemory memory,
                                           double forward, double side) {
        long pos = projectedRouteCell(mob, forward, side);
        BlockPos projected = BlockPos.of(pos);
        double risk = 0;
        for (BlockPos candidate : new BlockPos[]{projected, projected.below(), projected.above()}) {
            if (!level.hasChunkAt(candidate)) continue;
            var state = level.getBlockState(candidate);
            if (state.isAir()) continue;
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (memory.avoids(id)) risk = Math.max(risk,
                    Math.min(1, Math.abs(memory.preference(id)) * 3.0));
        }
        return risk;
    }

    private static Entity findPerchHost(Mob fly, ServerLevel level, int radius) {
        Entity best = null;
        double bestDistance = (double) radius * radius;
        for (Entity candidate : level.getEntities(fly, fly.getBoundingBox().inflate(radius), entity -> entity.isAlive()
                && ((entity instanceof net.minecraft.world.entity.animal.Animal
                && !(entity instanceof net.minecraft.world.entity.animal.Bee)
                && !(entity instanceof net.minecraft.world.entity.TamableAnimal))
                || entity instanceof net.minecraft.world.entity.npc.Villager))) {
            double distance = fly.position().distanceTo(perchPosition(candidate));
            if (distance < bestDistance) { best = candidate; bestDistance = distance; }
        }
        return best;
    }

    private static Vec3 perchPosition(Entity host) {
        return new Vec3(host.getX(), host.getBoundingBox().maxY + 0.12, host.getZ());
    }

    private static BlockPos findNearestShelter(Mob mob, ServerLevel level) {
        BlockPos origin = mob.blockPosition();
        java.util.ArrayDeque<BlockPos> frontier = new java.util.ArrayDeque<>();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        frontier.add(origin);
        visited.add(origin.asLong());
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {0, 1, 0}, {0, -1, 0}};
        while (!frontier.isEmpty()) {
            BlockPos cell = frontier.removeFirst();
            if (cell.getX() >= origin.getX() - 8 && cell.getX() <= origin.getX() + 8
                    && cell.getZ() >= origin.getZ() - 8 && cell.getZ() <= origin.getZ() + 8
                    && level.hasChunkAt(cell)) {
                var state = level.getBlockState(cell);
                if (state.isAir() && level.getFluidState(cell).isEmpty()
                        && !level.isRainingAt(cell) && !level.canSeeSky(cell)
                        && hasFlightClearance(mob, level, cell)) return cell.immutable();
            }
            for (int[] step : steps) {
                BlockPos next = cell.offset(step[0], step[1], step[2]);
                if (next.getY() < origin.getY() - 2 || next.getY() > origin.getY() + 3
                        || Math.abs(next.getX() - origin.getX()) > 8
                        || Math.abs(next.getZ() - origin.getZ()) > 8
                        || !level.hasChunkAt(next) || !visited.add(next.asLong())) continue;
                if (hasFlightClearance(mob, level, next)) frontier.addLast(next);
            }
        }
        return null;
    }

    private static boolean hasFlightClearance(Mob mob, ServerLevel level, BlockPos cell) {
        if (!level.getFluidState(cell).isEmpty()) return false;
        var box = mob.getBoundingBox().move(cell.getX() + 0.5 - mob.getX(),
                cell.getY() - mob.getY(), cell.getZ() + 0.5 - mob.getZ()).expandTowards(0, 0.025, 0);
        return level.noCollision(mob, box);
    }

    private static double blockClearance(ServerLevel level, Mob mob, Vec3 origin, double yaw, double distance) {
        Vec3 direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        HitResult hit = level.clip(new ClipContext(origin, origin.add(direction.scale(distance)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return Math.min(distance, Math.sqrt(hit.getLocation().distanceToSqr(origin)));
    }

    private static float sampleEye(ServerLevel level, Mob mob, Vec3 origin, double yaw) {
        Vec3 direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        HitResult sight = level.clip(new ClipContext(origin, origin.add(direction.scale(10)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return (float) level.getMaxLocalRawBrightness(BlockPos.containing(sight.getLocation())) / 15f;
    }

    private static boolean beginWallPerch(NeuroFlyEntity fly, ServerLevel level, Attachment attachment, long gameTick) {
        Vec3 eye = fly.getEyePosition();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            Vec3 normal = new Vec3(direction.getStepX(), 0, direction.getStepZ());
            HitResult hit = level.clip(new ClipContext(eye, eye.add(normal.scale(1.15)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, fly));
            if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                    || blockHit.getType() != HitResult.Type.BLOCK) continue;
            Vec3 faceNormal = new Vec3(blockHit.getDirection().getStepX(), 0, blockHit.getDirection().getStepZ());
            if (faceNormal.lengthSqr() == 0) continue;
            Vec3 anchor = blockHit.getLocation().add(faceNormal.scale(fly.getBbWidth() * 0.5 + 0.025))
                    .add(0, -fly.getBbHeight() * 0.5, 0);
            Vec3 shift = anchor.subtract(fly.position());
            if (!level.noCollision(fly, fly.getBoundingBox().move(shift))) continue;
            fly.setPos(anchor);
            attachment.wallAnchor = anchor;
            attachment.wallYaw = (float) Math.toDegrees(Math.atan2(-faceNormal.x, faceNormal.z));
            fly.setYRot(attachment.wallYaw);
            fly.yBodyRot = attachment.wallYaw;
            fly.setDeltaMovement(Vec3.ZERO);
            fly.setPerched(true);
            attachment.landingApproachTicks = 0;
            attachment.landingOrigin = attachment.landingTarget = null;
            attachment.wallClingTicks = 50;
            attachment.wallClingCooldownUntil = gameTick + 100;
            return true;
        }
        return false;
    }

    public static String setMode(Mob mob, ControlMode mode) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return "Attach a brain to this mob first.";
        a.mode = mode;
        mob.setNoAi(noAiFor(mob, a));
        if (mob.isNoAi()) mob.getNavigation().stop();
        return null;
    }

    public static ControlMode modeOf(Mob mob) {
        Attachment attachment = ATTACHED.get(mob.getUUID());
        return attachment == null ? ControlMode.ASSISTED : attachment.mode;
    }

    private static boolean noAiFor(Mob mob, Attachment attachment) {
        return attachment.originalNoAi || attachment.mode == ControlMode.NEURAL
                || (mob instanceof NeuroFlyEntity || mob instanceof net.minecraft.world.entity.npc.Villager
                || mob instanceof Zombie || mob instanceof net.minecraft.world.entity.animal.Bee)
                && attachment.mode == ControlMode.ASSISTED;
    }

    private static Vec3 safeNeuroFlyMotion(ServerLevel level, Mob mob, double vx, double vy, double vz) {
        Vec3 requested = new Vec3(vx, vy, vz);
        if (level.noCollision(mob, mob.getBoundingBox().move(requested))) return requested;
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed > 1.0e-4) {
            double yaw = Math.atan2(-vx, vz);
            for (double offset : new double[]{Math.toRadians(35), -Math.toRadians(35),
                    Math.toRadians(70), -Math.toRadians(70), Math.PI}) {
                double candidateYaw = yaw + offset;
                Vec3 candidate = new Vec3(-Math.sin(candidateYaw) * speed, vy,
                        Math.cos(candidateYaw) * speed);
                if (level.noCollision(mob, mob.getBoundingBox().move(candidate))) return candidate;
            }
        }
        if (Math.abs(vy) > 1.0e-4) {
            Vec3 vertical = new Vec3(0, vy, 0);
            if (level.noCollision(mob, mob.getBoundingBox().move(vertical))) return vertical;
        }
        return Vec3.ZERO;
    }

    private static void recordBlockedMovement(Attachment attachment, long gameTick, double forward,
                                             Vec3 requested, Vec3 before, Vec3 after) {
        double intended = requested.horizontalDistance();
        double progress = after.subtract(before).horizontalDistance();
        if (forward > 0.35 && intended > 0.025 && progress < intended * 0.2) {
            if (++attachment.blockedMovementTicks >= 12 && gameTick >= attachment.nextBlockedPainTick) {
                attachment.navigation.markBlocked(projectedRouteCell(attachment.mob, 1.5, 0), gameTick);
                attachment.blockedPainUntil = gameTick + 2;
                attachment.nextBlockedPainTick = gameTick + 100;
                attachment.blockedMovementTicks = 0;
            }
        } else {
            attachment.blockedMovementTicks = Math.max(0, attachment.blockedMovementTicks - 2);
        }
    }

    public static String startTrial(Mob mob, String label, int durationTicks) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return "Attach a brain to this mob first.";
        long tick = mob.level().getGameTime();
        TrialSession candidate = new TrialSession();
        TrialSession.Snapshot started;
        try { started = candidate.start(label, tick, durationTicks); }
        catch (IllegalArgumentException | ArithmeticException invalid) { return invalid.getMessage(); }
        TrialSession.Snapshot previous = a.trial.end();
        if (previous != null) TelemetryLog.trialEvent("trial_end", mob, a.mode, previous, tick);
        a.trial.start(started.label(), started.startedAtTick(), Math.toIntExact(started.durationTicks()));
        TelemetryLog.trialEvent("trial_start", mob, a.mode, started, tick);
        return null;
    }

    public static String endTrial(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return "Attach a brain to this mob first.";
        TrialSession.Snapshot ended = a.trial.end();
        if (ended == null) return "No trial is active.";
        TelemetryLog.trialEvent("trial_end", mob, a.mode, ended, mob.level().getGameTime());
        return null;
    }

    public static String clearStimuli(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return "Attach a brain to this mob first.";
        a.stimuli.clear();
        return null;
    }

    public static String describe(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        if (a == null) return mob.getName().getString() + ": no NeuroLab brain attached.";
        FlyBrain.Snapshot snapshot = a.worker.snapshot();
        long gameTick = mob.level().getGameTime();
        TrialSession.Snapshot trial = a.trial.current(gameTick);
        String flight = "";
        if (mob instanceof NeuroFlyEntity neuroFly) {
            String state = a.wallClingTicks > 0 ? "wall perch " + a.wallClingTicks + "t"
                    : a.surfacePerchTicks > 0 ? "surface rest " + a.surfacePerchTicks + "t"
                    : a.landingApproachTicks > 0 ? "landing " + a.landingApproachTicks + "t"
                    : a.shelterTarget != null ? "shelter " + a.shelterTarget.toShortString()
                    : neuroFly.isPerched() ? "perched" : "cruise";
            flight = String.format(Locale.ROOT,
                    " · flight %s · target %+.2f/%.2f · body %.2f/%+.2f · yaw %.0f",
                    state, a.lastWorldCue.targetTurn(), a.lastWorldCue.targetStrength(),
                    a.lastBodyCommand.forward(), a.lastBodyCommand.turn(), neuroFly.getYRot());
        }
        return String.format(Locale.ROOT,
                "%s · mode %s · neural tick %d · spikes %d · active %d · real-time %.2fx · test pulses %d · trial %s · original AI %s%s",
                mob.getName().getString(), a.mode.id(), snapshot.tick(), snapshot.spikes(),
                snapshot.activeNeurons(), a.worker.realTimeFactor(),
                a.stimuli.activeCount(gameTick), trial == null ? "none" : trial.label() + " (" + trial.elapsedTicks(gameTick) + "/" + trial.durationTicks() + " ticks)",
                a.originalNoAi ? "disabled" : "enabled", flight);
    }

    public static CompletableFuture<ConnectomeData> connectomeStatus() { return data; }
    public static int attachedCount() { return ATTACHED.size(); }
    public static int workerCount() { return BrainWorker.workerCount(); }
    public static double realTimeFactor(Mob mob) {
        Attachment a = ATTACHED.get(mob.getUUID());
        return a == null ? 0 : a.worker.realTimeFactor();
    }

    public static void shutdown() {
        for (Attachment a : ATTACHED.values()) {
            persistState(a);
            a.worker.close();
            TrialSession.Snapshot trial = a.trial.end();
            if (trial != null) TelemetryLog.trialEvent("trial_end", a.mob, a.mode, trial, a.mob.level().getGameTime());
            a.mob.setNoAi(a.originalNoAi);
        }
        ATTACHED.clear();
        AUTO_ATTACH_PENDING.clear();
    }


    private static final class Attachment {
        private final Mob mob;
        private final BrainWorker worker;
        private final boolean originalNoAi;
        private final StimulusSchedule stimuli = new StimulusSchedule();
        private final NaturalDriveState naturalDrive;
        private final ExperienceNavigation navigation;
        private final BlockOutcomeMemory blockOutcomes;
        private PlayerThreatMemory playerThreat;
        private ControlMode mode = ControlMode.ASSISTED;
        private double manualVerticalVelocity;
        private double flightVelocityX;
        private double flightVelocityZ;
        private final TrialSession trial = new TrialSession();
        private int persistedLearningRevision;
        private volatile BrainTelemetryPayload latestTelemetry;
        private float lastHealth;
        private double lastItemDistance = Double.POSITIVE_INFINITY;
        private BlockPos flowerTarget;
        private BlockPos treeTarget;
        private long lastPlantSearchTick = Long.MIN_VALUE;
        private BlockPos shelterTarget;
        private BlockPos waterTarget;
        private long lastWaterSearchTick = Long.MIN_VALUE;
        private long lastShelterSearchTick = Long.MIN_VALUE;
        private EmbodimentDecoder.WorldCue lastWorldCue = EmbodimentDecoder.WorldCue.NONE;
        private EmbodimentDecoder.Command lastBodyCommand =
                new EmbodimentDecoder.Command(0, 0, 0, false, false);
        private Entity perchHostTarget;
        private long lastHostSearchTick = Long.MIN_VALUE;
        private long nextPerchOpportunityTick;
        private long perchCooldownUntil;
        private int perchedTicks;
        private long wallClingCooldownUntil;
        private int wallClingTicks;
        private Vec3 wallAnchor;
        private float wallYaw;
        private int landingApproachTicks;
        private Vec3 landingOrigin;
        private Vec3 landingTarget;
        private int landingTurnSign;
        private int surfacePerchTicks;
        private Vec3 surfacePerchAnchor;
        private long nextSurfacePerchTick;
        private final EscapeJumpGate jumpGate = new EscapeJumpGate();
        private long nextZombieContactRewardTick;
        private int blockedMovementTicks;
        private long blockedPainUntil = Long.MIN_VALUE;
        private long nextBlockedPainTick;

        private Attachment(Mob mob, BrainWorker worker, boolean originalNoAi) {
            this.mob = mob;
            this.worker = worker;
            this.originalNoAi = originalNoAi;
            this.lastHealth = mob.getHealth();
            var persistent = mob.getPersistentData();
            this.naturalDrive = new NaturalDriveState(
                    persistent.contains(SATIETY_TAG) ? persistent.getFloat(SATIETY_TAG) : 1,
                    persistent.contains(HYDRATION_TAG) ? persistent.getFloat(HYDRATION_TAG) : 1,
                    persistent.contains(RAIN_STRESS_TAG) ? persistent.getFloat(RAIN_STRESS_TAG) : 0,
                    persistent.getLongArray(REWARDED_FLOWERS_TAG),
                    persistent.getLongArray(REWARDED_TREES_TAG),
                    persistent.getLongArray(EXPLORED_CELLS_TAG));
            this.navigation = new ExperienceNavigation(new float[]{
                    persistent.getFloat(LEARNED_CUES_TAG + "Flower"),
                    persistent.getFloat(LEARNED_CUES_TAG + "Tree"),
                    persistent.getFloat(LEARNED_CUES_TAG + "Water"),
                    persistent.getFloat(LEARNED_CUES_TAG + "Fire"),
                    persistent.getFloat(LEARNED_CUES_TAG + "Zombie"),
                    persistent.getFloat(LEARNED_CUES_TAG + "Villager"),
                    persistent.getFloat(LEARNED_CUES_TAG + "Player")});
            this.playerThreat = new PlayerThreatMemory(
                    persistent.hasUUID(THREAT_PLAYER_TAG) ? persistent.getUUID(THREAT_PLAYER_TAG) : null,
                    persistent.getInt(THREAT_HITS_TAG));
            this.blockOutcomes = new BlockOutcomeMemory(persistent.getCompound(BLOCK_OUTCOMES_TAG));
            this.naturalDrive.seedExplorationCell(explorationCell(mob.blockPosition()));
            this.nextPerchOpportunityTick = 200 + Math.floorMod(
                    (int) mob.getUUID().getMostSignificantBits(), 800);
            this.nextSurfacePerchTick = mob.level().getGameTime() + 100 + Math.floorMod(
                    (int) mob.getUUID().getLeastSignificantBits(), 120);
        }
    }

    static final class EscapeJumpGate {
        private boolean escapeLatched;
        private long nextAllowedTick;

        boolean shouldJump(boolean escape, boolean onGround, long tick) {
            boolean risingEdge = escape && !escapeLatched;
            escapeLatched = escape;
            if (!risingEdge || !onGround || tick < nextAllowedTick) return false;
            nextAllowedTick = tick + 20;
            return true;
        }

        boolean shouldJump(boolean escape, boolean blockedAhead, double forward,
                           boolean onGround, long tick) {
            return shouldJump(escape || blockedAhead && forward > 0.15, onGround, tick);
        }
    }

    private static void persistLearning(Attachment attachment) {
        int revision = attachment.worker.learningRevision();
        if (revision <= attachment.persistedLearningRevision) return;
        attachment.mob.getPersistentData().putLongArray(LEARNED_SYNAPSES_TAG,
                attachment.worker.learnedSynapses());
        attachment.persistedLearningRevision = revision;
    }

    private static void persistState(Attachment attachment) {
        persistLearning(attachment);
        var persistent = attachment.mob.getPersistentData();
        persistent.putFloat(SATIETY_TAG, attachment.naturalDrive.satiety());
        persistent.putFloat(HYDRATION_TAG, attachment.naturalDrive.hydration());
        persistent.putFloat(RAIN_STRESS_TAG, attachment.naturalDrive.rainStress());
        persistent.putLongArray(REWARDED_FLOWERS_TAG, attachment.naturalDrive.rewardedFlowers());
        persistent.putLongArray(REWARDED_TREES_TAG, attachment.naturalDrive.rewardedTrees());
        persistent.putLongArray(EXPLORED_CELLS_TAG, attachment.naturalDrive.visitedCells());
        float[] cues = attachment.navigation.snapshot();
        persistent.putFloat(LEARNED_CUES_TAG + "Flower", cues[0]);
        persistent.putFloat(LEARNED_CUES_TAG + "Tree", cues[1]);
        persistent.putFloat(LEARNED_CUES_TAG + "Water", cues[2]);
        persistent.putFloat(LEARNED_CUES_TAG + "Fire", cues[3]);
        persistent.putFloat(LEARNED_CUES_TAG + "Zombie", cues[4]);
        persistent.putFloat(LEARNED_CUES_TAG + "Villager", cues[5]);
        persistent.putFloat(LEARNED_CUES_TAG + "Player", cues[6]);
        if (attachment.playerThreat.aggressor() != null)
            persistent.putUUID(THREAT_PLAYER_TAG, attachment.playerThreat.aggressor());
        persistent.putInt(THREAT_HITS_TAG, attachment.playerThreat.damagingHits());
        persistent.put(BLOCK_OUTCOMES_TAG, attachment.blockOutcomes.save());
    }

    private record WorldSample(FlyBrain.Drive senses, EmbodimentDecoder.WorldCue cue,
                               Entity perchHost, double hostDistance, boolean rainSeeking,
                               BlockPos shelterTarget, boolean zombieNearby) {}

    private record PlantTargets(BlockPos flower, BlockPos tree) {}

    private record SoundPulse(ResourceLocation dimension, Vec3 position, float volume, float pitch, long tick) {}
    private record SoundSample(double turnAway, double strength) {
        private static final SoundSample NONE = new SoundSample(0, 0);
    }

}
