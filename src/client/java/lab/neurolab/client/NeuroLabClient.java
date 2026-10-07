package lab.neurolab.client;

import com.mojang.blaze3d.platform.InputConstants;
import lab.neurolab.client.render.NeuroFlyModel;
import lab.neurolab.client.render.NeuroFlyRenderer;
import lab.neurolab.minecraft.NeuroLabEntities;
import lab.neurolab.minecraft.NeuroMonitorBlockEntity;
import lab.neurolab.minecraft.TelemetryNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "neurolab", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NeuroLabClient {
    private static final KeyMapping DASHBOARD = new KeyMapping("key.neurolab.analysis",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.neurolab");
    private static final KeyMapping VIEW_TARGET = new KeyMapping("key.neurolab.view",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.neurolab");
    private static final KeyMapping GUIDE = new KeyMapping("key.neurolab.guide",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.neurolab");
    private static final KeyMapping TOGGLE_VIEW_HUD = new KeyMapping("key.neurolab.view_hud",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.neurolab");
    private static boolean dashboardChordDown;

    private NeuroLabClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DASHBOARD);
        event.register(VIEW_TARGET);
        event.register(GUIDE);
        event.register(TOGGLE_VIEW_HUD);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NeuroFlyModel.LAYER, NeuroFlyModel::createLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NeuroLabEntities.NEURO_FLY.get(), NeuroFlyRenderer::new);
        event.registerBlockEntityRenderer(NeuroLabEntities.NEURO_MONITOR_ENTITY.get(), NeuroMonitorRenderer::new);
        event.registerBlockEntityRenderer(NeuroLabEntities.PETRI_DISH_ENTITY.get(), PetriDishRenderer::new);
    }

    @SubscribeEvent
    public static void registerViewOverlay(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.PRE_SLEEP_STACK,
                ResourceLocation.fromNamespaceAndPath("neurolab", "neuro_view"), ForgeLayeredDraw.HOTBAR,
                (graphics, deltaTracker) -> NeuroViewOverlay.render(graphics));
    }

    @Mod.EventBusSubscriber(modid = "neurolab", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientEvents {
        private static int lastCameraEntityId = Integer.MIN_VALUE;
        private static final NeuroViewPerspective VIEW_PERSPECTIVE = new NeuroViewPerspective();

        private ClientEvents() {}

        @SubscribeEvent
        public static void renderFeedback(RenderLivingEvent.Post<?, ?> event) {
            NeuroBrainHologramRenderer.render(event);
            FeedbackGlyphRenderer.render(event);
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.getWindow() == null) return;
            String dimension = client.level == null ? null : client.level.dimension().location().toString();
            if (dimension != null && TelemetryHistory.changeDimension(dimension)) {
                TelemetryRoster.clear();
                SynapseInspectionState.clear();
                ClientViewTelemetry.clear();
                TelemetryNetwork.requestRoster();
            }
            long window = client.getWindow().getWindow();
            var camera = client.getCameraEntity();
            int cameraEntityId = camera == null ? Integer.MIN_VALUE : camera.getId();
            boolean cameraChanged = cameraEntityId != lastCameraEntityId;
            lastCameraEntityId = cameraEntityId;
            boolean viewingMob = camera instanceof net.minecraft.world.entity.Mob && camera != client.player;
            CameraType perspective = VIEW_PERSPECTIVE.update(client.options.getCameraType(), viewingMob);
            if (perspective != client.options.getCameraType()) client.options.setCameraType(perspective);
            if (NeuroViewOverlay.shouldEnableOnCameraChange(cameraChanged,
                    viewingMob, camera == client.player))
                DashboardPreferences.enableViewOverlay();

            boolean control = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
            boolean chordDown = control && DASHBOARD.isDown();
            if (chordDown && !dashboardChordDown && client.screen == null) {
                var monitor = nearbyMonitor(client);
                if (monitor != null) NeuroAnalysisScreen.openAtMonitor(monitor.getBlockPos());
                else if (client.player != null) client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("Open the Neuro Monitor PC to view the lab dashboard."), true);
            }
            dashboardChordDown = chordDown;
            while (GUIDE.consumeClick()) {
                if (client.screen == null) client.setScreen(new NeuroGuideScreen(null));
            }
            while (TOGGLE_VIEW_HUD.consumeClick()) {
                if (client.screen instanceof net.minecraft.client.gui.screens.ChatScreen) continue;
                if (client.screen != null && client.screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox)
                    continue;
                DashboardPreferences.toggleViewOverlay();
                if (client.player != null) client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                DashboardPreferences.showViewOverlay() ? "screen.neurolab.monitor.hud_on"
                                        : "screen.neurolab.monitor.hud_off"), true);
            }
            while (VIEW_TARGET.consumeClick()) {
                if (client.screen != null || client.player == null) continue;
                if (client.getCameraEntity() != null && client.getCameraEntity() != client.player) {
                    TelemetryNetwork.requestView(-1);
                } else if (client.hitResult instanceof EntityHitResult hit) {
                    DashboardPreferences.enableViewOverlay();
                    TelemetryNetwork.requestView(hit.getEntity().getId());
                } else {
                    client.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Aim at a brain-attached mob and press V to view it."), true);
                }
            }
        }

        private static NeuroMonitorBlockEntity nearbyMonitor(Minecraft client) {
            if (client.level == null || client.player == null) return null;
            var origin = client.player.blockPosition();
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-6, -4, -6), origin.offset(6, 4, 6))) {
                if (client.level.getBlockEntity(pos) instanceof NeuroMonitorBlockEntity monitor) return monitor;
            }
            return null;
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            Minecraft client = Minecraft.getInstance();
            CameraType perspective = VIEW_PERSPECTIVE.update(client.options.getCameraType(), false);
            if (perspective != client.options.getCameraType()) client.options.setCameraType(perspective);
            lastCameraEntityId = Integer.MIN_VALUE;
            TelemetryHistory.clear();
            SynapseInspectionState.clear();
            ClientViewTelemetry.clear();
            TelemetryRoster.clear();
        }

    }
}
