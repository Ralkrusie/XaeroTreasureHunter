package com.treasurehunter.gui;

import com.treasurehunter.TreasureHunterMod;
import com.treasurehunter.scan.IgnoreRegionStore;
import com.treasurehunter.scan.IgnoreRegionStore.Region;
import com.treasurehunter.scan.IgnoreRegionStore.Shape;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.function.BiConsumer;

/** 独立区域编辑器与 Xaero 的半径/形状子页；点击保存才提交，返回则丢弃输入。 */
public final class IgnoreRegionEditScreen extends Screen {
    private final Screen parent;
    private final Region original;
    private final BiConsumer<Integer, Shape> waypointSave;
    private final String id;
    private int initialRadius;
    private Shape shape;
    private EditBox radius;
    private EditBox name;
    private EditBox x;
    private EditBox z;
    private Button save;
    private Button shapeButton;

    public IgnoreRegionEditScreen(Screen parent, Region original) {
        super(Component.translatable("screen.treasurehunter.ignore.edit"));
        this.parent = parent;
        this.original = original;
        this.waypointSave = null;
        this.id = original == null ? UUID.randomUUID().toString() : original.id();
        this.initialRadius = original == null ? 4 : original.radiusChunks();
        this.shape = original == null ? Shape.CIRCLE : original.shape();
    }

    public IgnoreRegionEditScreen(Screen parent, int initialRadius, Shape shape, BiConsumer<Integer, Shape> save) {
        super(Component.translatable("screen.treasurehunter.ignore.waypoint"));
        this.parent = parent;
        this.original = null;
        this.waypointSave = save;
        this.id = null;
        this.initialRadius = initialRadius;
        this.shape = shape;
    }

    @Override
    protected void init() {
        int left = width / 2 - 110;
        int y = waypointSave == null ? 100 : 64;
        String oldRadius = radius == null ? Integer.toString(initialRadius) : radius.getValue();
        if (waypointSave == null) {
            var pos = minecraft.player.blockPosition();
            String oldName = name == null ? original == null
                    ? Component.translatable("option.treasurehunter.ignore.default_name").getString() : original.name() : name.getValue();
            String oldX = x == null ? Integer.toString(original == null ? pos.getX() : original.x()) : x.getValue();
            String oldZ = z == null ? Integer.toString(original == null ? pos.getZ() : original.z()) : z.getValue();
            name = field(left, 42, 220, "option.treasurehunter.ignore.name", oldName);
            x = field(left, 70, 106, "option.treasurehunter.ignore.x", oldX);
            z = field(left + 114, 70, 106, "option.treasurehunter.ignore.z", oldZ);
            if (original != null && original.anchor() != null) {
                name.setEditable(false);
                x.setEditable(false);
                z.setEditable(false);
            }
        }
        radius = field(left + 128, y, 92, "option.treasurehunter.ignore.radius", oldRadius);
        radius.setMaxLength(3);
        shapeButton = addRenderableWidget(Button.builder(shapeLabel(), button -> {
            shape = shape == Shape.CIRCLE ? Shape.SQUARE : Shape.CIRCLE;
            shapeButton.setMessage(shapeLabel());
        }).bounds(left, y + 28, 220, 20).build());
        save = addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.save"), button -> commit())
                .bounds(left, y + 70, 106, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.back"), button -> onClose())
                .bounds(left + 114, y + 70, 106, 20).build());
        if (original != null) {
            addRenderableWidget(Button.builder(Component.translatable("button.treasurehunter.ignore.remove"), button -> {
                TreasureHunterMod.ignoreRegions().remove(original.id());
                TreasureHunterMod.scanner().clearMarkers();
                onClose();
            }).bounds(left, y + 98, 220, 20).build());
        }
    }

    private EditBox field(int left, int y, int width, String key, String value) {
        var box = new EditBox(font, left, y, width, 20, Component.translatable(key));
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private Component shapeLabel() {
        return Component.translatable("option.treasurehunter.ignore.shape",
                Component.translatable("option.treasurehunter.ignore.shape." + shape.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private int validRadius() {
        try {
            int value = Integer.parseInt(radius.getValue());
            return value >= 0 && value <= IgnoreRegionStore.MAX_RADIUS_CHUNKS ? value : -1;
        } catch (NumberFormatException e) { return -1; }
    }

    private boolean valid() {
        if (validRadius() < 0) return false;
        if (waypointSave != null) return true;
        try {
            int px = Integer.parseInt(x.getValue());
            int pz = Integer.parseInt(z.getValue());
            return !name.getValue().isBlank() && Math.abs((long) px) <= 30_000_000 && Math.abs((long) pz) <= 30_000_000;
        } catch (NumberFormatException e) { return false; }
    }

    private void commit() {
        if (!valid()) return;
        int value = validRadius();
        if (waypointSave != null) waypointSave.accept(value, shape);
        else {
            var store = TreasureHunterMod.ignoreRegions();
            if (value == 0) store.remove(id);
            else store.put(new Region(id, name.getValue().trim(),
                    original == null ? minecraft.level.dimension().identifier().toString() : original.dimension(),
                    Integer.parseInt(x.getValue()), Integer.parseInt(z.getValue()), value, shape,
                    original == null ? null : original.anchor()));
            TreasureHunterMod.scanner().clearMarkers();
        }
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        save.active = valid();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("option.treasurehunter.ignore.radius"), width / 2 - 110, radius.getY() + 6, 0xFFFFFFFF);
        if (waypointSave == null) {
            graphics.text(font, Component.translatable("option.treasurehunter.ignore.x"), x.getX(), x.getY() - 10, 0xFFA0A0A0);
            graphics.text(font, Component.translatable("option.treasurehunter.ignore.z"), z.getX(), z.getY() - 10, 0xFFA0A0A0);
        }
        graphics.centeredText(font, Component.translatable("screen.treasurehunter.ignore.radius_hint"),
                width / 2, radius.getY() + 54, 0xFFA0A0A0);
        if (waypointSave != null) {
            int y = radius.getY() + 98;
            for (var line : font.split(Component.translatable("screen.treasurehunter.ignore.waypoint_hint"), Math.min(width - 24, 300))) {
                graphics.centeredText(font, line, width / 2, y, 0xFFB8C7D9);
                y += 11;
            }
        }
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }
}
