package pl.autosell;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ESP graczy/rud, tracery, radar i HUD - wszystko rysowane 2D przez własną projekcję (bez ryzykownych API renderu). */
public final class Visuals {
    private Visuals() {
    }

    // ---------- projekcja świat -> ekran ----------
    static final class Proj {
        final double cx, cy, cz, fx, fy, fz, rx, rz, ux, uy, uz, focal;
        final int W, H;

        Proj(Camera cam, MinecraftClient mc) {
            Vec3d p = cam.getPos();
            cx = p.x;
            cy = p.y;
            cz = p.z;
            double yaw = Math.toRadians(cam.getYaw());
            double pitch = Math.toRadians(cam.getPitch());
            fx = -Math.sin(yaw) * Math.cos(pitch);
            fy = -Math.sin(pitch);
            fz = Math.cos(yaw) * Math.cos(pitch);
            rx = -Math.cos(yaw);
            rz = -Math.sin(yaw);
            // up = right x forward (right.y = 0)
            ux = -rz * fy;
            uy = rz * fx - rx * fz;
            uz = rx * fy;
            W = mc.getWindow().getScaledWidth();
            H = mc.getWindow().getScaledHeight();
            double fov = mc.options.getFov().getValue();
            focal = (H / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        }

        /** Współrzędne w przestrzeni kamery: out = {right, up, forward}. */
        void cam(double x, double y, double z, double[] out) {
            double dx = x - cx, dy = y - cy, dz = z - cz;
            out[0] = dx * rx + dz * rz;
            out[1] = dx * ux + dy * uy + dz * uz;
            out[2] = dx * fx + dy * fy + dz * fz;
        }

        boolean project(double x, double y, double z, double[] out) {
            cam(x, y, z, out);
            if (out[2] < 0.05) return false;
            double sx = W / 2.0 + out[0] / out[2] * focal;
            double sy = H / 2.0 - out[1] / out[2] * focal;
            out[0] = sx;
            out[1] = sy;
            return true;
        }

        /** Prostokąt ekranowy dla pudełka 3D; null gdy którykolwiek róg jest za kamerą. */
        double[] box(double x1, double y1, double z1, double x2, double y2, double z2) {
            double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
            double[] o = new double[3];
            for (int i = 0; i < 8; i++) {
                double x = (i & 1) == 0 ? x1 : x2;
                double y = (i & 2) == 0 ? y1 : y2;
                double z = (i & 4) == 0 ? z1 : z2;
                if (!project(x, y, z, o)) return null;
                minX = Math.min(minX, o[0]);
                maxX = Math.max(maxX, o[0]);
                minY = Math.min(minY, o[1]);
                maxY = Math.max(maxY, o[1]);
            }
            return new double[]{minX, minY, maxX, maxY};
        }
    }

    // ---------- skan rud (rozłożony na wiele ticków) ----------
    public record OreHit(int x, int y, int z, int color, double dist) {
    }

    private static volatile List<OreHit> oreHits = List.of();
    private static List<OreHit> building = new ArrayList<>();
    private static Map<Block, Integer> oreColors = new HashMap<>();
    private static int scanX = Integer.MIN_VALUE;
    private static int scanRange;
    private static int scanCx, scanCy, scanCz;
    private static boolean fbApplied;
    private static int fbTimer;

    private static void rebuildOreMap(AutoSellConfig cfg) {
        Map<Block, Integer> m = new HashMap<>();
        if (cfg.espOresAll) {
            for (Ore o : Ore.values()) {
                for (Block b : o.blocks()) m.put(b, o.color);
            }
        } else {
            Ore o = cfg.ore();
            for (Block b : o.blocks()) m.put(b, o.color);
        }
        oreColors = m;
    }

