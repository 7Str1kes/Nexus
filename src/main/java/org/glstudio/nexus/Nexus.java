package org.glstudio.nexus;

import org.glstudio.nexus.utils.LoggerUtils;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class Nexus {

    @Getter
    private static JavaPlugin instance;

    public static void init(JavaPlugin plugin) {
        instance = plugin;
        LoggerUtils.sendEnable(plugin);
    }

    public static void shutdown(JavaPlugin plugin) {
        LoggerUtils.sendDisable(plugin);
        instance = null;
    }
}
