package com.treasurehunter.gui;

import com.treasurehunter.TreasureHunterConfig;
import com.treasurehunter.TreasureHunterMod;
import com.treasurehunter.scan.ChestCategory;
import com.treasurehunter.scan.ServerFilterMode;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 结构筛选：通用结构之后展示单人和服务器专用栏目，滚动区域保留全部选项。 */
public final class ChestFilterScreen extends Screen {
    private final Screen parent;
    private final TreasureHunterConfig config;
    private FilterPanel panel;

    public ChestFilterScreen(Screen parent, TreasureHunterConfig config) {
        super(Component.translatable("screen.treasurehunter.filter.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        double previousScroll = panel == null ? 0 : panel.scrollAmount();
        int gridWidth = Math.min(420, width - 40);
        int left = (width - gridWidth) / 2;
        panel = addRenderableWidget(new FilterPanel(left, 42, gridWidth, Math.max(32, height - 78)));
        panel.setScrollAmount(previousScroll);
        int footerWidth = (gridWidth - 12) / 3;
        addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.all_on"), button -> setAll(true))
                .bounds(left, height - 28, footerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.all_off"), button -> setAll(false))
                .bounds(left + footerWidth + 6, height - 28, footerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.back"), button -> onClose())
                .bounds(left + 2 * (footerWidth + 6), height - 28, footerWidth, 20).build());
    }

    private void setAll(boolean enabled) {
        config.setAllChestCategoriesEnabled(enabled);
        var scanner = TreasureHunterMod.scanner();
        if (scanner != null) {
            if (enabled) scanner.requestRescan();
            else for (ChestCategory category : ChestCategory.values()) scanner.removeMarkersOfCategory(category);
        }
    }

    private Component categoryLabel(ChestCategory category) {
        return Component.translatable(config.isChestCategoryDisabled(category)
                ? "screen.treasurehunter.category_off" : "screen.treasurehunter.category_on", category.label());
    }

    /** 为左侧图标和右侧状态色块留出文字区，长名称仍使用原版的滚动显示。 */
    private static final class FilterButton extends Button.Plain {
        private final boolean icon;

        FilterButton(int x, int y, int width, Component label, OnPress action, boolean icon) {
            super(x, y, width, 20, label, action, DEFAULT_NARRATION);
            this.icon = icon;
        }

        @Override
        protected void extractDefaultLabel(ActiveTextCollector collector) {
            collector.acceptScrollingWithDefaultCenter(getMessage(), getX() + (icon ? 22 : 4),
                    getRight() - 13, getY(), getBottom());
        }
    }

    /** 原版容器控件负责滚动条、鼠标和键盘焦点；子按钮位置随滚动更新。 */
    private final class FilterPanel extends AbstractContainerWidget {
        private record Row(Button button, int x, int y, ChestCategory category, ItemStack icon) { }
        private record Heading(int y, Component label) { }
        private final List<Row> rows = new ArrayList<>();
        private final List<Heading> headings = new ArrayList<>();
        private int totalHeight;

        FilterPanel(int x, int y, int width, int height) {
            super(x, y, width, height, ChestFilterScreen.this.title);
            int columns = ChestFilterScreen.this.width >= 360 ? 3 : 2;
            int cellWidth = (width - (columns - 1) * 6) / columns;
            int index = 0;
            for (ChestCategory category : ChestCategory.values()) {
                if (category == ChestCategory.OTHER_LOOT || category == ChestCategory.OTHER) continue;
                addCategory(category, (index % columns) * (cellWidth + 6), (index / columns) * 24, cellWidth, null);
                index++;
            }
            int nextY = ((index + columns - 1) / columns) * 24 + 4;
            headings.add(new Heading(nextY, Component.translatable("screen.treasurehunter.filter.singleplayer")));
            nextY += 16;
            addCategory(ChestCategory.OTHER_LOOT, 0, nextY, cellWidth, "screen.treasurehunter.filter.custom_hint");
            nextY += 32;
            headings.add(new Heading(nextY, Component.translatable("screen.treasurehunter.filter.server")));
            nextY += 16;
            addCategory(ChestCategory.OTHER, 0, nextY, cellWidth, "screen.treasurehunter.filter.other_hint");
            // 过滤力度的名称较长，占剩余列宽，保留「其他箱」的一列宽度。
            int modeX = columns == 2 ? 0 : cellWidth + 6;
            if (columns == 2) nextY += 24;
            int modeWidth = width - modeX;
            var mode = new FilterButton(x + modeX, y + nextY, modeWidth, modeLabel(), button -> {
                config.serverFilterMode = config.serverFilterMode.next();
                config.save();
                if (TreasureHunterMod.scanner() != null && minecraft.getSingleplayerServer() == null) {
                    TreasureHunterMod.scanner().clearMarkers();
                }
            }, false);
            rows.add(new Row(mode, modeX, nextY, null, ItemStack.EMPTY));
            totalHeight = nextY + 24;
            positionRows();
        }

        private Component modeLabel() {
            return Component.translatable("option.treasurehunter.server_mode", config.serverFilterMode.label());
        }

        private void addCategory(ChestCategory category, int x, int y, int width, String hintKey) {
            var button = new FilterButton(getX() + x, getY() + y, width, categoryLabel(category), ignored -> {
                boolean enabled = config.isChestCategoryDisabled(category);
                config.setChestCategoryEnabled(category, enabled);
                var scanner = TreasureHunterMod.scanner();
                if (scanner != null) {
                    if (enabled) scanner.requestRescan();
                    else scanner.removeMarkersOfCategory(category);
                }
            }, true);
            button.setTooltip(Tooltip.create(Component.translatable(hintKey == null ? category.translationKey() : hintKey)));
            rows.add(new Row(button, x, y, category, OptionIcons.categoryIcon(category)));
        }

        private void positionRows() {
            if (rows == null) return;
            for (var row : rows) row.button().setPosition(getX() + row.x(), getY() + row.y() - (int) scrollAmount());
        }

        @Override
        public void setScrollAmount(double amount) {
            super.setScrollAmount(amount);
            positionRows();
        }

        @Override
        public void setFocused(GuiEventListener child) {
            super.setFocused(child);
            if (child instanceof Button button) {
                if (button.getY() < getY()) setScrollAmount(scrollAmount() + button.getY() - getY());
                else if (button.getBottom() > getBottom()) setScrollAmount(scrollAmount() + button.getBottom() - getBottom());
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return rows.stream().map(Row::button).toList();
        }

        @Override
        protected int contentHeight() { return totalHeight; }

        @Override
        protected double scrollRate() { return 24; }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
            positionRows();
            graphics.enableScissor(getX(), getY(), getRight(), getBottom());
            try {
                for (var heading : headings) {
                    int y = getY() + heading.y() - (int) scrollAmount();
                    graphics.centeredText(font, heading.label(), getX() + getWidth() / 2, y, 0xFFD3DFEC);
                }
                for (var row : rows) {
                    var button = row.button();
                    if (button.getBottom() <= getY() || button.getY() >= getBottom()) continue;
                    button.setMessage(row.category() == null ? modeLabel() : categoryLabel(row.category()));
                    if (row.category() == null) {
                        button.setTooltip(Tooltip.create(Component.translatable("screen.treasurehunter.server_mode."
                                + config.serverFilterMode.name().toLowerCase(java.util.Locale.ROOT))));
                    }
                    button.extractRenderState(graphics, mouseX, mouseY, tick);
                    if (!row.icon().isEmpty()) graphics.fakeItem(row.icon(), button.getX() + 2, button.getY() + 2);
                    boolean enabled = row.category() == null ? config.serverFilterMode != ServerFilterMode.OFF
                            : !config.isChestCategoryDisabled(row.category());
                    int indicatorX = button.getRight() - 9;
                    graphics.fill(indicatorX, button.getY() + 7, indicatorX + 6, button.getY() + 13,
                            enabled ? 0xFF55FF55 : 0xFFFF5555);
                }
            } finally { graphics.disableScissor(); }
            extractScrollbar(graphics, mouseX, mouseY);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            if (getFocused() instanceof Button button) button.updateNarration(output.nest());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("screen.treasurehunter.filter.hint"), width / 2, 29, 0xFFA0A0A0);
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }
}
