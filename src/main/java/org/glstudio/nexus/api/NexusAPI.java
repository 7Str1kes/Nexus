package org.glstudio.nexus.api;

import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.utils.*;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class NexusAPI {

    private static final NexusAPI instance = new NexusAPI();

    // Public utils
    private Tasks tasks;
    private DurationParser durationParser;
    private FormatUtils formatUtils;
    private LocationUtils locationUtils;
    private LoggerUtils loggerUtils;

    private NexusAPI() {
        this.initUtils();
    }

    private void initUtils() {
        this.tasks = new Tasks();
        this.durationParser = new DurationParser();
        this.formatUtils = new FormatUtils();
        this.locationUtils = new LocationUtils();
        this.loggerUtils = new LoggerUtils();
    }

    public CommandManager createCommandManager(JavaPlugin plugin) {
        return new CommandManager(plugin);
    }

    public static NexusAPI get() {
        return instance;
    }
}