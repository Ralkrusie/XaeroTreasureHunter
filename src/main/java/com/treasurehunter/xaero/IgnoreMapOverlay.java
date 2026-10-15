package com.treasurehunter.xaero;

import com.treasurehunter.TreasureHunterMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.ResourceKey;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.Objects;

/** 世界地图上的青色边框与向内渐弱的荧光；只提交普通 GUI 几何，不修改 Xaero 渲染器。 */
public final class IgnoreMapOverlay {
    private static final double BAND_GUI_PIXELS = 0.5;
    private static boolean warned;

    private IgnoreMapOverlay() { }

    public static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        if (TreasureHunterMod.config() == null || !TreasureHunterMod.config().showIgnoreOutlines
                || !TreasureHunterMod.ignoreRegions().isOpen()) return;
        var store = TreasureHunterMod.ignoreRegions();
        var regions = store.regions();
        if (regions.isEmpty()) return;
        try {
            Object processor = XaeroAccess.call(screen, "getMapProcessor");
            if (processor == null) return;
            Object mapWorld = XaeroAccess.call(processor, "getMapWorld");
            if (mapWorld == null) return;
            Object key = XaeroAccess.call(mapWorld, "getCurrentDimensionId");
            if (!(key instanceof ResourceKey<?> dimensionKey)) return;
            String dimension = dimensionKey.identifier().toString();
            double cameraX = (double) XaeroAccess.field(screen, "cameraX");
            double cameraZ = (double) XaeroAccess.field(screen, "cameraZ");
            // javap 校验（世界地图 1.46.4 / 1.47.0）：scale 是物理像素/格，screenScale 是 GUI 缩放。
            double pixelsPerBlock = (double) XaeroAccess.field(screen, "scale");
            double guiScale = (double) XaeroAccess.field(screen, "screenScale");
            if (!Double.isFinite(pixelsPerBlock) || pixelsPerBlock <= 0 || !Double.isFinite(guiScale) || guiScale <= 0) return;
            int physicalWidth = (int) Math.ceil(screen.width * guiScale);
            int physicalHeight = (int) Math.ceil(screen.height * guiScale);
            if (physicalWidth <= 0 || physicalHeight <= 0) return;
            String worldPath = null;
            boolean worldResolved = false;
            var outlines = new ArrayList<IgnoreOutlineRenderState.Outline>();
            for (var region : regions) {
                if (!dimension.equals(region.dimension())) continue;
                double cx = screen.width * guiScale / 2.0 + (region.x() + 0.5 - cameraX) * pixelsPerBlock;
                double cy = screen.height * guiScale / 2.0 + (region.z() + 0.5 - cameraZ) * pixelsPerBlock;
                double radius = region.radiusChunks() * 16 * pixelsPerBlock;
                var outline = new IgnoreOutlineRenderState.Outline(cx, cy, radius, guiScale * BAND_GUI_PIXELS, region.shape());
                if (!IgnoreOutlineRenderState.isVisible(outline, physicalWidth, physicalHeight)) continue;
                if (region.anchor() != null) {
                    if (!store.isLinkedAvailable(region)) continue;
                    // 独立区域不需要读取 Xaero 路径点世界，更不必每帧遍历其他维度。
                    if (!worldResolved) { worldPath = viewedWaypointWorld(dimension); worldResolved = true; }
                    if (!region.anchor().worldPath().equals(worldPath)) continue;
                }
                outlines.add(outline);
            }
            if (outlines.isEmpty()) return;
            // javap 校验：原版 addGuiElement 会逐元素进行相交排序。整帧仅提交一个批次，
            // 不能把圆环逐像素行拆成数万次 fill，否则会放大排序与对象分配开销。
            var renderState = (GuiRenderState) XaeroAccess.field(graphics, "guiRenderState");
            var pose = new Matrix3x2f(graphics.pose()).scale((float) (1 / guiScale));
            var clip = new ScreenRectangle(0, 0, screen.width, screen.height).transformAxisAligned(graphics.pose());
            graphics.nextStratum();
            renderState.addGuiElement(new IgnoreOutlineRenderState(pose, clip, outlines, physicalWidth, physicalHeight));
        } catch (Throwable error) {
            if (!warned) TreasureHunterMod.LOGGER.warn("Xaero TreasureHunter: 忽略区域轮廓显示暂不可用：{}", error.toString());
            warned = true;
        }
    }

    private static String viewedWaypointWorld(String dimension) throws ReflectiveOperationException {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap")) return null;
        Object current = XaeroAccess.currentWorld();
        if (current == null) return null;
        if (dimension.equals(XaeroAccess.dimension(current))) return XaeroAccess.worldPath(current);
        Object node = XaeroAccess.call(current, "getNode");
        String path = null;
        for (Object world : XaeroAccess.allWorlds(current)) {
            if (dimension.equals(XaeroAccess.dimension(world)) && Objects.equals(node, XaeroAccess.call(world, "getNode"))) {
                if (path != null) return null; // 同维度映射不唯一时不在错误世界绘制关联区域。
                path = XaeroAccess.worldPath(world);
            }
        }
        return path;
    }

}
