package com.poseidon.zombiecustomdrops;

import com.poseidon.zombiecustomdrops.config.ZcdConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ZombieCustomDrops.MODID)
public class ZombieCustomDrops {

    public static final String MODID = "zombie_custom_drops";

    public ZombieCustomDrops() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ZcdConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigLoad);
        MinecraftForge.EVENT_BUS.register(DropHandler.class);
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ZcdConfig.SPEC) {
            ZcdConfig.rebuild();
        }
    }
}
