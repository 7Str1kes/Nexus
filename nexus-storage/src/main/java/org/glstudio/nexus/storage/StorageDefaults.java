package org.glstudio.nexus.storage;

/**
 * The names a plugin's {@code database.yml} falls back to.
 *
 * @param pluginName  the plugin's name, which names its connection pools
 * @param database    the MySQL, PostgreSQL and Mongo database, such as {@code gl_bounties}
 * @param file        the SQLite ({@code <file>.db}) and H2 file, such as {@code bounties}
 * @param tablePrefix put before every table and collection, such as {@code glbounties_}
 */
public record StorageDefaults(String pluginName, String database, String file, String tablePrefix) {

    /**
     * The usual names of a GL plugin: {@code gl_<name>}, {@code <name>} and {@code gl<name>_}.
     *
     * @param pluginName the plugin's name, such as {@code GL-Bounties}
     * @param name       the short name, such as {@code bounties}
     * @return the defaults
     */
    public static StorageDefaults of(String pluginName, String name) {
        return new StorageDefaults(pluginName, "gl_" + name, name, "gl" + name + "_");
    }
}
