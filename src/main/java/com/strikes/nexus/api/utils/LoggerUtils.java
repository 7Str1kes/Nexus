package com.strikes.nexus.api.utils;

import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class LoggerUtils {

    private static final String LINE = "&#8b5cf6&m                                   ";
    private static final String PREFIX = "&#8b5cf6[&#c084fcNexus&#8b5cf6] ";

    private static ConsoleCommandSender console = Bukkit.getConsoleSender();

    @Setter
    private static boolean debugEnabled = false;

    private static void send(String message) {
        if (console == null) console = Bukkit.getConsoleSender();
        try {
            console.sendMessage(CC.t(message));
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Nexus] Failed to send colored message: " + e.getMessage());
        }
    }

    public static void log(String message) {
        send(PREFIX + "&#a78bfa" + message);
    }

    public static void logInfo(String message) {
        send(PREFIX + "&#c084fc[INFO] &#e0b0ff" + message);
    }

    public static void logWarn(String message) {
        send(PREFIX + "&#fbbf24[WARN] &#fde68a" + message);
    }

    public static void logError(String message) {
        send(PREFIX + "&#ef4444[ERROR] &#fca5a5" + message);
    }

    public static void logSuccess(String message) {
        send(PREFIX + "&#10b981[SUCCESS] &#86efac" + message);
    }

    public static void logDebug(String category, String message) {
        if (!debugEnabled) return;
        send(PREFIX + "&#3b82f6[DEBUG] [" + category + "] &#93c5fd" + message);
    }

    public static void logException(String context, Throwable t) {
        logError("Exception in [" + context + "]: " + t.getMessage());
        for (StackTraceElement el : t.getStackTrace()) {
            send(PREFIX + "&#ef4444  at &#fca5a5" + el.toString());
        }
    }

    private static void sendPluginMessage(JavaPlugin plugin, String statusLine) {
        String authors = String.join(", ", plugin.getDescription().getAuthors());
        try {
            log(LINE);
            log("");
            log("&#c084fc&l" + plugin.getName());
            log("");
            log("&#a78bfa» &#f0e8ffAuthor: &#c084fc" + authors);
            log("&#a78bfa» &#f0e8ffVersion: &#c084fc" + plugin.getDescription().getVersion());
            log("");
            log(statusLine);
            log(LINE);
        } catch (Exception e) {
            logException("LoggerUtils#sendPluginMessage", e);
        }
    }

    public static void sendEnable(JavaPlugin plugin) {
        sendPluginMessage(plugin, "&#10b981&lPlugin enabled successfully.");
    }

    public static void sendDisable(JavaPlugin plugin) {
        sendPluginMessage(plugin, "&#fd7988&lPlugin disabled successfully.");
    }
}