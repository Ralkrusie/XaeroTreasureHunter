package com.treasurehunter.gui;

import com.treasurehunter.TreasureHunterConfig;
import com.treasurehunter.TreasureHunterMod;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 当前世界的扫描区域黑名单和持久记录管理；全局扫描偏好留在设置首页。 */
public final class WorldSettingsScreen extends SettingsPage {
    private final TreasureHunterConfig config;

    public WorldSettingsScreen(Screen parent, TreasureHunterConfig config) {
        super(parent, Component.translatable("screen.treasurehunter.world.title"));
        this.config = config;
        hint = Component.translatable("screen.treasurehunter.world.hint");
    }

    @Override
    protected void init() {
        begin();
        var areas = action(Component.translatable("button.treasurehunter.ignore_regions"),
                () -> minecraft.setScreenAndShow(new IgnoreRegionsScreen(this, config)));
        areas.active = minecraft.level != null && minecraft.player != null;
        var opened = action(Component.translatable("button.treasurehunter.forget_looted"), () -> {
            TreasureHunterMod.scanner().clearScavengedMemory();
            minecraft.player.sendOverlayMessage(Component.translatable("message.treasurehunter.forgot_looted"));
        });
        opened.active = minecraft.level != null && minecraft.player != null;
        var placed = action(Component.translatable("button.treasurehunter.forget_placed"),
                () -> TreasureHunterMod.scanner().clearPlacedMemory());
        placed.active = minecraft.level != null && minecraft.getSingleplayerServer() == null;
        var explanation = Tooltip.create(Component.translatable("screen.treasurehunter.world.reset_hint"));
        opened.setTooltip(explanation);
        placed.setTooltip(explanation);
        finish();
    }
}
