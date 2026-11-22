package pl.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Logika automatycznej sprzedaży: /sklep -> kompas -> czerwona wełna -> shift+PPM na rudzie.
 * Odtworzona 1:1 z wersji 1.0.4 (która działała).
 */
public class SellTask {
    enum State { IDLE, WAIT_OPEN, SETTLE, ACT, WAIT_RESULT, SELLING, VERIFY }

    private static final int MAX_STEPS = 4;
    private static final int OPEN_TIMEOUT_TICKS = 100;
    private static final int SETTLE_TICKS = 6;
    private static final int RESULT_TIMEOUT_TICKS = 40;
    private static final int CLICK_DELAY_TICKS = 2;
    private static final int VERIFY_TICKS = 10;
    private static final int MAX_PASSES = 3;
    private static final int STAGE_COMPASS = 0;
    private static final int STAGE_CONFIRM = 1;
    private static final int STAGE_SELL = 2;

    private final AutoSellConfig cfg;
    private State state = State.IDLE;
    private int ticks;
    private int steps;
    private int stage;
    private int passes;
    private int sellClicks;
    private int cooldown;
    private int lastContainerId = -1;
    private long nextSellAt;
    private boolean pausedBaritone;
    private long nextMineCheck;
    private boolean manual;
    private boolean verbose;
    private final ArrayDeque<Integer> sellQueue = new ArrayDeque<>();

    public SellTask(AutoSellConfig cfg) {
        this.cfg = cfg;
        resetTimer();
    }

    public void resetTimer() {
        nextSellAt = System.currentTimeMillis() + cfg.intervalSeconds * 1000L;
    }

    public long secondsToNext() {
        return Math.max(0L, (nextSellAt - System.currentTimeMillis()) / 1000L);
    }

    /** 0..1 - ile czasu minęło od ostatniej sprzedaży. */
    public float progress() {
        float total = cfg.intervalSeconds * 1000f;
        float left = Math.max(0L, nextSellAt - System.currentTimeMillis());
        return Math.max(0f, Math.min(1f, 1f - left / total));
    }

    public boolean busy() {
        return state != State.IDLE;
    }

    public String stateLabel() {
        return switch (state) {
            case IDLE -> "Oczekiwanie";
            case WAIT_OPEN -> "Otwieram /sklep";
            case SETTLE, ACT, WAIT_RESULT -> "Nawiguję po menu";
            case SELLING -> "Sprzedaję";
            case VERIFY -> "Sprawdzam";
        };
    }

    public void sellNow() {
        manual = true;
        nextSellAt = 0L;
    }

