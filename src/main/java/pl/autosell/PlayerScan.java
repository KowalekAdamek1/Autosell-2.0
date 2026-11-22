package pl.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lista graczy w promieniu + zestaw "śledzonych" graczy. */
public final class PlayerScan {
    public static final Set<UUID> PINNED = new HashSet<>();

    public record Entry(AbstractClientPlayerEntity player, String name, double dist,
                        int x, int y, int z, float hp, float maxHp, UUID uuid) {
        public boolean pinned() {
            return PINNED.contains(uuid);
        }
    }

    private PlayerScan() {
    }

    public static List<Entry> scan(MinecraftClient mc, double radius) {
        List<Entry> out = new ArrayList<>();
        if (mc.world == null || mc.player == null) return out;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double dx = p.getX() - px, dy = p.getY() - py, dz = p.getZ() - pz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > radius) continue;
            out.add(new Entry(p, p.getName().getString(), d,
                    (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()),
                    p.getHealth(), p.getMaxHealth(), p.getUuid()));
        }
        out.sort((a, b) -> Double.compare(a.dist(), b.dist()));
        return out;
    }

    public static void togglePin(UUID id) {
        if (!PINNED.remove(id)) PINNED.add(id);
    }

    public static int distColor(double d) {
        if (d <= 100) return Theme.bad;
        if (d <= 250) return Theme.warn;
        return Theme.good;
    }
}
