# Zombie Custom Drops

Configurable extra item drops for zombies — Forge 1.20.1.

The stock mods that make zombies drop loot are fine, but none of them let you
pick **which items**, **the drop chance**, or **the amount**. This one does.

## Configuration

Config file: `config/zombie_custom_drops-common.toml` (created on first launch).

```toml
[drops]
# Each entry rolls independently: "namespace:item;chance;min;max"
#   chance = 0.0-1.0   (0.15 = 15% chance)
#   min/max = optional stack-size range, defaults to 1
extraDrops = [
    "minecraft:iron_ingot;0.08;1;1",
    "minecraft:rotten_flesh;0.35;1;2",
    "minecraft:bone;0.20;1;2",
    "tacz:ammo;0.05;4;12"          # works with modded items too
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

Changes apply on config reload — no restart needed.

## Build

```
gradlew build
```

Jar lands in `build/libs/zombie_custom_drops-1.20.1-1.0.0.jar`.
