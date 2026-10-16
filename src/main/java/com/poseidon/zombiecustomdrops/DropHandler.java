package com.poseidon.zombiecustomdrops;   // package: root mod package

import com.poseidon.zombiecustomdrops.config.ZcdConfig;                    // config: entries(), isTarget(), REQUIRE_PLAYER_KILL, debug()
import net.minecraft.world.entity.item.ItemEntity;                          // the in-world item drop entity we add to the drop list
import net.minecraft.world.entity.player.Player;                            // used for the requirePlayerKill check (killer must be a player)
import net.minecraft.world.item.ItemStack;                                  // stack of items carried by an ItemEntity
import net.minecraft.util.RandomSource;                                     // per-entity random source for chance + count rolls
import net.minecraftforge.event.entity.living.LivingDropsEvent;             // Forge event fired when a living entity dies and drops loot
import net.minecraftforge.eventbus.api.SubscribeEvent;                      // annotation that marks a method as an event handler
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
                && !(event.getSource().getEntity() instanceof Player)) return; // ...and killer wasn't a player -> skip (stops mob-farm loot)

        RandomSource rng = entity.getRandom();                              // the dead entity's own random source
        for (ZcdConfig.DropEntry drop : ZcdConfig.entries()) {              // each configured rule rolls independently
            if (rng.nextFloat() >= drop.chance()) continue;                 // chance check failed -> this entry doesn't drop this kill

            int count = drop.min();                                         // stack size starts at min
            if (drop.max() > drop.min()) {                                  // a range was configured (max > min)?
                count += rng.nextInt(drop.max() - drop.min() + 1);          // ...roll a uniform count inside [min, max]
            }

            ItemEntity dropEntity = new ItemEntity(                         // build the in-world drop entity
                    entity.level(),                                         // same dimension the unit died in
                    entity.getX(),                                          // drop at the corpse position
                    entity.getY() + 0.3,                                    // slightly above the ground so it pops visibly
                    entity.getZ(),
                    new ItemStack(drop.item(), count)                       // the rolled item stack
            );
            event.getDrops().add(dropEntity);                               // append to Forge's drop list - other mods can still veto it

            if (ZcdConfig.debug()) {                                        // debugLog config on? -> log the roll result
                LOGGER.info("[zombie_custom_drops] {} dropped {} x{} (chance {})",
                        entity.getType().toString(), drop.item(), count, drop.chance());
            }
        }
    }

    private DropHandler() {}                                                // private ctor - utility class, no instances
}
