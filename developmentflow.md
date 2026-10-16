# Zombie Custom Drops — Developer Architecture & Codeflow Guide

> **Target Version:** Minecraft 1.20.1 | Forge 47.4.0+ | Java 17
> **Mappings:** Parchment 1.20.1 (2023.08.20-1.20.1)
> **Dependencies:** Forge only — zero runtime deps
> **License:** MIT

---

## 📑 Table of Contents

1. [Architecture & Design Philosophy](#1-architecture--design-philosophy)
2. [Source Layout](#2-source-layout)
3. [Execution Flow](#3-execution-flow)
   - [Boot & Config Lifecycle](#boot--config-lifecycle)
   - [Drop Roll Flow](#drop-roll-flow)
4. [Configuration Pipeline](#4-configuration-pipeline)
5. [Extension Recipes](#5-extension-recipes)
6. [Conventions & Safety Rules](#6-conventions--safety-rules)

---

## 1. Architecture & Design Philosophy

`zombie_custom_drops` is deliberately minimal: **one Forge event subscriber, one compiled config**. No registries, no packets, no mixins — the mod never loads a class from another mod, so it cannot break on dependency absence.

```
+-----------------------------------------------------------------------+
|                             MINECRAFT 1.20.1                          |
+------------------------------------+----------------------------------+
                                     |
        zombie_custom_drops-common.toml  (ForgeConfigSpec, COMMON scope)
                                     |
               +---------------------v---------------------+
               |              ZcdConfig                     |
               |  raw TOML lists --> parsed DropEntry set   |
               |  entity ids --> EntityType set + TagKeys   |
               |  volatile snapshot swapped on rebuild()    |
               +---------------------+---------------------+
                                     | entries() / isTarget()
               +---------------------v---------------------+
               |             DropHandler                    |
               |  @SubscribeEvent LivingDropsEvent          |
               |  filter -> chance roll -> stack size roll  |
               |  -> event.getDrops().add(ItemEntity)       |
               +--------------------------------------------+
```

Design rules that hold everywhere in this codebase:

- **Parse once, read forever.** TOML strings are resolved to `Item`/`EntityType`/`TagKey` objects in `rebuild()`; the hot path (`onLivingDrops`) never parses text or hits the registry.
- **Volatile snapshot swap.** `entries`/`entitySet`/`entityTags` are immutable `List.copyOf`/`Set.copyOf` results assigned to `volatile` fields — a config reload mid-tick can never hand the event handler a half-built list.
- **Warn, don't crash.** Bad config lines log a warning and are skipped; a typo in a pack's config must never take down the event bus.
- **Server-only logic.** `onLivingDrops` returns early on `isClientSide` — `LivingDropsEvent` technically fires on both sides and client insertion would ghost items.

---

## 2. Source Layout

```
src/main/java/com/poseidon/zombiecustomdrops/
├── ZombieCustomDrops.java      # @Mod entrypoint - config registration + bus wiring
├── DropHandler.java            # LivingDropsEvent subscriber (game bus)
└── config/
    └── ZcdConfig.java          # ForgeConfigSpec + parser + compiled snapshot

src/main/resources/
├── META-INF/mods.toml          # mod metadata (values injected from gradle.properties)
├── pack.mcmeta
└── logo.png                    # CurseForge/in-game mod icon (built by tools/, gitignored)

config/zombie_custom_drops-common.toml   # runtime config, created on first launch
tools/                            # local asset generator (PIL) - never shipped
```

## 3. Execution Flow

### Boot & Config Lifecycle

```
FML mod construction
  └── ZombieCustomDrops.<init>()
        ├── registerConfig(COMMON, ZcdConfig.SPEC)     <-- spec frozen at class-load
        ├── modBus.addListener(onConfigLoad)           <-- hot-reload hook
        └── EVENT_BUS.register(DropHandler.class)      <-- game-bus subscriber

ModConfigEvent (Loading OR Reloading)
  └── onConfigLoad(event)
        └── if spec == ZcdConfig.SPEC -> ZcdConfig.rebuild()
              ├── EXTRA_DROPS list -> parse() each line -> List<DropEntry>
              └── ENTITY_FILTER list -> EntityType set + TagKey list
```

`ModConfigEvent` fires on file save too, so **config edits hot-reload without a restart** — `rebuild()` re-parses and atomically swaps the volatile snapshots.

### Drop Roll Flow

```
Entity dies -> Forge fires LivingDropsEvent
  └── DropHandler.onLivingDrops(event)
        ├── isClientSide?            -> return
        ├── isTarget(entity.getType())?
        │     entitySet.contains(type)            O(1)
        │     or type.is(tag) for each TagKey     tag union check
        ├── requirePlayerKill && killer not Player -> return
        └── for each DropEntry:
              ├── rng.nextFloat() >= chance  -> skip
              ├── count = min + rng.nextInt(max - min + 1)
              ├── event.getDrops().add(new ItemEntity(...))
              └── debugLog? -> LOGGER.info(item, count, chance)
```

Notes:

- Drops are appended to `event.getDrops()` — the collection Forge turns into real world entities — rather than spawned directly. Other mods listening later still see and can veto them.
- Each entry rolls **independently** — a kill can drop several configured items, or none.
- `rng` is `entity.getRandom()` — the per-entity RandomSource, deterministic-per-entity-seed friendly.

## 4. Configuration Pipeline

`config/zombie_custom_drops-common.toml`:

```toml
[drops]
extraDrops = ["minecraft:iron_ingot;0.08;1;1", ...]   # item;chance;min;max

[filter]
entities = ["minecraft:zombie", "#forge:zombies", ...] # ids or #tags
requirePlayerKill = false
debugLog = false
```

Parser rules (`ZcdConfig.parse`):

| Field | Rule | On bad input |
|---|---|---|
| item | `ResourceLocation.tryParse` + `BuiltInRegistries.ITEM.getOptional` | warn + skip entry |
| chance | `Float.parseFloat`, clamped `[0, 1]` | warn + skip entry |
| min/max | ints; 2 fields = chance+max, 3 = chance+min+max, omitted = 1 | warn + skip entry |
| entity | `#id` → `TagKey`, else `BuiltInRegistries.ENTITY_TYPE` | warn + skip entry |

Clamps: `min >= 1`, `max >= min`, `chance ∈ [0,1]` — never trust TOML.

## 5. Extension Recipes

**Add a drop rule** — edit `extraDrops`, save; `rebuild()` picks it up live. No code change needed.

**Add a new drop condition** (e.g. per-biome, looting-scaled):
1. Add the key to `ZcdConfig`'s builder (inside `[filter]` or a new section).
2. Read it in `DropHandler.onLivingDrops` before the roll — keep the early-outs ordered cheapest-first (`isClientSide` → `isTarget` → cheap flags → rolls).

**Per-entity drop tables** (different drops for drowned vs zombie):
- Either extend the entry format (`item;chance;min;max;entity`) or add a second list section. Parse into the same `DropEntry` shape and filter inside the roll loop — keep `rebuild()` the only parser.

**Adding a dependency** (e.g. modded-item tags): keep it soft — `ModList.get().isLoaded(...)` gate + `BuiltInRegistries` lookups already return empty for missing ids, so modded items in `extraDrops` degrade gracefully today.

## 6. Conventions & Safety Rules

- `ForgeRegistries`/`BuiltInRegistries` lookups are `@Nullable`-ish (`getOptional`) — always handle absent ids with a warn, never `get()` blindly.
- Log via `LOGGER` (Log4j2), never `System.out`.
- No client classes (`Minecraft`, `Dist.CLIENT` paths) — the mod is environment-agnostic by design.
- Keep `DropHandler` allocation-free in steady state: `ItemEntity` allocation only happens on a successful roll.
- Commits are authored by the repo owner (Poseidon9991).
- `tools/` is `.gitignore`d — asset generation is a local-only concern; `docs/` and `src/main/resources/logo.png` hold the committed outputs.
```
