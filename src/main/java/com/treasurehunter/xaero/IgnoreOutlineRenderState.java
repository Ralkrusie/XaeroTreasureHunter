package com.treasurehunter.xaero;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.treasurehunter.scan.IgnoreRegionStore.Shape;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.List;

/** 整帧免扫描轮廓的单个 GUI 批次；圆弧按物理像素误差细分，只生成可见边缘。 */
record IgnoreOutlineRenderState(Matrix3x2fc pose, ScreenRectangle scissorArea, List<Outline> outlines,
        int width, int height) implements GuiElementRenderState {
    /** 沿用四层内光：每层半个 GUI 像素，alpha 约为初版的一半。 */
    private static final int[] COLORS = {0x6F69F5FF, 0x4469F5FF, 0x2869F5FF, 0x1469F5FF};
    /** 弦到圆弧的最大距离不超过四分之一物理像素，避免随 GUI 缩放出现折线。 */
    private static final double MAX_ARC_ERROR = 0.25;
    private static final int MAX_ARC_DEPTH = 24;

    record Outline(double x, double y, double radius, double band, Shape shape) { }

    IgnoreOutlineRenderState {
        pose = new Matrix3x2f(pose);
        outlines = List.copyOf(outlines);
    }

    static boolean isVisible(Outline outline, int width, int height) {
        double x = outline.x(), y = outline.y(), radius = outline.radius();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(radius)
                || !Double.isFinite(outline.band()) || radius <= 0 || outline.band() <= 0
                || x + radius < 0 || y + radius < 0 || x - radius > width || y - radius > height) return false;
        double inner = Math.max(0, radius - COLORS.length * outline.band());
        if (outline.shape() == Shape.SQUARE) {
            return !(x - inner <= 0 && y - inner <= 0 && x + inner >= width && y + inner >= height);
        }
        // 视口完全处于圆环内部时没有边缘可画；圆的包围盒相交不代表圆环可见。
        double nearestX = Math.max(0, Math.max(-x, x - width));
        double nearestY = Math.max(0, Math.max(-y, y - height));
        if (Math.hypot(nearestX, nearestY) > radius) return false;
        double farthestX = Math.max(Math.abs(x), Math.abs(width - x));
        double farthestY = Math.max(Math.abs(y), Math.abs(height - y));
        return Math.hypot(farthestX, farthestY) >= inner;
    }

    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override public ScreenRectangle bounds() { return scissorArea; }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        for (var outline : outlines) {
            if (!isVisible(outline, width, height)) continue;
            if (outline.shape() == Shape.SQUARE) square(vertices, outline);
            else {
                // 每个象限的 x/y 单调，端点包围盒可安全裁掉整段不可见圆弧。
                arc(vertices, outline, 1, 0, 0, 1, 0);
                arc(vertices, outline, 0, 1, -1, 0, 0);
                arc(vertices, outline, -1, 0, 0, -1, 0);
                arc(vertices, outline, 0, -1, 1, 0, 0);
            }
        }
    }

    private void square(VertexConsumer vertices, Outline outline) {
        for (int i = 0; i < COLORS.length; i++) {
            double a = outline.radius() - i * outline.band();
            if (a <= 0) break;
            double band = Math.min(a, outline.band());
            double x = outline.x(), y = outline.y();
            rectangle(vertices, x - a, y - a, x + a, y - a + band, COLORS[i]);
            rectangle(vertices, x - a, y + a - band, x + a, y + a, COLORS[i]);
            rectangle(vertices, x - a, y - a + band, x - a + band, y + a - band, COLORS[i]);
            rectangle(vertices, x + a - band, y - a + band, x + a, y + a - band, COLORS[i]);
        }
    }

    private void arc(VertexConsumer vertices, Outline outline, double ux0, double uy0,
            double ux1, double uy1, int depth) {
        double x0 = outline.x() + outline.radius() * ux0, y0 = outline.y() + outline.radius() * uy0;
        double x1 = outline.x() + outline.radius() * ux1, y1 = outline.y() + outline.radius() * uy1;
        double glow = COLORS.length * outline.band();
        if (Math.max(x0, x1) + glow < 0 || Math.min(x0, x1) - glow > width
                || Math.max(y0, y1) + glow < 0 || Math.min(y0, y1) - glow > height) return;
        double sumX = ux0 + ux1, sumY = uy0 + uy1;
        double length = Math.hypot(sumX, sumY);
        double error = outline.radius() * (1 - length / 2);
        if (error > MAX_ARC_ERROR && depth < MAX_ARC_DEPTH) {
            double midX = sumX / length, midY = sumY / length;
            arc(vertices, outline, ux0, uy0, midX, midY, depth + 1);
            arc(vertices, outline, midX, midY, ux1, uy1, depth + 1);
            return;
        }
        for (int i = 0; i < COLORS.length; i++) {
            double outer = outline.radius() - i * outline.band();
            if (outer <= 0) break;
            double inner = Math.max(0, outer - outline.band());
            vertex(vertices, outline.x() + outer * ux0, outline.y() + outer * uy0, COLORS[i]);
            vertex(vertices, outline.x() + inner * ux0, outline.y() + inner * uy0, COLORS[i]);
            vertex(vertices, outline.x() + inner * ux1, outline.y() + inner * uy1, COLORS[i]);
            vertex(vertices, outline.x() + outer * ux1, outline.y() + outer * uy1, COLORS[i]);
        }
    }

    private void rectangle(VertexConsumer vertices, double x0, double y0, double x1, double y1, int color) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0);
        x1 = Math.min(width, x1); y1 = Math.min(height, y1);
        if (x1 <= x0 || y1 <= y0) return;
        vertex(vertices, x0, y0, color);
        vertex(vertices, x0, y1, color);
        vertex(vertices, x1, y1, color);
        vertex(vertices, x1, y0, color);
    }

    private void vertex(VertexConsumer vertices, double x, double y, int color) {
        vertices.addVertexWith2DPose(pose, (float) x, (float) y).setColor(color);
    }
}
