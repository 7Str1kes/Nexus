package org.glstudio.nexus.storage;

import org.glstudio.nexus.utils.LoggerUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

/**
 * Idempotent, additive schema changes, safe to run on every startup.
 */
public final class Schema {

    private Schema() {
    }

    /**
     * Returns whether a table exists, checking the name as written and in upper case (H2 stores
     * unquoted names in upper case).
     *
     * @param connection the connection
     * @param table      the table name
     * @return {@code true} if found
     * @throws SQLException on database errors
     */
    public static boolean hasTable(Connection connection, String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        for (String candidate : new String[]{table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT)}) {
            try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, candidate, null)) {
                if (tables.next()) return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a table has a column, ignoring case.
     *
     * @param connection the connection
     * @param table      the table name
     * @param column     the column name
     * @return {@code true} if found
     * @throws SQLException on database errors
     */
    public static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        for (String candidate : new String[]{table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT)}) {
            try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null, candidate, null)) {
                while (columns.next()) {
                    if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds a column unless it exists.
     *
     * @param connection the connection
     * @param table      the table name
     * @param column     the column name
     * @param definition the column type and constraints, e.g. {@code VARCHAR(64) NULL}
     * @return {@code true} if the column was added
     * @throws SQLException on database errors
     */
    public static boolean addColumnIfMissing(Connection connection, String table, String column, String definition)
            throws SQLException {
        if (hasColumn(connection, table, column)) return false;
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
        LoggerUtils.logInfo("Migrated table '" + table + "': added column '" + column + "'.");
        return true;
    }

    /**
     * Creates an index, treating "already exists" as success. Works on engines without
     * {@code CREATE INDEX IF NOT EXISTS} (MySQL).
     *
     * @param connection the connection
     * @param index      the index name
     * @param table      the table name
     * @param columns    the indexed columns
     * @throws SQLException on any other database error
     */
    public static void createIndexIfMissing(Connection connection, String index, String table, String... columns)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX " + index + " ON " + table + " (" + String.join(", ", columns) + ")");
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (!message.contains("duplicate") && !message.contains("exists")) throw e;
        }
    }

    /**
     * A single-row version table ({@code id INT PRIMARY KEY, version INT, updated_at BIGINT}) recording
     * which data migrations have run.
     *
     * @param table   the table name, e.g. {@code schema_version}
     * @param dialect the dialect, for the upsert
     */
    public record VersionTable(String table, SqlDialect dialect) {

        /**
         * Creates the table if missing.
         *
         * @param connection the connection
         * @throws SQLException on database errors
         */
        public void ensure(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + table
                        + " (id INT PRIMARY KEY, version INT NOT NULL, updated_at BIGINT NOT NULL)");
            }
        }

        /**
         * Returns the recorded version.
         *
         * @param connection the connection
         * @return the version, {@code 0} if none was recorded
         * @throws SQLException on database errors
         */
        public int read(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT version FROM " + table + " WHERE id = 1")) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }

        /**
         * Records a version.
         *
         * @param connection the connection
         * @param version    the version reached
         * @throws SQLException on database errors
         */
        public void write(Connection connection, int version) throws SQLException {
            String sql = dialect.upsert(table, List.of("id"), List.of("id", "version", "updated_at"),
                    List.of("version", "updated_at"));
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, 1);
                statement.setInt(2, version);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }
    }
}
