package pl.autosell;

/** Paleta kolorów wyliczana z ustawień motywu (odcień, jasność, przezroczystość). */
public final class Theme {
    public static int accent, accentHover, accentDim, onAccent;
    public static int bg0, bg1, bg2, bg3, border;
    public static int text, textDim, textFaint;
    public static int good = 0xFF5CE08A, warn = 0xFFFFC857, bad = 0xFFFF5C6C;
    public static boolean light;
    public static int radius = 8;

    public record Preset(String name, float hue, float sat, float bright, float alpha) {
    }

    public static final Preset[] PRESETS = {
            new Preset("Noc", 265f, 0.65f, 0.07f, 0.96f),
            new Preset("Ocean", 200f, 0.75f, 0.08f, 0.96f),
            new Preset("Szmaragd", 150f, 0.70f, 0.07f, 0.96f),
            new Preset("Zachód", 20f, 0.80f, 0.08f, 0.96f),
            new Preset("Róża", 330f, 0.60f, 0.09f, 0.96f),
            new Preset("Mono", 0f, 0.00f, 0.08f, 0.96f),
            new Preset("Jasny", 215f, 0.70f, 0.93f, 0.98f),
    };

    private Theme() {
    }

    public static void refresh(AutoSellConfig c) {
        radius = c.themeRadius;
        float b = c.themeBright;
        light = b > 0.55f;
        int a = Math.round(c.themeAlpha * 255f);
        float dir = light ? -1f : 1f;
        bg0 = withAlpha(gray(b), a);
        bg1 = withAlpha(gray(clamp01(b + dir * 0.045f)), Math.min(255, a + 6));
        bg2 = withAlpha(gray(clamp01(b + dir * 0.095f)), 255);
        bg3 = withAlpha(gray(clamp01(b + dir * 0.15f)), 255);
        border = withAlpha(gray(clamp01(b + dir * 0.20f)), 255);
        accent = hsv(c.themeHue, c.themeSat, light ? 0.85f : 1f);
        accentHover = mix(accent, 0xFFFFFFFF, 0.18f);
        accentDim = mix(accent, bg0 | 0xFF000000, 0.65f);
        int lum = luminance(accent);
        onAccent = lum > 150 ? 0xFF12121A : 0xFFFFFFFF;
        text = light ? 0xFF1A1B24 : 0xFFF1F2F8;
        textDim = mix(text | 0xFF000000, bg0 | 0xFF000000, 0.42f);
        textFaint = mix(text | 0xFF000000, bg0 | 0xFF000000, 0.65f);
    }

    // ---------- narzędzia kolorów ----------

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    public static int gray(float v) {
        int c = Math.round(clamp01(v) * 255f);
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int luminance(int c) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    public static int mix(int c1, int c2, float t) {
        t = clamp01(t);
        int a = Math.round(lerp(c1 >>> 24, c2 >>> 24, t));
        int r = Math.round(lerp(c1 >> 16 & 255, c2 >> 16 & 255, t));
        int g = Math.round(lerp(c1 >> 8 & 255, c2 >> 8 & 255, t));
        int b = Math.round(lerp(c1 & 255, c2 & 255, t));
        return argb(a, r, g, b);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** h w stopniach 0..360, s i v w 0..1. */
    public static int hsv(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        float c = v * s;
        float hh = h / 60f;
        float x = c * (1f - Math.abs(hh % 2f - 1f));
        float r = 0, g = 0, b = 0;
        if (hh < 1) { r = c; g = x; }
        else if (hh < 2) { r = x; g = c; }
        else if (hh < 3) { g = c; b = x; }
        else if (hh < 4) { g = x; b = c; }
        else if (hh < 5) { r = x; b = c; }
        else { r = c; b = x; }
        float m = v - c;
        return argb(255, Math.round((r + m) * 255f), Math.round((g + m) * 255f), Math.round((b + m) * 255f));
    }

    public static int espColor(AutoSellConfig c) {
        return hsv(c.espHue, 0.85f, 1f);
    }
}
