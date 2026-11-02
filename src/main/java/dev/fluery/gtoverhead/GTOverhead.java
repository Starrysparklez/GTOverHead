package dev.fluery.gtoverhead;

import dev.fluery.gtoverhead.registry.GTOverheadRegistries;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(GTOverhead.MOD_ID)
public class GTOverhead {

    public static final String MOD_ID = "gtoverhead";
    public static final Logger LOGGER = LogManager.getLogger();
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public GTOverhead() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
                GTOverheadConfig.SPEC, "gt_overhead_lines-common.toml");

        GTOverheadRegistries.init();
        REGISTRATE.registerRegistrate();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
