package com.treasurehunter.xaero;

import com.treasurehunter.TreasureHunterMod;
import com.treasurehunter.gui.IgnoreRegionEditScreen;
import com.treasurehunter.scan.IgnoreRegionStore;
import com.treasurehunter.scan.IgnoreRegionStore.Anchor;
import com.treasurehunter.scan.IgnoreRegionStore.Region;
import com.treasurehunter.scan.IgnoreRegionStore.Shape;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** 在 Xaero 原生编辑页添加忽略范围入口，仅在其确认保存成功后更新独立关联数据。 */
public final class XaeroWaypointEditor {
    private static final String EDITOR = "xaero.common.gui.GuiAddWaypoint";
    private static final Map<Screen, Draft> DRAFTS = new WeakHashMap<>();
    private static boolean warned;
    private static int ticks;
    private static String scanningPath;

    /** 草稿不持有屏幕/控件引用，避免弱键被值间接引用而泄漏屏幕。 */
    private static final class Draft {
        int radius;
        Shape shape = Shape.CIRCLE;
        boolean changed;
    }

    public record Commit(Screen screen, List<Anchor> oldAnchors, int radius, Shape shape, boolean changed) { }

    private XaeroWaypointEditor() { }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (EDITOR.equals(screen.getClass().getName())) addControls(screen);
            if ("xaero.map.gui.GuiMap".equals(screen.getClass().getName())) {
                ScreenEvents.afterExtract(screen).register(IgnoreMapOverlay::draw);
            }
        });
    }

    private static void addControls(Screen screen) {
        try {
            // 26.5.3 / 26.6.0 的字段均已 javap 核对；更早/未知接口失败时只禁用此扩展。
            Button confirm = (Button) XaeroAccess.field(screen, "confirmButton");
            Draft draft = DRAFTS.get(screen);
            if (draft == null) {
                draft = new Draft();
                List<Anchor> anchors = originalAnchors(screen);
                if (!anchors.isEmpty()) {
                    Region region = TreasureHunterMod.ignoreRegions().find(anchors.getFirst());
                    if (region != null) { draft.radius = region.radiusChunks(); draft.shape = region.shape(); }
                }
                DRAFTS.put(screen, draft);
            }
            Draft current = draft;
            var button = Button.builder(Component.translatable("button.treasurehunter.ignore.set_waypoint"), ignored ->
                    Minecraft.getInstance().setScreenAndShow(new IgnoreRegionEditScreen(screen,
                            current.radius, current.shape, (radius, shape) -> {
                                current.radius = radius;
                                current.shape = shape;
                                current.changed = true;
                            }))).bounds(screen.width / 2 - 110, confirm.getY() - 26, 220, 20).build();
            button.setTooltip(Tooltip.create(Component.translatable("button.treasurehunter.ignore.link", draft.radius)));
            button.active = TreasureHunterMod.ignoreRegions().isOpen();
            Screens.getWidgets(screen).add(button);
        } catch (Throwable error) { warn(error); }
    }

    public static Commit beforeConfirm(Button button) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null || !EDITOR.equals(screen.getClass().getName())) return null;
        Draft draft = DRAFTS.get(screen);
        if (draft == null || !TreasureHunterMod.ignoreRegions().isOpen()) return null;
        try {
            if (XaeroAccess.field(screen, "confirmButton") != button || !button.active) return null;
            List<Anchor> old = originalAnchors(screen);
            if (draft.changed && draft.radius > 0) {
                // 子页已明确告知：启用范围会保存为永久点；先改表单，交给 Xaero 的原确认流程落盘。
                for (Object form : (Iterable<?>) XaeroAccess.field(screen, "editForms")) makePermanent(form);
                Object mutual = XaeroAccess.field(screen, "mutualForm");
                if (mutual != null) makePermanent(mutual);
            }
            return new Commit(screen, old, draft.radius, draft.shape, draft.changed);
        } catch (Throwable error) { warn(error); return null; }
    }

    private static void makePermanent(Object form) throws ReflectiveOperationException {
        if ((int) XaeroAccess.field(form, "disabledOrTemporary") >= 2) {
            XaeroAccess.setField(form, "disabledOrTemporary", 0);
            XaeroAccess.setField(form, "keepDisabledOrTemporary", false);
        }
    }

    public static void afterConfirm(Commit commit) {
        if (commit == null) return;
        try {
            Object screen = commit.screen();
            Object worlds = XaeroAccess.field(screen, "worlds");
            Object world = XaeroAccess.call(XaeroAccess.field(screen, "manager"), "getWorld",
                    XaeroAccess.call(worlds, "getCurrentKey"));
            String set = (String) XaeroAccess.call(XaeroAccess.field(screen, "sets"), "getCurrentSetKey");
            String dimension = XaeroAccess.dimension(world);
            if (dimension == null) return;
            var store = TreasureHunterMod.ignoreRegions();
            long previousRevision = store.revision();
            var removed = new ArrayList<String>();
            var replacements = new ArrayList<Region>();
            int index = 0;
            for (Object point : (Iterable<?>) XaeroAccess.field(screen, "waypointsEdited")) {
                Anchor anchor = XaeroAccess.anchor(world, set, point);
                Region old = index < commit.oldAnchors().size() ? store.find(commit.oldAnchors().get(index)) : null;
                int radius = commit.changed() ? commit.radius() : old == null ? 0 : old.radiusChunks();
                Shape shape = commit.changed() ? commit.shape() : old == null ? Shape.CIRCLE : old.shape();
                if (old != null) removed.add(old.id());
                if (radius > 0) {
                    replacements.add(new Region(old == null ? UUID.randomUUID().toString() : old.id(),
                            anchor.name(), dimension, anchor.x(), anchor.z(), radius, shape, anchor));
                }
                index++;
            }
            store.replaceLinked(removed, replacements);
            DRAFTS.remove(commit.screen());
            refreshLinks();
            if (store.revision() != previousRevision) TreasureHunterMod.scanner().clearMarkers();
        } catch (Throwable error) { warn(error); }
    }

    private static List<Anchor> originalAnchors(Screen screen) throws ReflectiveOperationException {
        var anchors = new ArrayList<Anchor>();
        Object world = XaeroAccess.field(screen, "defaultWorld");
        if (world == null) world = XaeroAccess.currentWorld();
        String set = (String) XaeroAccess.field(screen, "fromSet");
        if (set == null) set = (String) XaeroAccess.call(world, "getCurrentWaypointSetId");
        for (Object point : (Iterable<?>) XaeroAccess.field(screen, "waypointsEdited")) {
            anchors.add(XaeroAccess.anchor(world, set, point));
        }
        return anchors;
    }

    public static void tick() {
        if (++ticks % 20 == 0) refreshLinks();
    }

    public static String scanningPath() { return scanningPath; }

    public static void refreshLinks() {
        var store = TreasureHunterMod.ignoreRegions();
        if (store == null || !store.isOpen()) { scanningPath = null; return; }
        var anchors = new HashSet<Anchor>();
        try {
            if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
                Object current = XaeroAccess.currentWorld();
                if (current != null) {
                    scanningPath = XaeroAccess.worldPath(current);
                    for (Object world : XaeroAccess.allWorlds(current)) {
                        for (Object set : (Iterable<?>) XaeroAccess.call(world, "getIterableWaypointSets")) {
                            String name = (String) XaeroAccess.call(set, "getName");
                            for (Object point : (Iterable<?>) XaeroAccess.call(set, "getWaypoints")) {
                                if (!(boolean) XaeroAccess.call(point, "isTemporary")) anchors.add(XaeroAccess.anchor(world, name, point));
                            }
                        }
                    }
                } else scanningPath = null;
            } else scanningPath = null;
        } catch (Throwable error) { scanningPath = null; warn(error); }
        store.setLiveAnchors(anchors, scanningPath);
    }

    private static void warn(Throwable error) {
        if (!warned) TreasureHunterMod.LOGGER.warn("Xaero TreasureHunter: 忽略区域的 Xaero 联动暂不可用：{}", error.toString());
        warned = true;
    }
}
