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
 * 「扫描目标」子页（选项树第二层）：逐类开关要扫描哪些方块。
 *
 * <p>
 * 刷怪笼 / 箱子 / 木桶 / 钟 / 宝库 / 不祥宝库；关闭某类型时会同时移除
 * 该类已生成的标记与路径点；改动立即写入 config/treasurehunter.json。
 */
public class TargetScanScreen extends Screen {
    private final Screen parent;
    private final TreasureHunterConfig config;
    private final List<Row> rows = new ArrayList<>();

    /** 一行设置：按钮 + 左侧图标 + 实时开关状态。 */
    private record Row(AbstractWidget widget, ItemStack icon, BooleanSupplier enabled) {
    }

    public TargetScanScreen(Screen parent, TreasureHunterConfig config) {
        super(Component.translatable("screen.treasurehunter.targets.title"));
        this.parent = parent;
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

        y += 10;
        this.addRenderableWidget(
                Button.builder(Component.translatable("button.treasurehunter.back"), button -> this.onClose())
                        .bounds(x, y, buttonWidth, 20).build());
    }

    private void setTarget(TargetType type, boolean enabled) {
        switch (type) {
            case SPAWNER -> config.spawner = enabled;
            case CHEST -> config.chest = enabled;
            case BARREL -> config.barrel = enabled;
            case BELL -> config.bell = enabled;
            case VAULT -> config.vault = enabled;
            case OMINOUS_VAULT -> config.ominousVault = enabled;
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
        this.minecraft.setScreenAndShow(parent);
    }
}
