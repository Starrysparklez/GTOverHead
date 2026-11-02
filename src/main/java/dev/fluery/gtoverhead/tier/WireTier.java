package dev.fluery.gtoverhead.tier;

import dev.fluery.gtoverhead.GTOverheadConfig;
import dev.fluery.gtoverhead.registry.GTOverheadRegistries;

import com.gregtechceu.gtceu.api.GTValues;

import java.util.function.Supplier;

public enum WireTier {

    LOW(0, GTValues.LV, "lv"),
    MEDIUM(1, GTValues.MV, "mv"),
    HIGH(2, GTValues.HV, "hv"),
    EXTREME(3, GTValues.EV, "ev"),
    INSANE(4, GTValues.IV, "iv");

    public final int index;
    public final int gtTier;
    public final String shortName;

    WireTier(int index, int gtTier, String shortName) {
        this.index = index;
        this.gtTier = gtTier;
        this.shortName = shortName;
    }

    /** Approximate wire colour of the GT material, used for the rendered curve. */
    public int wireColor() {
        return switch (this) {
            case LOW -> 0xC8C8C8; // tin
            case MEDIUM -> 0xC46B43; // copper
            case HIGH -> 0xA7A7A7; // steel
            case EXTREME -> 0xD8DDE0; // aluminium
            case INSANE -> 0xE8D9BE; // platinum
        };
    }

    /**
     * Height of the insulator tip above the support face, in blocks, matching the
     * custom connector model of this tier. The wire anchors at this point.
     */
    public double tipHeight() {
        return this == LOW || this == MEDIUM ? 0.25 : 0.375;
    }

    public int lossPerStep() {
        return GTOverheadConfig.lossPerStep(index);
    }

    public long voltage() {
        return GTValues.V[gtTier];
    }

    public int maxAmperage() {
        return GTOverheadConfig.maxAmperage(index);
    }

    public Supplier<? extends dev.fluery.gtoverhead.block.ConnectorBlock> block() {
        return GTOverheadRegistries.CONNECTOR_BLOCKS.get(index);
    }

    public Supplier<? extends dev.fluery.gtoverhead.item.WireSpoolItem> spool() {
        return GTOverheadRegistries.SPOOL_ITEMS.get(index);
    }

    public static WireTier byIndex(int index) {
        if (index < 0 || index >= values().length) {
            return LOW;
        }
        return values()[index];
    }
}
