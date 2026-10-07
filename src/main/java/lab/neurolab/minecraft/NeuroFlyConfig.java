package lab.neurolab.minecraft;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-authoritative tuning for the game-world fly embodiment. */
public final class NeuroFlyConfig {
    public enum LightMode { BALANCED, SEEK_LIGHT, SEEK_SHADE, OFF }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<LightMode> LIGHT_MODE;
    public static final ForgeConfigSpec.DoubleValue LIGHT_STEERING;
    public static final ForgeConfigSpec.DoubleValue CRUISE_THROTTLE;
    public static final ForgeConfigSpec.DoubleValue CRUISE_ALTITUDE;
    public static final ForgeConfigSpec.DoubleValue ALTITUDE_CEILING;
    public static final ForgeConfigSpec.IntValue FLOWER_RADIUS;
    public static final ForgeConfigSpec.IntValue HOST_RADIUS;
    public static final ForgeConfigSpec.IntValue PERCH_TICKS;
    public static final ForgeConfigSpec.IntValue RAIN_SCAN_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue SEEK_SHELTER_IN_RAIN;
    public static final ForgeConfigSpec.BooleanValue PERCH_ON_ANIMALS_AND_VILLAGERS;
    public static final ForgeConfigSpec.IntValue TELEMETRY_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue ACTIVE_NEURON_SAMPLE_LIMIT;
    public static final ForgeConfigSpec.IntValue SAMPLE_LIMIT_CONFIG_VERSION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("neuro_fly_environment");
        LIGHT_MODE = builder.comment("Light-gradient response: BALANCED seeks dim light and avoids very bright spots; SEEK_LIGHT and SEEK_SHADE force one direction.")
                .defineEnum("light_mode", LightMode.BALANCED);
        LIGHT_STEERING = builder.comment("Strength of steering from the left/right light difference.")
                .defineInRange("light_steering", 0.9, 0.0, 1.0);
        CRUISE_THROTTLE = builder.comment("Gentle minimum assisted cruise throttle for Neuro Fly when no specific sensory target is present.")
                .defineInRange("cruise_throttle", 0.30, 0.05, 0.8);
        CRUISE_ALTITUDE = builder.comment("Neuro Fly preferred height above the local terrain in blocks.")
                .defineInRange("cruise_altitude_agl", 2.0, 1.0, 8.0);
        ALTITUDE_CEILING = builder.comment("Hard assisted-flight ceiling above local terrain in blocks; prevents unbounded climbing.")
                .defineInRange("altitude_ceiling_agl", 5.0, 2.0, 16.0);
        FLOWER_RADIUS = builder.comment("Loaded-block search radius for flower odor and nectar-like reward cues.")
                .defineInRange("flower_radius", 8, 3, 16);
        HOST_RADIUS = builder.comment("Search radius for occasional close approaches and perching on animals or villagers.")
                .defineInRange("host_radius", 8, 0, 16);
        PERCH_TICKS = builder.comment("Approximate duration of an animal/villager rest perch in game ticks.")
                .defineInRange("perch_ticks", 120, 20, 600);
        RAIN_SCAN_INTERVAL = builder.comment("Minimum ticks between local shelter searches while exposed to rain.")
                .defineInRange("rain_scan_interval", 40, 10, 200);
        SEEK_SHELTER_IN_RAIN = builder.define("seek_shelter_in_rain", true);
        PERCH_ON_ANIMALS_AND_VILLAGERS = builder.define("perch_on_animals_and_villagers", true);
        builder.pop();
        builder.push("experiment_telemetry");
        TELEMETRY_INTERVAL_TICKS = builder.comment("Telemetry cadence in game ticks. Set to 1 for 20 samples/second; this does not change neural integration speed.")
                .defineInRange("sample_every_game_ticks", 2, 1, 20);
        ACTIVE_NEURON_SAMPLE_LIMIT = builder.comment("Maximum unique firing-neuron IDs included in each sample (0 disables ID sampling; up to 65,536). At 20 mobs, a 65,536 limit can create substantial network and JSONL traffic; lower this for modest hardware.")
                .defineInRange("active_neuron_sample_limit", 65_536, 0, 65_536);
        SAMPLE_LIMIT_CONFIG_VERSION = builder.comment("Internal migration marker for the active-neuron sample default.")
                .defineInRange("sample_limit_config_version", 0, 0, 2);
        builder.pop();
        SPEC = builder.build();
    }

    private NeuroFlyConfig() {}

    static int migrateSampleLimit(int configuredLimit, int configVersion) {
        if (configVersion < 1 && configuredLimit == 256) return 65_536;
        if (configVersion < 2 && configuredLimit == 25_600) return 65_536;
        return configuredLimit;
    }
}
