package com.treasurehunter.xaero;

import com.treasurehunter.TreasureHunterMod;
import com.treasurehunter.scan.IgnoreRegionStore.Shape;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

/** 世界地图上的青色边框与向内渐弱的荧光；只提交普通 GUI 几何，不修改 Xaero 渲染器。 */
public final class IgnoreMapOverlay {
    /** 初版四层内光的宽度与 alpha 各减半：每层半个 GUI 像素，总宽两个 GUI 像素。 */
    private static final int[] COLORS = {0x6F69F5FF, 0x4469F5FF, 0x2869F5FF, 0x1469F5FF};
    private static final double BAND_GUI_PIXELS = 0.5;
    private static boolean warned;

    private IgnoreMapOverlay() { }

    public static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        if (TreasureHunterMod.config() == null || !TreasureHunterMod.config().showIgnoreOutlines
                || !TreasureHunterMod.ignoreRegions().isOpen()) return;
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
            String worldPath = viewedWaypointWorld(dimension);
            var store = TreasureHunterMod.ignoreRegions();
            // 地图使用自定义图片渲染层；追加层保证轮廓不会被图片批处理覆盖。
            graphics.nextStratum();
            graphics.pose().pushMatrix();
            try {
                graphics.enableScissor(0, 0, screen.width, screen.height);
                graphics.pose().scale((float) (1 / guiScale), (float) (1 / guiScale));
                for (var region : store.regions()) {
                    if (!dimension.equals(region.dimension())) continue;
                    if (region.anchor() != null && (!store.isLinkedAvailable(region)
                            || !region.anchor().worldPath().equals(worldPath))) continue;
                    double cx = screen.width * guiScale / 2.0 + (region.x() + 0.5 - cameraX) * pixelsPerBlock;
                    double cy = screen.height * guiScale / 2.0 + (region.z() + 0.5 - cameraZ) * pixelsPerBlock;
                    double radius = region.radiusChunks() * 16 * pixelsPerBlock;
                    if (cx + radius < 0 || cy + radius < 0 || cx - radius > physicalWidth || cy - radius > physicalHeight) continue;
                    double band = guiScale * BAND_GUI_PIXELS;
                    if (region.shape() == Shape.SQUARE) square(graphics, cx, cy, radius, band, physicalWidth, physicalHeight);
                    else circle(graphics, cx, cy, radius, band, physicalWidth, physicalHeight);
                }
            } finally {
                graphics.disableScissor();
                graphics.pose().popMatrix();
            }
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

    private static void square(GuiGraphicsExtractor g, double cx, double cy, double r, double band, int width, int height) {
        for (int i = 0; i < COLORS.length && r - i * band >= band; i++) {
            double a = r - i * band;
            fill(g, cx - a, cy - a, cx + a, cy - a + band, COLORS[i], width, height);
            fill(g, cx - a, cy + a - band, cx + a, cy + a, COLORS[i], width, height);
            fill(g, cx - a, cy - a + band, cx - a + band, cy + a - band, COLORS[i], width, height);
            fill(g, cx + a - band, cy - a + band, cx + a, cy + a - band, COLORS[i], width, height);
        }
    }

    private static void circle(GuiGraphicsExtractor g, double cx, double cy, double r, double band, int width, int height) {
        int minY = (int) Math.max(0, Math.floor(cy - r));
        int maxY = (int) Math.min(height - 1, Math.ceil(cy + r));
        for (int y = minY; y <= maxY; y++) {
            double dy = y + 0.5 - cy;
            for (int i = 0; i < COLORS.length && r - i * band > 0; i++) {
                double outer = r - i * band;
                if (Math.abs(dy) > outer) continue;
                double inner = Math.max(0, outer - band);
                double outerX = Math.sqrt(Math.max(0, outer * outer - dy * dy));
                double innerX = Math.abs(dy) < inner ? Math.sqrt(Math.max(0, inner * inner - dy * dy)) : 0;
                fill(g, cx - outerX, y, cx - innerX, y + 1, COLORS[i], width, height);
                fill(g, cx + innerX, y, cx + outerX, y + 1, COLORS[i], width, height);
            }
        }
    }

    private static void fill(GuiGraphicsExtractor g, double left, double top, double right, double bottom,
            int color, int width, int height) {
        int x1 = (int) Math.max(0, Math.floor(left));
        int y1 = (int) Math.max(0, Math.floor(top));
        int x2 = (int) Math.min(width, Math.ceil(right));
        int y2 = (int) Math.min(height, Math.ceil(bottom));
        if (x2 > x1 && y2 > y1) g.fill(x1, y1, x2, y2, color);
    }
}
