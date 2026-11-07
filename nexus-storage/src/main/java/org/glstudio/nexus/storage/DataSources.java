package org.glstudio.nexus.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Creates HikariCP pools and the JDBC URLs the GL plugins use.
 *
 * <p>Drivers are not bundled: each plugin loads the ones it needs through {@code libraries:} in its
 * {@code plugin.yml}.
 */
public final class DataSources {

    private static final int H2_DATABASE_ALREADY_CLOSED = 90121;

    private DataSources() {
    }

    /**
     * Creates a pool and checks that a connection can be opened.
     *
     * @param jdbcUrl  the JDBC URL
     * @param username the user, {@code null} for none
     * @param password the password, {@code null} for none
     * @param options  pool settings
     * @return the pool
     * @throws SQLException if no valid connection can be opened; the pool is closed again
     */
    public static HikariDataSource create(String jdbcUrl, String username, String password, PoolOptions options)
            throws SQLException {
        return create(jdbcUrl, username, password, options, Map.of());
    }

    /**
     * Opens and validates a pool, passing extra properties to the driver.
     *
     * @param jdbcUrl    the JDBC URL
     * @param username   the user, {@code null} for none
     * @param password   the password, {@code null} for none
     * @param options    the pool options
     * @param properties driver properties, e.g. {@code cachePrepStmts}
     * @return the open pool
     * @throws SQLException if the database cannot be reached
     */
    public static HikariDataSource create(String jdbcUrl, String username, String password, PoolOptions options,
                                          Map<String, String> properties) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        if (username != null) config.setUsername(username);
        if (password != null) config.setPassword(password);
        if (options.driverClassName() != null) config.setDriverClassName(options.driverClassName());
        config.setPoolName(options.poolName());
        config.setMaximumPoolSize(options.maximumPoolSize());
        if (options.minimumIdle() >= 0) config.setMinimumIdle(options.minimumIdle());
        config.setConnectionTimeout(options.connectionTimeout());
        // A fixed-size pool never retires a connection, and Hikari warns if it is given a timeout anyway.
        boolean fixedSize = options.minimumIdle() >= options.maximumPoolSize();
        if (options.idleTimeout() >= 0 && !fixedSize) config.setIdleTimeout(options.idleTimeout());
        config.setMaxLifetime(options.maxLifetime());
        if (options.keepaliveTime() >= 0) config.setKeepaliveTime(options.keepaliveTime());
        if (options.leakDetection() > 0) config.setLeakDetectionThreshold(options.leakDetection());
        properties.forEach(config::addDataSourceProperty);

        HikariDataSource dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) throw new SQLException("The database connection could not be validated.");
        } catch (SQLException | RuntimeException e) {
            dataSource.close();
            throw e;
        }
        return dataSource;
    }

    /**
     * Builds a MySQL URL with UTF-8 and, without SSL, public key retrieval (MySQL 8 refuses plain
     * connections otherwise).
     *
     * @param host     the host
     * @param port     the port
     * @param database the database name
     * @param useSsl   whether to use SSL
     * @return the URL
     */
    public static String mysqlUrl(String host, int port, String database, boolean useSsl) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&allowPublicKeyRetrieval=" + !useSsl
                + "&characterEncoding=UTF-8"
                + "&useUnicode=true";
    }

    /**
     * Builds a MariaDB URL.
     *
     * @param host     the host
     * @param port     the port
     * @param database the database name
     * @param useSsl   whether to use SSL
     * @return the URL
     */
    public static String mariadbUrl(String host, int port, String database, boolean useSsl) {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSsl=" + useSsl;
    }

    /**
     * Builds a PostgreSQL URL.
     *
     * @param host     the host
     * @param port     the port
     * @param database the database name
     * @return the URL
     */
    public static String postgresUrl(String host, int port, String database) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    /**
     * Builds a SQLite URL for a file.
     *
     * @param file the database file
     * @return the URL
     */
    public static String sqliteUrl(File file) {
        return "jdbc:sqlite:" + file.getAbsolutePath();
    }

    /**
     * Closes a pool on an H2 database opened with {@code DB_CLOSE_DELAY=-1}. Such a database outlives
     * its pool and only H2's exit hook would close it, once the plugin's class loader is gone, which
     * can lose the latest changes. {@code SHUTDOWN} writes it to disk first.
     *
     * @param dataSource the pool, closed on return
     * @throws SQLException if the database refused to shut down; the pool is closed anyway
     */
    public static void closeH2(HikariDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException e) {
            // The connection dies with the database, so closing it reports exactly this.
            if (e.getErrorCode() != H2_DATABASE_ALREADY_CLOSED) throw e;
        } finally {
            dataSource.close();
        }
    }

    /**
     * Builds an H2 file URL.
     *
     * @param file    the database file without the {@code .mv.db} suffix
     * @param options extra settings appended after a semicolon (e.g. {@code MODE=MySQL}), may be empty
     * @return the URL
     */
    public static String h2Url(File file, String options) {
        String base = "jdbc:h2:file:" + file.getAbsolutePath();
        return options == null || options.isBlank() ? base : base + ";" + options;
    }
}
