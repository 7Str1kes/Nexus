package com.strikes.nexus.api;

import com.strikes.nexus.api.utils.*;
import lombok.Getter;

@Getter
public class NexusAPI {

    private static final NexusAPI instance = new NexusAPI();

    // Public utils
    private Tasks tasks;
    private ItemBuilder itemBuilder;
    private DurationParser durationParser;
    private FormatUtils formatUtils;
    private LocationUtils locationUtils;
    private LoggerUtils loggerUtils;
    private TimeUtils timeUtils;

    private NexusAPI() {
        this.initUtils();
    }

    private void initUtils() {
        this.tasks = new Tasks();
        this.durationParser = new DurationParser();
        this.formatUtils = new FormatUtils();
        this.locationUtils = new LocationUtils();
        this.loggerUtils = new LoggerUtils();
        this.timeUtils = new TimeUtils();
    }

    public static NexusAPI get() {
        return instance;
    }
}
