package org.glstudio.nexus.storage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

/**
 * Small JDBC helpers that close every resource and roll back failed transactions.
 */
public final class Jdbc {

    private Jdbc() {
    }

    /**
     * Binds parameters to a statement.
     */
    @FunctionalInterface
    public interface Binder {
        /**
         * Sets the parameters.
         *
         * @param statement the statement
         * @throws SQLException on database errors
         */
        void bind(PreparedStatement statement) throws SQLException;
    }

    /**
     * Reads a result set.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface Mapper<T> {
        /**
         * Maps the rows.
         *
         * @param rows the result set, positioned before the first row
         * @return the result
         * @throws SQLException on database errors
         */
        T map(ResultSet rows) throws SQLException;
    }

    /**
     * Work done inside a transaction.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface Work<T> {
        /**
         * Runs the work.
         *
         * @param connection the connection, with auto-commit off
         * @return the result
         * @throws SQLException on database errors
         */
        T apply(Connection connection) throws SQLException;
    }

    /**
     * Runs a query.
     *
     * @param dataSource the pool
     * @param sql        the statement
     * @param binder     sets the parameters, may be {@code null}
     * @param mapper     reads the rows
     * @param <T>        the result type
     * @return the mapped result
     * @throws SQLException on database errors
     */
    public static <T> T query(DataSource dataSource, String sql, Binder binder, Mapper<T> mapper) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return query(connection, sql, binder, mapper);
        }
    }

    /**
     * Runs a query on an existing connection.
     *
     * @param connection the connection
     * @param sql        the statement
     * @param binder     sets the parameters, may be {@code null}
     * @param mapper     reads the rows
     * @param <T>        the result type
     * @return the mapped result
     * @throws SQLException on database errors
     */
    public static <T> T query(Connection connection, String sql, Binder binder, Mapper<T> mapper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return mapper.map(rows);
            }
        }
    }

    /**
     * Runs an insert, update or delete.
     *
     * @param dataSource the pool
     * @param sql        the statement
     * @param binder     sets the parameters, may be {@code null}
     * @return the number of affected rows
     * @throws SQLException on database errors
     */
    public static int update(DataSource dataSource, String sql, Binder binder) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return update(connection, sql, binder);
        }
    }

    /**
     * Runs an insert, update or delete on an existing connection.
     *
     * @param connection the connection
     * @param sql        the statement
     * @param binder     sets the parameters, may be {@code null}
     * @return the number of affected rows
     * @throws SQLException on database errors
     */
    public static int update(Connection connection, String sql, Binder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) binder.bind(statement);
            return statement.executeUpdate();
        }
    }

    /**
     * Runs {@code work} in a transaction: committed if it returns, rolled back if it throws.
     *
     * @param dataSource the pool
     * @param work       the work
     * @param <T>        the result type
     * @return what the work returned
     * @throws SQLException on database errors, with a failed rollback attached as suppressed
     */
    public static <T> T inTransaction(DataSource dataSource, Work<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // The connection goes back to the pool either way; Hikari resets it.
                }
            }
        }
    }

    /**
     * Binds a UUID as its string form, or SQL {@code NULL}.
     *
     * @param statement the statement
     * @param index     the parameter index
     * @param value     the UUID, may be {@code null}
     * @throws SQLException on database errors
     */
    public static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.toString());
        }
    }

    /**
     * Reads a UUID stored as a string.
     *
     * @param rows   the result set
     * @param column the column
     * @return the UUID, or {@code null} for SQL {@code NULL} or a malformed value
     * @throws SQLException on database errors
     */
    public static UUID getUuid(ResultSet rows, String column) throws SQLException {
        String raw = rows.getString(column);
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
