package com.treasurehunter.gui;

import com.treasurehunter.TreasureHunterConfig;
import com.treasurehunter.TreasureHunterMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 设置树入口：常用扫描设置直接展示，当前世界的数据管理进入世界设置。 */
public final class TreasureHunterConfigScreen extends SettingsPage {
    private final TreasureHunterConfig config;

    public TreasureHunterConfigScreen(TreasureHunterConfig config) { this(null, config); }

    public TreasureHunterConfigScreen(Screen parent, TreasureHunterConfig config) {
        super(parent, Component.translatable("screen.treasurehunter.config.title"));
        this.config = config;
    }

    @Override
    protected void init() {
        begin();
        toggle(Component.translatable("option.treasurehunter.server_enabled"), () -> config.serverScanEnabled, () -> {
            config.serverScanEnabled = !config.serverScanEnabled;
            refresh();
        });
        action(Component.translatable("button.treasurehunter.targets"),
                () -> minecraft.setScreenAndShow(new TargetScanScreen(this, config)));
        toggle(Component.translatable("option.treasurehunter.smart"), () -> config.smartLabels, () -> {
            config.smartLabels = !config.smartLabels;
            refresh();
        });
        action(Component.translatable("option.treasurehunter.filter"),
                () -> minecraft.setScreenAndShow(new ChestFilterScreen(this, config)));
        toggle(Component.translatable("option.treasurehunter.notify"), () -> config.notifyOnNew, () -> {
            config.notifyOnNew = !config.notifyOnNew;
            config.save();
        });
        toggle(Component.translatable("option.treasurehunter.merge"), () -> config.mergeNearbyMarkers, () -> {
            config.mergeNearbyMarkers = !config.mergeNearbyMarkers;
            refresh();
        });
        action(Component.translatable("button.treasurehunter.world_settings"),
                () -> minecraft.setScreenAndShow(new WorldSettingsScreen(this, config)));
        finish();
    }

    private void refresh() {
        config.save();
        if (TreasureHunterMod.scanner() != null) TreasureHunterMod.scanner().clearMarkers();
    }
}
