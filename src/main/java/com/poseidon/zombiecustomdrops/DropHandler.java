package com.poseidon.zombiecustomdrops;

import com.poseidon.zombiecustomdrops.config.ZcdConfig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DropHandler {

    private static final Logger LOGGER = LogManager.getLogger(ZombieCustomDrops.MODID);

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!ZcdConfig.isTarget(entity.getType())) return;
        if (ZcdConfig.REQUIRE_PLAYER_KILL.get() && !(event.getSource().getEntity() instanceof Player)) return;

        RandomSource rng = entity.getRandom();
        for (ZcdConfig.DropEntry drop : ZcdConfig.entries()) {
            if (rng.nextFloat() >= drop.chance()) continue;

            int count = drop.min();
            if (drop.max() > drop.min()) {
                count += rng.nextInt(drop.max() - drop.min() + 1);
            }

            ItemEntity dropEntity = new ItemEntity(
                    entity.level(),
                    entity.getX(),
                    entity.getY() + 0.3,
                    entity.getZ(),
                    new ItemStack(drop.item(), count)
            );
            event.getDrops().add(dropEntity);

            if (ZcdConfig.debug()) {
                LOGGER.info("[zombie_custom_drops] {} dropped {} x{} (chance {})",
                        entity.getType().toString(), drop.item(), count, drop.chance());
            }
        }
    }

    private DropHandler() {}
}
