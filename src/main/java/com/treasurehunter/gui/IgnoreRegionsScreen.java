package com.treasurehunter.gui;

import com.treasurehunter.TreasureHunterConfig;
import com.treasurehunter.TreasureHunterMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 当前世界忽略区域的分页管理页；无 Xaero 时仍可管理独立区域。 */
public final class IgnoreRegionsScreen extends Screen {
    private static final int PAGE_SIZE = 4;
    private final Screen parent;
    private final TreasureHunterConfig config;
    private int page;

    public IgnoreRegionsScreen(Screen parent, TreasureHunterConfig config) {
        super(Component.translatable("screen.treasurehunter.ignore.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        var regions = TreasureHunterMod.ignoreRegions().regions();
        page = Math.min(page, Math.max(0, (regions.size() - 1) / PAGE_SIZE));
        int left = width / 2 - 130;
        var add = addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.ignore.add"),
                button -> minecraft.setScreenAndShow(new IgnoreRegionEditScreen(this, null)))
                .bounds(left, 42, 126, 20).build());
        add.active = minecraft.level != null && minecraft.player != null;
        addRenderableWidget(Button.builder(Component.translatable("option.treasurehunter.ignore.outline",
                Component.translatable(config.showIgnoreOutlines ? "screen.treasurehunter.on" : "screen.treasurehunter.off")), button -> {
            config.showIgnoreOutlines = !config.showIgnoreOutlines;
            config.save();
            rebuildWidgets();
        }).bounds(left + 134, 42, 126, 20).build());
        for (int i = page * PAGE_SIZE; i < Math.min(regions.size(), (page + 1) * PAGE_SIZE); i++) {
            var region = regions.get(i);
            var label = Component.translatable("option.treasurehunter.ignore.region", region.name(), region.radiusChunks(),
                    Component.translatable("option.treasurehunter.ignore.shape." + region.shape().name().toLowerCase(java.util.Locale.ROOT)));
            if (!TreasureHunterMod.ignoreRegions().isActive(region)) {
                label.append(Component.translatable("option.treasurehunter.ignore.inactive"));
            }
            var edit = addRenderableWidget(Button.builder(label,
                    button -> minecraft.setScreenAndShow(new IgnoreRegionEditScreen(this, region)))
                    .bounds(left, 72 + (i % PAGE_SIZE) * 24, 260, 20).build());
            edit.setTooltip(Tooltip.create(Component.translatable("screen.treasurehunter.ignore.details",
                    region.dimension(), region.x(), region.z(),
                    Component.translatable(region.anchor() == null ? "option.treasurehunter.ignore.standalone" : "option.treasurehunter.ignore.linked"))));
        }
        var prev = addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.previous"), button -> {
            page--;
            rebuildWidgets();
        }).bounds(left, 178, 126, 20).build());
        prev.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.next"), button -> {
            page++;
            rebuildWidgets();
        }).bounds(left + 134, 178, 126, 20).build());
        next.active = (page + 1) * PAGE_SIZE < regions.size();
        addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.back"), button -> onClose())
                .bounds(left, 204, 260, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable(TreasureHunterMod.ignoreRegions().isOpen()
                && !TreasureHunterMod.ignoreRegions().isWritable() ? "screen.treasurehunter.ignore.read_only" : "screen.treasurehunter.ignore.hint"),
                width / 2, 31, 0xFFA0A0A0);
        if (TreasureHunterMod.ignoreRegions().regions().isEmpty()) {
            graphics.centeredText(font, Component.translatable(minecraft.level == null
                    ? "screen.treasurehunter.ignore.no_world" : "screen.treasurehunter.ignore.empty"), width / 2, 104, 0xFFB8C7D9);
        }
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }
}
