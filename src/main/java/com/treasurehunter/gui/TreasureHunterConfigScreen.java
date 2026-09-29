package com.treasurehunter.gui;

import com.treasurehunter.TargetType;
import com.treasurehunter.TreasureHunterConfig;
import com.treasurehunter.TreasureHunterMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * TreasureHunter 游戏内设置菜单：勾选要扫描的方块类型等。
 *
 * <p>
 * 快捷键 J 打开；所有改动立即写入 config/treasurehunter.json。
 * 关闭某类型时会同时移除该类已生成的标记与路径点。
 */
public class TreasureHunterConfigScreen extends Screen {
    private final TreasureHunterConfig config;
    private final List<Row> rows = new ArrayList<>();

    /** 一行设置：按钮 + 左侧图标 + 实时开关状态（动作按钮为 null）。 */
    private record Row(AbstractWidget widget, ItemStack icon, BooleanSupplier enabled) {
    }

    public TreasureHunterConfigScreen(TreasureHunterConfig config) {
        super(Component.translatable("screen.treasurehunter.config.title"));
        this.config = config;
    }

    @Override
    protected void init() {
        rows.clear();
        int buttonWidth = 220;
        int x = (this.width - buttonWidth) / 2;
        int y = 46;
        int step = 24;

        for (TargetType type : TargetType.values()) {
            AbstractWidget widget = this.addRenderableWidget(Button.builder(
                    stateLabel(type.label(), config.isEnabled(type)), button -> {
                        setTarget(type, !config.isEnabled(type));
                        button.setMessage(stateLabel(type.label(), config.isEnabled(type)));
                    }).bounds(x, y, buttonWidth, 20).build());
            rows.add(new Row(widget, OptionIcons.targetIcon(type), () -> config.isEnabled(type)));
            y += step;
        }

        AbstractWidget notifyButton = this.addRenderableWidget(Button.builder(
                stateLabel(Component.translatable("option.treasurehunter.notify"), config.notifyOnNew), button -> {
                    config.notifyOnNew = !config.notifyOnNew;
                    button.setMessage(stateLabel(Component.translatable("option.treasurehunter.notify"),
                            config.notifyOnNew));
                    config.save();
                }).bounds(x, y, buttonWidth, 20).build());
        rows.add(new Row(notifyButton, OptionIcons.notifyIcon(), () -> config.notifyOnNew));
        y += step;

        AbstractWidget singleplayerButton = this.addRenderableWidget(Button.builder(
                stateLabel(Component.translatable("option.treasurehunter.singleplayer"), config.singleplayerOnly),
                button -> {
                    config.singleplayerOnly = !config.singleplayerOnly;
                    button.setMessage(stateLabel(Component.translatable("option.treasurehunter.singleplayer"),
                            config.singleplayerOnly));
                    config.save();
                }).bounds(x, y, buttonWidth, 20).build());
        rows.add(new Row(singleplayerButton, OptionIcons.singleplayerIcon(), () -> config.singleplayerOnly));
        y += step;

        AbstractWidget smartButton = this.addRenderableWidget(Button.builder(
                stateLabel(Component.translatable("option.treasurehunter.smart"), config.smartLabels), button -> {
                    config.smartLabels = !config.smartLabels;
                    button.setMessage(stateLabel(Component.translatable("option.treasurehunter.smart"),
                            config.smartLabels));
                    config.save();
                }).bounds(x, y, buttonWidth, 20).build());
        rows.add(new Row(smartButton, OptionIcons.smartIcon(), () -> config.smartLabels));
        y += step;

        AbstractWidget mergeButton = this.addRenderableWidget(Button.builder(
                stateLabel(Component.translatable("option.treasurehunter.merge"), config.mergeNearbyMarkers), button -> {
                    config.mergeNearbyMarkers = !config.mergeNearbyMarkers;
                    button.setMessage(stateLabel(Component.translatable("option.treasurehunter.merge"),
                            config.mergeNearbyMarkers));
                    config.save();
                }).bounds(x, y, buttonWidth, 20).build());
        rows.add(new Row(mergeButton, OptionIcons.mergeIcon(), () -> config.mergeNearbyMarkers));
        y += step;

        AbstractWidget filterButton = this.addRenderableWidget(Button
                .builder(Component.translatable("option.treasurehunter.filter"),
                        button -> this.minecraft.setScreenAndShow(new ChestFilterScreen(this, config)))
                .bounds(x, y, buttonWidth, 20).build());
        rows.add(new Row(filterButton, OptionIcons.filterIcon(), null));
        y += step + 10;

        this.addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.done"), button -> this.onClose())
                .bounds(x, y, buttonWidth, 20).build());
    }

    private void setTarget(TargetType type, boolean enabled) {
        switch (type) {
            case SPAWNER -> config.spawner = enabled;
            case CHEST -> config.chest = enabled;
            case BARREL -> config.barrel = enabled;
            case BELL -> config.bell = enabled;
        }
        config.save();
        if (!enabled && TreasureHunterMod.scanner() != null) {
            TreasureHunterMod.scanner().removeMarkersOfType(type);
        }
    }

    /** 统一的"✔ 名称：开 / ✘ 名称：关"标签。 */
    private static Component stateLabel(Component name, boolean enabled) {
        return Component.translatable(enabled ? "screen.treasurehunter.state_on" : "screen.treasurehunter.state_off",
                name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (Row row : rows) {
            graphics.fakeItem(row.icon(), row.widget().getX() - 24, row.widget().getY() + 2);
            if (row.enabled() != null) {
                boolean on = row.enabled().getAsBoolean();
                int right = row.widget().getX() + row.widget().getWidth() + 6;
                graphics.fill(right, row.widget().getY() + 6, right + 8, row.widget().getY() + 14,
                        on ? 0xFF55FF55 : 0xFFFF5555);
            }
        }
        graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("screen.treasurehunter.config.hint"),
                this.width / 2, 31, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(null);
    }
}
