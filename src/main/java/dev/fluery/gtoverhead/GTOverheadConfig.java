package dev.fluery.gtoverhead;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class GTOverheadConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_WIRE_LENGTH;
    public static final ForgeConfigSpec.DoubleValue LOSS_DISTANCE;
    public static final ForgeConfigSpec.IntValue WIRE_SPOOL_SEGMENT_LENGTH;
    public static final ForgeConfigSpec.IntValue BUFFER_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> LOSS_PER_STEP;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> MAX_AMPERAGE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HAZARD_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HAZARD_DAMAGE;

    private static final String[] TIER_NAMES = { "lv_tin", "mv_copper", "hv_steel", "ev_aluminium", "iv_platinum" };
    private static final List<Integer> DEFAULT_LOSS_PER_STEP = List.of(1, 2, 1, 1, 1);
    private static final List<Integer> DEFAULT_MAX_AMPERAGE = List.of(1, 1, 1, 1, 2);
    private static final List<Double> DEFAULT_HAZARD_RADIUS = List.of(0.0, 0.5, 0.75, 1.0, 2.0);
    private static final List<Double> DEFAULT_HAZARD_DAMAGE = List.of(0.0, 1.0, 3.0, 5.0, 8.0);

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        MAX_WIRE_LENGTH = builder.comment("Maximum Euclidean wire distance in blocks.")
                .defineInRange("general.maxWireLength", 128, 4, 1024);

        LOSS_DISTANCE = builder.comment(
                "Loss step distance in blocks: one loss step is applied per this many blocks of wire,",
                "e.g. lossDistance=16 with lossPerStep=1 loses 1 EU/A every 16 blocks.",
                "Effective loss = floor(distance / lossDistance) * lossPerStep[tier].")
                .defineInRange("general.lossDistance", 20.0, 1.0, 1024.0);

        WIRE_SPOOL_SEGMENT_LENGTH = builder.comment(
                "Wire length covered by a single spool, in blocks. Linking consumes floor(distance / spoolLength)",
                "spools (at least one), e.g. a 256 block wire with a 16 block spool consumes 16 spools.")
                .defineInRange("general.wireSpoolSegmentLength", 16, 1, 1024);

        BUFFER_TICKS = builder.comment(
                "Internal energy buffer of a connector, in seconds worth of full-amperage throughput.")
                .defineInRange("general.bufferTicks", 20, 1, 200);

        builder.comment("Per-tier wire ratings. Tiers in order: " + String.join(", ", TIER_NAMES));
        LOSS_PER_STEP = builder.comment("EU loss per ampere per loss step (see general.lossDistance).")
                .defineList("tiers.lossPerStep", DEFAULT_LOSS_PER_STEP, o -> o instanceof Integer);
        MAX_AMPERAGE = builder.comment("Maximum amperage carried by the wire of this tier.")
                .defineList("tiers.maxAmperage", DEFAULT_MAX_AMPERAGE, o -> o instanceof Integer);

        builder.comment("Electrocution hazard of live wires. Tiers in order: " + String.join(", ", TIER_NAMES));
        HAZARD_RADIUS = builder.comment(
                "Shock radius in blocks around the wire. 0 disables shocking for the tier.",
                "Defaults: LV never shocks, MV/HV only on touch, EV within 1 block, IV within 2 blocks.")
                .defineList("hazard.radius", DEFAULT_HAZARD_RADIUS, o -> o instanceof Double || o instanceof Integer);
        HAZARD_DAMAGE = builder.comment("Damage (half hearts) dealt per shock hit.")
                .defineList("hazard.damage", DEFAULT_HAZARD_DAMAGE, o -> o instanceof Double || o instanceof Integer);

        SPEC = builder.build();
    }

    private GTOverheadConfig() {}

    public static int maxWireLength() {
        return SPEC.isLoaded() ? MAX_WIRE_LENGTH.get() : 128;
    }

    public static double lossDistance() {
        return SPEC.isLoaded() ? LOSS_DISTANCE.get() : 20.0;
    }

    public static int wireSpoolSegmentLength() {
        return SPEC.isLoaded() ? WIRE_SPOOL_SEGMENT_LENGTH.get() : 16;
    }

    public static int bufferTicks() {
        return SPEC.isLoaded() ? BUFFER_TICKS.get() : 20;
    }

    public static int lossPerStep(int tier) {
        List<? extends Integer> values = SPEC.isLoaded() ? LOSS_PER_STEP.get() : DEFAULT_LOSS_PER_STEP;
        if (tier < 0 || tier >= values.size()) {
            return 1;
        }
        return Math.max(0, values.get(tier));
    }

    public static int maxAmperage(int tier) {
        List<? extends Integer> values = SPEC.isLoaded() ? MAX_AMPERAGE.get() : DEFAULT_MAX_AMPERAGE;
        if (tier < 0 || tier >= values.size()) {
            return 1;
        }
        return Math.max(1, values.get(tier));
    }

    public static double hazardRadius(int tier) {
        List<? extends Double> values = SPEC.isLoaded() ? HAZARD_RADIUS.get() : DEFAULT_HAZARD_RADIUS;
        if (tier < 0 || tier >= values.size()) {
            return 0.0;
        }
        return Math.max(0.0, values.get(tier));
    }

    public static double hazardDamage(int tier) {
        List<? extends Double> values = SPEC.isLoaded() ? HAZARD_DAMAGE.get() : DEFAULT_HAZARD_DAMAGE;
        if (tier < 0 || tier >= values.size()) {
            return 0.0;
        }
        return Math.max(0.0, values.get(tier));
    }
}
