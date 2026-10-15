package com.treasurehunter.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** 紧凑设置页的通用排版，支持从 Mod Menu 返回原父页。 */
public abstract class SettingsPage extends Screen {
    protected final Screen parent;
    protected int rowY;
    protected Component hint = Component.translatable("screen.treasurehunter.config.hint");

    protected SettingsPage(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected void begin() { rowY = 46; toggles.clear(); }

    protected Button action(Component label, Runnable action) {
        Button button = addRenderableWidget(Button.builder(label, ignored -> action.run())
                .bounds(width / 2 - 110, rowY, 220, 20).build());
        rowY += 24;
        return button;
    }

    protected void toggle(Component label, BooleanSupplier value, Runnable change) {
        var button = action(stateLabel(label, value.getAsBoolean()), change);
        button.active = true;
        // 点击后由按钮的实时渲染前更新标签，避免为即时设置重建页面。
        toggles.add(new Toggle(button, label, value));
    }

    private record Toggle(Button button, Component label, BooleanSupplier value) { }
    private final java.util.List<Toggle> toggles = new java.util.ArrayList<>();

    protected void finish() {
        action(Component.translatable("button.treasurehunter.back"), this::onClose);
    }

    private static Component stateLabel(Component label, boolean enabled) {
        return Component.translatable(enabled ? "screen.treasurehunter.state_on" : "screen.treasurehunter.state_off", label);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        for (var toggle : toggles) toggle.button().setMessage(stateLabel(toggle.label(), toggle.value().getAsBoolean()));
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font, hint, width / 2, 31, 0xFFA0A0A0);
        for (var toggle : toggles) {
            var button = toggle.button();
            graphics.fill(button.getX() + button.getWidth() + 6, button.getY() + 6,
                    button.getX() + button.getWidth() + 14, button.getY() + 14,
                    toggle.value().getAsBoolean() ? 0xFF55FF55 : 0xFFFF5555);
        }
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }
}
