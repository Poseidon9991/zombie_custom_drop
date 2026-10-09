package com.poseidon.zombiecustomdrops.config;

import com.poseidon.zombiecustomdrops.ZombieCustomDrops;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ZcdConfig {

    private static final Logger LOGGER = LogManager.getLogger(ZombieCustomDrops.MODID);

    public static final ForgeConfigSpec SPEC;

    /**
     * Extra drop rules. Format: "namespace:item;chance;min;max"
     * chance is 0.0-1.0 (0.15 = 15%), min/max are stack counts (both optional,
     * default 1). Examples:
     * "minecraft:iron_ingot;0.1;1;2"
     * "minecraft:bone;0.5"
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_DROPS;

    /**
     * Entities that roll extra drops. Plain ids or #namespace:tag form.
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_FILTER;

    /** Only roll drops when the killing entity is a player. */
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PLAYER_KILL;

    /** Log every rolled drop to the console. */
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Zombie Custom Drops configuration",
                "Each entry in extraDrops rolls independently on every matching kill.",
                "Format: \"namespace:item;chance;min;max\"",
                "  chance : 0.0-1.0 (0.15 = 15% chance)",
                "  min/max: optional stack-size range, defaults to 1")
         .push("drops");

        EXTRA_DROPS = b.defineListAllowEmpty("extraDrops",
                List.of(
                        "minecraft:iron_ingot;0.08;1;1",
                        "minecraft:rotten_flesh;0.35;1;2",
                        "minecraft:bone;0.20;1;2"
                ),
                o -> o instanceof String);

        b.pop();

        b.comment("Which entities get extra drops.",
                "Plain entity ids (\"minecraft:zombie\") or entity-type tags (\"#minecraft:zombies\").")
         .push("filter");

        ENTITY_FILTER = b.defineListAllowEmpty("entities",
                List.of(
                        "minecraft:zombie",
                        "minecraft:husk",
                        "minecraft:drowned",
                        "minecraft:zombie_villager",
                        "minecraft:zombified_piglin"
                ),
                o -> o instanceof String);

        REQUIRE_PLAYER_KILL = b.comment("If true, extra drops only roll when a player lands the kill.")
                .define("requirePlayerKill", false);

        DEBUG_LOG = b.comment("Log every rolled extra drop (item, count, entity).")
                .define("debugLog", false);

        b.pop();
        SPEC = b.build();
    }

    public record DropEntry(Item item, float chance, int min, int max) {}

    private static volatile List<DropEntry> entries = List.of();
    private static volatile Set<EntityType<?>> entitySet = Set.of();
    private static volatile List<TagKey<EntityType<?>>> entityTags = List.of();

    public static void rebuild() {
        List<DropEntry> parsed = new ArrayList<>();
        for (String line : EXTRA_DROPS.get()) {
            DropEntry e = parse(line);
            if (e != null) parsed.add(e);
        }
        entries = List.copyOf(parsed);

        Set<EntityType<?>> set = new HashSet<>();
        List<TagKey<EntityType<?>>> tags = new ArrayList<>();
        for (String raw : ENTITY_FILTER.get()) {
            String s = raw.trim();
            if (s.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(s.substring(1));
                if (rl != null) {
                    tags.add(TagKey.create(Registries.ENTITY_TYPE, rl));
                } else {
                    LOGGER.warn("[zombie_custom_drops] bad entity tag '{}'", s);
                }
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(s);
            EntityType<?> type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            if (type != null) {
                set.add(type);
            } else {
                LOGGER.warn("[zombie_custom_drops] unknown entity '{}'", s);
            }
        }
        entitySet = Set.copyOf(set);
        entityTags = List.copyOf(tags);

        LOGGER.info("[zombie_custom_drops] {} drop rules, {} entity types, {} tags loaded",
                entries.size(), entitySet.size(), entityTags.size());
    }

    private static DropEntry parse(String line) {
        String[] parts = line.trim().split(";");
        if (parts.length < 2 || parts.length > 4) {
            LOGGER.warn("[zombie_custom_drops] bad drop entry '{}' (expected item;chance;min;max)", line);
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(parts[0].trim());
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (item == null) {
            LOGGER.warn("[zombie_custom_drops] unknown item '{}'", parts[0]);
            return null;
        }
        float chance;
        int min = 1, max = 1;
        try {
            chance = Float.parseFloat(parts[1].trim());
            if (parts.length >= 4) {
                min = Integer.parseInt(parts[2].trim());
                max = Integer.parseInt(parts[3].trim());
            } else if (parts.length == 3) {
                max = Integer.parseInt(parts[2].trim());
            }
        } catch (NumberFormatException ex) {
            LOGGER.warn("[zombie_custom_drops] bad numbers in '{}'", line);
            return null;
        }
        chance = Math.max(0f, Math.min(1f, chance));
        if (min < 1) min = 1;
        if (max < min) max = min;
        return new DropEntry(item, chance, min, max);
    }

    public static List<DropEntry> entries() {
        return entries;
    }

    public static boolean isTarget(EntityType<?> type) {
        if (entitySet.contains(type)) return true;
        for (TagKey<EntityType<?>> tag : entityTags) {
            if (type.is(tag)) return true;
        }
        return false;
    }

    public static boolean debug() {
        return DEBUG_LOG.get();
    }

    private ZcdConfig() {}
}
