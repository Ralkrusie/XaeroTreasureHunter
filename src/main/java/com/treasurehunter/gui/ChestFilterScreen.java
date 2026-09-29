package com.treasurehunter.gui;

import com.treasurehunter.TreasureHunterConfig;
import com.treasurehunter.TreasureHunterMod;
import com.treasurehunter.scan.ChestCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 箱子类型过滤：逐类开关"是否扫描"。
 *
 * <p>
 * 关闭某一类后，该类箱子不再登记；已登记的会被移除。重新打开后，下次半径重扫
 * （约 1 秒内）会重新发现当前已加载区域里的该类箱子。关闭"结构智能识别"时
 * 分类不生效、所有箱子都会被扫描。
 */
public class ChestFilterScreen extends Screen {
    private final Screen parent;
    private final TreasureHunterConfig config;
    private final List<IconRow> iconRows = new ArrayList<>();

    /** 图标行：分类按钮 + 代表物品图标（状态色块在渲染时按实时配置绘制）。 */
    private record IconRow(AbstractWidget widget, ItemStack icon, ChestCategory category) {
    }

    public ChestFilterScreen(Screen parent, TreasureHunterConfig config) {
        super(Component.translatable("screen.treasurehunter.filter.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        ChestCategory[] categories = ChestCategory.values();
        int columns = 3;
        int buttonWidth = 104;
        int buttonHeight = 20;
        int spacingX = 6;
        int spacingY = 4;
        int rows = (categories.length + columns - 1) / columns;
        int gridWidth = columns * buttonWidth + (columns - 1) * spacingX;
        int startX = (this.width - gridWidth) / 2;
        int startY = 40;

        iconRows.clear();
        for (int i = 0; i < categories.length; i++) {
            ChestCategory category = categories[i];
            int x = startX + (i % columns) * (buttonWidth + spacingX);
            int y = startY + (i / columns) * (buttonHeight + spacingY);
            AbstractWidget widget = this.addRenderableWidget(Button.builder(categoryLabel(category), button -> {
                boolean currentlyEnabled = !config.isChestCategoryDisabled(category);
                config.setChestCategoryEnabled(category, !currentlyEnabled);
                button.setMessage(categoryLabel(category));
                if (currentlyEnabled && TreasureHunterMod.scanner() != null) {
                    TreasureHunterMod.scanner().removeMarkersOfCategory(category);
                }
            }).bounds(x, y, buttonWidth, buttonHeight).build());
            iconRows.add(new IconRow(widget, OptionIcons.categoryIcon(category), category));
        }

        int footerY = startY + rows * (buttonHeight + spacingY) + 8;
        int footerWidth = (gridWidth - spacingX * 2) / 3;
        this.addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.all_on"), button -> {
            config.setAllChestCategoriesEnabled(true);
            this.minecraft.setScreenAndShow(new ChestFilterScreen(this.parent, this.config));
        }).bounds(startX, footerY, footerWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.all_off"), button -> {
            config.setAllChestCategoriesEnabled(false);
            if (TreasureHunterMod.scanner() != null) {
                for (ChestCategory category : ChestCategory.values()) {
                    TreasureHunterMod.scanner().removeMarkersOfCategory(category);
                }
            }
            this.minecraft.setScreenAndShow(new ChestFilterScreen(this.parent, this.config));
        }).bounds(startX + footerWidth + spacingX, footerY, footerWidth, 20).build());
        this.addRenderableWidget(
                Button.builder(Component.translatable("button.treasurehunter.back"), button -> this.onClose())
                        .bounds(startX + 2 * (footerWidth + spacingX), footerY, footerWidth, 20).build());
    }

    private Component categoryLabel(ChestCategory category) {
        boolean enabled = !config.isChestCategoryDisabled(category);
        return Component.literal(enabled ? "✔ " : "✘ ").append(category.label());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (IconRow row : iconRows) {
            graphics.fakeItem(row.icon(), row.widget().getX() + 2, row.widget().getY() + 2);
            boolean enabled = !config.isChestCategoryDisabled(row.category());
            int x = row.widget().getX() + row.widget().getWidth() - 9;
            int y = row.widget().getY() + 7;
            graphics.fill(x, y, x + 6, y + 6, enabled ? 0xFF55FF55 : 0xFFFF5555);
        }
        graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("screen.treasurehunter.filter.hint"),
                this.width / 2, 29, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }
}
