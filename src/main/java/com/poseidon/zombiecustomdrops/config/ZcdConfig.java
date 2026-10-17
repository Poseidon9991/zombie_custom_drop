package com.poseidon.zombiecustomdrops.config;   // package: config subtree

import com.poseidon.zombiecustomdrops.ZombieCustomDrops;                    // main class - used for the logger's mod-id tag
import net.minecraft.core.registries.BuiltInRegistries;                     // vanilla registry access (ITEM, ENTITY_TYPE lookup)
import net.minecraft.core.registries.Registries;                            // registry key constants (needed to build TagKeys)
import net.minecraft.resources.ResourceLocation;                            // "namespace:path" id parser
import net.minecraft.nbt.CompoundTag;                                        // parsed NBT payload carried by a drop entry
import net.minecraft.nbt.TagParser;                                          // vanilla SNBT ("{key:value}") text parser
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

    /** Chance bonus added per Looting enchant level on the kill weapon. */
    public static final ForgeConfigSpec.DoubleValue LOOTING_BONUS;          // TOML double: +chance per Looting level

    /** Hard cap on extra drop stacks per single kill (0 = unlimited). */
    public static final ForgeConfigSpec.IntValue MAX_DROPS_PER_KILL;        // TOML int: stop rolling after N successes

    static {                                                                // static block - defines every config key before register()
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();          // builder accumulates sections + keys + comments

        b.comment("Zombie Custom Drops configuration",                      // header comment written into the TOML file
                "Each entry in extraDrops rolls independently on every matching kill.",
                "Format: \"[entity=]item[{snbt}];chance;min;max\"",
                "  entity : optional mob id or #tag restricting the entry (\"minecraft:drowned=...\", \"#forge:zombies=...\")",
                "  {snbt} : optional NBT applied to the stack (\"minecraft:potion{Potion:\\\"minecraft:healing\\\"}\")",
                "  chance : 0.0-1.0 (0.15 = 15% chance)",
                "  min/max: optional stack-size range, defaults to 1")
         .push("drops");                                                    // open the [drops] TOML section

        EXTRA_DROPS = b.defineListAllowEmpty("extraDrops",                  // key "extraDrops" inside [drops] - empty list allowed
                List.of(                                                    // default entries shipped with the mod - chances scale with rarity
                        "minecraft:rotten_flesh;0.35;1;2",                  // junk/common: 35% of 1-2 rotten flesh
                        "minecraft:bone;0.20;1;2",                          // common: 20% of 1-2 bones
                        "minecraft:coal;0.15;1;2",                          // common fuel: 15% of 1-2 coal
                        "minecraft:carrot;0.10;1;2",                        // food drop: 10% of 1-2 carrots
                        "minecraft:potato;0.10;1;2",                        // food drop: 10% of 1-2 potatoes
                        "minecraft:iron_ingot;0.08;1;1",                    // useful: 8% of 1 iron ingot
                        "minecraft:phantom_membrane;0.05;1;1",              // uncommon (elytra repair): 5% of 1
                        "minecraft:ender_pearl;0.04;1;1",                   // rare (teleport resource): 4% of 1
                        "minecraft:blaze_rod;0.03;1;1",                     // rarest (nether loot in the overworld): 3% of 1
                        "minecraft:drowned=minecraft:copper_ingot;0.30;1;3" // per-entity example: drowned only, 30% of 1-3 copper
                ),
                o -> o instanceof String);                                  // validator: every element must be a string

        MAX_DROPS_PER_KILL = b.comment("Extra item types per kill are drawn from all successful rolls; this caps how many actually drop (0 = unlimited).")
                .defineInRange("maxExtraDropsPerKill", 1, 0, 64);           // default 1: vanilla drops + at most one extra item type

        LOOTING_BONUS = b.comment("Chance bonus per Looting enchant level on the kill (0.01 = +1% per level).")
                .defineInRange("lootingChanceBonus", 0.01D, 0.0D, 1.0D);    // default +1%/level - vanilla rare-drop feel

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

    /** One compiled drop rule: resolved item + optional NBT + optional entity restriction + roll params. */
    public record DropEntry(Item item, CompoundTag tag,
                            EntityType<?> onlyEntity, TagKey<EntityType<?>> onlyTag,
                            float chance, int min, int max) {
        public boolean appliesTo(EntityType<?> type) {                      // does this rule fire for the mob that died?
            if (onlyEntity != null) return onlyEntity == type;              // exact mob restriction -> strict type match
            if (onlyTag != null) return type.is(onlyTag);                   // tag restriction -> membership test
            return true;                                                    // no restriction -> any target entity rolls it
        }
    }

    /** Hardcoded zombie-family scope: the mod is called Zombie Custom Drops - nothing else may roll drops. */
    private static final Set<EntityType<?>> ZOMBIE_FAMILY = Set.of(         // the only entity types allowed to receive extra drops
            EntityType.ZOMBIE,                                              // zombie
            EntityType.HUSK,                                                // husk
            EntityType.DROWNED,                                             // drowned
            EntityType.ZOMBIE_VILLAGER,                                     // zombie villager
            EntityType.ZOMBIFIED_PIGLIN                                     // zombified piglin
    );

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
            if (type == null) {
                LOGGER.warn("[zombie_custom_drops] unknown entity '{}'", s);      // unknown/bad id -> warn + skip
            } else if (!ZOMBIE_FAMILY.contains(type)) {                     // resolved but not a zombie-family mob?
                LOGGER.warn("[zombie_custom_drops] '{}' is outside the zombie family - ignored", s);  // refuse non-zombie targets
            } else {
                set.add(type);                                              // known zombie-family entity -> into the filter set
            }
        }
        entitySet = Set.copyOf(set);                                        // swap in the immutable entity set
        entityTags = List.copyOf(tags);                                     // swap in the immutable tag list

        LOGGER.info("[zombie_custom_drops] {} drop rules, {} entity types, {} tags loaded",
                entries.size(), entitySet.size(), entityTags.size());       // summary line - confirms the reload worked
    }

    private static DropEntry parse(String line) {                           // parse one "[entity=]item[{nbt}];chance;min;max" string
        String trimmed = line.trim();                                       // strip stray whitespace once
        int firstSep = trimmed.indexOf(';');                                // head = item spec, tail = roll numbers
        if (firstSep < 0) {                                                 // no chance field at all -> malformed
            LOGGER.warn("[zombie_custom_drops] bad drop entry '{}' (expected item;chance;min;max)", line);
            return null;
        }
        String head = trimmed.substring(0, firstSep).trim();                // "[entity=]item[{nbt}]"
        String tail = trimmed.substring(firstSep + 1);                      // "chance[;min;max]" - NBT lives only in head,
                                                                          // so ';' inside {..} would still break it;
                                                                          // keep semicolons out of NBT strings
        String[] parts = tail.split(";");                                   // numeric fields after the head
        if (parts.length < 1 || parts.length > 3) {                         // chance + up to min/max
            LOGGER.warn("[zombie_custom_drops] bad drop entry '{}' (expected item;chance;min;max)", line);
            return null;
        }

        EntityType<?> onlyEntity = null;                                    // optional exact-mob restriction
        TagKey<EntityType<?>> onlyTag = null;                               // optional #tag restriction
        int eq = head.indexOf('=');                                         // "entity=item..." prefix marker
        if (eq >= 0) {                                                      // per-entity table syntax present
            String ent = head.substring(0, eq).trim();                      // left side: id or #tag
            head = head.substring(eq + 1).trim();                           // right side continues as the item spec
            if (ent.startsWith("#")) {                                      // "#namespace:tag=..." form
                ResourceLocation tagRl = ResourceLocation.tryParse(ent.substring(1));  // parse the tag id
                if (tagRl != null) {
                    onlyTag = TagKey.create(Registries.ENTITY_TYPE, tagRl);  // resolve to an entity-type TagKey
                } else {
                    LOGGER.warn("[zombie_custom_drops] bad entity tag in '{}'", line);  // malformed tag -> skip entry
                    return null;
                }
            } else {
                ResourceLocation entRl = ResourceLocation.tryParse(ent);    // plain "namespace:mob=..." form
                onlyEntity = entRl == null ? null
                        : BuiltInRegistries.ENTITY_TYPE.getOptional(entRl).orElse(null);  // resolve via entity registry
                if (onlyEntity == null) {
                    LOGGER.warn("[zombie_custom_drops] unknown entity in '{}'", line);   // unknown mob -> skip entry
                    return null;
                }
                if (!ZOMBIE_FAMILY.contains(onlyEntity)) {                  // prefix names a non-zombie mob?
                    LOGGER.warn("[zombie_custom_drops] '{}' in '{}' is outside the zombie family - ignored", ent, line);
                    return null;                                            // dead rule - it could never fire anyway
                }
            }
        }

        CompoundTag tag = null;                                             // optional NBT payload for the stack
        int brace = head.indexOf('{');                                      // "item{snbt}" marker
        if (brace >= 0) {                                                   // NBT suffix present
            String snbt = head.substring(brace).trim();                     // everything from '{' is the tag text
            head = head.substring(0, brace).trim();                         // the bare item id remains
            if (!snbt.endsWith("}")) {                                      // unclosed NBT -> malformed
                LOGGER.warn("[zombie_custom_drops] unclosed NBT in '{}'", line);
                return null;
            }
            try {
                tag = TagParser.parseTag(snbt);                             // vanilla SNBT parser -> CompoundTag
            } catch (Exception ex) {                                        // any syntax error -> warn + skip
                LOGGER.warn("[zombie_custom_drops] bad NBT in '{}' ({})", line, ex.getMessage());
                return null;
            }
        }

        ResourceLocation rl = ResourceLocation.tryParse(head);              // parse the item id
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);  // resolve it (modded items work too)
        if (item == null) {
            LOGGER.warn("[zombie_custom_drops] unknown item '{}'", head);   // unresolvable item id -> warn + skip
            return null;
        }
        float chance;                                                       // roll probability
        int min = 1, max = 1;                                               // stack range - both default to 1
        try {
            chance = Float.parseFloat(parts[0].trim());                     // field 2 = chance
            if (parts.length >= 3) {                                        // 4 fields = item;chance;min;max
                min = Integer.parseInt(parts[1].trim());                    // field 3 = min
                max = Integer.parseInt(parts[2].trim());                    // field 4 = max
            } else if (parts.length == 2) {                                 // 3 fields = item;chance;max (min stays 1)
                max = Integer.parseInt(parts[1].trim());                    // field 3 = max
            }
        } catch (NumberFormatException ex) {                                // non-numeric field -> warn + skip
            LOGGER.warn("[zombie_custom_drops] bad numbers in '{}'", line);
            return null;
        }
        chance = Math.max(0f, Math.min(1f, chance));                        // clamp chance into [0, 1] - never trust TOML
        if (min < 1) min = 1;                                               // stacks can't be smaller than 1
        if (max < min) max = min;                                           // an inverted range collapses to min
        return new DropEntry(item, tag, onlyEntity, onlyTag, chance, min, max);  // the compiled rule
    }

    public static List<DropEntry> entries() {                               // accessor: the current compiled drop rules
        return entries;
    }

    public static boolean isTarget(EntityType<?> type) {                    // does this entity type get extra drops?
        if (!ZOMBIE_FAMILY.contains(type)) return false;                    // hard scope: creepers/skeletons/etc never roll, even via #tags
        if (entitySet.contains(type)) return true;                          // O(1) plain-id hit
        for (TagKey<EntityType<?>> tag : entityTags) {                      // otherwise check each configured tag
            if (type.is(tag)) return true;                                  // entity is a member of the tag -> target
        }
        return false;                                                       // not in set or any tag -> skip
    }

    public static boolean debug() {                                         // accessor: is debugLog currently on?
        return DEBUG_LOG.get();
    }

    public static double lootingBonus() {                                   // accessor: chance bonus per Looting level
        return LOOTING_BONUS.get();
    }

    public static int maxDropsPerKill() {                                   // accessor: extra-drop cap per kill (0 = unlimited)
        return MAX_DROPS_PER_KILL.get();
    }

    private ZcdConfig() {}                                                  // private ctor - utility class, no instances
}
