package dev.fluery.gtoverhead;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

@SuppressWarnings("unused")
@GTAddon
public class GTOverheadAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTOverhead.REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return GTOverhead.MOD_ID;
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        // recipes are intentionally managed through KubeJS in the TerraFirmaGreg modpack
    }
}
