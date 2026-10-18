package com.poseidon.zombiecustomdrops;   // package: root mod package

import com.poseidon.zombiecustomdrops.config.ZcdConfig;                    // config: entries(), isTarget(), kill gates, looting/cap, debug()
import net.minecraft.world.damagesource.DamageSource;                        // damage source wrapper - resolves who landed the kill
import net.minecraft.world.entity.Entity;                                    // base entity type for the killer lookup
import net.minecraft.world.entity.OwnableEntity;                             // tamed/pet entities that expose getOwner()
import net.minecraft.world.entity.item.ItemEntity;                          // the in-world item drop entity we add to the drop list
import net.minecraft.world.entity.player.Player;                            // used for the requirePlayerKill check (killer must be a player)
import net.minecraft.world.item.ItemStack;                                  // stack of items carried by an ItemEntity
import net.minecraft.util.RandomSource;                                     // per-entity random source for chance + count rolls
//? if >=1.21 {
/*import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;          // NeoForge: same event, new package
import net.neoforged.bus.api.SubscribeEvent;                                 // NeoForge's relocated eventbus annotation
import net.minecraft.core.component.DataComponents;                          // 1.20.5+: item data lives in typed components
import net.minecraft.world.item.component.CustomData;                        // component wrapper that holds arbitrary item NBT
import net.minecraft.world.item.enchantment.EnchantmentHelper;               // reads enchant levels off an item stack
import net.minecraft.world.item.enchantment.Enchantments;                    // datapacked vanilla enchantment keys (looting)
import net.minecraft.core.registries.Registries;                             // registry key constants (enchantment registry)
import net.minecraft.world.entity.LivingEntity;                              // the killer who may be wielding a Looting weapon
*///?} else {
import net.minecraftforge.event.entity.living.LivingDropsEvent;             // Forge event fired when a living entity dies and drops loot
import net.minecraftforge.eventbus.api.SubscribeEvent;                      // annotation that marks a method as an event handler
//?}
import java.util.ArrayList;                                                  // winner list while picking capped drops
import java.util.List;                                                       // list type for the winner pick
import org.apache.logging.log4j.LogManager;                                 // Log4j logger factory
import org.apache.logging.log4j.Logger;                                     // logger interface for console output

public final class DropHandler {                                            // utility class - all static, never instantiated

    private static final Logger LOGGER = LogManager.getLogger(ZombieCustomDrops.MODID);   // console logger tagged with the mod id

    @SubscribeEvent                                                         // Forge calls this for every LivingDropsEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        var entity = event.getEntity();                                     // the entity that just died
        if (entity.level().isClientSide) return;                            // drop logic is server-only; client insertion would ghost items
        if (!ZcdConfig.isTarget(entity.getType())) return;                  // entity type not in the filter list/tags -> no extra drops
        if (ZcdConfig.REQUIRE_PLAYER_KILL.get()                             // kill-gating option enabled?
                && !isPlayerKill(event.getSource())) return;                // ...and no player (or their pet) earned it -> skip (stops mob-farm loot)

        RandomSource rng = entity.getRandom();                              // the dead entity's own random source
        //? if >=1.21 {
        /*int lootingLevel = lootingLevelOf(event.getSource());             // NeoForge: event has no looting field - read it off the weapon
        *///?} else {
        int lootingLevel = event.getLootingLevel();                         // Forge: looting level rides along on the drop event
        //?}
        double lootingBoost = lootingLevel * ZcdConfig.lootingBonus();  // +chance per Looting level on the kill
        int cap = ZcdConfig.maxDropsPerKill();                              // per-kill extra-drop cap (0 = unlimited)

        List<ZcdConfig.DropEntry> winners = new ArrayList<>();              // entries that passed their roll this kill
        for (ZcdConfig.DropEntry drop : ZcdConfig.entries()) {              // each configured rule rolls independently
            if (!drop.appliesTo(entity.getType())) continue;                // per-entity restriction mismatch -> skip entry
            float chance = (float) Math.min(1.0D, drop.chance() + lootingBoost);  // Looting can raise, never above 100%
            if (rng.nextFloat() < chance) winners.add(drop);                // chance passed -> candidate for this kill
        }

        int picks = cap > 0 ? Math.min(cap, winners.size()) : winners.size();  // how many winners actually drop
        while (winners.size() > picks) {                                    // too many winners for the cap?
            winners.remove(rng.nextInt(winners.size()));                    // discard random losers - keeps rare entries fair
        }

        for (ZcdConfig.DropEntry drop : winners) {                          // spawn every surviving winner
            int count = drop.min();                                         // stack size starts at min
            if (drop.max() > drop.min()) {                                  // a range was configured (max > min)?
                count += rng.nextInt(drop.max() - drop.min() + 1);          // ...roll a uniform count inside [min, max]
            }

            ItemStack stack = new ItemStack(drop.item(), count);            // the rolled item stack
            if (drop.tag() != null) {                                       // entry carried an NBT payload?
                //? if >=1.21 {
                /*stack.set(DataComponents.CUSTOM_DATA, CustomData.of(drop.tag().copy()));  // 1.20.5+: NBT attaches via CUSTOM_DATA component
                *///?} else {
                stack.setTag(drop.tag().copy());                            // Forge: attach a copy - entries are shared, never mutate them
                //?}
            }

            ItemEntity dropEntity = new ItemEntity(                         // build the in-world drop entity
                    entity.level(),                                         // same dimension the unit died in
                    entity.getX(),                                          // drop at the corpse position
                    entity.getY() + 0.3,                                    // slightly above the ground so it pops visibly
                    entity.getZ(),
                    stack                                                   // the rolled (possibly NBT-tagged) stack
            );
            event.getDrops().add(dropEntity);                               // append to Forge's drop list - other mods can still veto it

            if (ZcdConfig.debug()) {                                        // debugLog config on? -> log the roll result
                LOGGER.info("[zombie_custom_drops] {} dropped {} x{} (winners {})",
                        entity.getType().toString(), drop.item(), count, winners.size());
            }
        }
    }

    //? if >=1.21 {
    /*private static int lootingLevelOf(DamageSource source) {              // NeoForge 1.21: looting is a datapacked enchant - ask the weapon
        Entity killer = source.getEntity();                                 // the entity credited with the killing damage
        if (!(killer instanceof LivingEntity living)) return 0;             // environment/trap kill -> no wielder, no looting
        var ench = living.level().registryAccess()                          // dynamic registry access (enchantments are datapacked now)
                .registryOrThrow(Registries.ENCHANTMENT)                    // the enchantment registry - always present
                .getHolderOrThrow(Enchantments.LOOTING);                    // holder for minecraft:looting
        return EnchantmentHelper.getItemEnchantmentLevel(ench, living.getMainHandItem());  // Looting level on the held weapon
    }
    *///?}

    private static boolean isPlayerKill(DamageSource source) {              // did a player - or a player's pet - land the kill?
        Entity killer = source.getEntity();                                 // the entity credited with the killing damage
        if (killer instanceof Player) return true;                          // direct player kill (sword, bow shot credited to shooter, etc.)
        if (killer instanceof OwnableEntity pet) {                          // wolf/cat/other tamed animal made the kill
            return pet.getOwner() instanceof Player;                        // ...count it if a player owns the pet
        }
        return false;                                                       // mob, trap or environment kill -> not a player kill
    }

    private DropHandler() {}                                                // private ctor - utility class, no instances
}
