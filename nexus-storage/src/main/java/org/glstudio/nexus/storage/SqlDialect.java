package org.glstudio.nexus.storage;

import java.util.List;
import java.util.StringJoiner;

/**
 * SQL that differs between engines. Only statement shapes live here; tables, columns and queries
 * stay in each plugin.
 */
public enum SqlDialect {

    /** MySQL: {@code INSERT ... ON DUPLICATE KEY UPDATE c = VALUES(c)}. */
    MYSQL,
    /** MariaDB: same syntax as MySQL. */
    MARIADB,
    /** PostgreSQL: {@code INSERT ... ON CONFLICT (k) DO UPDATE SET c = EXCLUDED.c}. */
    POSTGRESQL,
    /** SQLite: {@code ON CONFLICT} like PostgreSQL, plus {@code INSERT OR REPLACE}. */
    SQLITE,
    /** H2: {@code MERGE INTO ... KEY (k) VALUES (...)}. */
    H2;

    /**
     * Returns the dialect of a SQL backend.
     *
     * @param type the backend
     * @return the dialect
     * @throws IllegalArgumentException for YAML and Mongo
     */
    public static SqlDialect of(DatabaseType type) {
        return switch (type) {
            case MYSQL -> MYSQL;
            case MARIADB -> MARIADB;
            case POSTGRESQL -> POSTGRESQL;
            case SQLITE -> SQLITE;
            case H2 -> H2;
            default -> throw new IllegalArgumentException(type + " is not a SQL backend");
        };
    }

    /**
     * Builds an insert that updates {@code updateColumns} when a row with the same key exists. Bind
     * one parameter per entry of {@code columns}, in order.
     *
     * @param table         the table
     * @param keyColumns    the primary or unique key columns
     * @param columns       every inserted column, keys included
     * @param updateColumns the columns overwritten on conflict
     * @return the statement
     */
    public String upsert(String table, List<String> keyColumns, List<String> columns, List<String> updateColumns) {
        String insert = "INSERT INTO " + table + columnsAndPlaceholders(columns);
        return switch (this) {
            case MYSQL, MARIADB -> insert + " ON DUPLICATE KEY UPDATE " + assignments(updateColumns, "VALUES(", ")");
            case POSTGRESQL, SQLITE -> insert + " ON CONFLICT (" + String.join(", ", keyColumns) + ") DO UPDATE SET "
                    + assignments(updateColumns, "EXCLUDED.", "");
            case H2 -> "MERGE INTO " + table + " (" + String.join(", ", columns) + ") KEY ("
                    + String.join(", ", keyColumns) + ") VALUES (" + placeholders(columns.size()) + ")";
        };
    }

    /**
     * Builds an insert that replaces the whole row on a key conflict: {@code INSERT OR REPLACE} on
     * SQLite, {@code REPLACE INTO} on MySQL/MariaDB, {@code MERGE} on H2, and an upsert of every
     * non-key column on PostgreSQL.
     *
     * @param table      the table
     * @param keyColumns the primary or unique key columns
     * @param columns    every inserted column, keys included
     * @return the statement
     */
    public String replace(String table, List<String> keyColumns, List<String> columns) {
        return switch (this) {
            case SQLITE -> "INSERT OR REPLACE INTO " + table + columnsAndPlaceholders(columns);
            case MYSQL, MARIADB -> "REPLACE INTO " + table + columnsAndPlaceholders(columns);
            case H2 -> upsert(table, keyColumns, columns, columns);
            case POSTGRESQL -> upsert(table, keyColumns, columns,
                    columns.stream().filter(column -> !keyColumns.contains(column)).toList());
        };
    }

    /**
     * Returns {@code " (a, b, c) VALUES (?, ?, ?)"}.
     *
     * @param columns the columns
     * @return the fragment, with a leading space
     */
    public static String columnsAndPlaceholders(List<String> columns) {
        return " (" + String.join(", ", columns) + ") VALUES (" + placeholders(columns.size()) + ")";
    }

    /**
     * Returns {@code count} comma-separated question marks.
     *
     * @param count how many
     * @return the placeholders
     */
    public static String placeholders(int count) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < count; i++) joiner.add("?");
        return joiner.toString();
    }

    private static String assignments(List<String> columns, String prefix, String suffix) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String column : columns) joiner.add(column + " = " + prefix + column + suffix);
        return joiner.toString();
    }
}
