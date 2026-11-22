package pl.autosell;

import java.lang.reflect.Method;

/** Integracja z Baritone przez refleksję (Baritone jest opcjonalny). */
public final class BaritoneBridge {
    private static Object baritone;
    private static Object commandManager;
    private static Method execute;
    private static Method getMineProcess;
    private static Method isActive;
    private static boolean tried;
    private static boolean available;

    private BaritoneBridge() {
    }

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Object provider = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
            baritone = Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
            Class<?> ib = Class.forName("baritone.api.IBaritone");
            commandManager = ib.getMethod("getCommandManager").invoke(baritone);
            execute = Class.forName("baritone.api.command.manager.ICommandManager").getMethod("execute", String.class);
            getMineProcess = ib.getMethod("getMineProcess");
            isActive = Class.forName("baritone.api.process.IBaritoneProcess").getMethod("isActive");
            available = true;
            AutoSellMod.LOGGER.info("Wykryto Baritone - integracja włączona");
        } catch (Throwable t) {
            available = false;
        }
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    public static void run(String command) {
        if (!isAvailable()) return;
        try {
            execute.invoke(commandManager, command);
        } catch (Throwable t) {
            AutoSellMod.LOGGER.warn("Baritone: nie udało się wykonać '" + command + "'", t);
        }
    }

    public static boolean isMining() {
        if (!isAvailable()) return false;
        try {
            Object process = getMineProcess.invoke(baritone);
            return (Boolean) isActive.invoke(process);
        } catch (Throwable t) {
            return false;
        }
    }
}
