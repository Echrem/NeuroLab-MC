package lab.neurolab.minecraft;

import lab.neurolab.brain.FlyBrain;
import lab.neurolab.brain.EmbodimentDecoder;
import lab.neurolab.brain.ControlMode;
import lab.neurolab.brain.TrialSession;
import lab.neurolab.brain.StimulusSchedule;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

@Mod(NeuroLabMod.MOD_ID)
public final class NeuroLabMod {
    public static final String MOD_ID = "neurolab";

    public NeuroLabMod(FMLJavaModLoadingContext context) {
        var modBus = context.getModEventBus();
        NeuroLabEntities.ENTITY_TYPES.register(modBus);
        NeuroLabEntities.ITEMS.register(modBus);
        NeuroLabEntities.BLOCKS.register(modBus);
        NeuroLabEntities.BLOCK_ENTITY_TYPES.register(modBus);
        context.registerConfig(ModConfig.Type.COMMON, NeuroFlyConfig.SPEC,
                "neurolab-common.toml");
        TelemetryNetwork.initialize();
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void migrateCommonConfig(ModConfigEvent.Loading event) {
            if (event.getConfig().getSpec() != NeuroFlyConfig.SPEC
                    || NeuroFlyConfig.SAMPLE_LIMIT_CONFIG_VERSION.get() >= 2) return;
            int current = NeuroFlyConfig.ACTIVE_NEURON_SAMPLE_LIMIT.get();
            int migrated = NeuroFlyConfig.migrateSampleLimit(current,
                    NeuroFlyConfig.SAMPLE_LIMIT_CONFIG_VERSION.get());
            if (migrated != current) NeuroFlyConfig.ACTIVE_NEURON_SAMPLE_LIMIT.set(migrated);
            NeuroFlyConfig.SAMPLE_LIMIT_CONFIG_VERSION.set(2);
            NeuroFlyConfig.SAMPLE_LIMIT_CONFIG_VERSION.save();
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void useLabToolOnAnyMob(PlayerInteractEvent.EntityInteract event) {
            if (!(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity target)) return;
            var item = event.getItemStack().getItem();
            if (!(item instanceof NeuroMindImprinterItem || item instanceof NeuroStimulusProbeItem
                    || item instanceof NeuroTrialRecorderItem)) return;
            var result = item.interactLivingEntity(event.getItemStack(), event.getEntity(), target, event.getHand());
            event.setCancellationResult(result);
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void recordWorldSound(net.minecraftforge.event.PlayLevelSoundEvent.AtPosition event) {
            BrainAttachmentService.recordSound(event.getLevel(), event.getPosition(),
                    event.getOriginalVolume(), event.getOriginalPitch());
        }

        @SubscribeEvent
        public static void recordEntitySound(net.minecraftforge.event.PlayLevelSoundEvent.AtEntity event) {
            BrainAttachmentService.recordSound(event.getLevel(), event.getEntity().position(),
                    event.getOriginalVolume(), event.getOriginalPitch());
        }

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void learnRepeatedPlayerThreat(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
            if (!event.isCanceled() && event.getAmount() > 0 && event.getEntity() instanceof Mob mob) {
                if (event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player player)
                    BrainAttachmentService.recordPlayerAttack(mob, player);
                else if (event.getSource().getEntity() == null)
                    BrainAttachmentService.recordEnvironmentalDamage(mob);
            }
        }

        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("neurolab").requires(source -> source.hasPermission(2))
                    .then(Commands.literal("attach").then(Commands.argument("mob", EntityArgument.entity())
                            .executes(ctx -> {
                                Entity entity = EntityArgument.getEntity(ctx, "mob");
                                if (!(entity instanceof Mob mob)) {
                                    ctx.getSource().sendFailure(Component.literal("Choose a living mob entity."));
                                    return 0;
                                }
                                if (BrainAttachmentService.isAttached(mob)) {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            mob.getName().getString() + " already has a NeuroLab brain."), false);
                                    return 1;
                                }
                                String failure = BrainAttachmentService.attachFailure(mob);
                                if (failure == null && BrainAttachmentService.attach(mob)) {
                                    ctx.getSource().sendSuccess(() -> Component.literal("NeuroLab connectome attached to "
                                            + mob.getName().getString() + ". Assisted mode preserves vanilla movement."), false);
                                    return 1;
                                }
                                String reason = failure == null ? BrainAttachmentService.attachFailure(mob) : failure;
                                ctx.getSource().sendFailure(Component.literal(reason == null
                                        ? "Could not attach this mob; check the server log for details." : reason));
                                return 0;
                            })))
                    .then(Commands.literal("detach").then(Commands.argument("mob", EntityArgument.entity())
                            .executes(ctx -> {
                                Entity entity = EntityArgument.getEntity(ctx, "mob");
                                if (!(entity instanceof Mob mob) || !BrainAttachmentService.detach(mob)) {
                                    ctx.getSource().sendFailure(Component.literal("That mob has no NeuroLab brain."));
                                    return 0;
                                }
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Neural control detached; the previous AI state was restored."), false);
                                return 1;
                            })))
                    .then(Commands.literal("status").executes(ctx -> {
                        var future = BrainAttachmentService.connectomeStatus();
                        String msg = future.isCompletedExceptionally() ? "Dataset failed to load"
                                : !future.isDone() ? "Dataset loading"
                                : "Dataset " + future.join().dataset + " · " + future.join().neurons()
                                + " neurons · " + future.join().connections() + " directed edges · mean out-degree "
                                + String.format(java.util.Locale.ROOT, "%.2f", future.join().graphStats.meanOutDegree(future.join().neurons()))
                                + " · max out-degree " + future.join().graphStats.maximumOutDegree()
                                + " · inhibitory neurons " + future.join().graphStats.inhibitoryNeurons()
                                + " · unknown polarity " + future.join().graphStats.unclassifiedSignNeurons();
                        var logging = TelemetryLog.status();
                        ctx.getSource().sendSuccess(() -> Component.literal(msg + " · attached brains "
                                + BrainAttachmentService.attachedCount() + " · shared simulation workers "
                                + BrainAttachmentService.workerCount() + " · log "
                                + (logging.failed() ? "FAILED" : logging.running() ? "recording" : "stopped")
                                + " · queued " + logging.queued() + " · dropped " + logging.dropped()), false);
                        return 1;
                    }))
                    .then(Commands.literal("neuron").then(Commands.argument("index", IntegerArgumentType.integer(0, 2_000_000))
                            .executes(ctx -> {
                                var future = BrainAttachmentService.connectomeStatus();
                                if (!future.isDone() || future.isCompletedExceptionally()) {
                                    ctx.getSource().sendFailure(Component.literal("Connectome is not ready yet."));
                                    return 0;
                                }
                                String details = future.join().describeNeuron(IntegerArgumentType.getInteger(ctx, "index"));
                                ctx.getSource().sendSuccess(() -> Component.literal(details), false);
                                return details.startsWith("Neuron index outside") ? 0 : 1;
                            })))
                    .then(Commands.literal("inspect").then(Commands.argument("mob", EntityArgument.entity())
                            .executes(ctx -> withMob(ctx, mob -> {
                                ctx.getSource().sendSuccess(() -> Component.literal(BrainAttachmentService.describe(mob)), false);
                                return null;
                            }))))
                    .then(Commands.literal("trial").then(Commands.argument("mob", EntityArgument.entity())
                            .then(Commands.argument("label", StringArgumentType.word())
                                    .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 12000))
                                            .executes(ctx -> withMob(ctx, mob -> {
                                                String label = StringArgumentType.getString(ctx, "label");
                                                int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                                String failure = BrainAttachmentService.startTrial(mob, label, ticks);
                                                if (failure == null) ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "Trial '" + label + "' started for " + ticks + " world ticks."), false);
                                                return failure;
                                            })))))
                    .then(Commands.literal("endtrial").then(Commands.argument("mob", EntityArgument.entity())
                            .executes(ctx -> withMob(ctx, mob -> {
                                String failure = BrainAttachmentService.endTrial(mob);
                                if (failure == null) ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Trial ended and recorded."), false);
                                return failure;
                            }))))
                    .then(Commands.literal("unstimulate").then(Commands.argument("mob", EntityArgument.entity())
                            .executes(ctx -> withMob(ctx, mob -> {
                                String failure = BrainAttachmentService.clearStimuli(mob);
                                if (failure == null) ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Test pulses cleared; natural senses continue."), false);
                                return failure;
                            }))))
                    .then(Commands.literal("mode").then(Commands.argument("mob", EntityArgument.entity())
                            .then(Commands.argument("mode", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                            java.util.Arrays.stream(ControlMode.values()).map(ControlMode::id), builder))
                                    .executes(ctx -> withMob(ctx, mob -> {
                                        ControlMode mode;
                                        try { mode = ControlMode.valueOf(StringArgumentType.getString(ctx, "mode")
                                                .toUpperCase(java.util.Locale.ROOT)); }
                                        catch (IllegalArgumentException invalid) { return "Use assisted, neural, or observe."; }
                                        String failure = BrainAttachmentService.setMode(mob, mode);
                                        if (failure == null) ctx.getSource().sendSuccess(() -> Component.literal(
                                                "Control mode: " + mode.id() + (mode == ControlMode.OBSERVE
                                                        ? ". Original AI restored; neural output is observation only."
                                                        : mode == ControlMode.NEURAL ? ". Reflex fallback disabled."
                                                        : ". Reflex fallback enabled when neural output is silent.")), false);
                                        return failure;
                                    })))))
                    .then(Commands.literal("stimulate")
                            .then(Commands.argument("mob", EntityArgument.entity())
                                    .then(Commands.argument("sense", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                    java.util.Arrays.stream(StimulusSchedule.Sense.values())
                                                            .map(sense -> sense.name().toLowerCase(java.util.Locale.ROOT)), builder))
                                            .then(Commands.argument("strength", FloatArgumentType.floatArg(0, 1))
                                                    .executes(ctx -> stimulate(ctx, 40))
                                                    .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                                                            .executes(ctx -> stimulate(ctx,
                                                                    IntegerArgumentType.getInteger(ctx, "ticks"))))))))));
        }

        private static int withMob(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                   java.util.function.Function<Mob, String> action)
                throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            Entity entity = EntityArgument.getEntity(ctx, "mob");
            String failure = entity instanceof Mob mob ? action.apply(mob) : "Choose a living mob entity.";
            if (failure == null) return 1;
            ctx.getSource().sendFailure(Component.literal(failure));
            return 0;
        }

        private static int stimulate(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                     int ticks) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            Entity entity = EntityArgument.getEntity(ctx, "mob");
            if (!(entity instanceof Mob mob)) {
                ctx.getSource().sendFailure(Component.literal("Choose a living mob entity."));
                return 0;
            }
            String failure = BrainAttachmentService.stimulate(mob,
                    StringArgumentType.getString(ctx, "sense"),
                    FloatArgumentType.getFloat(ctx, "strength"), ticks);
            if (failure != null) {
                ctx.getSource().sendFailure(Component.literal(failure));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Applied "
                    + StringArgumentType.getString(ctx, "sense") + " stimulus for " + ticks + " ticks."), false);
            return 1;
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
            BrainAttachmentService.tick();
        }

        @SubscribeEvent
        public static void onEntityUnload(EntityLeaveLevelEvent event) {
            if (event.getEntity() instanceof Mob mob) BrainAttachmentService.detach(mob);
        }

        @SubscribeEvent
        public static void onEntityJoin(EntityJoinLevelEvent event) {
            if (!event.getLevel().isClientSide()) BrainAttachmentService.queueAutoAttach(event.getEntity());
        }

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            BrainAttachmentService.startup();
            TelemetryLog.start(event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("neurolab/events.jsonl"),
                    net.minecraftforge.fml.ModList.get().getModContainerById(MOD_ID).orElseThrow()
                            .getModInfo().getVersion().toString(), "male-cns:v1.0");
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            BrainAttachmentService.shutdown();
            TelemetryLog.stop();
        }
    }

    static void sendTelemetry(Mob mob, FlyBrain.Snapshot snapshot, FlyBrain.Drive senses,
                              EmbodimentDecoder.Command body, boolean testStimulus, ControlMode mode,
                              TrialSession.Snapshot trial, float satiety, float hydration,
                              int learnedSynapseCount, int learningRevision) {
        BrainTelemetryPayload packet = new BrainTelemetryPayload(mob.getId(), (int) snapshot.spikes(), snapshot.synapticEvents(),
                snapshot.activeNeurons(), (float) snapshot.forward(), (float) snapshot.turn(),
                (float) snapshot.lift(), (float) BrainAttachmentService.realTimeFactor(mob), snapshot.escape(),
                senses.light(), senses.leftEye(), senses.rightEye(), senses.looming(), senses.tactile(),
                senses.odor(), senses.taste(), senses.pain(), senses.reward(),
                (float) body.forward(), (float) body.turn(),
                (float) body.lift(), body.escape(), body.reflex(), testStimulus, mode,
                senses.sound(), satiety, hydration, learnedSynapseCount, learningRevision,
                sampledNeuronIds(snapshot));
        BrainAttachmentService.updateTelemetry(mob, packet);
        TelemetryLog.record(packet, mob.getUUID(), mob.level().dimension().location().toString(),
                mob.level().getGameTime(), trial, snapshot.tick(), mob.getX(), mob.getY(), mob.getZ());
        TelemetryNetwork.CHANNEL.send(packet, PacketDistributor.TRACKING_ENTITY.with(mob));
        TelemetryNetwork.sendCameraTelemetry(mob, packet);
    }

    private static int[] sampledNeuronIds(FlyBrain.Snapshot snapshot) {
        return snapshot.firingNeuronIds(NeuroFlyConfig.ACTIVE_NEURON_SAMPLE_LIMIT.get());
    }
}
