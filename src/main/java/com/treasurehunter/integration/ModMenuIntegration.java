package com.treasurehunter.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.treasurehunter.TreasureHunterMod;
import com.treasurehunter.gui.TreasureHunterConfigScreen;

/** Mod Menu 可选设置入口；未安装 Mod Menu 时 Fabric 不会调用此入口。 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new TreasureHunterConfigScreen(parent, TreasureHunterMod.config());
    }
}
