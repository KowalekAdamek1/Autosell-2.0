package pl.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Główne GUI: zakładki AutoSell / Gracze / Wizualne / Wygląd / Ustawienia (tryb "immediate"). */
public class MainScreen extends Screen {
    private static final String[] TABS = {"AutoSell", "Gracze", "Wizualne", "Wygląd", "Ustawienia"};
    private static final String[] SUBS = {
            "Automatyczna sprzedaż rud i Baritone",
            "Gracze w pobliżu i odległość w metrach",
            "ESP, tracery, radar i HUD",
            "Kolory, jasność i styl interfejsu",
            "Klawisz, reset i informacje"};
    private static final int SIDEBAR = 120;
    private static final int ROW = 30;
    private static final int TITLE = 26;

    private static int selectedTab = 0;

    private final AutoSellConfig cfg;
    private final SellTask task;
    private TextRenderer tr;
    private DrawContext g;

    private final float[] scroll = new float[TABS.length];
    private final float[] scrollTarget = new float[TABS.length];
    private final Map<String, Float> anims = new HashMap<>();
    private long lastNs = System.nanoTime();
    private final long openNs = System.nanoTime();
    private float dt;

    // wejście
    private int mx, my;
    private boolean mouseDown, pendingClick, click;
    private String activeSlider;
    private boolean listeningKey;
    private boolean armed; // klawisz GUI został puszczony od otwarcia
    private boolean dirty;
    private int clipX1, clipY1, clipX2, clipY2;
    private int hoverPlayer = -1;

    private static ItemStack[] tabIcons;

