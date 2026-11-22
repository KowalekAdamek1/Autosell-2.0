package pl.autosell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoSellConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("autosell.json");

    // --- AutoSell (zgodne ze starą wersją 1.0.4) ---
    public boolean enabled = false;
    public String ore = Ore.COPPER.name();
    public int guiKey = 344; // Right Shift
    public int intervalSeconds = 120;
    public boolean onlyOnAnarchia = true;
    public boolean autoMine = true;
    public boolean pauseBaritoneWhileSelling = true;
    public boolean debug = false;

    // --- Motyw ---
    public float themeHue = 265f;
    public float themeSat = 0.65f;
    public float themeBright = 0.07f;
    public float themeAlpha = 0.96f;
    public int themeRadius = 8;
    public boolean animations = true;

    // --- Gracze ---
    public int playerRadius = 500;

    // --- ESP graczy ---
    public boolean espPlayers = true;
    public boolean espBoxes = true;
    public boolean espFill = false;
    public boolean espNames = true;
    public boolean espHealth = true;
    public boolean espTracers = false;
    public boolean tracerFromCenter = false;
    public float espHue = 350f;

    // --- ESP rud ---
    public boolean espOres = false;
    public boolean espOresAll = false;
    public int oreRange = 24;

    // --- Świat / HUD ---
    public boolean fullbright = false;
    public boolean hudInfo = true;
    public boolean radar = false;
    public int radarSize = 110;
    public int radarRange = 100;

    public Ore ore() {
        return Ore.byName(ore);
    }

    public static AutoSellConfig load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    AutoSellConfig c = GSON.fromJson(r, AutoSellConfig.class);
                    if (c != null) {
                        if (c.intervalSeconds < 10) c.intervalSeconds = 120;
                        c.clamp();
                        return c;
                    }
                }
            }
        } catch (Exception e) {
            AutoSellMod.LOGGER.warn("Nie udało się wczytać configu, używam domyślnego", e);
        }
        AutoSellConfig c = new AutoSellConfig();
        c.save();
        return c;
    }

    private void clamp() {
        playerRadius = Math.max(10, Math.min(2000, playerRadius));
        oreRange = Math.max(8, Math.min(48, oreRange));
        radarSize = Math.max(70, Math.min(200, radarSize));
        radarRange = Math.max(20, Math.min(1000, radarRange));
        themeRadius = Math.max(0, Math.min(12, themeRadius));
        themeAlpha = Math.max(0.35f, Math.min(1f, themeAlpha));
        themeBright = Math.max(0.03f, Math.min(0.97f, themeBright));
        themeSat = Math.max(0f, Math.min(1f, themeSat));
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            AutoSellMod.LOGGER.warn("Nie udało się zapisać configu", e);
        }
    }

    /** Przywraca wszystkie ustawienia do domyślnych (zostawia ten sam obiekt). */
    public void resetAll() {
        AutoSellConfig d = new AutoSellConfig();
        for (Field f : AutoSellConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                f.set(this, f.get(d));
            } catch (IllegalAccessException ignored) {
            }
        }
        save();
    }
}
