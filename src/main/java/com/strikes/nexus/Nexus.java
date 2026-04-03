package com.strikes.nexus;

import com.strikes.nexus.api.utils.LoggerUtils;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class Nexus extends JavaPlugin {

    @Getter
    private static Nexus instance;

    @Override
    public void onEnable() {
        instance = this;

        LoggerUtils.sendEnable(this);
    }

    @Override
    public void onDisable() {
        LoggerUtils.sendDisable(this);
    }
}
