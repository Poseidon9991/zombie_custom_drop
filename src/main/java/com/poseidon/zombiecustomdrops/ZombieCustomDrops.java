package com.poseidon.zombiecustomdrops;   // package: matches the mod group + modid

import com.poseidon.zombiecustomdrops.config.ZcdConfig;                    // our config spec + compiled rule snapshot
import net.minecraftforge.common.MinecraftForge;                            // the Forge GAME event bus (world/entity events)
import net.minecraftforge.fml.ModLoadingContext;                            // entry point used to register the config file
import net.minecraftforge.fml.common.Mod;                                   // @Mod annotation - marks this as the mod's main class
import net.minecraftforge.fml.config.ModConfig;                             // config scope enum (COMMON/SERVER/CLIENT)
import net.minecraftforge.fml.event.config.ModConfigEvent;                  // event fired when the TOML loads or is saved
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;          // access to this mod's own (mod-lifecycle) event bus
import net.minecraftforge.fml.loading.FMLPaths;                             // resolves the config/ folder on disk

import java.io.IOException;                                                 // file-read/write failures while prettifying
import java.nio.file.Files;                                                 // read/write the TOML lines
import java.nio.file.Path;                                                  // path to config/zombie_custom_drops-common.toml
import java.util.ArrayList;                                                 // rebuilt line list while formatting
import java.util.List;                                                      // list type for file lines

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
            prettyPrintLists();                                             // Forge writes lists as [a,b] one-liners - rewrite them multiline
        }
    }

    // Forge's TOML serializer has no style option - it always emits
    // "key = [a, b, c]" inline. We post-process the file after load so the
    // generated config ships multiline like a hand-edited file. Rewriting
    // fires the file watcher -> this runs again -> finds nothing inline ->
    // skips the write -> converges after one extra parse.
    private static void prettyPrintLists() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(MODID + "-common.toml");   // our generated TOML on disk
        List<String> lines;
        try {
            lines = Files.readAllLines(file);                               // load the raw file text
        } catch (IOException e) {
            return;                                                         // unreadable/missing -> leave it alone
        }

        List<String> out = new ArrayList<>();                               // the rewritten file content
        boolean changed = false;                                            // did any line need reformatting?
        for (String line : lines) {                                         // walk the file line by line
            String expanded = expandInlineArray(line);                      // "k = [a, b]" -> multiline block or null
            if (expanded == null) {
                out.add(line);                                              // not an inline array -> keep as-is
            } else {
                out.addAll(List.of(expanded.split("\n", -1)));              // splice in the multiline form
                changed = true;                                             // file content changed
            }
        }
        if (!changed) return;                                               // nothing inline -> don't touch the file (breaks watcher loop)

        try {
            Files.write(file, out);                                         // write the prettified TOML back
        } catch (IOException e) {                                           // write failed -> the config still works inline
            org.apache.logging.log4j.LogManager.getLogger(MODID)
                    .warn("[zombie_custom_drops] could not pretty-print config: {}", e.getMessage());
        }
    }

    // "  key = ["a", "b"]" -> "  key = [\n      "a",\n      "b",\n  ]".
    // Returns null for non-array lines and empty [] lists (left inline).
    // Element splitting ignores commas inside "..." strings and {...} NBT.
    private static String expandInlineArray(String line) {
        int eq = line.indexOf('=');                                         // find the key/value split
        if (eq < 0) return null;                                            // section header / comment / blank line
        String value = line.substring(eq + 1).trim();                       // text after '='
        if (!value.startsWith("[") || !value.endsWith("]")) return null;    // not a single-line array
        String inner = value.substring(1, value.length() - 1).trim();       // strip the outer brackets
        if (inner.isEmpty()) return null;                                   // "key = []" stays inline

        List<String> elements = splitTopLevel(inner);                       // split on commas outside strings/braces
        if (elements.size() < 2) return null;                               // single-element or unsplittable -> keep inline

        String indent = line.substring(0, line.length() - line.stripLeading().length());  // preserve the key's indent
        String key = line.substring(0, eq + 1);                             // "  key =" prefix
        StringBuilder sb = new StringBuilder(key).append(" [");             // open the array on the key line
        for (String el : elements) {
            sb.append('\n').append(indent).append("    ").append(el.trim()).append(',');  // one element per line
        }
        sb.append('\n').append(indent).append(']');                         // closing bracket on its own line
        return sb.toString();                                               // the multiline block
    }

    // Splits "a, b, c" on commas that sit outside double quotes and {} braces,
    // so "minecraft:potion{Potion:\"x\",other:1}" stays one element.
    private static List<String> splitTopLevel(String inner) {
        List<String> parts = new ArrayList<>();                             // collected elements
        StringBuilder cur = new StringBuilder();                            // current element buffer
        boolean inString = false;                                           // inside a "..." string?
        int braces = 0;                                                     // {} nesting depth
        for (int i = 0; i < inner.length(); i++) {                          // walk each character
            char c = inner.charAt(i);
            if (c == '\\' && inString) {                                    // escaped char inside a string
                cur.append(c).append(inner.charAt(++i));                    // swallow it verbatim (handles \" and \\)
                continue;
            }
            if (c == '"') inString = !inString;                             // quote toggles string mode
            else if (!inString && c == '{') braces++;                       // enter NBT group
            else if (!inString && c == '}') braces--;                       // leave NBT group
            if (c == ',' && !inString && braces == 0) {                     // top-level comma -> element boundary
                parts.add(cur.toString());
                cur.setLength(0);                                           // start the next element
            } else {
                cur.append(c);                                              // ordinary char -> append
            }
        }
        if (!cur.isEmpty()) parts.add(cur.toString());                      // flush the trailing element
        return parts;
    }
}
