package com.treasurehunter.gui;

import com.treasurehunter.TreasureHunterConfig;
import com.treasurehunter.TreasureHunterMod;
import com.treasurehunter.scan.ChestCategory;
import com.treasurehunter.scan.ServerFilterMode;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.util.FormattedCharSequence;
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
    private static final int GAP = 6;
    private static final int ROW_GAP = 4;
    private static final int ICON_INSET = 22;
    private static final int TEXT_INSET = 4;
    private static final int STATE_INSET = 13;
    private static final int MIN_BUTTON_HEIGHT = 20;
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
        // 根据当前字体实测译文，不按语言代码分支；宽屏可容纳三列较长名称。
        int preferredCellWidth = preferredCellWidth();
        int gridWidth = Math.min(Math.min(620, Math.max(420, preferredCellWidth * 3 + GAP * 2)), width - 40);
        int left = (width - gridWidth) / 2;
        int footerWidth = (gridWidth - GAP * 2) / 3;
        var enableAll = Component.translatable("button.treasurehunter.all_on");
        var disableAll = Component.translatable("button.treasurehunter.all_off");
        var back = Component.translatable("button.treasurehunter.back");
        int footerHeight = MIN_BUTTON_HEIGHT;
        for (var label : List.of(enableAll, disableAll, back)) {
            footerHeight = Math.max(footerHeight, textHeight(label, footerWidth - TEXT_INSET * 2));
        }
        int footerY = height - 8 - footerHeight;
        panel = addRenderableWidget(new FilterPanel(left, 42, gridWidth, Math.max(32, footerY - 50)));
        panel.setScrollAmount(previousScroll);
        addRenderableWidget(new FilterButton(left, footerY, footerWidth, footerHeight, enableAll,
                button -> setAll(true), false, TEXT_INSET));
        addRenderableWidget(new FilterButton(left + footerWidth + GAP, footerY, footerWidth, footerHeight, disableAll,
                button -> setAll(false), false, TEXT_INSET));
        addRenderableWidget(new FilterButton(left + 2 * (footerWidth + GAP), footerY, footerWidth, footerHeight, back,
                button -> onClose(), false, TEXT_INSET));
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
                ? "screen.treasurehunter.category_off" : "screen.treasurehunter.category_on", category.filterLabel());
    }

    private int preferredCellWidth() {
        int result = 0;
        for (var category : ChestCategory.values()) {
            if (category == ChestCategory.OTHER_LOOT || category == ChestCategory.OTHER) continue;
            for (String key : new String[] {"screen.treasurehunter.category_on", "screen.treasurehunter.category_off"}) {
                result = Math.max(result, font.width(Component.translatable(key, category.filterLabel())));
            }
        }
        return result + ICON_INSET + STATE_INSET;
    }

    private int labelHeight(Component label, int width, boolean icon) {
        return textHeight(label, width - (icon ? ICON_INSET : TEXT_INSET) - STATE_INSET);
    }

    private int textHeight(Component label, int textWidth) {
        return Math.max(MIN_BUTTON_HEIGHT, font.split(label, Math.max(1, textWidth)).size() * font.lineHeight + 8);
    }

    private int categoryHeight(ChestCategory category, int width) {
        return Math.max(labelHeight(Component.translatable("screen.treasurehunter.category_on", category.filterLabel()), width, true),
                labelHeight(Component.translatable("screen.treasurehunter.category_off", category.filterLabel()), width, true));
    }

    /** 保留原版按钮、焦点与旁白，长名称静态换行，状态切换不改变行高。 */
    private final class FilterButton extends Button.Plain {
        private final boolean icon;
        private final int rightInset;
        private Component wrappedMessage;
        private int wrappedWidth = -1;
        private List<FormattedCharSequence> lines = List.of();

        FilterButton(int x, int y, int width, int height, Component label, OnPress action, boolean icon) {
            this(x, y, width, height, label, action, icon, STATE_INSET);
        }

        FilterButton(int x, int y, int width, int height, Component label, OnPress action, boolean icon, int rightInset) {
            super(x, y, width, height, label, action, DEFAULT_NARRATION);
            this.icon = icon;
            this.rightInset = rightInset;
        }

        @Override
        protected void extractDefaultLabel(ActiveTextCollector collector) {
            int left = getX() + (icon ? ICON_INSET : TEXT_INSET);
            int textWidth = Math.max(1, getRight() - rightInset - left);
            if (wrappedWidth != textWidth || !getMessage().equals(wrappedMessage)) {
                lines = font.split(getMessage(), textWidth);
                wrappedMessage = getMessage();
                wrappedWidth = textWidth;
            }
            int y = getY() + (getHeight() - lines.size() * font.lineHeight) / 2;
            for (var line : lines) {
                collector.accept(TextAlignment.CENTER, left + textWidth / 2, y, line);
                y += font.lineHeight;
            }
        }
    }

    /** 原版容器控件负责滚轮和键盘焦点；隐藏会遮挡最右列的滚动条，子按钮位置随滚动更新。 */
    private final class FilterPanel extends AbstractContainerWidget {
        private record Row(Button button, int x, int y, ChestCategory category, ItemStack icon) { }
        private record Heading(int y, FormattedCharSequence label) { }
        private final List<Row> rows = new ArrayList<>();
        private final List<Heading> headings = new ArrayList<>();
        private int totalHeight;

        FilterPanel(int x, int y, int width, int height) {
            super(x, y, width, height, ChestFilterScreen.this.title);
            int columns = Math.max(1, Math.min(3, (width + GAP) / (preferredCellWidth() + GAP)));
            int cellWidth = (width - (columns - 1) * GAP) / columns;
            var categories = java.util.Arrays.stream(ChestCategory.values())
                    .filter(category -> category != ChestCategory.OTHER_LOOT && category != ChestCategory.OTHER).toList();
            int nextY = 0;
            for (int start = 0; start < categories.size(); start += columns) {
                int end = Math.min(start + columns, categories.size());
                int rowHeight = MIN_BUTTON_HEIGHT;
                for (int i = start; i < end; i++) rowHeight = Math.max(rowHeight, categoryHeight(categories.get(i), cellWidth));
                for (int i = start; i < end; i++) {
                    addCategory(categories.get(i), (i - start) * (cellWidth + GAP), nextY, cellWidth, rowHeight, null);
                }
                nextY += rowHeight + ROW_GAP;
            }
            nextY = addHeading(nextY + 8, "screen.treasurehunter.filter.singleplayer");
            int customHeight = categoryHeight(ChestCategory.OTHER_LOOT, width);
            addCategory(ChestCategory.OTHER_LOOT, 0, nextY, width, customHeight, "screen.treasurehunter.filter.custom_hint");
            nextY = addHeading(nextY + customHeight + 12, "screen.treasurehunter.filter.server");
            int otherHeight = categoryHeight(ChestCategory.OTHER, cellWidth);
            addCategory(ChestCategory.OTHER, 0, nextY, cellWidth, otherHeight, "screen.treasurehunter.filter.other_hint");
            nextY += otherHeight + ROW_GAP;
            // 长选项独占整行；预留所有模式所需高度，切换时不跳动也不丢失焦点。
            int modeHeight = MIN_BUTTON_HEIGHT;
            for (var value : ServerFilterMode.values()) {
                modeHeight = Math.max(modeHeight, labelHeight(Component.translatable("option.treasurehunter.server_mode", value.label()), width, false));
            }
            var mode = new FilterButton(x, y + nextY, width, modeHeight, modeLabel(), button -> {
                config.serverFilterMode = config.serverFilterMode.next();
                config.save();
                if (TreasureHunterMod.scanner() != null && minecraft.getSingleplayerServer() == null) {
                    TreasureHunterMod.scanner().clearMarkers();
                }
            }, false);
            rows.add(new Row(mode, 0, nextY, null, ItemStack.EMPTY));
            totalHeight = nextY + modeHeight + ROW_GAP;
            positionRows();
        }

        private Component modeLabel() {
            return Component.translatable("option.treasurehunter.server_mode", config.serverFilterMode.label());
        }

        private int addHeading(int y, String key) {
            for (var line : font.split(Component.translatable(key), getWidth())) {
                headings.add(new Heading(y, line));
                y += font.lineHeight;
            }
            return y + 7;
        }

        private void addCategory(ChestCategory category, int x, int y, int width, int height, String hintKey) {
            var button = new FilterButton(getX() + x, getY() + y, width, height, categoryLabel(category), ignored -> {
                boolean enabled = config.isChestCategoryDisabled(category);
                config.setChestCategoryEnabled(category, enabled);
                var scanner = TreasureHunterMod.scanner();
                if (scanner != null) {
                    if (enabled) scanner.requestRescan();
                    else scanner.removeMarkersOfCategory(category);
                }
            }, true);
            button.setTooltip(Tooltip.create(hintKey == null ? category.label()
                    : Component.translatable(hintKey)));
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
        protected boolean isOverScrollbar(double mouseX, double mouseY) {
            // 不绘制滚动条后也不留下不可见的拖拽热区，按钮最右侧仍可点击。
            return false;
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
                    if (!row.icon().isEmpty()) graphics.fakeItem(row.icon(), button.getX() + 2, button.getY() + (button.getHeight() - 16) / 2);
                    boolean enabled = row.category() == null ? config.serverFilterMode != ServerFilterMode.OFF
                            : !config.isChestCategoryDisabled(row.category());
                    int indicatorX = button.getRight() - 9;
                    int indicatorY = button.getY() + (button.getHeight() - 6) / 2;
                    graphics.fill(indicatorX, indicatorY, indicatorX + 6, indicatorY + 6,
                            enabled ? 0xFF55FF55 : 0xFFFF5555);
                }
            } finally { graphics.disableScissor(); }
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
