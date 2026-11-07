package org.glstudio.nexus.storage;

import java.util.Locale;
import java.util.Optional;

/**
 * The storage backends a plugin can be configured with in {@code database.yml}.
 */
public enum DatabaseType {

    /** Flat YAML files in the data folder. */
    YAML("yaml"),
    /** A SQLite file in the data folder. */
    SQLITE("sqlite"),
    /** An H2 file in the data folder. */
    H2("h2"),
    /** A MySQL server. */
    MYSQL("mysql"),
    /** A MariaDB server. */
    MARIADB("mariadb"),
    /** A PostgreSQL server. */
    POSTGRESQL("postgresql"),
    /** A MongoDB server. */
    MONGO("mongo");

    private final String id;

    DatabaseType(String id) {
        this.id = id;
    }

    /**
     * Returns the name used in configuration files.
     *
     * @return the id, e.g. {@code mysql}
     */
    public String id() {
        return id;
    }

    /**
     * Returns whether several servers can share this backend. File-based backends live inside one
     * server's data folder.
     *
     * @return {@code true} for server-based backends
     */
    public boolean isShared() {
        return this == MYSQL || this == MARIADB || this == POSTGRESQL || this == MONGO;
    }

    /**
     * Returns whether this backend is reached through JDBC.
     *
     * @return {@code true} for SQL backends
     */
    public boolean isSql() {
        return this != YAML && this != MONGO;
    }

    /**
     * Parses a configured name, accepting common aliases ({@code postgres}, {@code mongodb},
     * {@code flatfile}...).
     *
     * @param raw the configured value
     * @return the type, or empty if unknown
     */
    public static Optional<DatabaseType> parse(String raw) {
        if (raw == null) return Optional.empty();
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "yaml", "yml", "flatfile", "file" -> Optional.of(YAML);
            case "sqlite" -> Optional.of(SQLITE);
            case "h2" -> Optional.of(H2);
            case "mysql" -> Optional.of(MYSQL);
            case "mariadb" -> Optional.of(MARIADB);
            case "postgresql", "postgres", "pgsql" -> Optional.of(POSTGRESQL);
            case "mongo", "mongodb" -> Optional.of(MONGO);
            default -> Optional.empty();
        };
    }

    /**
     * Parses a configured name, falling back for unknown values.
     *
     * @param raw      the configured value
     * @param fallback used when unknown
     * @return the type
     */
    public static DatabaseType parse(String raw, DatabaseType fallback) {
        return parse(raw).orElse(fallback);
    }
}