    public MainScreen(AutoSellConfig cfg, SellTask task) {
        super(Text.literal("AutoSell"));
        this.cfg = cfg;
        this.task = task;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ================= zdarzenia =================

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            pendingClick = true;
            mouseDown = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) {
            mouseDown = false;
            activeSlider = null;
            saveIfDirty();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scrollTarget[selectedTab] -= (float) (vertical * 30.0);
        if (scrollTarget[selectedTab] < 0) scrollTarget[selectedTab] = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningKey) {
            listeningKey = false;
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                cfg.guiKey = keyCode;
                dirty = true;
                AutoSellMod.blockOpen = true;
            }
            return true;
        }
        if (keyCode == cfg.guiKey && armed) {
            AutoSellMod.blockOpen = true;
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        saveIfDirty();
        super.removed();
    }

    private void saveIfDirty() {
        if (dirty) {
            cfg.save();
            dirty = false;
        }
    }

    // ================= pomocnicze =================

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private float anim(String id, float target) {
        float cur = anims.getOrDefault(id, target);
        if (!cfg.animations) {
            anims.put(id, target);
            return target;
        }
        cur += (target - cur) * Math.min(1f, dt * 14f);
        anims.put(id, cur);
        return cur;
    }

    private boolean hit(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h
                && mx >= clipX1 && mx < clipX2 && my >= clipY1 && my < clipY2;
    }

    private boolean clicked(int x, int y, int w, int h) {
        return click && hit(x, y, w, h);
    }

    private void card(int x, int y, int w, int h, String title) {
        Gfx.rrectBorder(g, x, y, w, h, Theme.radius, Theme.bg1, Theme.withAlpha(Theme.border, 200));
        if (title != null) {
            Gfx.rrect(g, x + 10, y + 10, 3, 9, 1, Theme.accent);
            Gfx.text(g, tr, title, x + 18, y + 10, Theme.text);
        }
    }

    private boolean button(String id, int x, int y, int w, int h, String label, int style) {
        boolean hov = hit(x, y, w, h);
        float a = anim("b" + id, hov ? 1f : 0f);
        int base, over, fg;
        if (style == 1) {
            base = Theme.accent; over = Theme.accentHover; fg = Theme.onAccent;
        } else if (style == 2) {
            base = Theme.withAlpha(Theme.bad, 70); over = Theme.withAlpha(Theme.bad, 130); fg = Theme.text;
        } else {
            base = Theme.bg2; over = Theme.bg3; fg = Theme.text;
        }
        Gfx.rrect(g, x, y, w, h, Math.max(2, Theme.radius - 2), Theme.mix(base, over, a));
        Gfx.textCenter(g, tr, label, x + w / 2, y + (h - 8) / 2, fg);
        return clicked(x, y, w, h);
    }

    /** Wiersz z przełącznikiem: zwraca nową wartość. */
    private boolean toggle(String id, int x, int y, int w, String label, String desc, boolean v) {
        boolean hov = hit(x, y, w, ROW - 2);
        float h = anim("th" + id, hov ? 1f : 0f);
        if (h > 0.01f) Gfx.rrect(g, x - 4, y, w + 8, ROW - 2, 5, Theme.withAlpha(Theme.bg2, (int) (150 * h)));
        float on = anim("t" + id, v ? 1f : 0f);
        if (desc == null) {
            Gfx.text(g, tr, label, x + 2, y + (ROW - 2 - 8) / 2, Theme.text);
        } else {
            Gfx.text(g, tr, label, x + 2, y + 4, Theme.text);
            Gfx.text(g, tr, desc, x + 2, y + 15, Theme.textDim);
        }
        int sw = 30, sh = 14;
        int sx = x + w - sw - 2, sy = y + (ROW - 2 - sh) / 2;
        Gfx.rrect(g, sx, sy, sw, sh, 7, Theme.mix(Theme.bg3, Theme.accent, on));
        int kx = sx + 2 + Math.round(on * (sw - sh));
        Gfx.circle(g, kx + 5, sy + 7, 5, Theme.mix(Theme.textDim, 0xFFFFFFFF, on));
        if (clicked(x, y, w, ROW - 2)) {
            dirty = true;
            return !v;
        }
        return v;
    }

    private float slider(String id, int x, int y, int w, String label, String valueText,
                         float min, float max, float val, float step, boolean rainbow) {
        Gfx.text(g, tr, label, x + 2, y, Theme.text);
        Gfx.textRight(g, tr, valueText, x + w - 2, y, Theme.textDim);
        int ty = y + 15, th = 6;
        int tx = x + 2, tw = w - 4;
        boolean over = hit(x, y - 2, w, 28);
        if (click && over) activeSlider = id;
        if (mouseDown && id.equals(activeSlider)) {
            float t = Math.max(0f, Math.min(1f, (mx - tx) / (float) tw));
            float nv = min + t * (max - min);
            if (step > 0) nv = Math.round(nv / step) * step;
            nv = Math.max(min, Math.min(max, nv));
            if (nv != val) {
                val = nv;
                dirty = true;
            }
        }
        float frac = (val - min) / (max - min);
        if (rainbow) {
            for (int i = 0; i < tw; i++) {
                g.fill(tx + i, ty, tx + i + 1, ty + th, Theme.hsv(360f * i / tw, 0.85f, 1f));
            }
        } else {
            Gfx.rrect(g, tx, ty, tw, th, 3, Theme.bg3);
            int fw = Math.round(tw * frac);
            Gfx.rrect(g, tx, ty, Math.max(th, fw), th, 3, Theme.accent);
        }
        float k = anim("k" + id, (over || id.equals(activeSlider)) ? 1f : 0f);
        int kx = tx + Math.round(tw * frac);
        Gfx.circle(g, kx, ty + th / 2, 6 + Math.round(k * 1.5f), Theme.withAlpha(0xFF000000, 120));
        Gfx.circle(g, kx, ty + th / 2, 5 + Math.round(k * 1.5f), 0xFFFFFFFF);
        return val;
    }

    private static String fmtTime(long s) {
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static ItemStack[] icons() {
        if (tabIcons == null) {
            tabIcons = new ItemStack[]{
                    new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.PLAYER_HEAD),
                    new ItemStack(Items.ENDER_EYE), new ItemStack(Items.PAINTING), new ItemStack(Items.COMPARATOR)};
        }
        return tabIcons;
    }

    // ================= render =================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        g = ctx;
        tr = this.textRenderer;
        Theme.refresh(cfg);
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        float open = cfg.animations ? ease((now - openNs) / 220_000_000f) : 1f;

        if (!armed && !InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), cfg.guiKey)) {
            armed = true;
        }
        click = pendingClick;
        pendingClick = false;
        mx = mouseX;
        my = mouseY;
        clipX1 = 0;
        clipY1 = 0;
        clipX2 = width;
        clipY2 = height;

        g.fill(0, 0, width, height, Theme.argb((int) (140 * open), 0, 0, 0));

        int ww = Math.min(width - 24, 620);
        int wh = Math.min(height - 24, 370);
        int wx = (width - ww) / 2;
        int wy = (height - wh) / 2 + Math.round((1f - open) * 16f);
        int r = Theme.radius;

        for (int i = 6; i >= 1; i--) {
            Gfx.rrect(g, wx - i, wy - i + 3, ww + 2 * i, wh + 2 * i, r + i, Theme.argb(9, 0, 0, 0));
        }
        Gfx.rrect(g, wx - 1, wy - 1, ww + 2, wh + 2, r + 1, Theme.withAlpha(Theme.border, 255));
        Gfx.rrect(g, wx, wy, ww, wh, r, Theme.withAlpha(Theme.bg0, 255));

        // pasek boczny
        Gfx.rrect(g, wx, wy, SIDEBAR, wh, r, Theme.bg1);
        g.fill(wx + SIDEBAR - r, wy, wx + SIDEBAR, wy + wh, Theme.bg1);
        g.fill(wx + SIDEBAR, wy + 8, wx + SIDEBAR + 1, wy + wh - 8, Theme.withAlpha(Theme.border, 180));
        drawSidebar(wx, wy, wh);

        // nagłówek treści
        int cx = wx + SIDEBAR + 16;
        int cw = ww - SIDEBAR - 32;
        Gfx.textScaled(g, tr, TABS[selectedTab], cx, wy + 12, 1.4f, Theme.text);
        Gfx.text(g, tr, SUBS[selectedTab], cx, wy + 27, Theme.textDim);
        // przycisk zamknięcia
        int bx = wx + ww - 26, by = wy + 10;
        boolean hovClose = hit(bx, by, 16, 16);
        float ca = anim("close", hovClose ? 1f : 0f);
        Gfx.rrect(g, bx, by, 16, 16, 5, Theme.mix(Theme.bg1, Theme.withAlpha(Theme.bad, 255), ca * 0.8f));
        Gfx.line(g, bx + 4, by + 4, bx + 11, by + 11, Theme.text, 1);
        Gfx.line(g, bx + 11, by + 4, bx + 4, by + 11, Theme.text, 1);
        if (click && hovClose) {
            close();
            return;
        }

        // obszar treści (przewijany)
        int areaY = wy + 46;
        int areaH = wh - 46 - 10;
        clipX1 = cx - 6;
        clipX2 = cx + cw + 8;
        clipY1 = areaY;
        clipY2 = areaY + areaH;
        g.enableScissor(clipX1, clipY1, clipX2, clipY2);

        scroll[selectedTab] += (scrollTarget[selectedTab] - scroll[selectedTab]) * Math.min(1f, dt * 16f);
        int top = areaY - Math.round(scroll[selectedTab]);
        int end;
        hoverPlayer = -1;
        switch (selectedTab) {
            case 0 -> end = tabAutoSell(cx, top, cw);
            case 1 -> end = tabPlayers(cx, top, cw);
            case 2 -> end = tabVisuals(cx, top, cw);
            case 3 -> end = tabTheme(cx, top, cw);
            default -> end = tabSettings(cx, top, cw);
        }
        g.disableScissor();

        int contentH = end - top + 8;
        float maxScroll = Math.max(0, contentH - areaH);
        if (scrollTarget[selectedTab] > maxScroll) scrollTarget[selectedTab] = maxScroll;
        if (maxScroll > 0) {
            int trackH = areaH;
            int barH = Math.max(24, (int) (trackH * (areaH / (float) contentH)));
            int barY = areaY + (int) ((trackH - barH) * (scroll[selectedTab] / maxScroll));
            Gfx.rrect(g, wx + ww - 8, barY, 3, barH, 1, Theme.withAlpha(Theme.accent, 170));
        }
        clipX1 = 0;
        clipY1 = 0;
        clipX2 = width;
        clipY2 = height;
    }

    private void drawSidebar(int wx, int wy, int wh) {
        // logo
        Gfx.circle(g, wx + 20, wy + 24, 8, Theme.accent);
        Gfx.circle(g, wx + 20, wy + 24, 3, Theme.bg1);
        Gfx.textScaled(g, tr, "AutoSell", wx + 34, wy + 14, 1.25f, Theme.text);
        Gfx.text(g, tr, "v" + "2.0", wx + 35, wy + 27, Theme.textDim);

        int y = wy + 50;
        ItemStack[] ic = icons();
        for (int i = 0; i < TABS.length; i++) {
            int x = wx + 8, w = SIDEBAR - 16, h = 28;
            boolean sel = i == selectedTab;
            boolean hov = hit(x, y, w, h);
            float s = anim("tab" + i, sel ? 1f : (hov ? 0.5f : 0f));
            if (s > 0.01f) {
                Gfx.rrect(g, x, y, w, h, Math.max(3, Theme.radius - 2),
                        Theme.mix(Theme.withAlpha(Theme.bg1, 0), Theme.withAlpha(Theme.accent, 70), s));
            }
            if (sel) Gfx.rrect(g, x, y + 6, 3, h - 12, 1, Theme.accent);
            g.drawItem(ic[i], x + 8, y + 6);
            Gfx.text(g, tr, TABS[i], x + 30, y + 10, sel ? Theme.text : Theme.mix(Theme.textDim, Theme.text, s));
            if (click && hov) {
                selectedTab = i;
                scrollTarget[i] = 0;
                scroll[i] = 0;
                listeningKey = false;
            }
            y += h + 3;
        }

        // status na dole
        int sy = wy + wh - 54;
        Gfx.rrect(g, wx + 8, sy, SIDEBAR - 16, 44, Theme.radius, Theme.bg2);
        int dot = cfg.enabled ? Theme.good : Theme.textFaint;
        Gfx.circle(g, wx + 20, sy + 12, 3, dot);
        Gfx.text(g, tr, cfg.enabled ? "Aktywny" : "Wyłączony", wx + 28, sy + 8, Theme.text);
        String line2 = !cfg.enabled ? "AutoSell off"
                : (task.busy() ? task.stateLabel() : "Za " + fmtTime(task.secondsToNext()));
        Gfx.text(g, tr, line2, wx + 16, sy + 24, Theme.textDim);
    }

    // ================= zakładka: AutoSell =================

    private int tabAutoSell(int x, int y, int w) {
        // --- status ---
        card(x, y, w, 104, null);
        boolean en = toggle("en", x + 12, y + 8, w - 24, "AutoSell",
                "Co " + cfg.intervalSeconds + " s sprzedaje: " + cfg.ore().label + " (/sklep)", cfg.enabled);
        if (en != cfg.enabled) {
            cfg.enabled = en;
            if (en) task.resetTimer();
        }
        int py = y + 44;
        String big = task.busy() ? task.stateLabel() : (cfg.enabled ? fmtTime(task.secondsToNext()) : "--:--");
        Gfx.textScaled(g, tr, big, x + 14, py, 1.8f, cfg.enabled ? Theme.text : Theme.textFaint);
        int barX = x + 14, barW = w - 28, barY = y + 68;
        Gfx.rrect(g, barX, barY, barW, 5, 2, Theme.bg3);
        float prog = cfg.enabled ? (task.busy() ? 1f : task.progress()) : 0f;
        float pa = anim("prog", prog);
        Gfx.rrect(g, barX, barY, Math.max(5, Math.round(barW * pa)), 5, 2, Theme.accent);
        int bw = (w - 28 - 8) / 2;
        if (button("sellnow", x + 14, y + 80, bw, 18, "Sprzedaj teraz", 1)) {
            task.sellNow();
        }
        if (button("reset", x + 14 + bw + 8, y + 80, bw, 18, "Reset timera", 0)) {
            task.resetTimer();
        }
        y += 104 + 10;

        // --- wybór rudy ---
        int oreH = 140;
        card(x, y, w, oreH, "Wybór rudy");
        Ore[] ores = Ore.values();
        int cols = 5, gap = 8;
        int cellW = (w - 24 - gap * (cols - 1)) / cols, cellH = 46;
        for (int i = 0; i < ores.length; i++) {
            int cxx = x + 12 + (i % cols) * (cellW + gap);
            int cyy = y + 30 + (i / cols) * (cellH + 8);
            Ore o = ores[i];
            boolean sel = o == cfg.ore();
            boolean hov = hit(cxx, cyy, cellW, cellH);
            float a = anim("ore" + i, sel ? 1f : (hov ? 0.4f : 0f));
            Gfx.rrect(g, cxx, cyy, cellW, cellH, Math.max(3, Theme.radius - 2),
                    Theme.mix(Theme.bg2, Theme.withAlpha(Theme.accent, 255), a * 0.35f));
            if (sel) {
                Gfx.rrect(g, cxx, cyy + cellH - 3, cellW, 3, 1, o.color);
            }
            Item icon = o.icon();
            g.drawItem(new ItemStack(icon), cxx + cellW / 2 - 8, cyy + 6);
            Gfx.textCenter(g, tr, o.label, cxx + cellW / 2, cyy + 28, sel ? Theme.text : Theme.textDim);
            if (clicked(cxx, cyy, cellW, cellH)) {
                cfg.ore = o.name();
                dirty = true;
            }
        }
        y += oreH + 10;

        // --- interwał ---
        card(x, y, w, 58, null);
        float iv = slider("interval", x + 12, y + 10, w - 24, "Odstęp między sprzedażami",
                cfg.intervalSeconds + " s (" + fmtTime(cfg.intervalSeconds) + ")",
                10, 600, cfg.intervalSeconds, 5, false);
        if ((int) iv != cfg.intervalSeconds) {
            cfg.intervalSeconds = (int) iv;
            task.resetTimer();
        }
        y += 58 + 10;

        // --- Baritone ---
        int barH = 168;
        card(x, y, w, barH, "Baritone");
        boolean avail = BaritoneBridge.isAvailable();
        boolean mining = avail && BaritoneBridge.isMining();
        int sx = x + w - 12;
        Gfx.circle(g, sx - tr.getWidth(avail ? "wykryty" : "nie wykryty") - 8, y + 14, 3, avail ? Theme.good : Theme.bad);
        Gfx.textRight(g, tr, avail ? (mining ? "kopie" : "wykryty") : "nie wykryty", sx, y + 10, Theme.textDim);
        cfg.autoMine = toggle("am", x + 12, y + 30, w - 24, "Automatyczne kopanie",
                "Baritone kopie wybraną rudę, gdy AutoSell jest włączony", cfg.autoMine);
        cfg.pauseBaritoneWhileSelling = toggle("pb", x + 12, y + 60, w - 24, "Pauza podczas sprzedaży",
                "Wstrzymuje Baritone na czas /sklep i wznawia po", cfg.pauseBaritoneWhileSelling);
        int bw4 = (w - 24 - 3 * 6) / 4;
        int by = y + 96;
        if (button("mine", x + 12, by, bw4, 22, "Kop rudę", 1)) BaritoneBridge.run("mine " + cfg.ore().mineBlocks());
        if (button("stop", x + 12 + (bw4 + 6), by, bw4, 22, "Stop", 2)) BaritoneBridge.run("stop");
        if (button("pause", x + 12 + 2 * (bw4 + 6), by, bw4, 22, "Pauza", 0)) BaritoneBridge.run("pause");
        if (button("resume", x + 12 + 3 * (bw4 + 6), by, bw4, 22, "Wznów", 0)) BaritoneBridge.run("resume");
        Gfx.text(g, tr, avail ? "Komendy trafiają bezpośrednio do Baritone."
                : "Zainstaluj mod Baritone, aby użyć tych przycisków.", x + 14, y + 130, Theme.textFaint);
        Gfx.text(g, tr, "Kopie: " + cfg.ore().mineBlocks().replace(" ", ", "), x + 14, y + 145, Theme.textFaint);
        y += barH + 10;

        // --- zaawansowane ---
        card(x, y, w, 96, "Zaawansowane");
        cfg.onlyOnAnarchia = toggle("oa", x + 12, y + 28, w - 24, "Tylko anarchia.gg",
                "Nie sprzedawaj na innych serwerach", cfg.onlyOnAnarchia);
        cfg.debug = toggle("dbg", x + 12, y + 58, w - 24, "Debug", "Wypisuje kroki i listę slotów menu na ekranie", cfg.debug);
        y += 96;
        return y;
    }

    // ================= zakładka: Gracze =================

    private int tabPlayers(int x, int y, int w) {
        MinecraftClient mc = MinecraftClient.getInstance();
        card(x, y, w, 58, null);
        int rad = Math.round(slider("radius", x + 12, y + 10, w - 24, "Promień wyszukiwania",
                cfg.playerRadius + " m", 50, 1000, cfg.playerRadius, 10, false));
        cfg.playerRadius = rad;
        y += 58 + 10;

        List<PlayerScan.Entry> list = PlayerScan.scan(mc, cfg.playerRadius);
        int radarW = Math.min(180, w * 38 / 100);
        int listW = w - radarW - 10;

        // lista
        Gfx.text(g, tr, "W zasięgu: " + list.size(), x + 2, y + 2, Theme.textDim);
        int ly = y + 16;
        if (list.isEmpty()) {
            card(x, ly, listW, 70, null);
            Gfx.textCenter(g, tr, "Brak graczy w promieniu " + cfg.playerRadius + " m", x + listW / 2, ly + 22, Theme.text);
            Gfx.textCenter(g, tr, "Lista odświeża się na żywo", x + listW / 2, ly + 38, Theme.textDim);
            ly += 70;
        }
        for (int i = 0; i < list.size(); i++) {
            PlayerScan.Entry e = list.get(i);
            int rh = 36;
            boolean hov = hit(x, ly, listW, rh);
            if (hov) hoverPlayer = i;
            float a = anim("pr" + i, hov ? 1f : 0f);
            Gfx.rrectBorder(g, x, ly, listW, rh, Theme.radius, Theme.mix(Theme.bg1, Theme.bg2, a),
                    e.pinned() ? Theme.accent : Theme.withAlpha(Theme.border, 200));
            int hue = Math.abs(e.name().hashCode()) % 360;
            Gfx.rrect(g, x + 7, ly + 7, 22, 22, 5, Theme.hsv(hue, 0.55f, 0.85f));
            String ini = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase();
            Gfx.textCenter(g, tr, ini, x + 18, ly + 14, 0xFF101018);
            Gfx.text(g, tr, e.name(), x + 36, ly + 7, Theme.text);
            Gfx.text(g, tr, "X " + e.x() + "  Y " + e.y() + "  Z " + e.z() + "   HP " + Math.round(e.hp()),
                    x + 36, ly + 20, Theme.textDim);
            String d = (int) e.dist() + " m";
            int chipW = 44;
            int chipX = x + listW - chipW - 8;
            Gfx.textRight(g, tr, d, chipX - 8, ly + 14, PlayerScan.distColor(e.dist()));
            if (button("pin" + i, chipX, ly + 8, chipW, 20, e.pinned() ? "Śledzony" : "Śledź", e.pinned() ? 1 : 0)) {
                PlayerScan.togglePin(e.uuid());
            }
            ly += rh + 5;
        }
        int listEnd = ly;

        // radar
        int rx = x + listW + 10;
        int ry = y;
        int rsz = radarW;
        card(rx, ry, rsz, rsz + 26, null);
        Gfx.text(g, tr, "Radar", rx + 10, ry + 8, Theme.text);
        int rcx = rx + rsz / 2, rcy = ry + 26 + (rsz - 10) / 2;
        int rr = (rsz - 24) / 2;
        Gfx.circle(g, rcx, rcy, rr, Theme.bg2);
        Gfx.ring(g, rcx, rcy, rr, Theme.withAlpha(Theme.border, 255));
        Gfx.ring(g, rcx, rcy, rr * 2 / 3, Theme.withAlpha(Theme.border, 150));
        Gfx.ring(g, rcx, rcy, rr / 3, Theme.withAlpha(Theme.border, 150));
        g.fill(rcx - rr, rcy, rcx + rr + 1, rcy + 1, Theme.withAlpha(Theme.border, 120));
        g.fill(rcx, rcy - rr, rcx + 1, rcy + rr + 1, Theme.withAlpha(Theme.border, 120));
        // linia skanująca
        double ang = (System.nanoTime() / 1_000_000_000.0) * 1.6;
        Gfx.line(g, rcx, rcy, rcx + Math.cos(ang) * rr, rcy + Math.sin(ang) * rr, Theme.withAlpha(Theme.accent, 120), 1);
        Gfx.textCenter(g, tr, "N", rcx, rcy - rr - 10, Theme.textDim);
        double scale = rr / (double) cfg.playerRadius;
        for (int i = 0; i < list.size(); i++) {
            PlayerScan.Entry e = list.get(i);
            double dx = e.player().getX() - mc.player.getX();
            double dz = e.player().getZ() - mc.player.getZ();
            int px = rcx + (int) Math.round(dx * scale);
            int py = rcy + (int) Math.round(dz * scale);
            boolean hl = i == hoverPlayer;
            int col = e.pinned() ? 0xFFFFD84A : PlayerScan.distColor(e.dist());
            Gfx.circle(g, px, py, hl ? 4 : 2, col);
            if (hl) Gfx.ring(g, px, py, 6, 0xFFFFFFFF);
        }
        Gfx.circle(g, rcx, rcy, 2, Theme.accent);
        Gfx.textCenter(g, tr, cfg.playerRadius + " m", rcx, ry + rsz + 10, Theme.textDim);
        int radarEnd = ry + rsz + 26;

        int end = Math.max(listEnd, radarEnd) + 8;
        // podpowiedź
        Gfx.text(g, tr, "Serwer wysyła graczy tylko w swoim zasięgu śledzenia,", x + 2, end, Theme.textFaint);
        Gfx.text(g, tr, "więc realny zasięg zależy od serwera.", x + 2, end + 11, Theme.textFaint);
        return end + 24;
    }

    // ================= zakładka: Wizualne =================

    private int tabVisuals(int x, int y, int w) {
        int ix = x + 12, iw = w - 24;

        int h1 = TITLE + 6 * ROW + 40 + 8;
        card(x, y, w, h1, "ESP graczy");
        int yy = y + TITLE + 4;
        cfg.espPlayers = toggle("ep", ix, yy, iw, "ESP graczy", "Pokazuje graczy przez ściany w promieniu z zakładki Gracze", cfg.espPlayers); yy += ROW;
        cfg.espBoxes = toggle("eb", ix, yy, iw, "Ramki", null, cfg.espBoxes); yy += ROW;
        cfg.espFill = toggle("ef", ix, yy, iw, "Wypełnienie ramek", null, cfg.espFill); yy += ROW;
        cfg.espNames = toggle("enm", ix, yy, iw, "Nick i odległość", "Nad graczem: nick + metry", cfg.espNames); yy += ROW;
        cfg.espHealth = toggle("eh", ix, yy, iw, "Pasek zdrowia", null, cfg.espHealth); yy += ROW;
        cfg.espTracers = toggle("et", ix, yy, iw, "Tracery (linie do graczy)", null, cfg.espTracers); yy += ROW;
        cfg.espHue = slider("ehue", ix, yy + 4, iw, "Kolor ESP", (int) cfg.espHue + "°", 0, 360, cfg.espHue, 1, true);
        y += h1 + 10;

        int h2 = TITLE + 2 * ROW + 44;
        card(x, y, w, h2, "ESP rud");
        yy = y + TITLE + 4;
        cfg.espOres = toggle("eo", ix, yy, iw, "ESP rud", "Skanuje okolicę i podświetla rudy przez ściany", cfg.espOres); yy += ROW;
        cfg.espOresAll = toggle("eoa", ix, yy, iw, "Wszystkie rudy", "Wyłączone: tylko ruda wybrana w AutoSell", cfg.espOresAll); yy += ROW;
        cfg.oreRange = Math.round(slider("orange", ix, yy + 4, iw, "Zasięg skanowania", cfg.oreRange + " bloków", 8, 48, cfg.oreRange, 1, false));
        y += h2 + 10;

        int h3 = TITLE + ROW + 4;
        card(x, y, w, h3, "Świat");
        yy = y + TITLE + 4;
        cfg.fullbright = toggle("fb", ix, yy, iw, "Fullbright", "Pełna jasność (efekt widzenia w ciemności, tylko po stronie klienta)", cfg.fullbright);
        y += h3 + 10;

        int h4 = TITLE + 2 * ROW + 2 * 40 + 8;
        card(x, y, w, h4, "HUD");
        yy = y + TITLE + 4;
        cfg.hudInfo = toggle("hi", ix, yy, iw, "Panel informacyjny", "FPS, koordynaty, timer sprzedaży, liczba graczy", cfg.hudInfo); yy += ROW;
        cfg.radar = toggle("rd", ix, yy, iw, "Radar na ekranie", "Mini-radar graczy w prawym górnym rogu", cfg.radar); yy += ROW;
        cfg.radarSize = Math.round(slider("rsize", ix, yy + 4, iw, "Rozmiar radaru", cfg.radarSize + " px", 70, 200, cfg.radarSize, 5, false)); yy += 40;
        cfg.radarRange = Math.round(slider("rrange", ix, yy + 4, iw, "Zasięg radaru", cfg.radarRange + " m", 20, 1000, cfg.radarRange, 10, false));
        y += h4;
        return y;
    }

    // ================= zakładka: Wygląd =================

    private int tabTheme(int x, int y, int w) {
        int ix = x + 12, iw = w - 24;
        // presety
        int h1 = 82;
        card(x, y, w, h1, "Gotowe motywy");
        Theme.Preset[] ps = Theme.PRESETS;
        int gap = 6;
        int cw = (iw - gap * (ps.length - 1)) / ps.length;
        for (int i = 0; i < ps.length; i++) {
            Theme.Preset p = ps[i];
            int px = ix + i * (cw + gap), py = y + 30;
            boolean hov = hit(px, py, cw, 44);
            float a = anim("pre" + i, hov ? 1f : 0f);
            Gfx.rrect(g, px, py, cw, 44, Math.max(3, Theme.radius - 2), Theme.mix(Theme.bg2, Theme.bg3, a));
            int sw = Theme.hsv(p.hue(), p.sat(), p.bright() > 0.5f ? 0.9f : 1f);
            Gfx.circle(g, px + cw / 2, py + 14, 8, p.bright() > 0.5f ? 0xFFE8E8EE : Theme.gray(p.bright() + 0.1f));
            Gfx.circle(g, px + cw / 2, py + 14, 5, sw);
            Gfx.textCenter(g, tr, p.name(), px + cw / 2, py + 30, Theme.textDim);
            if (clicked(px, py, cw, 44)) {
                cfg.themeHue = p.hue();
                cfg.themeSat = p.sat();
                cfg.themeBright = p.bright();
                cfg.themeAlpha = p.alpha();
                dirty = true;
            }
        }
        y += h1 + 10;

        int h2 = TITLE + 5 * 40 + ROW + 14;
        card(x, y, w, h2, "Własne kolory");
        int yy = y + TITLE + 6;
        cfg.themeHue = slider("th", ix, yy, iw, "Kolor akcentu", (int) cfg.themeHue + "°", 0, 360, cfg.themeHue, 1, true); yy += 40;
        cfg.themeSat = slider("ts", ix, yy, iw, "Nasycenie", Math.round(cfg.themeSat * 100) + "%", 0f, 1f, cfg.themeSat, 0.01f, false); yy += 40;
        cfg.themeBright = slider("tb", ix, yy, iw, "Jasność tła", Math.round(cfg.themeBright * 100) + "%", 0.03f, 0.97f, cfg.themeBright, 0.01f, false); yy += 40;
        cfg.themeAlpha = slider("ta", ix, yy, iw, "Przezroczystość okna", Math.round(cfg.themeAlpha * 100) + "%", 0.35f, 1f, cfg.themeAlpha, 0.01f, false); yy += 40;
        cfg.themeRadius = Math.round(slider("tr", ix, yy, iw, "Zaokrąglenie rogów", cfg.themeRadius + " px", 0, 12, cfg.themeRadius, 1, false)); yy += 40;
        cfg.animations = toggle("anim", ix, yy, iw, "Animacje", "Płynne przejścia i otwieranie okna", cfg.animations);
        y += h2 + 10;

        // podgląd
        int h3 = 74;
        card(x, y, w, h3, "Podgląd");
        int bw = (iw - 12) / 3;
        button("pv1", ix, y + 32, bw, 24, "Przycisk", 1);
        button("pv2", ix + bw + 6, y + 32, bw, 24, "Przycisk", 0);
        Gfx.rrect(g, ix + 2 * (bw + 6), y + 38, 30, 14, 7, Theme.accent);
        Gfx.circle(g, ix + 2 * (bw + 6) + 23, y + 45, 5, 0xFFFFFFFF);
        Gfx.text(g, tr, "Włączone", ix + 2 * (bw + 6) + 38, y + 41, Theme.text);
        y += h3;
        return y;
    }

    // ================= zakładka: Ustawienia =================

    private int tabSettings(int x, int y, int w) {
        int ix = x + 12, iw = w - 24;
        card(x, y, w, 84, "Klawisz GUI");
        String keyName;
        try {
            keyName = InputUtil.fromKeyCode(cfg.guiKey, 0).getLocalizedText().getString();
        } catch (Exception e) {
            keyName = "klawisz " + cfg.guiKey;
        }
        Gfx.text(g, tr, "Otwiera i zamyka to okno", ix + 2, y + 36, Theme.textDim);
        String label = listeningKey ? "Naciśnij klawisz..." : keyName;
        if (button("bind", ix + iw - 140, y + 28, 140, 22, label, listeningKey ? 1 : 0)) {
            listeningKey = true;
        }
        Gfx.text(g, tr, "Kliknij przycisk i wciśnij nowy klawisz (Esc anuluje).", ix + 2, y + 62, Theme.textFaint);
        y += 84 + 10;

        card(x, y, w, 70, "Config");
        Gfx.text(g, tr, "Plik: config/autosell.json", ix + 2, y + 32, Theme.textDim);
        if (button("reset", ix, y + 44, 170, 18, "Przywróć domyślne wszystko", 2)) {
            cfg.resetAll();
            task.resetTimer();
        }
        y += 70 + 10;

        card(x, y, w, 96, "O modzie");
        Gfx.text(g, tr, "AutoSell 2.0  -  Fabric 1.21.4", ix + 2, y + 32, Theme.text);
        Gfx.text(g, tr, "Sprzedaż rud w /sklep, Baritone, gracze w promieniu, ESP.", ix + 2, y + 46, Theme.textDim);
        Gfx.text(g, tr, "ESP i radar są rysowane na ekranie (nie zmieniają świata).", ix + 2, y + 58, Theme.textDim);
        Gfx.text(g, tr, "Fullbright działa tylko po stronie klienta.", ix + 2, y + 70, Theme.textDim);
        y += 96;
        return y;
    }
}
