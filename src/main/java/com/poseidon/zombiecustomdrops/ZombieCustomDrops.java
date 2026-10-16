package com.poseidon.zombiecustomdrops;   // package: matches the mod group + modid

import com.poseidon.zombiecustomdrops.config.ZcdConfig;                    // our config spec + compiled rule snapshot
import net.minecraftforge.common.MinecraftForge;                            // the Forge GAME event bus (world/entity events)
import net.minecraftforge.fml.ModLoadingContext;                            // entry point used to register the config file
import net.minecraftforge.fml.common.Mod;                                   // @Mod annotation - marks this as the mod's main class
import net.minecraftforge.fml.config.ModConfig;                             // config scope enum (COMMON/SERVER/CLIENT)
import net.minecraftforge.fml.event.config.ModConfigEvent;                  // event fired when the TOML loads or is saved
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;          // access to this mod's own (mod-lifecycle) event bus

@Mod(ZombieCustomDrops.MODID)                                               // declares this class as the mod with id zombie_custom_drops
public class ZombieCustomDrops {

    public static final String MODID = "zombie_custom_drops";               // mod id constant - mods.toml must match this

    public ZombieCustomDrops() {                                            // constructor - runs once during mod construction
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ZcdConfig.SPEC);   // register our ForgeConfigSpec -> creates config/zombie_custom_drops-common.toml
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigLoad); // subscribe to config load/reload events on the MOD bus
        MinecraftForge.EVENT_BUS.register(DropHandler.class);               // subscribe our static event handler to the GAME bus (fires LivingDropsEvent etc.)
    }

    private void onConfigLoad(ModConfigEvent event) {                       // called when the TOML is first loaded AND on every later save/reload
        if (event.getConfig().getSpec() == ZcdConfig.SPEC) {                // ignore other mods' config events - only rebuild when OUR spec changed
            ZcdConfig.rebuild();                                            // re-parse the TOML strings into resolved Item/EntityType objects
        }
    }
}
