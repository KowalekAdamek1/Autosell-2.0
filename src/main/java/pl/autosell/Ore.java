package pl.autosell;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public enum Ore {
    COPPER("Miedź", 0xFFE0834F, "copper_ore deepslate_copper_ore",
            "copper_ingot", "raw_copper", "copper_ore", "deepslate_copper_ore", "copper_block"),
    IRON("Żelazo", 0xFFD8D8D8, "iron_ore deepslate_iron_ore",
            "iron_ingot", "raw_iron", "iron_ore", "deepslate_iron_ore", "iron_block"),
    GOLD("Złoto", 0xFFFFD54A, "gold_ore deepslate_gold_ore",
            "gold_ingot", "raw_gold", "gold_ore", "deepslate_gold_ore", "gold_block"),
    DIAMOND("Diament", 0xFF4AEDD9, "diamond_ore deepslate_diamond_ore",
            "diamond", "diamond_ore", "deepslate_diamond_ore", "diamond_block"),
    EMERALD("Szmaragd", 0xFF17DD62, "emerald_ore deepslate_emerald_ore",
            "emerald", "emerald_ore", "deepslate_emerald_ore", "emerald_block"),
    LAPIS("Lapis", 0xFF4A74E0, "lapis_ore deepslate_lapis_ore",
            "lapis_lazuli", "lapis_ore", "deepslate_lapis_ore", "lapis_block"),
    REDSTONE("Redstone", 0xFFFF3030, "redstone_ore deepslate_redstone_ore",
            "redstone", "redstone_ore", "deepslate_redstone_ore", "redstone_block"),
    COAL("Węgiel", 0xFF9A9A9A, "coal_ore deepslate_coal_ore",
            "coal", "coal_ore", "deepslate_coal_ore", "coal_block"),
    QUARTZ("Kwarc", 0xFFEFE6DA, "nether_quartz_ore",
            "quartz", "nether_quartz_ore", "quartz_block"),
    NETHERITE("Netheryt", 0xFFB4745C, "ancient_debris",
            "netherite_ingot", "netherite_scrap", "netherite_block", "ancient_debris");

    public final String label;
    public final int color;
    public final String mineIds;
    public final List<Item> items;
    /** id przedmiotu użytego jako ikona w GUI. */
    public final String iconId;

    Ore(String label, int color, String mineIds, String... itemIds) {
        this.label = label;
        this.color = color;
        this.mineIds = mineIds;
        this.iconId = itemIds[0];
        List<Item> list = new ArrayList<>();
        for (String id : itemIds) {
            list.add(Registries.ITEM.get(Identifier.of("minecraft", id)));
        }
        this.items = List.copyOf(list);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Item item : items) {
            if (stack.isOf(item)) return true;
        }
        return false;
    }

    /** Argumenty komendy Baritone "mine". */
    public String mineBlocks() {
        return mineIds;
    }

    public Item icon() {
        return Registries.ITEM.get(Identifier.of("minecraft", iconId));
    }

    public List<Block> blocks() {
        List<Block> out = new ArrayList<>();
        for (String id : mineIds.split(" ")) {
            out.add(Registries.BLOCK.get(Identifier.of("minecraft", id)));
        }
        return out;
    }

    public static Ore byName(String name) {
        if (name != null) {
            for (Ore o : values()) {
                if (o.name().equalsIgnoreCase(name)) return o;
            }
        }
        return COPPER;
    }
}
