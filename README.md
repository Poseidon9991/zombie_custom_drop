<div align="center">

![Zombie Custom Drops](docs/banner.png)

# Zombie Custom Drops

**Configurable extra item drops for zombie-family mobs — item, chance, and stack size per entry.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62b47a?style=flat-square&logo=minecraft)](https://www.minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.4.0%2B-DFA86A?style=flat-square)](https://files.minecraftforge.net)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

</div>

---

Stock "zombies drop more loot" mods give you whatever the author hardcoded.
**Zombie Custom Drops** hands you the config: pick **which items**, set the
**drop chance**, choose the **stack range** — per entry, hot-reloaded, works
with vanilla and modded items alike.

## Features

- **Per-entry drop rules** — `item;chance;min;max`, each entry rolls independently
- **Whole zombie family** — zombie, husk, drowned, zombie villager, zombified piglin out of the box
- **Modded items welcome** — any `namespace:item` id, including TaCZ ammo, mod currency, quest items
- **Entity-type tags** — `#minecraft:undead`, `#forge:zombies`, any tag you want
- **Player-kill option** — gate drops behind player kills to keep mob farms honest
- **Hot reload** — `/reload`-free: config changes apply on file save
- **Zero dependencies** — single jar, Forge only

## Configuration

`config/zombie_custom_drops-common.toml` (created on first launch):

```toml
[drops]
# Each entry rolls independently: "namespace:item;chance;min;max"
#   chance = 0.0-1.0   (0.15 = 15% chance)
#   min/max = optional stack-size range, defaults to 1
extraDrops = [
    "minecraft:rotten_flesh;0.35;1;2",    # common junk
    "minecraft:bone;0.20;1;2",
    "minecraft:coal;0.15;1;2",
    "minecraft:carrot;0.10;1;2",
    "minecraft:potato;0.10;1;2",
    "minecraft:iron_ingot;0.08;1;1",
    "minecraft:phantom_membrane;0.05;1;1",# uncommon
    "minecraft:ender_pearl;0.04;1;1",     # rare
    "minecraft:blaze_rod;0.03;1;1",       # rarest
    "tacz:ammo;0.05;4;12"                 # modded items work too
]

[filter]
# Entities that roll extra drops. Plain ids or #namespace:entity_type_tag.
entities = [
    "minecraft:zombie",
    "minecraft:husk",
    "minecraft:drowned",
    "minecraft:zombie_villager",
    "minecraft:zombified_piglin"
]
# true = drops only when a player lands the kill (no farm drops)
requirePlayerKill = false
# true = log each rolled drop to console
debugLog = false
```

### Entry format

| Field   | Type        | Example          | Notes                                  |
|---------|-------------|------------------|----------------------------------------|
| item    | `mod:item`  | `tacz:ammo`      | Any registered item — modded included  |
| chance  | `0.0`–`1.0` | `0.08` (8%)      | Rolled independently per kill          |
| min/max | int         | `4;12`           | Stack range; omit both to default to 1 |

## Build

```
gradlew build
```

Jar lands in `build/libs/zombie_custom_drops-1.20.1-1.0.0.jar`.

## Requirements

|           |              |
|-----------|--------------|
| Minecraft | 1.20.1       |
| Forge     | 47.4.0+      |
| Java      | 17           |

## License

MIT — see [LICENSE](LICENSE).
