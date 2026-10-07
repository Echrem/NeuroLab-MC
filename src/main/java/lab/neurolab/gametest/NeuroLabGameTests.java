package lab.neurolab.gametest;

import lab.neurolab.brain.ControlMode;
import lab.neurolab.minecraft.BrainAttachmentService;
import lab.neurolab.minecraft.NeuroLabEntities;
import lab.neurolab.minecraft.TelemetryLog;
import lab.neurolab.minecraft.NeuroFlyConfig;
import lab.neurolab.entity.NeuroFlyEntity;
import lab.neurolab.minecraft.PetriDishBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;

/** Runs only in Forge's development GameTest server, never in a production world. */
@GameTestHolder("neurolab")
public final class NeuroLabGameTests {
    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void repeatedPlayerDamageBuildsAnIdentitySpecificLearnedCue(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        net.minecraft.server.level.ServerPlayer[] attacker = new net.minecraft.server.level.ServerPlayer[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                        && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(), "Connectome not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    subject[0] = helper.spawn(EntityType.SHEEP, new BlockPos(1, 1, 1));
                    attacker[0] = helper.makeMockServerPlayerInLevel();
                    helper.assertTrue(BrainAttachmentService.attach(subject[0]), "Threat-learning subject did not attach");
                })
                .thenExecute(() -> helper.assertTrue(subject[0].hurt(
                        attacker[0].damageSources().playerAttack(attacker[0]), 1), "First player damage was not applied"))
                .thenIdle(12)
                .thenExecute(() -> helper.assertTrue(subject[0].hurt(
                        attacker[0].damageSources().playerAttack(attacker[0]), 1), "Second player damage was not applied"))
                .thenIdle(12)
                .thenExecute(() -> helper.assertTrue(subject[0].hurt(
                        attacker[0].damageSources().playerAttack(attacker[0]), 1), "Third player damage was not applied"))
                .thenExecute(() -> {
                    var data = subject[0].getPersistentData();
                    helper.assertTrue(data.getInt("NeuroLabThreatHits") == 3,
                            "Applied damage events did not accumulate for the remembered player");
                    helper.assertTrue(data.getFloat("NeuroLabLearnedCuesPlayer") < -0.12f,
                            "Repeated attacks did not cross the learned avoidance cue strength");
                    helper.assertTrue(data.hasUUID("NeuroLabThreatPlayer")
                            && data.getUUID("NeuroLabThreatPlayer").equals(attacker[0].getUUID()),
                            "The threat memory did not retain the attacker's identity");
                    BrainAttachmentService.detach(subject[0]);
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void pcKillActionCanUseTheValidatedSubjectLifecycle(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                        && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(), "Connectome not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    subject[0] = helper.spawn(EntityType.SHEEP, new BlockPos(1, 1, 1));
                    helper.assertTrue(BrainAttachmentService.attach(subject[0]), "Kill-test subject did not attach");
                    helper.assertTrue(BrainAttachmentService.startTrial(subject[0], "kill-test", 200) == null,
                            "Could not mark kill-test interval");
                    helper.assertTrue(BrainAttachmentService.killSubject(subject[0]), "Selected subject termination failed");
                    helper.assertTrue(!subject[0].isAlive(), "Kill action left the selected mob alive");
                    helper.assertTrue(!BrainAttachmentService.isAttached(subject[0]),
                            "Terminated subject retained its connectome worker");
                })
                .thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void recorderMarksTrialsAndEmptyNeedsDoNotDamageMob(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                        && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(), "Connectome not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    java.nio.file.Path logFile = helper.getLevel().getServer()
                            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("neurolab/events.jsonl");
                    TelemetryLog.start(logFile, "gametest", "male-cns:v1.0");
                    Mob mob = helper.spawn(EntityType.SHEEP, new BlockPos(1, 1, 1));
                    subject[0] = mob;
                    helper.assertTrue(TelemetryLog.status().running(), "GameTest telemetry writer did not start: "
                            + TelemetryLog.status());
                    mob.setNoAi(true);
                    mob.getPersistentData().putFloat("NeuroLabSatiety", 0);
                    mob.getPersistentData().putFloat("NeuroLabHydration", 0);
                    helper.assertTrue(BrainAttachmentService.attach(mob), "Recorder subject attachment failed");
                    BrainAttachmentService.setMode(mob, ControlMode.OBSERVE);
                    var player = helper.makeMockServerPlayerInLevel();
                    player.setShiftKeyDown(false);
                    player.setPose(net.minecraft.world.entity.Pose.STANDING);
                    var item = NeuroLabEntities.NEURO_TRIAL_RECORDER.get();
                    var stack = new net.minecraft.world.item.ItemStack(item);
                    item.interactLivingEntity(stack, player, mob, net.minecraft.world.InteractionHand.MAIN_HAND);
                    String description = BrainAttachmentService.describe(mob);
                    helper.assertTrue(description.contains("trial field-"),
                            "Recorder did not start a labeled trial: " + description);
                    player.getCooldowns().removeCooldown(item);
                    player.setShiftKeyDown(true);
                    item.interactLivingEntity(stack, player, mob, net.minecraft.world.InteractionHand.MAIN_HAND);
                    helper.assertTrue(BrainAttachmentService.describe(mob).contains("trial none"),
                            "Sneak-use did not end the trial");
                    TelemetryLog.stop();
                    try {
                        var events = java.nio.file.Files.readAllLines(logFile).stream()
                                .map(com.google.gson.JsonParser::parseString)
                                .filter(e -> e.isJsonObject() && e.getAsJsonObject().has("event"))
                                .map(e -> e.getAsJsonObject().get("event").getAsString()).toList();
                        helper.assertTrue(events.contains("trial_start") && events.contains("trial_end"),
                                "Recorder did not save both trial boundaries to JSONL");
                    } catch (java.io.IOException error) {
                        throw new AssertionError("Could not inspect recorder's experiment log", error);
                    }
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    Mob mob = subject[0];
                    helper.assertTrue(mob.isAlive() && mob.getHealth() == mob.getMaxHealth(),
                            "Empty hunger/thirst damaged or killed the subject");
                    var sample = BrainAttachmentService.roster(helper.getLevel()).stream()
                            .filter(e -> e.entityId() == mob.getId()).findFirst().orElseThrow().sample();
                    helper.assertTrue(sample.hunger() > 0.99 && sample.thirst() > 0.99,
                            "Depleted needs did not reach telemetry");
                    BrainAttachmentService.detach(mob);
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void newProbesReachHorseAndVillagerSensoryTelemetry(GameTestHelper helper) {
        java.util.List<Mob> subjects = new java.util.ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                        && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(), "Connectome not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    var player = helper.makeMockServerPlayerInLevel();
                    var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
                    for (EntityType<?> type : List.of(EntityType.HORSE, EntityType.VILLAGER)) {
                        Mob mob = (Mob) helper.spawn(type, new BlockPos(1, 1, 1));
                        subjects.add(mob);
                        helper.assertTrue(BrainAttachmentService.attach(mob), "Tool subject attachment failed");
                        BrainAttachmentService.setMode(mob, ControlMode.OBSERVE);
                        for (var item : List.of(NeuroLabEntities.NEURO_ACOUSTIC_PROBE.get(),
                                NeuroLabEntities.NEURO_GUSTATORY_PROBE.get(), NeuroLabEntities.NEURO_LOOMING_PROBE.get())) {
                            player.getCooldowns().removeCooldown(item);
                            player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(item));
                            var event = new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(player, hand, mob);
                            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
                            helper.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction(),
                                    "Lab probe did not take precedence over mob interaction");
                        }
                    }
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    for (Mob mob : subjects) {
                        var sample = BrainAttachmentService.roster(helper.getLevel()).stream()
                                .filter(e -> e.entityId() == mob.getId()).findFirst().orElseThrow().sample();
                        helper.assertTrue(sample.testStimulus() && sample.sound() >= 0.75f
                                && sample.taste() >= 0.75f && sample.looming() >= 0.75f,
                                "Probe input was not visible in sensory telemetry");
                        BrainAttachmentService.clearStimuli(mob);
                        BrainAttachmentService.detach(mob);
                    }
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void attachedVillagerMovesAndObservationRestoresAi(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        Vec3[] start = new Vec3[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var future = BrainAttachmentService.connectomeStatus();
                    helper.assertTrue(future.isDone() && !future.isCompletedExceptionally(),
                            "Connectome is not ready yet");
                })
                .thenExecute(() -> {
                    helper.assertTrue(!BrainAttachmentService.connectomeStatus().isCompletedExceptionally(), "Dataset loading failed");
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    subject[0] = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
                    double yBeforeAttach = subject[0].getY();
                    helper.assertTrue(BrainAttachmentService.attach(subject[0]), "Villager attachment failed");
                    helper.assertTrue(Math.abs(subject[0].getY() - yBeforeAttach) < 1e-6,
                            "Attaching a brain directly shifted the mob vertically");
                    helper.assertTrue(subject[0].isNoAi(), "Villager retained hard-coded vanilla flee behavior");
                    start[0] = subject[0].position();
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    Mob mob = subject[0];
                    Vec3 displacement = mob.position().subtract(start[0]);
                    helper.assertTrue(displacement.horizontalDistanceSqr() > 0.01, "Attached villager did not move horizontally");
                    helper.assertTrue(BrainAttachmentService.setMode(mob, ControlMode.OBSERVE) == null, "Mode change failed");
                    helper.assertTrue(!mob.isNoAi(), "Observation did not restore vanilla AI");
                    helper.assertTrue(BrainAttachmentService.detach(mob), "Detach failed");
                    helper.assertTrue(!mob.isNoAi(), "Detach did not preserve original AI");
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void attachedZombieHasNoInnateChaseAndCactusContactIsPainful(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                                && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(),
                        "Connectome is not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(2, 0, 1), Blocks.SAND);
                    helper.setBlock(new BlockPos(2, 1, 1), Blocks.CACTUS);
                    subject[0] = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
                    helper.assertTrue(BrainAttachmentService.attach(subject[0]), "Zombie attachment failed");
                    helper.assertTrue(subject[0].isNoAi(), "Attached zombie retained vanilla chase goals");
                    subject[0].setPos(subject[0].getX() + 0.22, subject[0].getY(), subject[0].getZ());
                    BrainAttachmentService.setMode(subject[0], ControlMode.OBSERVE);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    var sample = BrainAttachmentService.roster(helper.getLevel()).stream()
                            .filter(entry -> entry.entityId() == subject[0].getId())
                            .map(lab.neurolab.minecraft.BrainRosterPayload.Entry::sample)
                            .findFirst().orElse(null);
                    helper.assertTrue(sample != null && sample.pain() >= 0.5f,
                            "Cactus side contact did not enter negative feedback telemetry");
                    helper.assertTrue(BrainAttachmentService.detach(subject[0]), "Zombie detach failed");
                    helper.assertTrue(!subject[0].isNoAi(), "Zombie vanilla AI was not restored");
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void attachedBeeDoesNotUseVanillaFlowerSeeking(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                                && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(),
                        "Connectome is not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    subject[0] = helper.spawn(EntityType.BEE, new BlockPos(1, 1, 1));
                    helper.assertTrue(BrainAttachmentService.attach(subject[0]), "Bee attachment failed");
                    helper.assertTrue(subject[0].isNoAi(), "Attached bee retained vanilla flower-seeking goals");
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertTrue(subject[0].isAlive(), "Attached bee did not survive assisted control");
                    helper.assertTrue(BrainAttachmentService.setMode(subject[0], ControlMode.OBSERVE) == null,
                            "Bee observation mode failed");
                    helper.assertTrue(!subject[0].isNoAi(), "Bee vanilla AI was not restored in observation mode");
                    BrainAttachmentService.detach(subject[0]);
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void attachedLandMobKeepsGravityAndDoesNotHover(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        double[] spawnY = new double[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                                && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(),
                        "Connectome is not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    for (int y = 1; y < 7; y++) helper.setBlock(new BlockPos(1, y, 1), Blocks.AIR);
                    for (int y = 1; y < 7; y++) for (int edge = 0; edge < 3; edge++) {
                        helper.setBlock(new BlockPos(0, y, edge), Blocks.STONE);
                        helper.setBlock(new BlockPos(2, y, edge), Blocks.STONE);
                        if (edge == 1) {
                            helper.setBlock(new BlockPos(edge, y, 0), Blocks.STONE);
                            helper.setBlock(new BlockPos(edge, y, 2), Blocks.STONE);
                        }
                    }
                    subject[0] = helper.spawn(EntityType.SHEEP, new BlockPos(1, 5, 1));
                    spawnY[0] = subject[0].getY();
                    helper.assertTrue(BrainAttachmentService.attach(subject[0]), "Land mob attachment failed");
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(!subject[0].isNoGravity(), "Assisted attachment disabled gravity");
                    helper.assertTrue(subject[0].getY() < spawnY[0] - 0.1,
                            "Attached land mob did not descend from spawn height");
                    helper.assertTrue(BrainAttachmentService.setMode(subject[0], ControlMode.OBSERVE) == null,
                            "Could not isolate gravity from assisted movement");
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(subject[0].onGround(), "Land mob did not settle after assistance was disabled; y="
                            + subject[0].getY() + " motion=" + subject[0].getDeltaMovement());
                    helper.assertTrue(subject[0].getY() < helper.absolutePos(new BlockPos(1, 1, 1)).getY() + 0.5,
                            "Attached land mob is hovering instead of falling; y="
                            + subject[0].getY());
                    BrainAttachmentService.detach(subject[0]);
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void neuralModeLandMobUsesGravityWithoutVanillaAi(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                                && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(),
                        "Connectome is not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    for (int y = 1; y < 7; y++) for (int edge = 0; edge < 3; edge++) {
                        helper.setBlock(new BlockPos(0, y, edge), Blocks.STONE);
                        helper.setBlock(new BlockPos(2, y, edge), Blocks.STONE);
                        if (edge == 1) {
                            helper.setBlock(new BlockPos(edge, y, 0), Blocks.STONE);
                            helper.setBlock(new BlockPos(edge, y, 2), Blocks.STONE);
                        }
                    }
                    subject[0] = helper.spawn(EntityType.SHEEP, new BlockPos(1, 5, 1));
                    helper.assertTrue(BrainAttachmentService.attach(subject[0]), "Neural-mode attachment failed");
                    helper.assertTrue(BrainAttachmentService.setMode(subject[0], ControlMode.NEURAL) == null,
                            "Could not enable neural mode");
                    helper.assertTrue(subject[0].isNoAi(), "Neural mode left vanilla AI enabled");
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(subject[0].onGround(), "Neural-mode land mob did not settle; y="
                            + subject[0].getY());
                    helper.assertTrue(subject[0].getY() < 1.5, "Neural-mode land mob hovered without vanilla AI; y="
                            + subject[0].getY());
                    BrainAttachmentService.detach(subject[0]);
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000, batch = "neuro_fly_landing")
    public static void flyAutomaticallyAttachesAndRemainsAlive(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        Vec3[] start = new Vec3[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var future = BrainAttachmentService.connectomeStatus();
                    helper.assertTrue(future.isDone() && !future.isCompletedExceptionally(),
                            "Connectome is not ready yet");
                })
                .thenExecute(() -> {
                    helper.assertTrue(!BrainAttachmentService.connectomeStatus().isCompletedExceptionally(), "Dataset loading failed");
                    for (int x = 0; x < 61; x++) for (int z = 0; z < 61; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    subject[0] = helper.spawn(NeuroLabEntities.NEURO_FLY.get(), new BlockPos(30, 1, 30));
                    helper.assertTrue(Math.abs(subject[0].getBbWidth() - 1.0f) < 0.001f
                                    && Math.abs(subject[0].getBbHeight() - 1.0f) < 0.001f,
                            "Neuro Fly does not occupy a one-block hitbox");
                    helper.assertTrue(Math.abs(subject[0].getBoundingBox().getXsize() - 1.0) < 0.001
                                    && Math.abs(subject[0].getBoundingBox().getYsize() - 1.0) < 0.001
                                    && Math.abs(subject[0].getBoundingBox().getZsize() - 1.0) < 0.001,
                            "Neuro Fly's live collision box does not match its one-block dimensions");
                    start[0] = subject[0].position();
                })
                // The accelerated harness does not establish a reliable real-time landing interval.
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(subject[0].isAlive() && !subject[0].isRemoved(), "Fly disappeared");
                    helper.assertTrue(subject[0].isPersistenceRequired(), "Fly is eligible for distance despawn");
                    helper.assertTrue(BrainAttachmentService.isAttached(subject[0]), "Fly did not auto-attach");
                    helper.assertTrue(subject[0].position().distanceToSqr(start[0]) > 0.04,
                            "Attached Neuro Fly did not move in the petri-world flight controller");
                    helper.assertTrue(Math.abs(subject[0].getY() - start[0].y) > 0.08,
                            "Attached Neuro Fly did not move vertically; lift may not reach entity movement");
                    int ground = helper.getLevel().getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            subject[0].blockPosition().getX(), subject[0].blockPosition().getZ());
                    helper.assertTrue(subject[0].getY() <= ground + NeuroFlyConfig.ALTITUDE_CEILING.get() + 0.5,
                            "Assisted Neuro Fly climbed beyond its configured terrain-relative ceiling");
                    helper.assertTrue(helper.getLevel().noCollision(subject[0]),
                            "Neuro Fly collision box overlaps a solid block after flight");
                    BrainAttachmentService.detach(subject[0]);
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void flyUnderRoofUsesTheFloorBelowIt(GameTestHelper helper) {
        NeuroFlyEntity[] fly = new NeuroFlyEntity[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                                && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(),
                        "Connectome is not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                        helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                    }
                    fly[0] = helper.spawn(NeuroLabEntities.NEURO_FLY.get(), new BlockPos(2, 1, 2));
                    fly[0].setYRot(0);
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(BrainAttachmentService.isAttached(fly[0]), "Roof subject did not auto-attach");
                    helper.assertTrue(fly[0].getY() < helper.absolutePos(new BlockPos(0, 2, 0)).getY() - 0.05,
                            "Roof was mistaken for the floor and the fly climbed into its underside");
                    helper.assertTrue(helper.getLevel().noCollision(fly[0]),
                            "Fly overlaps the roof after assisted flight");
                    BrainAttachmentService.detach(fly[0]);
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void flyPerchesOnAWallWithItsBodySnappedToTheSurface(GameTestHelper helper) {
        NeuroFlyEntity[] fly = new NeuroFlyEntity[1];
        Vec3[] before = new Vec3[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                                && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(),
                        "Connectome is not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    for (int x = 0; x < 3; x++) for (int y = 1; y < 3; y++)
                        helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
                    fly[0] = helper.spawn(NeuroLabEntities.NEURO_FLY.get(), new BlockPos(1, 1, 1));
                    fly[0].setYRot(0);
                    before[0] = fly[0].position();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(fly[0].isPerched(), "Neuro Fly failed to enter the wall-perch pose");
                    helper.assertTrue(fly[0].position().distanceToSqr(before[0]) > 0.02,
                            "Wall-perch alignment did not move the fly to the block face");
                    helper.assertTrue(helper.getLevel().noCollision(fly[0]),
                            "Wall-perched Neuro Fly overlaps the wall hitbox");
                    BrainAttachmentService.detach(fly[0]);
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000, skyAccess = true,
            batch = "neuro_rain_shelter")
    public static void neuroFlySeeksAndStaysUnderARoofDuringRain(GameTestHelper helper) {
        NeuroFlyEntity[] fly = new NeuroFlyEntity[1];
        boolean[] rainStartedExposed = {false};
        String[] rainStartStatus = {"not measured"};
        BlockPos shelter = new BlockPos(2, 2, 1);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                                && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(),
                        "Connectome is not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    for (int y = 2; y < 380; y++)
                        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    helper.setBlock(new BlockPos(2, 3, 1), Blocks.OAK_PLANKS);
                    helper.getLevel().setWeatherParameters(0, 1200, true, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().isRaining(),
                        "Rain did not begin in the test world"))
                .thenExecute(() -> {
                    fly[0] = helper.spawn(NeuroLabEntities.NEURO_FLY.get(), new BlockPos(0, 1, 1));
                    BlockPos spawnEye = BlockPos.containing(fly[0].getEyePosition());
                    boolean raining = helper.getLevel().isRaining();
                    boolean skyOpen = helper.getLevel().canSeeSky(spawnEye);
                    boolean rainAtSubject = helper.getLevel().isRainingAt(spawnEye);
                    rainStartedExposed[0] = raining && skyOpen && rainAtSubject;
                    rainStartStatus[0] = "spawnEye=" + spawnEye + " raining=" + raining
                            + " skyOpen=" + skyOpen + " rainAtSubject=" + rainAtSubject;
                    helper.assertTrue(BrainAttachmentService.attach(fly[0]),
                            "Could not synchronously attach rain test brain");
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    BlockPos eye = BlockPos.containing(fly[0].getEyePosition());
                    boolean sheltered = !helper.getLevel().canSeeSky(eye);
                    boolean nearShelter = fly[0].blockPosition().closerThan(helper.absolutePos(shelter), 2.0);
                    boolean attached = BrainAttachmentService.isAttached(fly[0]);
                    boolean collisionFree = helper.getLevel().noCollision(fly[0]);
                    Vec3 finalPosition = fly[0].position();
                    String status = BrainAttachmentService.describe(fly[0]) + " · position " + finalPosition;
                    helper.getLevel().setWeatherParameters(1200, 0, false, false);
                    BrainAttachmentService.detach(fly[0]);
                    helper.assertTrue(attached,
                            "Rain test fly did not get its brain attached");
                    helper.assertTrue(rainStartedExposed[0],
                            "Rain test fixture never exposed the subject to precipitation; " + rainStartStatus[0]);
                    helper.assertTrue(sheltered,
                            "Neuro Fly remained exposed instead of entering the roof shelter; pos="
                                    + finalPosition + " eye=" + eye + " · " + status);
                    helper.assertTrue(nearShelter,
                            "Neuro Fly reached cover at an unexpected location; " + status);
                    helper.assertTrue(collisionFree,
                            "Rain-sheltering Neuro Fly overlaps a solid block; " + status);
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void attachingBrainPreservesVanillaMobSizeAndGroundLevel(GameTestHelper helper) {
        List<EntityType<? extends Mob>> types = List.of(EntityType.SHEEP);
        Mob[] subjects = new Mob[types.size()];
        float[] widths = new float[types.size()];
        float[] heights = new float[types.size()];
        double[] yBefore = new double[types.size()];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var future = BrainAttachmentService.connectomeStatus();
                    helper.assertTrue(future.isDone() && !future.isCompletedExceptionally(),
                            "Connectome is not ready yet");
                })
                .thenExecute(() -> {
                    helper.assertTrue(!BrainAttachmentService.connectomeStatus().isCompletedExceptionally(),
                            "Dataset loading failed");
                    for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    for (int i = 0; i < types.size(); i++) {
                        Mob mob = helper.spawn(types.get(i), new BlockPos(1, 1, 1));
                        subjects[i] = mob;
                        widths[i] = mob.getBbWidth();
                        heights[i] = mob.getBbHeight();
                        yBefore[i] = mob.getY();
                        String failure = BrainAttachmentService.attachFailure(mob);
                        helper.assertTrue(failure == null, "Attach rejected for " + types.get(i) + ": " + failure);
                        helper.assertTrue(BrainAttachmentService.attach(mob), "Could not attach " + types.get(i));
                        helper.assertTrue(BrainAttachmentService.setMode(mob, ControlMode.OBSERVE) == null,
                                "Could not set observation mode for " + types.get(i));
                        assertVanillaDimensions(helper, mob, widths[i], heights[i], "immediately after attaching");
                    }
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    for (int i = 0; i < subjects.length; i++) {
                        Mob mob = subjects[i];
                        assertVanillaDimensions(helper, mob, widths[i], heights[i], "after ticking");
                        helper.assertTrue(Math.abs(mob.getY() - yBefore[i]) < 0.15,
                                "Attached " + types.get(i) + " drifted vertically in observation mode");
                        helper.assertTrue(BrainAttachmentService.detach(mob), "Could not detach " + types.get(i));
                    }
                }).thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3", timeoutTicks = 12000)
    public static void chairPullsSubjectAndReleasesAfterSixSeconds(GameTestHelper helper) {
        Mob[] subject = new Mob[1];
        PetriDishBlockEntity[] chair = new PetriDishBlockEntity[1];
        Vec3[] start = new Vec3[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BrainAttachmentService.connectomeStatus().isDone()
                                && !BrainAttachmentService.connectomeStatus().isCompletedExceptionally(),
                        "Connectome is not ready"))
                .thenExecute(() -> {
                    for (int x = 0; x < 9; x++) for (int z = 0; z < 3; z++)
                        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    BlockPos chairPos = new BlockPos(1, 1, 1);
                    helper.setBlock(chairPos, NeuroLabEntities.PETRI_DISH.get());
                    helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(chairPos))
                                    instanceof PetriDishBlockEntity,
                            "Experiment chair block entity did not load");
                    chair[0] = (PetriDishBlockEntity) helper.getLevel()
                            .getBlockEntity(helper.absolutePos(chairPos));
                    subject[0] = helper.spawn(EntityType.SHEEP, new BlockPos(6, 1, 1));
                    start[0] = subject[0].position();
                    helper.assertTrue(BrainAttachmentService.attach(subject[0]), "Could not attach chair subject");
                    chair[0].startProcedure(subject[0]);
                    helper.assertTrue(subject[0].isNoAi(), "Chair procedure did not restrain vanilla AI");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(chair[0].procedureStartTick() != 0,
                            "Chair released the subject before the six-second procedure ended");
                    helper.assertTrue(subject[0].isNoAi(), "Subject was not restrained during the procedure");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(chair[0].procedureStartTick() == 0, "Chair procedure did not finish at 6 seconds");
                    helper.assertTrue(!subject[0].isNoAi(), "Chair did not restore the subject's AI");
                    helper.assertTrue(BrainAttachmentService.modeOf(subject[0]) == ControlMode.ASSISTED,
                            "Chair did not restore the subject's previous neural mode");
                    helper.assertTrue(subject[0].position().distanceToSqr(start[0]) > 1,
                            "Chair did not pull the subject from nearby range");
                    BrainAttachmentService.detach(subject[0]);
                }).thenSucceed();
    }

    private static void assertVanillaDimensions(GameTestHelper helper, Mob mob,
                                                float width, float height, String phase) {
        helper.assertTrue(Math.abs(mob.getBbWidth() - width) < 0.001f
                        && Math.abs(mob.getBbHeight() - height) < 0.001f,
                mob.getType() + " hitbox dimensions changed " + phase);
        helper.assertTrue(Math.abs(mob.getBoundingBox().getXsize() - width) < 0.001
                        && Math.abs(mob.getBoundingBox().getYsize() - height) < 0.001
                        && Math.abs(mob.getBoundingBox().getZsize() - width) < 0.001,
                mob.getType() + " live collision box changed " + phase);
    }
}
