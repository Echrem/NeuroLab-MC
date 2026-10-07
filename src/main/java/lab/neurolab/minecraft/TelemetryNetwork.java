package lab.neurolab.minecraft;

import lab.neurolab.client.ClientTelemetry;
import lab.neurolab.client.TelemetryRoster;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

public final class TelemetryNetwork {
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> ROSTER_REQUESTS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> SYNAPSE_REQUESTS =
            new java.util.concurrent.ConcurrentHashMap<>();
    public static final Channel<CustomPacketPayload> CHANNEL = createChannel();
    private static final Channel<CustomPacketPayload> CAMERA_TELEMETRY_CHANNEL = createCameraTelemetryChannel();
    private static final Channel<CustomPacketPayload> VIEW_CHANNEL = createViewChannel();
    private static final Channel<CustomPacketPayload> OPEN_MONITOR_CHANNEL = createOpenMonitorChannel();
    private static final Channel<CustomPacketPayload> MONITOR_ACTION_CHANNEL = createMonitorActionChannel();
    private static final Channel<CustomPacketPayload> ROSTER_REQUEST_CHANNEL = createRosterRequestChannel();
    private static final Channel<CustomPacketPayload> ROSTER_RESPONSE_CHANNEL = createRosterResponseChannel();
    private static final Channel<CustomPacketPayload> SYNAPSE_REQUEST_CHANNEL = createSynapseRequestChannel();
    private static final Channel<CustomPacketPayload> SYNAPSE_RESPONSE_CHANNEL = createSynapseResponseChannel();

    private TelemetryNetwork() {}

