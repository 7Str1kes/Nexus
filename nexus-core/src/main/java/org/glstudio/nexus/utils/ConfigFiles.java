package org.glstudio.nexus.utils;

import java.util.Collection;
import java.util.function.BiConsumer;

/**
 * What plugins do with all their config files at once: check their {@code config-version}, and make
 * sure every one reads before any is reloaded.
 */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    /**
     * Updates the {@code config-version} line of old files, warning in the console about each.
     *
     * @param files    the files
     * @param expected the version the plugin writes
     */
    public static void checkVersions(Collection<ConfigFile> files, int expected) {
        files.forEach(file -> file.checkVersion(expected, LoggerUtils::logWarn));
    }

    /**
     * Checks that every file reads, before a reload. The first one that does not is logged and
     * reported, and the rest are not checked.
     *
     * @param files    the files
     * @param reporter told about the file that does not read and its error, such as to message the
     *                 sender
     * @return {@code true} if all read and the reload may go on
     */
    public static boolean validateAll(Collection<ConfigFile> files, BiConsumer<ConfigFile, String> reporter) {
        for (ConfigFile file : files) {
            String error = file.validate();
            if (error != null) {
                LoggerUtils.logError(file.getFileName() + " was not reloaded: " + error);
                reporter.accept(file, error);
                return false;
            }
        }
        return true;
    }
}
