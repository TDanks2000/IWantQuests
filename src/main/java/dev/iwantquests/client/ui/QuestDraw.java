package dev.iwantquests.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Low-level drawing helpers shared by the quest book screen. */
public final class QuestDraw {
    private QuestDraw() {}

    /**
     * Draws a right-angle connector: horizontal from (x0,y0) to the middle x, vertical to y1, horizontal to x1.
     * {@code pad} grows each segment on every side, so an outline pass uses {@code pad = 1} with the same thickness.
     */
    public static void elbow(
            GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int thickness, int pad, int color) {
        int midX = (x0 + x1) / 2;
        horizontal(g, x0, midX, y0, thickness, pad, color);
        vertical(g, midX, y0, y1, thickness, pad, color);
        horizontal(g, midX, x1, y1, thickness, pad, color);
    }

    /** Straight connector, used for freely arranged quest trees. */
    public static void direct(
            GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int thickness, int pad, int color) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().rotate((float) Math.atan2(y1 - y0, x1 - x0));
        int half = thickness / 2;
        g.fill(-pad, -half - pad, (int) Math.ceil(length) + pad, thickness - half + pad, color);
        g.pose().popMatrix();
    }

    private static void horizontal(GuiGraphicsExtractor g, int a, int b, int y, int thickness, int pad, int color) {
        g.fill(Math.min(a, b) - pad, y - pad, Math.max(a, b) + thickness + pad, y + thickness + pad, color);
    }

    private static void vertical(GuiGraphicsExtractor g, int x, int a, int b, int thickness, int pad, int color) {
        g.fill(x - pad, Math.min(a, b) - pad, x + thickness + pad, Math.max(a, b) + thickness + pad, color);
    }

    /** Filled rectangle with a 1px border drawn inside it. */
    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** Rectangle with diagonally cut corners, {@code cut} pixels on each side. */
    public static void chamfer(GuiGraphicsExtractor g, int x, int y, int w, int h, int cut, int color) {
        cut = Math.max(0, Math.min(cut, Math.min(w, h) / 2));
        for (int row = 0; row < cut; row++) {
            int inset = cut - row;
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
        g.fill(x, y + cut, x + w, y + h - cut, color);
    }

    /**
     * Fills a node geometry: {@code circle}, {@code square}, {@code rounded}, {@code octagon}, {@code hexagon} or
     * {@code diamond}. Shapes are rasterised a row at a time so they stay crisp at any zoom.
     */
    public static void geometry(GuiGraphicsExtractor g, String geometry, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        switch (geometry) {
            case "square" -> g.fill(x, y, x + w, y + h, color);
            case "rounded" -> chamfer(g, x, y, w, h, Math.max(1, Math.min(w, h) / 8), color);
            case "octagon" -> chamfer(g, x, y, w, h, Math.max(1, Math.round(Math.min(w, h) * .29f)), color);
            case "diamond" -> rows(g, x, y, w, h, color, t -> t * w / 2.0);
            case "hexagon" -> rows(g, x, y, w, h, color, t -> t * w / 4.0);
            default -> {
                double rx = w / 2.0, ry = h / 2.0;
                rows(g, x, y, w, h, color, t -> rx - rx * Math.sqrt(Math.max(0, 1 - t * t)));
            }
        }
    }

    /** Fills each pixel row, inset from both sides by {@code inset(t)}, where t is 0 at the middle row and 1 at the edge. */
    private static void rows(
            GuiGraphicsExtractor g,
            int x,
            int y,
            int w,
            int h,
            int color,
            java.util.function.DoubleUnaryOperator inset) {
        for (int row = 0; row < h; row++) {
            double t = Math.abs(row + .5 - h / 2.0) / (h / 2.0);
            int in = (int) Math.round(inset.applyAsDouble(t));
            if (in * 2 < w) g.fill(x + in, y + row, x + w - in, y + row + 1, color);
        }
    }

    /** A geometry with a border: the edge colour first, then the face inset by {@code border}. */
    public static void framed(
            GuiGraphicsExtractor g, String geometry, int x, int y, int w, int h, int border, int edge, int face) {
        geometry(g, geometry, x, y, w, h, edge);
        geometry(g, geometry, x + border, y + border, w - border * 2, h - border * 2, face);
    }

    /**
     * A themed panel: the nine-slice sprite when the theme supplies one, otherwise a flat fill with an optional
     * 1px outline (skipped when the border is fully transparent).
     */
    public static void panel(
            GuiGraphicsExtractor g, @Nullable Identifier sprite, int x, int y, int w, int h, int fill, int border) {
        if (sprite != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
            return;
        }
        g.fill(x, y, x + w, y + h, fill);
        if ((border >>> 24) != 0) g.outline(x, y, w, h, border);
    }

    /** A small triangle pointing along (dx, dy), used as a connector arrowhead. */
    public static void arrowhead(
            GuiGraphicsExtractor g, double tipX, double tipY, double dx, double dy, int size, int color) {
        double length = Math.hypot(dx, dy);
        if (length < 1) return;
        g.pose().pushMatrix();
        g.pose().translate((float) tipX, (float) tipY);
        g.pose().rotate((float) Math.atan2(dy, dx));
        for (int i = 0; i < size; i++) g.fill(-size + i, -(i + 1) / 2, -size + i + 1, (i + 1) / 2 + 1, color);
        g.pose().popMatrix();
    }

    /** Multiplies the colour channels by {@code factor}, keeping alpha. */
    public static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xff) * factor));
        int gr = Math.min(255, Math.round(((argb >> 8) & 0xff) * factor));
        int b = Math.min(255, Math.round((argb & 0xff) * factor));
        return (argb & 0xff000000) | (r << 16) | (gr << 8) | b;
    }

    /** Blends {@code top} over {@code base} by {@code amount} (0..1); the result is opaque when base is. */
    public static int mix(int base, int top, float amount) {
        int a = Math.round(((base >>> 24) & 0xff) * (1 - amount) + ((top >>> 24) & 0xff) * amount);
        int r = Math.round(((base >> 16) & 0xff) * (1 - amount) + ((top >> 16) & 0xff) * amount);
        int gr = Math.round(((base >> 8) & 0xff) * (1 - amount) + ((top >> 8) & 0xff) * amount);
        int b = Math.round((base & 0xff) * (1 - amount) + (top & 0xff) * amount);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    /** Draws an item scaled from its native 16x16 size into a {@code size} x {@code size} box at (x, y). */
    public static void itemScaled(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) return;
        float factor = size / 16f;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(factor, factor);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    /** Parses a GUI sprite or texture ID from config, returning null for blank or invalid input. */
    public static @Nullable Identifier sprite(@Nullable String id) {
        return id == null || id.isBlank() ? null : Identifier.tryParse(id);
    }
}
