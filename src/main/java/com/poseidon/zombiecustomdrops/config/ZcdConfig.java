package com.poseidon.zombiecustomdrops.config;   // package: config subtree

import com.poseidon.zombiecustomdrops.ZombieCustomDrops;                    // main class - used for the logger's mod-id tag
import net.minecraft.core.registries.BuiltInRegistries;                     // vanilla registry access (ITEM, ENTITY_TYPE lookup)
import net.minecraft.core.registries.Registries;                            // registry key constants (needed to build TagKeys)
import net.minecraft.resources.ResourceLocation;                            // "namespace:path" id parser
import net.minecraft.tags.TagKey;                                           // a #tag reference for entity types
import net.minecraft.world.entity.EntityType;                               // entity types (zombie, husk, ...)
import net.minecraft.world.item.Item;                                       // item types from the item registry
import net.minecraftforge.common.ForgeConfigSpec;                           // Forge's TOML config system
import org.apache.logging.log4j.LogManager;                                 // Log4j logger factory
import org.apache.logging.log4j.Logger;                                     // logger interface for console output

import java.util.ArrayList;                                                 // mutable list used while parsing
import java.util.HashSet;                                                   // mutable set used while resolving entity ids
import java.util.List;                                                      // list type for drop entries + tags
import java.util.Set;                                                       // set type for the resolved entity filter

public final class ZcdConfig {                                              // utility class - static spec + compiled snapshot, no instances

    private static final Logger LOGGER = LogManager.getLogger(ZombieCustomDrops.MODID);   // console logger tagged with the mod id

    public static final ForgeConfigSpec SPEC;                               // the frozen config spec handed to Forge at register()

    /**
     * Extra drop rules. Format: "namespace:item;chance;min;max"
     * chance is 0.0-1.0 (0.15 = 15%), min/max are stack counts (both optional,
     * default 1). Examples:
     * "minecraft:iron_ingot;0.1;1;2"
     * "minecraft:bone;0.5"
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_DROPS;   // raw TOML string list - parsed in rebuild()

    /**
     * Entities that roll extra drops. Plain ids or #namespace:tag form.
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_FILTER; // raw TOML string list - ids or #tags

    /** Only roll drops when the killing entity is a player. */
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PLAYER_KILL;   // TOML boolean: gate drops behind player kills

    /** Log every rolled drop to the console. */
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG;             // TOML boolean: log each successful roll

    static {                                                                // static block - defines every config key before register()
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();          // builder accumulates sections + keys + comments

        b.comment("Zombie Custom Drops configuration",                      // header comment written into the TOML file
                "Each entry in extraDrops rolls independently on every matching kill.",
                "Format: \"namespace:item;chance;min;max\"",
                "  chance : 0.0-1.0 (0.15 = 15% chance)",
                "  min/max: optional stack-size range, defaults to 1")
         .push("drops");                                                    // open the [drops] TOML section

        EXTRA_DROPS = b.defineListAllowEmpty("extraDrops",                  // key "extraDrops" inside [drops] - empty list allowed
                List.of(                                                    // default entries shipped with the mod
                        "minecraft:iron_ingot;0.08;1;1",                    // 8% chance of 1 iron ingot
                        "minecraft:rotten_flesh;0.35;1;2",                  // 35% chance of 1-2 rotten flesh
                        "minecraft:bone;0.20;1;2"                           // 20% chance of 1-2 bones
                ),
                o -> o instanceof String);                                  // validator: every element must be a string

        b.pop();                                                            // close the [drops] section

        b.comment("Which entities get extra drops.",                        // header comment for the filter section
                "Plain entity ids (\"minecraft:zombie\") or entity-type tags (\"#minecraft:zombies\").")
         .push("filter");                                                   // open the [filter] TOML section

        ENTITY_FILTER = b.defineListAllowEmpty("entities",                  // key "entities" inside [filter]
                List.of(                                                    // default targets: the whole zombie family
                        "minecraft:zombie",
                        "minecraft:husk",
                        "minecraft:drowned",
                        "minecraft:zombie_villager",
                        "minecraft:zombified_piglin"
                ),
                o -> o instanceof String);                                  // validator: strings only

        REQUIRE_PLAYER_KILL = b.comment("If true, extra drops only roll when a player lands the kill.")
                .define("requirePlayerKill", false);                        // default false - farms work out of the box

        DEBUG_LOG = b.comment("Log every rolled extra drop (item, count, entity).")
                .define("debugLog", false);                                 // default false - silent unless enabled

        b.pop();                                                            // close the [filter] section
        SPEC = b.build();                                                   // freeze the builder into the immutable spec
    }

    public record DropEntry(Item item, float chance, int min, int max) {}   // one compiled drop rule: resolved item + roll params

    private static volatile List<DropEntry> entries = List.of();            // compiled drop rules - volatile so a reload swap is atomic
    private static volatile Set<EntityType<?>> entitySet = Set.of();        // resolved plain entity ids - volatile for the same reason
    private static volatile List<TagKey<EntityType<?>>> entityTags = List.of();  // resolved #tag filters - volatile likewise