    private void say(MinecraftClient mc, String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§7[AutoSell] §f" + msg), true);
        }
    }

    private boolean serverOk(MinecraftClient mc) {
        if (mc.player == null) return false;
        if (!cfg.onlyOnAnarchia) return true;
        net.minecraft.client.network.ServerInfo info = mc.getCurrentServerEntry();
        return info != null && info.address != null && info.address.toLowerCase(Locale.ROOT).contains("anarchia.gg");
    }

    private boolean hasOre(PlayerEntity player) {
        Ore ore = cfg.ore();
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (ore.matches(player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    public void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            state = State.IDLE;
            sellQueue.clear();
            return;
        }
        boolean active = cfg.enabled || manual || state != State.IDLE;
        if (!active) return;
        if (!serverOk(mc)) {
            if (manual) {
                ServerInfo info = mc.getCurrentServerEntry();
                say(mc, "Nie jesteś na anarchia.gg (adres: " + (info == null ? "brak - singleplayer" : info.address)
                        + "). Wyłącz w GUI opcję 'Tylko anarchia.gg'.");
                manual = false;
            }
            if (state != State.IDLE) abort(mc);
            return;
        }
        switch (state) {
            case IDLE -> idle(mc);
            case WAIT_OPEN -> {
                ticks++;
                if (isShop(mc)) {
                    state = State.SETTLE;
                    ticks = 0;
                } else if (ticks > OPEN_TIMEOUT_TICKS) {
                    if (verbose) say(mc, "Menu /sklep się nie otworzyło (serwer nie odpowiedział na komendę).");
                    abort(mc);
                }
            }
            case SETTLE -> {
                if (!isShop(mc)) {
                    abort(mc);
                    return;
                }
                ticks++;
                if (ticks >= SETTLE_TICKS) {
                    state = State.ACT;
                    ticks = 0;
                    if (cfg.debug) dump(mc);
                }
            }
            case ACT -> act(mc);
            case WAIT_RESULT -> waitResult(mc);
            case SELLING -> selling(mc);
            case VERIFY -> verify(mc);
        }
    }

    private void idle(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (cfg.autoMine && cfg.enabled && now >= nextMineCheck && mc.currentScreen == null
                && BaritoneBridge.isAvailable()) {
            nextMineCheck = now + 10000L;
            if (!BaritoneBridge.isMining()) {
                BaritoneBridge.run("mine " + cfg.ore().mineBlocks());
            }
        }
        if (now < nextSellAt) return;
        if (mc.currentScreen != null && !manual) return;
        verbose = cfg.debug || manual;
        manual = false;
        resetTimer();
        if (!hasOre(mc.player)) {
            if (verbose) {
                say(mc, "Nie masz w ekwipunku: " + cfg.ore().label + " (szukam: " + cfg.ore().items + ").");
            }
            return;
        }
        if (cfg.pauseBaritoneWhileSelling && BaritoneBridge.isAvailable()) {
            BaritoneBridge.run("pause");
            pausedBaritone = true;
        }
        if (verbose) say(mc, "Wpisuję /sklep...");
        mc.player.networkHandler.sendChatCommand("sklep");
        state = State.WAIT_OPEN;
        ticks = 0;
        steps = 0;
        stage = STAGE_COMPASS;
        passes = 0;
        sellClicks = 0;
        cooldown = 0;
        lastContainerId = -1;
        sellQueue.clear();
    }

    private boolean isShop(MinecraftClient mc) {
        return mc.currentScreen instanceof HandledScreen
                && mc.player.currentScreenHandler != null
                && mc.player.currentScreenHandler.syncId != 0;
    }

    private void act(MinecraftClient mc) {
        if (!isShop(mc)) {
            finish(mc);
            return;
        }
        ScreenHandler h = mc.player.currentScreenHandler;
        int total = h.slots.size();
        if (stage == STAGE_SELL) {
            startSelling(mc);
            return;
        }
        int containerSize = Math.max(0, total - 36);
        int compass = -1;
        int namedRed = -1;
        int anyRed = -1;
        for (int i = 0; i < containerSize; i++) {
            ItemStack s = h.slots.get(i).getStack();
            if (s.isEmpty()) continue;
            String id = itemId(s);
            if (id.equals("compass")) {
                if (compass < 0) compass = i;
            } else if (id.equals("red_wool")) {
                if (anyRed < 0) anyRed = i;
                if (namedRed < 0 && s.getName().getString().toLowerCase(Locale.ROOT).contains("sprzeda")) {
                    namedRed = i;
                }
            }
        }
        if (stage == STAGE_COMPASS) {
            if (compass >= 0) {
                if (verbose) say(mc, "Klikam kompas (slot " + compass + ").");
                navClick(mc, compass);
                stage = STAGE_CONFIRM;
                return;
            }
            if (namedRed >= 0) {
                if (verbose) say(mc, "Klikam czerwony blok 'Sprzedaż przedmiotów' (slot " + namedRed + ").");
                navClick(mc, namedRed);
                stage = STAGE_SELL;
                return;
            }
            fail(mc, "Nie znalazłem kompasu w menu /sklep. Włącz Debug w GUI i wyślij mi listę slotów.");
            return;
        }
        int target = namedRed >= 0 ? namedRed : anyRed;
        if (target >= 0) {
            if (verbose) say(mc, "Klikam czerwony blok 'Sprzedaż przedmiotów' (slot " + target + ").");
            navClick(mc, target);
            stage = STAGE_SELL;
            return;
        }
        fail(mc, "Nie znalazłem czerwonej wełny 'Sprzedaż przedmiotów' po kliknięciu kompasu. Włącz Debug w GUI i wyślij mi listę slotów.");
    }

    private void navClick(MinecraftClient mc, int slot) {
        ScreenHandler h = mc.player.currentScreenHandler;
        lastContainerId = h.syncId;
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        steps++;
        state = State.WAIT_RESULT;
        ticks = 0;
    }

    private void waitResult(MinecraftClient mc) {
        ticks++;
        boolean shop = isShop(mc);
        if (shop && mc.player.currentScreenHandler.syncId != lastContainerId && steps < MAX_STEPS) {
            state = State.SETTLE;
            ticks = 0;
            return;
        }
        if (ticks < RESULT_TIMEOUT_TICKS) return;
        if (shop && steps < MAX_STEPS) {
            state = State.SETTLE;
            ticks = 0;
            return;
        }
        if (verbose) say(mc, "Serwer nie otworzył następnego menu po kliknięciu (krok " + steps + ").");
        finish(mc);
    }

    private void fail(MinecraftClient mc, String msg) {
        if (verbose) say(mc, msg);
        finish(mc);
    }

    private void collectOre(ScreenHandler h) {
        sellQueue.clear();
        Ore ore = cfg.ore();
        int limit = h.slots.size() - 36;
        for (int i = 0; i < limit; i++) {
            if (ore.matches(h.slots.get(i).getStack())) sellQueue.add(i);
        }
    }

    private void startSelling(MinecraftClient mc) {
        ScreenHandler h = mc.player.currentScreenHandler;
        collectOre(h);
        if (sellQueue.isEmpty()) {
            if (verbose) say(mc, "W menu sprzedaży nie widzę: " + cfg.ore().label + ".");
            finish(mc);
            return;
        }
        if (verbose) say(mc, "Sprzedaję (shift+PPM), slotów: " + sellQueue.size() + ".");
        lastContainerId = h.syncId;
        passes++;
        cooldown = 0;
        state = State.SELLING;
        ticks = 0;
    }

    private void selling(MinecraftClient mc) {
        if (!isShop(mc)) {
            abort(mc);
            return;
        }
        ScreenHandler h = mc.player.currentScreenHandler;
        if (h.syncId != lastContainerId) {
            finish(mc);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        Integer slot = sellQueue.pollFirst();
        if (slot != null) {
            if (slot < h.slots.size()) {
                mc.interactionManager.clickSlot(h.syncId, slot, 1, SlotActionType.QUICK_MOVE, mc.player);
                sellClicks++;
            }
            cooldown = CLICK_DELAY_TICKS - 1;
            return;
        }
        state = State.VERIFY;
        ticks = 0;
    }

    private void verify(MinecraftClient mc) {
        if (!isShop(mc)) {
            abort(mc);
            return;
        }
        if (++ticks < VERIFY_TICKS) return;
        ScreenHandler h = mc.player.currentScreenHandler;
        collectOre(h);
        if (!sellQueue.isEmpty() && passes < MAX_PASSES && h.syncId == lastContainerId) {
            if (verbose) {
                say(mc, "Zostało jeszcze " + sellQueue.size() + " slotów, ponawiam (próba " + (passes + 1) + ").");
            }
            passes++;
            cooldown = 0;
            state = State.SELLING;
            ticks = 0;
            return;
        }
        if (verbose) say(mc, "Gotowe (kliknięć: " + (steps + sellClicks) + ").");
        finish(mc);
    }

    private static String itemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath();
    }

    private void dump(MinecraftClient mc) {
        ScreenHandler h = mc.player.currentScreenHandler;
        int n = Math.max(0, h.slots.size() - 36);
        say(mc, "Menu (" + n + " slotów): " + mc.currentScreen.getTitle().getString());
        for (int i = 0; i < n; i++) {
            ItemStack s = h.slots.get(i).getStack();
            if (s.isEmpty()) continue;
            say(mc, i + " " + Registries.ITEM.getId(s.getItem()) + " " + s.getName().getString());
        }
    }

    private void finish(MinecraftClient mc) {
        if (mc.player != null && isShop(mc)) mc.player.closeHandledScreen();
        cleanup();
    }

    private void abort(MinecraftClient mc) {
        if (mc.player != null && isShop(mc)) mc.player.closeHandledScreen();
        cleanup();
    }

    private void cleanup() {
        state = State.IDLE;
        ticks = 0;
        sellQueue.clear();
        if (pausedBaritone) {
            BaritoneBridge.run("resume");
            pausedBaritone = false;
        }
    }
}
