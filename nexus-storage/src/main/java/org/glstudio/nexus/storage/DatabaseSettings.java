package org.glstudio.nexus.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.glstudio.nexus.utils.ResolvedConfig;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What a plugin's {@code database.yml} says, and the connection it asks for: a SQL pool (SQLite, H2,
 * MySQL, MariaDB or PostgreSQL) or a Mongo database. Plugins build their repositories on top.
 *
 * <pre>
 * type: sqlite            # sqlite, h2, mysql, mariadb, postgresql or mongo
 * table-prefix: glbounties_
 * connect-timeout: 10     # seconds
 * sqlite: {file: bounties.db}
 * h2: {file: bounties}
 * mysql: {host, port, database, username, password, use-ssl, pool-size}   # also mariadb
 * postgresql: {host, port, database, username, password, pool-size}
 * mongo: {uri, database}
 * </pre>
 */
public final class DatabaseSettings {

    private final ResolvedConfig config;
    private final StorageDefaults defaults;
    private final DatabaseType type;

    private DatabaseSettings(ResolvedConfig config, StorageDefaults defaults, DatabaseType type) {
        this.config = config;
        this.defaults = defaults;
        this.type = type;
    }

    /**
     * Reads the settings. A type that is not supported, or {@code yaml}, falls back to SQLite.
     *
     * @param config   the resolved {@code database.yml}
     * @param defaults the plugin's names
     * @return the settings
     */
    public static DatabaseSettings read(ResolvedConfig config, StorageDefaults defaults) {
        DatabaseType type = DatabaseType.parse(config.getString("type", "sqlite"))
                .filter(parsed -> parsed != DatabaseType.YAML)
                .orElse(DatabaseType.SQLITE);
        return new DatabaseSettings(config, defaults, type);
    }

    /**
     * Warns when {@code type} names a database that is not supported, for a config validator.
     *
     * @param type what {@code database.yml} says
     * @param warn told about it
     */
    public static void warnIfUnsupported(String type, Consumer<String> warn) {
        if (DatabaseType.parse(type).filter(parsed -> parsed != DatabaseType.YAML).isEmpty()) {
            warn.accept("database.yml: type '" + type + "' is not supported. Use sqlite, h2, mysql, "
                    + "mariadb, postgresql or mongo. Falling back to sqlite.");
        }
    }

    /**
     * Returns the database to use.
     *
     * @return the type, never {@code YAML}
     */
    public DatabaseType type() {
        return type;
    }

    /**
     * Returns whether the data goes to Mongo.
     *
     * @return {@code true} for {@code mongo}
     */
    public boolean isMongo() {
        return type == DatabaseType.MONGO;
    }

    /**
     * Returns what is put before every table and collection.
     *
     * @return the prefix
     */
    public String tablePrefix() {
        return config.getString("table-prefix", defaults.tablePrefix());
    }

    /**
     * Returns how long connecting may take.
     *
     * @return 1 to 60 seconds
     */
    public Duration timeout() {
        return Duration.ofSeconds(Math.max(1, Math.min(60, config.getInt("connect-timeout", 10))));
    }

    /**
     * Opens the SQL pool. SQLite and H2 files go in {@code <dataFolder>/data}.
     *
     * @param dataFolder the plugin's folder
     * @return the pool
     * @throws SQLException if the data folder cannot be made or the pool cannot open
     * @throws IllegalStateException if the type is Mongo
     */
    public HikariDataSource openPool(File dataFolder) throws SQLException {
        File data = new File(dataFolder, "data");
        if (!data.exists() && !data.mkdirs()) throw new SQLException("Could not create " + data.getPath());
        PoolOptions options = PoolOptions.named(defaults.pluginName() + "-" + type.id());
        return switch (type) {
            case SQLITE -> DataSources.create(DataSources.sqliteUrl(new File(data,
                            config.getString("sqlite.file", defaults.file() + ".db"))), null, null,
                    options.maximumPoolSize(1).driver("org.sqlite.JDBC"), Map.of("journal_mode", "WAL"));
            case H2 -> DataSources.create(DataSources.h2Url(new File(data, config.getString("h2.file", defaults.file())),
                    "DB_CLOSE_DELAY=-1"), null, null, options.maximumPoolSize(4).driver("org.h2.Driver"));
            case MYSQL, MARIADB -> DataSources.create(DataSources.mysqlUrl(config.getString("mysql.host", "localhost"),
                            config.getInt("mysql.port", 3306), config.getString("mysql.database", defaults.database()),
                            config.getBoolean("mysql.use-ssl", true)),
                    config.getString("mysql.username", "root"), config.getString("mysql.password", ""),
                    options.maximumPoolSize(Math.max(2, config.getInt("mysql.pool-size", 6)))
                            .driver("com.mysql.cj.jdbc.Driver"));
            case POSTGRESQL -> DataSources.create(DataSources.postgresUrl(
                            config.getString("postgresql.host", "localhost"), config.getInt("postgresql.port", 5432),
                            config.getString("postgresql.database", defaults.database())),
                    config.getString("postgresql.username", "postgres"), config.getString("postgresql.password", ""),
                    options.maximumPoolSize(Math.max(2, config.getInt("postgresql.pool-size", 6)))
                            .driver("org.postgresql.Driver"));
            default -> throw new IllegalStateException(type + " is not a SQL backend");
        };
    }

    /**
     * Connects to Mongo.
     *
     * @return the connection
     */
    public MongoConnector.Handle connectMongo() {
        return MongoConnector.connect(config.getString("mongo.uri", "mongodb://localhost:27017"),
                config.getString("mongo.database", defaults.database()), timeout());
    }

    /**
     * Explains why the database could not start, with hints about its section of the file.
     *
     * @param cause what failed
     * @return the error to throw
     */
    public IllegalStateException failure(Exception cause) {
        String section = type == DatabaseType.MARIADB ? "mysql" : type.id();
        return new IllegalStateException("The " + type.id() + " database could not start. "
                + ConnectionHints.describe(cause, section), cause);
    }
}