    public static void rebuild() {                                          // re-parse the TOML into compiled objects (on load + every save)
        List<DropEntry> parsed = new ArrayList<>();                         // fresh mutable list for this parse pass
        for (String line : EXTRA_DROPS.get()) {                             // walk every raw "item;chance;min;max" string
            DropEntry e = parse(line);                                      // parse one line -> DropEntry or null on bad input
            if (e != null) parsed.add(e);                                   // keep only entries that parsed cleanly
        }
        entries = List.copyOf(parsed);                                      // swap in the immutable snapshot - readers never see a half-built list

        Set<EntityType<?>> set = new HashSet<>();                           // fresh set for resolved entity types
        List<TagKey<EntityType<?>>> tags = new ArrayList<>();               // fresh list for tag filters
        for (String raw : ENTITY_FILTER.get()) {                            // walk every raw filter string
            String s = raw.trim();                                          // strip stray whitespace
            if (s.startsWith("#")) {                                        // "#namespace:path" -> entity-type tag filter
                ResourceLocation rl = ResourceLocation.tryParse(s.substring(1));  // parse the id after the '#'
                if (rl != null) {
                    tags.add(TagKey.create(Registries.ENTITY_TYPE, rl));    // build a TagKey into the ENTITY_TYPE registry
                } else {
                    LOGGER.warn("[zombie_custom_drops] bad entity tag '{}'", s);  // malformed tag id -> warn + skip
                }
                continue;                                                   // tag handled - next entry
            }
            ResourceLocation rl = ResourceLocation.tryParse(s);             // plain "namespace:path" entity id
            EntityType<?> type = rl == null ? null
                    : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);  // resolve via the entity registry (absent mod ids -> empty)
            if (type != null) {
                set.add(type);                                              // known entity -> into the filter set
            } else {
                LOGGER.warn("[zombie_custom_drops] unknown entity '{}'", s);      // unknown/bad id -> warn + skip
            }
        }
        entitySet = Set.copyOf(set);                                        // swap in the immutable entity set
        entityTags = List.copyOf(tags);                                     // swap in the immutable tag list

        LOGGER.info("[zombie_custom_drops] {} drop rules, {} entity types, {} tags loaded",
                entries.size(), entitySet.size(), entityTags.size());       // summary line - confirms the reload worked
    }

    private static DropEntry parse(String line) {                           // parse one "item;chance;min;max" string -> DropEntry or null
        String[] parts = line.trim().split(";");                            // split on semicolons
        if (parts.length < 2 || parts.length > 4) {                         // need at least item+chance, at most all four fields
            LOGGER.warn("[zombie_custom_drops] bad drop entry '{}' (expected item;chance;min;max)", line);
            return null;                                                    // wrong field count -> skip entry
        }
        ResourceLocation rl = ResourceLocation.tryParse(parts[0].trim());   // parse the item id
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);  // resolve it (modded items work too)
        if (item == null) {
            LOGGER.warn("[zombie_custom_drops] unknown item '{}'", parts[0]);  // unresolvable item id -> warn + skip
            return null;
        }
        float chance;                                                       // roll probability
        int min = 1, max = 1;                                               // stack range - both default to 1
        try {
            chance = Float.parseFloat(parts[1].trim());                     // field 2 = chance
            if (parts.length >= 4) {                                        // 4 fields = item;chance;min;max
                min = Integer.parseInt(parts[2].trim());                    // field 3 = min
                max = Integer.parseInt(parts[3].trim());                    // field 4 = max
            } else if (parts.length == 3) {                                 // 3 fields = item;chance;max (min stays 1)
                max = Integer.parseInt(parts[2].trim());                    // field 3 = max
            }
        } catch (NumberFormatException ex) {                                // non-numeric field -> warn + skip
            LOGGER.warn("[zombie_custom_drops] bad numbers in '{}'", line);
            return null;
        }
        chance = Math.max(0f, Math.min(1f, chance));                        // clamp chance into [0, 1] - never trust TOML
        if (min < 1) min = 1;                                               // stacks can't be smaller than 1
        if (max < min) max = min;                                           // an inverted range collapses to min
        return new DropEntry(item, chance, min, max);                       // the compiled rule
    }

    public static List<DropEntry> entries() {                               // accessor: the current compiled drop rules
        return entries;
    }

    public static boolean isTarget(EntityType<?> type) {                    // does this entity type get extra drops?
        if (entitySet.contains(type)) return true;                          // O(1) plain-id hit
        for (TagKey<EntityType<?>> tag : entityTags) {                      // otherwise check each configured tag
            if (type.is(tag)) return true;                                  // entity is a member of the tag -> target
        }
        return false;                                                       // not in set or any tag -> skip
    }

    public static boolean debug() {                                         // accessor: is debugLog currently on?
        return DEBUG_LOG.get();
    }

    private ZcdConfig() {}                                                  // private ctor - utility class, no instances
}