    private static Channel<CustomPacketPayload> createChannel() {
        PayloadConnection<CustomPacketPayload> connection = ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "main"))
                .networkProtocolVersion(8)
                .optional()
                .payloadChannel();
        return connection.play()
                .flow(PacketFlow.CLIENTBOUND)
                .addMain(BrainTelemetryPayload.TYPE, BrainTelemetryPayload.CODEC,
                        (payload, context) -> {
                            if (context.isClientSide()) {
                                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                                        () -> () -> ClientTelemetry.accept(payload));
                            }
                        })
                .build();
    }

    private static Channel<CustomPacketPayload> createCameraTelemetryChannel() {
        return ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "camera_telemetry"))
                .networkProtocolVersion(1).optional().payloadChannel().play().flow(PacketFlow.CLIENTBOUND)
                .addMain(CameraTelemetryPayload.TYPE, CameraTelemetryPayload.CODEC, (payload, context) -> {
                    if (context.isClientSide()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> lab.neurolab.client.ClientViewTelemetry.accept(payload.sample()));
                }).build();
    }

    private static Channel<CustomPacketPayload> createViewChannel() {
        return ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "view"))
                .networkProtocolVersion(1).optional().payloadChannel().play()
                .flow(PacketFlow.SERVERBOUND)
                .addMain(NeuroViewRequestPayload.TYPE, NeuroViewRequestPayload.CODEC, (payload, context) -> {
                    var player = context.getSender();
                    if (player == null) return;
                    if (payload.entityId() < 0) {
                        player.setCamera(player);
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Neuro view closed."), true);
                        return;
                    }
                    var entity = player.level().getEntity(payload.entityId());
                    if (entity instanceof net.minecraft.world.entity.Mob mob
                            && BrainAttachmentService.isAttached(mob) && player.distanceToSqr(mob) <= 48 * 48) {
                        player.setCamera(mob);
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "Viewing " + mob.getName().getString() + " · V returns to your own view."), true);
                    } else {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "Aim at a brain-attached mob within 48 blocks."), true);
                    }
                }).build();
    }

    private static Channel<CustomPacketPayload> createRosterRequestChannel() {
        return ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "roster_request"))
                .networkProtocolVersion(1).optional().payloadChannel().play().flow(PacketFlow.SERVERBOUND)
                .addMain(BrainRosterRequestPayload.TYPE, BrainRosterRequestPayload.CODEC, (payload, context) -> {
                    var player = context.getSender();
                    if (!(player instanceof ServerPlayer serverPlayer)
                            || !(serverPlayer.level() instanceof ServerLevel level)) return;
                    long now = System.nanoTime();
                    Long previous = ROSTER_REQUESTS.get(serverPlayer.getUUID());
                    if (previous != null && now - previous < 500_000_000L) return;
                    ROSTER_REQUESTS.put(serverPlayer.getUUID(), now);
                    ROSTER_RESPONSE_CHANNEL.send(BrainAttachmentService.rosterPayload(level),
                            PacketDistributor.PLAYER.with(serverPlayer));
                }).build();
    }

    private static Channel<CustomPacketPayload> createRosterResponseChannel() {
        return ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "roster_response"))
                .networkProtocolVersion(1).optional().payloadChannel().play().flow(PacketFlow.CLIENTBOUND)
                .addMain(BrainRosterPayload.TYPE, BrainRosterPayload.CODEC, (payload, context) -> {
                    if (context.isClientSide()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                        TelemetryRoster.replace(payload);
                        for (BrainRosterPayload.Entry entry : payload.entries())
                            if (entry.sample() != null) ClientTelemetry.accept(entry.sample());
                    });
                }).build();
    }

    private static Channel<CustomPacketPayload> createSynapseRequestChannel() {
        return ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "synapse_request"))
                .networkProtocolVersion(1).optional().payloadChannel().play().flow(PacketFlow.SERVERBOUND)
                .addMain(SynapseInspectionRequestPayload.TYPE, SynapseInspectionRequestPayload.CODEC, (payload, context) -> {
                    if (!(context.getSender() instanceof ServerPlayer player)) return;
                    long now = System.nanoTime();
                    Long previous = SYNAPSE_REQUESTS.get(player.getUUID());
                    if (previous != null && now - previous < 300_000_000L) return;
                    SYNAPSE_REQUESTS.put(player.getUUID(), now);
                    if (!(player.level().getEntity(payload.entityId()) instanceof Mob mob)
                            || player.distanceToSqr(mob) > 48 * 48 || !BrainAttachmentService.isAttached(mob)) return;
                    SynapseInspectionPayload response = BrainAttachmentService.inspectSynapses(mob, payload.neuronId());
                    if (response != null) SYNAPSE_RESPONSE_CHANNEL.send(response, PacketDistributor.PLAYER.with(player));
                }).build();
    }

    private static Channel<CustomPacketPayload> createSynapseResponseChannel() {
        return ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "synapse_response"))
                .networkProtocolVersion(1).optional().payloadChannel().play().flow(PacketFlow.CLIENTBOUND)
                .addMain(SynapseInspectionPayload.TYPE, SynapseInspectionPayload.CODEC, (payload, context) -> {
                    if (context.isClientSide()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> lab.neurolab.client.SynapseInspectionState.accept(payload));
                }).build();
    }

    private static Channel<CustomPacketPayload> createOpenMonitorChannel() {
        return ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "monitor_open"))
                .networkProtocolVersion(1).optional().payloadChannel().play().flow(PacketFlow.CLIENTBOUND)
                .addMain(OpenNeuroMonitorPayload.TYPE, OpenNeuroMonitorPayload.CODEC, (payload, context) -> {
                    if (context.isClientSide()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> lab.neurolab.client.NeuroAnalysisScreen.openAtMonitor(payload.position()));
                }).build();
    }

    private static Channel<CustomPacketPayload> createMonitorActionChannel() {
        return ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "monitor_actions"))
                .networkProtocolVersion(4).optional().payloadChannel().play().flow(PacketFlow.SERVERBOUND)
                .addMain(NeuroMonitorActionPayload.TYPE, NeuroMonitorActionPayload.CODEC,
                        (payload, context) -> handleMonitorAction(context.getSender(), payload)).build();
    }

    private static void handleMonitorAction(ServerPlayer player, NeuroMonitorActionPayload payload) {
        if (player == null || !(player.level() instanceof ServerLevel level)
                || player.distanceToSqr(payload.position().getCenter()) > 64
                || !level.hasChunkAt(payload.position())
                || !(level.getBlockEntity(payload.position()) instanceof NeuroMonitorBlockEntity monitor)) return;
        Mob subject = monitor.subject() == null ? null
                : level.getEntity(monitor.subject()) instanceof Mob mob ? mob : null;
        switch (payload.action()) {
            case ATTACH_NEAREST -> {
                if (subject == null || !subject.isAlive() || subject.distanceToSqr(payload.position().getCenter()) > 32 * 32) {
                    subject = linkedPetriSubject(level, payload.position());
                    if (subject == null)
                        subject = level.getEntitiesOfClass(Mob.class, new AABB(payload.position()).inflate(6), Mob::isAlive)
                                .stream().min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(payload.position().getCenter())))
                                .orElse(null);
                }
                if (subject == null) {
                    player.displayClientMessage(Component.literal("Place a living mob within 6 blocks of the monitor."), true);
                    return;
                }
                if (!BrainAttachmentService.isAttached(subject)) {
                    String failure = BrainAttachmentService.attachFailure(subject);
                    if (failure != null || !BrainAttachmentService.attach(subject)) {
                        player.displayClientMessage(Component.literal(failure == null ? "Could not attach a brain." : failure), true);
                        return;
                    }
                }
                monitor.bind(subject.getUUID());
                String trialFailure = BrainAttachmentService.startTrial(subject, "neuro-monitor", 1200);
                player.displayClientMessage(Component.literal("Monitor linked: " + subject.getName().getString()
                        + (trialFailure == null ? " · 60 s trial started." : " · trial already active.")), true);
            }
            case SET_MODE -> {
                if (!validSubject(player, payload.position(), subject)) return;
                String failure = BrainAttachmentService.setMode(subject, payload.mode());
                player.displayClientMessage(Component.literal(failure == null
                        ? "Control mode: " + payload.mode().id() : failure), true);
            }
            case END_TRIAL -> {
                if (!validSubject(player, payload.position(), subject)) return;
                String failure = BrainAttachmentService.endTrial(subject);
                player.displayClientMessage(Component.literal(failure == null ? "Trial ended and recorded." : failure), true);
            }
            case DETACH -> {
                if (!validSubject(player, payload.position(), subject)) return;
                BrainAttachmentService.endTrial(subject);
                BrainAttachmentService.detach(subject);
                monitor.clearSubject();
                player.displayClientMessage(Component.literal("Subject detached; vanilla AI restored."), true);
            }
            case SAVE_MIND -> {
                if (!validSubject(player, payload.position(), subject)) return;
                long[] learned = BrainAttachmentService.copyMindSnapshot(subject);
                if (!monitor.saveMindProfile(payload.profileName(), learned)) {
                    player.displayClientMessage(Component.literal("Use a 1–32 character name (letters, numbers, spaces, _ or -); up to 16 imprints."), true);
                    return;
                }
                player.displayClientMessage(Component.literal("Saved learned-synapse imprint: " + payload.profileName()), true);
            }
            case APPLY_MIND -> {
                if (!validSubject(player, payload.position(), subject)) return;
                long[] learned = monitor.mindProfile(payload.profileName());
                if (learned == null) {
                    player.displayClientMessage(Component.literal("No saved imprint with that name."), true);
                    return;
                }
                String failure = BrainAttachmentService.applyMindCopy(subject, learned);
                player.displayClientMessage(Component.literal(failure == null
                        ? "Applied learned-synapse imprint: " + payload.profileName() : failure), true);
            }
            case KILL_SUBJECT -> {
                // Entity IDs are client-view scoped and can be stale when the PC roster
                // contains a mob outside the client's tracking range. Resolve the stable
                // UUID first; retain ID/monitor binding only for older callers.
                java.util.UUID requestedUuid = payload.targetUuid() != null ? payload.targetUuid()
                        : payload.targetEntityId() < 0 && subject != null ? subject.getUUID() : null;
                Mob target = requestedUuid != null
                        ? level.getEntity(requestedUuid) instanceof Mob mob ? mob : null
                        : payload.targetEntityId() < 0 ? subject
                        : level.getEntity(payload.targetEntityId()) instanceof Mob mob ? mob : null;
                if (requestedUuid == null || target == null
                        || !target.getUUID().equals(requestedUuid)
                        || !target.isAlive() || target.level() != level
                        || !BrainAttachmentService.isAttached(target)) {
                    player.displayClientMessage(Component.literal(
                            "Selected subject is no longer active in this experiment."), true);
                    return;
                }
                String name = target.getName().getString();
                boolean clearBinding = target.getUUID().equals(monitor.subject());
                if (!BrainAttachmentService.killSubject(target)) {
                    player.displayClientMessage(Component.literal("Selected mob is no longer alive."), true);
                    return;
                }
                if (clearBinding) monitor.clearSubject();
                player.displayClientMessage(Component.literal("Experiment subject terminated: " + name), true);
            }
        }
    }

    private static boolean validSubject(ServerPlayer player, BlockPos monitor, Mob subject) {
        if (subject != null && subject.isAlive() && subject.level() == player.level()
                && subject.distanceToSqr(monitor.getCenter()) <= 32 * 32
                && BrainAttachmentService.isAttached(subject)) return true;
        player.displayClientMessage(Component.literal("No active subject. Use ATTACH NEAREST first."), true);
        return false;
    }

    private static Mob linkedPetriSubject(ServerLevel level, BlockPos monitor) {
        Mob nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos dishPos : BlockPos.betweenClosed(monitor.offset(-8, -4, -8), monitor.offset(8, 4, 8))) {
            if (!(level.getBlockEntity(dishPos) instanceof PetriDishBlockEntity dish) || dish.subject() == null) continue;
            if (level.getEntity(dish.subject()) instanceof Mob candidate && candidate.isAlive()
                    && BrainAttachmentService.isAttached(candidate)) {
                double distance = candidate.distanceToSqr(monitor.getCenter());
                if (distance < nearestDistance) { nearest = candidate; nearestDistance = distance; }
            }
        }
        return nearest;
    }

    public static void openMonitor(ServerPlayer player, BlockPos position) {
        OPEN_MONITOR_CHANNEL.send(new OpenNeuroMonitorPayload(position), PacketDistributor.PLAYER.with(player));
    }

    public static void monitorAction(BlockPos position, NeuroMonitorActionPayload.Action action,
                                     lab.neurolab.brain.ControlMode mode) {
        monitorAction(position, action, mode, "");
    }

    public static void monitorAction(BlockPos position, NeuroMonitorActionPayload.Action action,
                                     lab.neurolab.brain.ControlMode mode, String profileName) {
        MONITOR_ACTION_CHANNEL.send(new NeuroMonitorActionPayload(position, action, mode, profileName),
                PacketDistributor.SERVER.noArg());
    }

    public static void monitorAction(BlockPos position, NeuroMonitorActionPayload.Action action,
                                     lab.neurolab.brain.ControlMode mode, int targetEntityId) {
        MONITOR_ACTION_CHANNEL.send(new NeuroMonitorActionPayload(position, action, mode, "", targetEntityId),
                PacketDistributor.SERVER.noArg());
    }

    public static void monitorAction(BlockPos position, NeuroMonitorActionPayload.Action action,
                                     lab.neurolab.brain.ControlMode mode, int targetEntityId,
                                     java.util.UUID targetUuid) {
        MONITOR_ACTION_CHANNEL.send(new NeuroMonitorActionPayload(position, action, mode, "",
                targetEntityId, targetUuid), PacketDistributor.SERVER.noArg());
    }

    public static void requestView(int entityId) {
        VIEW_CHANNEL.send(new NeuroViewRequestPayload(entityId), PacketDistributor.SERVER.noArg());
    }

    public static void requestRoster() {
        ROSTER_REQUEST_CHANNEL.send(new BrainRosterRequestPayload(), PacketDistributor.SERVER.noArg());
    }

    public static void requestSynapses(int entityId, int neuronId) {
        SYNAPSE_REQUEST_CHANNEL.send(new SynapseInspectionRequestPayload(entityId, neuronId), PacketDistributor.SERVER.noArg());
    }

    public static void sendCameraTelemetry(Mob mob, BrainTelemetryPayload payload) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players())
            if (player.getCamera() == mob)
                CAMERA_TELEMETRY_CHANNEL.send(new CameraTelemetryPayload(payload),
                        PacketDistributor.PLAYER.with(player));
    }

    public static void initialize() {
    }
}
