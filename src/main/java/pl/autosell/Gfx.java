package pl.autosell;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Proste narzędzia rysowania (zaokrąglone prostokąty, linie, okręgi, tekst). */
public final class Gfx {
    private Gfx() {
    }

    public static void rrect(DrawContext g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, c);
        }
        g.fill(x, y + r, x + w, y + h - r, c);
    }

    /** Zaokrąglony prostokąt z obwódką o grubości 1 px. */
    public static void rrectBorder(DrawContext g, int x, int y, int w, int h, int r, int fill, int border) {
        rrect(g, x, y, w, h, r, border);
        rrect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public static void circle(DrawContext g, int cx, int cy, int r, int c) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy + 0.25)));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, c);
        }
    }

    public static void ring(DrawContext g, int cx, int cy, int r, int c) {
        int steps = Math.max(24, r * 5);
        for (int i = 0; i < steps; i++) {
            double a = i * 2 * Math.PI / steps;
            int px = cx + (int) Math.round(Math.cos(a) * r);
            int py = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, c);
        }
    }

    public static void line(DrawContext g, double x1, double y1, double x2, double y2, int c, int t) {
        double dx = x2 - x1, dy = y2 - y1;
        int steps = (int) Math.min(4000, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        if (steps <= 0) {
            g.fill((int) x1, (int) y1, (int) x1 + t, (int) y1 + t, c);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int px = (int) Math.round(x1 + dx * i / steps);
            int py = (int) Math.round(y1 + dy * i / steps);
            g.fill(px, py, px + t, py + t, c);
        }
    }

    public static void boxOutline(DrawContext g, int x1, int y1, int x2, int y2, int c) {
        int dark = 0xAA000000;
        // zewnętrzny cień dla czytelności
        g.fill(x1 - 1, y1 - 1, x2 + 1, y1, dark);
        g.fill(x1 - 1, y2, x2 + 1, y2 + 1, dark);
        g.fill(x1 - 1, y1, x1, y2, dark);
        g.fill(x2, y1, x2 + 1, y2, dark);
        g.fill(x1, y1, x2, y1 + 1, c);
        g.fill(x1, y2 - 1, x2, y2, c);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, c);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, c);
    }

    public static void text(DrawContext g, TextRenderer tr, String s, int x, int y, int c) {
        g.drawText(tr, s, x, y, c, false);
    }

    public static void textShadow(DrawContext g, TextRenderer tr, String s, int x, int y, int c) {
        g.drawText(tr, s, x, y, c, true);
    }

    public static void textCenter(DrawContext g, TextRenderer tr, String s, int cx, int y, int c) {
        g.drawText(tr, s, cx - tr.getWidth(s) / 2, y, c, false);
    }

    public static void textRight(DrawContext g, TextRenderer tr, String s, int rx, int y, int c) {
        g.drawText(tr, s, rx - tr.getWidth(s), y, c, false);
    }

    public static void textScaled(DrawContext g, TextRenderer tr, String s, int x, int y, float scale, int c) {
        g.getMatrices().push();
        g.getMatrices().translate(x, y, 0);
        g.getMatrices().scale(scale, scale, 1f);
        g.drawText(tr, s, 0, 0, c, false);
        g.getMatrices().pop();
    }

    public static void pill(DrawContext g, TextRenderer tr, String s, int cx, int y, int fg, int bg) {
        int w = tr.getWidth(s) + 8;
        rrect(g, cx - w / 2, y - 1, w, 11, 3, bg);
        g.drawText(tr, s, cx - tr.getWidth(s) / 2, y, fg, false);
    }
}