    public static void tick(MinecraftClient mc, AutoSellConfig cfg) {
        if (mc.player == null || mc.world == null) {
            oreHits = List.of();
            fbApplied = false;
            return;
        }
        // fullbright przez efekt widzenia w ciemności (tylko po stronie klienta)
        if (cfg.fullbright) {
            if (fbTimer-- <= 0) {
                fbTimer = 150;
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false, false));
            }
            fbApplied = true;
        } else if (fbApplied) {
            fbApplied = false;
            fbTimer = 0;
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }

        if (!cfg.espOres) {
            oreHits = List.of();
            scanX = Integer.MIN_VALUE;
            return;
        }
        int range = cfg.oreRange;
        if (scanX == Integer.MIN_VALUE) {
            rebuildOreMap(cfg);
            scanRange = range;
            scanCx = (int) Math.floor(mc.player.getX());
            scanCy = (int) Math.floor(mc.player.getY());
            scanCz = (int) Math.floor(mc.player.getZ());
            scanX = -scanRange;
            building = new ArrayList<>();
        }
        int slices = Math.max(1, (2 * scanRange + 1) / 12);
        int bottom = mc.world.getBottomY();
        int top = bottom + mc.world.getHeight() - 1;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int s = 0; s < slices && scanX <= scanRange; s++, scanX++) {
            int x = scanCx + scanX;
            for (int dy = -scanRange; dy <= scanRange; dy++) {
                int y = scanCy + dy;
                if (y < bottom || y > top) continue;
                for (int dz = -scanRange; dz <= scanRange; dz++) {
                    pos.set(x, y, scanCz + dz);
                    BlockState st = mc.world.getBlockState(pos);
                    if (st.isAir()) continue;
                    Integer col = oreColors.get(st.getBlock());
                    if (col != null) {
                        double ddx = x + 0.5 - mc.player.getX(), ddy = y + 0.5 - mc.player.getY(),
                                ddz = scanCz + dz + 0.5 - mc.player.getZ();
                        building.add(new OreHit(x, y, scanCz + dz, col, Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz)));
                    }
                }
            }
        }
        if (scanX > scanRange) {
            building.sort(Comparator.comparingDouble(OreHit::dist));
            oreHits = building.size() > 250 ? new ArrayList<>(building.subList(0, 250)) : building;
            scanX = Integer.MIN_VALUE; // następny cykl od nowa
        }
    }

    // ---------- HUD ----------
    public static void render(DrawContext g, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        AutoSellConfig cfg = AutoSellMod.CFG;
        if (cfg == null || mc.player == null || mc.world == null || mc.options.hudHidden) return;
        Theme.refresh(cfg);
        Camera cam = mc.gameRenderer.getCamera();
        Proj pr = new Proj(cam, mc);

        boolean needPlayers = cfg.espPlayers || cfg.radar || !PlayerScan.PINNED.isEmpty() || cfg.hudInfo;
        List<PlayerScan.Entry> players = needPlayers ? PlayerScan.scan(mc, cfg.playerRadius) : List.of();

        if (cfg.espOres) drawOres(g, mc, cfg, pr);
        if (cfg.espPlayers || !PlayerScan.PINNED.isEmpty()) drawPlayers(g, mc, cfg, pr, players, td);
        if (cfg.radar) drawRadar(g, mc, cfg, players);
        if (cfg.hudInfo) drawInfo(g, mc, cfg, players.size());
    }

    private static void drawPlayers(DrawContext g, MinecraftClient mc, AutoSellConfig cfg, Proj pr,
                                    List<PlayerScan.Entry> players, float td) {
        int base = Theme.espColor(cfg);
        double[] o = new double[3];
        for (int i = players.size() - 1; i >= 0; i--) { // dalekie najpierw, bliskie na wierzchu
            PlayerScan.Entry e = players.get(i);
            boolean pinned = e.pinned();
            if (!cfg.espPlayers && !pinned) continue;
            Vec3d p = e.player().getLerpedPos(td);
            double w = e.player().getWidth() / 2.0 + 0.05;
            double h = e.player().getHeight() + 0.1;
            int col = pinned ? 0xFFFFD84A : base;

            double[] b = pr.box(p.x - w, p.y, p.z - w, p.x + w, p.y + h, p.z + w);
            boolean tracer = cfg.espTracers || pinned;
            if (b == null) {
                if (tracer) drawBehindArrow(g, pr, p.x, p.y + h / 2, p.z, col);
                continue;
            }
            int x1 = (int) b[0], y1 = (int) b[1], x2 = (int) b[2], y2 = (int) b[3];
            if (x2 < -50 || x1 > pr.W + 50 || y2 < -50 || y1 > pr.H + 50) {
                if (tracer) tracerTo(g, pr, (x1 + x2) / 2.0, y2, col, cfg);
                continue;
            }
            if (cfg.espFill) g.fill(x1, y1, x2, y2, Theme.withAlpha(col, 45));
            if (cfg.espBoxes) Gfx.boxOutline(g, x1, y1, x2, y2, col);
            if (cfg.espHealth) {
                float frac = Math.max(0f, Math.min(1f, e.hp() / Math.max(1f, e.maxHp())));
                int bx = x1 - 5;
                g.fill(bx - 1, y1 - 1, bx + 3, y2 + 1, 0xAA000000);
                int hh = (int) ((y2 - y1) * frac);
                int hc = Theme.mix(Theme.bad, Theme.good, frac);
                g.fill(bx, y2 - hh, bx + 2, y2, hc);
            }
            if (cfg.espNames || pinned) {
                String s = e.name() + "  " + (int) e.dist() + " m";
                Gfx.pill(g, mc.textRenderer, s, (x1 + x2) / 2, y1 - 13, 0xFFFFFFFF, 0xB0000000);
            }
            if (tracer) tracerTo(g, pr, (x1 + x2) / 2.0, y2, col, cfg);
        }
    }

    private static void tracerTo(DrawContext g, Proj pr, double ex, double ey, int col, AutoSellConfig cfg) {
        double ox = pr.W / 2.0, oy = cfg.tracerFromCenter ? pr.H / 2.0 : pr.H;
        double dx = ex - ox, dy = ey - oy;
        // przytnij koniec do ekranu
        double t = 1.0;
        if (ex < 0) t = Math.min(t, (0 - ox) / dx);
        if (ex > pr.W) t = Math.min(t, (pr.W - ox) / dx);
        if (ey < 0) t = Math.min(t, (0 - oy) / dy);
        if (ey > pr.H) t = Math.min(t, (pr.H - oy) / dy);
        if (t < 0) t = 0;
        Gfx.line(g, ox, oy, ox + dx * t, oy + dy * t, Theme.withAlpha(col, 200), 1);
    }

    /** Cel za plecami: krótka kreska od środka ekranu w jego stronę. */
    private static void drawBehindArrow(DrawContext g, Proj pr, double x, double y, double z, int col) {
        double[] c = new double[3];
        pr.cam(x, y, z, c);
        double len = Math.hypot(c[0], c[1]);
        if (len < 1e-6) return;
        double nx = c[0] / len, ny = -c[1] / len;
        double r = Math.min(pr.W, pr.H) * 0.32;
        double ox = pr.W / 2.0, oy = pr.H / 2.0;
        Gfx.line(g, ox + nx * r * 0.7, oy + ny * r * 0.7, ox + nx * r, oy + ny * r, Theme.withAlpha(col, 220), 2);
    }

    private static void drawOres(DrawContext g, MinecraftClient mc, AutoSellConfig cfg, Proj pr) {
        List<OreHit> hits = oreHits;
        int shown = 0;
        for (OreHit h : hits) {
            if (shown >= 200) break;
            if (h.dist() > cfg.oreRange + 2) continue;
            double[] b = pr.box(h.x(), h.y(), h.z(), h.x() + 1, h.y() + 1, h.z() + 1);
            if (b == null) continue;
            int x1 = (int) b[0], y1 = (int) b[1], x2 = (int) b[2], y2 = (int) b[3];
            if (x2 < 0 || x1 > pr.W || y2 < 0 || y1 > pr.H) continue;
            if (x2 - x1 < 3) x2 = x1 + 3;
            if (y2 - y1 < 3) y2 = y1 + 3;
            g.fill(x1, y1, x2, y2, Theme.withAlpha(h.color(), 55));
            Gfx.boxOutline(g, x1, y1, x2, y2, h.color());
            shown++;
        }
    }

    private static void drawInfo(DrawContext g, MinecraftClient mc, AutoSellConfig cfg, int nearby) {
        List<String> lines = new ArrayList<>();
        lines.add(mc.getCurrentFps() + " FPS");
        lines.add((int) mc.player.getX() + "  " + (int) mc.player.getY() + "  " + (int) mc.player.getZ());
        SellTask task = AutoSellMod.TASK;
        if (cfg.enabled && task != null) {
            long s = task.secondsToNext();
            lines.add(task.busy() ? "Sprzedaję..." : "Sprzedaż za " + (s / 60) + ":" + String.format("%02d", s % 60));
        }
        lines.add("Gracze: " + nearby);
        int tw = mc.textRenderer.getWidth("AutoSell");
        for (String l : lines) tw = Math.max(tw, mc.textRenderer.getWidth(l));
        int w = tw + 20, h = 18 + lines.size() * 11 + 4;
        int x = 6, y = 6;
        Gfx.rrect(g, x, y, w, h, Theme.radius, Theme.withAlpha(Theme.bg0, 215));
        Gfx.rrect(g, x, y + 2, 3, 12, 1, Theme.accent);
        Gfx.textShadow(g, mc.textRenderer, "AutoSell", x + 9, y + 5, Theme.accent);
        int ty = y + 19;
        for (String l : lines) {
            Gfx.text(g, mc.textRenderer, l, x + 9, ty, Theme.text);
            ty += 11;
        }
    }

    private static void drawRadar(DrawContext g, MinecraftClient mc, AutoSellConfig cfg, List<PlayerScan.Entry> players) {
        int size = cfg.radarSize;
        int x = mc.getWindow().getScaledWidth() - size - 8;
        int y = 8;
        int half = size / 2;
        int cx = x + half, cy = y + half;
        Gfx.rrectBorder(g, x, y, size, size, Theme.radius, Theme.withAlpha(Theme.bg0, 200), Theme.withAlpha(Theme.border, 220));
        g.fill(x + 3, cy, x + size - 3, cy + 1, Theme.withAlpha(Theme.border, 160));
        g.fill(cx, y + 3, cx + 1, y + size - 3, Theme.withAlpha(Theme.border, 160));
        Gfx.ring(g, cx, cy, half / 2, Theme.withAlpha(Theme.border, 160));
        Gfx.ring(g, cx, cy, half - 4, Theme.withAlpha(Theme.border, 200));
        double yaw = Math.toRadians(mc.player.getYaw());
        double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
        for (PlayerScan.Entry e : players) {
            double dx = e.player().getX() - mc.player.getX();
            double dz = e.player().getZ() - mc.player.getZ();
            double right = dx * -cosY + dz * -sinY;
            double fwd = dx * -sinY + dz * cosY;
            double scale = (half - 6) / (double) cfg.radarRange;
            double px = right * scale, py = -fwd * scale;
            double len = Math.hypot(px, py);
            boolean clamped = len > half - 6;
            if (clamped) {
                px = px / len * (half - 6);
                py = py / len * (half - 6);
            }
            int col = e.pinned() ? 0xFFFFD84A : Theme.espColor(cfg);
            if (clamped) col = Theme.withAlpha(col, 110);
            Gfx.circle(g, cx + (int) Math.round(px), cy + (int) Math.round(py), 2, col);
        }
        Gfx.circle(g, cx, cy, 2, Theme.accent);
        Gfx.textCenter(g, mc.textRenderer, cfg.radarRange + " m", cx, y + size - 11, Theme.textDim);
    }
}
