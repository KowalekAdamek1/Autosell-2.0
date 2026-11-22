package pl.autosell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoSellMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("AutoSell");

    public static AutoSellConfig CFG;
    public static SellTask TASK;
    /** Blokuje ponowne otwarcie GUI w tej samej sekwencji wciśnięcia klawisza (zamknięcie klawiszem). */
    public static boolean blockOpen;

    private boolean keyWasDown;

    @Override
    public void onInitializeClient() {
        CFG = AutoSellConfig.load();
        TASK = new SellTask(CFG);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> Visuals.render(ctx, tickCounter.getTickDelta(false)));
    }

    private void onTick(MinecraftClient mc) {
        boolean down = InputUtil.isKeyPressed(mc.getWindow().getHandle(), CFG.guiKey);
        if (!down) blockOpen = false;
        if (down && !keyWasDown && !blockOpen && mc.currentScreen == null && mc.player != null) {
            mc.setScreen(new MainScreen(CFG, TASK));
        }
        keyWasDown = down;
        TASK.tick(mc);
        Visuals.tick(mc, CFG);
    }
}
