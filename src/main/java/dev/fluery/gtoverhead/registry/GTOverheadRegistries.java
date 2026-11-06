package dev.fluery.gtoverhead.registry;

import dev.fluery.gtoverhead.GTOverhead;
import dev.fluery.gtoverhead.block.ConnectorBlock;
import dev.fluery.gtoverhead.block.GarlandBlock;
import dev.fluery.gtoverhead.block.RelayConnectorBlock;
import dev.fluery.gtoverhead.blockentity.ConnectorBlockEntity;
import dev.fluery.gtoverhead.blockentity.RelayConnectorBlockEntity;
import dev.fluery.gtoverhead.item.VoltmeterItem;
import dev.fluery.gtoverhead.item.WireSpoolItem;
import dev.fluery.gtoverhead.tier.WireTier;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class GTOverheadRegistries {

    public static final List<Supplier<ConnectorBlock>> CONNECTOR_BLOCKS = new ArrayList<>();
    public static final List<Supplier<WireSpoolItem>> SPOOL_ITEMS = new ArrayList<>();

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, GTOverhead.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, GTOverhead.MOD_ID);

    public static Supplier<Item> EMPTY_SPOOL = () -> null;
    public static Supplier<GarlandBlock> GARLAND = () -> null;
    public static Supplier<VoltmeterItem> VOLTMETER = () -> null;
    public static Supplier<RelayConnectorBlock> RELAY_BLOCK;

    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("gtoverhead",
            () -> CreativeModeTab.builder()
                    .icon(() -> SPOOL_ITEMS.get(1).get().getDefaultInstance())
                    .title(Component.translatable("item_group.gtoverhead.gtoverhead"))
                    .displayItems((params, output) -> {
                        for (var spool : SPOOL_ITEMS) {
                            output.accept(spool.get());
                        }
                        output.accept(EMPTY_SPOOL.get());
                        output.accept(VOLTMETER.get());
                        for (var connector : CONNECTOR_BLOCKS) {
                            output.accept(connector.get());
                        }
                        output.accept(RELAY_BLOCK.get());
                        output.accept(GARLAND.get());
                    })
                    .build());

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ConnectorBlockEntity>> CONNECTOR_BE_TYPE = BLOCK_ENTITY_TYPES
            .register("connector", () -> BlockEntityType.Builder
                    .of(ConnectorBlockEntity::new,
                            CONNECTOR_BLOCKS.stream().map(s -> (ConnectorBlock) s.get()).toArray(ConnectorBlock[]::new))
                    .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<RelayConnectorBlockEntity>> RELAY_BE_TYPE = BLOCK_ENTITY_TYPES
            .register("relay_connector", () -> BlockEntityType.Builder
                    .of(RelayConnectorBlockEntity::new, RELAY_BLOCK.get())
                    .build(null));

    public static void init() {
        BLOCK_ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());

        CREATIVE_MODE_TABS.register(FMLJavaModLoadingContext.get().getModEventBus());

        RELAY_BLOCK = registerRelay(GTOverhead.REGISTRATE);
        registerEmptySpool(GTOverhead.REGISTRATE);
        registerGarland(GTOverhead.REGISTRATE);
        registerVoltmeter(GTOverhead.REGISTRATE);

        for (WireTier tier : WireTier.values()) {
            var block = registerConnector(GTOverhead.REGISTRATE, tier);
            CONNECTOR_BLOCKS.add(block);
            var spool = registerSpool(GTOverhead.REGISTRATE, tier);
            SPOOL_ITEMS.add(spool);
        }
    }

    private static Supplier<ConnectorBlock> registerConnector(GTRegistrate registrate, WireTier tier) {
        var block = registrate.block(tier.shortName + "_connector", props -> new ConnectorBlock(props, tier))
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(props -> props.strength(0.75F, 6.0F))
                .simpleItem()
                .register();
        return () -> block.get();
    }

    private static Supplier<RelayConnectorBlock> registerRelay(GTRegistrate registrate) {
        var block = registrate.block("structural_connector", RelayConnectorBlock::new)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(props -> props.strength(0.75F, 6.0F))
                .simpleItem()
                .register();
        return () -> block.get();
    }

    private static Supplier<WireSpoolItem> registerSpool(GTRegistrate registrate, WireTier tier) {
        var item = registrate.item(spoolName(tier), props -> new WireSpoolItem(props, tier))
                .register();
        return () -> item.get();
    }

    private static void registerEmptySpool(GTRegistrate registrate) {
        var item = registrate.item("empty_spool", Item::new).register();
        EMPTY_SPOOL = () -> item.get();
    }

    private static void registerGarland(GTRegistrate registrate) {
        var block = registrate.block("insulator_garland", GarlandBlock::new)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(props -> props.strength(0.75F, 6.0F))
                .simpleItem()
                .register();
        GARLAND = () -> block.get();
    }

    private static void registerVoltmeter(GTRegistrate registrate) {
        var item = registrate.item("voltmeter", VoltmeterItem::new).register();
        VOLTMETER = () -> item.get();
    }

    public static String spoolName(WireTier tier) {
        return switch (tier) {
            case LOW -> "tin_spool";
            case MEDIUM -> "copper_spool";
            case HIGH -> "steel_spool";
            case EXTREME -> "aluminium_spool";
            case INSANE -> "platinum_spool";
        };
    }

    private GTOverheadRegistries() {}
}
