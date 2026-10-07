package lab.neurolab.minecraft;

import lab.neurolab.entity.NeuroFlyEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = NeuroLabMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NeuroLabEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, NeuroLabMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NeuroLabMod.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, NeuroLabMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, NeuroLabMod.MOD_ID);

    public static final RegistryObject<EntityType<NeuroFlyEntity>> NEURO_FLY = ENTITY_TYPES.register("neuro_fly",
            () -> EntityType.Builder.of(NeuroFlyEntity::new, MobCategory.AMBIENT)
                .sized(1.0f, 1.0f)
                    .clientTrackingRange(8)
                    .build("neuro_fly"));

    public static final RegistryObject<Item> NEURO_FLY_SPAWN_EGG = ITEMS.register("neuro_fly_spawn_egg",
            () -> new ForgeSpawnEggItem(NEURO_FLY, 0x34343B, 0xC76D32, new Item.Properties()));
    public static final RegistryObject<Item> NEURO_VIEWER = ITEMS.register("neuro_viewer",
            () -> new NeuroViewerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NEURO_MIND_IMPRINTER = ITEMS.register("neuro_mind_imprinter",
            () -> new NeuroMindImprinterItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NEURO_VISUAL_PROBE = ITEMS.register("neuro_visual_probe",
            () -> new NeuroStimulusProbeItem(StimulusProbeChannel.VISUAL, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NEURO_TACTILE_PROBE = ITEMS.register("neuro_tactile_probe",
            () -> new NeuroStimulusProbeItem(StimulusProbeChannel.TACTILE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NEURO_OLFACTORY_PROBE = ITEMS.register("neuro_olfactory_probe",
            () -> new NeuroStimulusProbeItem(StimulusProbeChannel.OLFACTORY, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NEURO_ACOUSTIC_PROBE = ITEMS.register("neuro_acoustic_probe",
            () -> new NeuroStimulusProbeItem(StimulusProbeChannel.ACOUSTIC, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NEURO_GUSTATORY_PROBE = ITEMS.register("neuro_gustatory_probe",
            () -> new NeuroStimulusProbeItem(StimulusProbeChannel.GUSTATORY, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NEURO_LOOMING_PROBE = ITEMS.register("neuro_looming_probe",
            () -> new NeuroStimulusProbeItem(StimulusProbeChannel.LOOMING, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> NEURO_TRIAL_RECORDER = ITEMS.register("neuro_trial_recorder",
            () -> new NeuroTrialRecorderItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Block> NEURO_MONITOR = BLOCKS.register("neuro_monitor",
            () -> new NeuroMonitorBlock(BlockBehaviour.Properties.of().strength(2.5f)
                    .sound(SoundType.COPPER).noOcclusion()));
    public static final RegistryObject<BlockEntityType<NeuroMonitorBlockEntity>> NEURO_MONITOR_ENTITY =
            BLOCK_ENTITY_TYPES.register("neuro_monitor", () -> BlockEntityType.Builder
                    .of(NeuroMonitorBlockEntity::new, NEURO_MONITOR.get()).build(null));
    public static final RegistryObject<Item> NEURO_MONITOR_ITEM = ITEMS.register("neuro_monitor",
            () -> new net.minecraft.world.item.BlockItem(NEURO_MONITOR.get(), new Item.Properties()));
    public static final RegistryObject<Block> PETRI_DISH = BLOCKS.register("petri_dish",
            () -> new PetriDishBlock(BlockBehaviour.Properties.of().strength(1.5f)
                    .sound(SoundType.GLASS).noOcclusion().isViewBlocking((state, level, pos) -> false)));
    public static final RegistryObject<BlockEntityType<PetriDishBlockEntity>> PETRI_DISH_ENTITY =
            BLOCK_ENTITY_TYPES.register("petri_dish", () -> BlockEntityType.Builder
                    .of(PetriDishBlockEntity::new, PETRI_DISH.get()).build(null));
    public static final RegistryObject<Item> PETRI_DISH_ITEM = ITEMS.register("petri_dish",
            () -> new net.minecraft.world.item.BlockItem(PETRI_DISH.get(), new Item.Properties()));

    private NeuroLabEntities() {}

    @SubscribeEvent
    public static void addAttributes(EntityAttributeCreationEvent event) {
        event.put(NEURO_FLY.get(), NeuroFlyEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FLYING_SPEED, 0.48)
                .build());
    }

    @SubscribeEvent
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(NEURO_FLY_SPAWN_EGG.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(NEURO_VIEWER.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(NEURO_MIND_IMPRINTER.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(NEURO_VISUAL_PROBE.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(NEURO_TACTILE_PROBE.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(NEURO_OLFACTORY_PROBE.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(NEURO_ACOUSTIC_PROBE.get());
            event.accept(NEURO_GUSTATORY_PROBE.get());
            event.accept(NEURO_LOOMING_PROBE.get());
            event.accept(NEURO_TRIAL_RECORDER.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(NEURO_MONITOR_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) event.accept(PETRI_DISH_ITEM.get());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, path);
    }
}
