package org.glstudio.nexus.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {

    private HikariDataSource dataSource;

    @BeforeEach
    void open() throws SQLException {
        dataSource = DataSources.create("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "",
                PoolOptions.named("test").maximumPoolSize(2));
        Jdbc.update(dataSource, "CREATE TABLE homes (owner VARCHAR(36), name VARCHAR(32), world VARCHAR(64), "
                + "PRIMARY KEY (owner, name))", null);
    }

    @AfterEach
    void close() {
        dataSource.close();
    }

    @Test
    void typesParseAliasesAndKnowWhatIsShared() {
        assertEquals(Optional.of(DatabaseType.POSTGRESQL), DatabaseType.parse(" Postgres "));
        assertEquals(DatabaseType.YAML, DatabaseType.parse("nonsense", DatabaseType.YAML));
        assertTrue(DatabaseType.MONGO.isShared());
        assertFalse(DatabaseType.SQLITE.isShared());
        assertFalse(DatabaseType.MONGO.isSql());
    }

    @Test
    void dialectsBuildTheUpsertEachEngineExpects() {
        List<String> keys = List.of("owner", "name");
        List<String> columns = List.of("owner", "name", "world");
        assertEquals("INSERT INTO homes (owner, name, world) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE world = VALUES(world)",
                SqlDialect.MYSQL.upsert("homes", keys, columns, List.of("world")));
        assertEquals("INSERT INTO homes (owner, name, world) VALUES (?, ?, ?) ON CONFLICT (owner, name) DO UPDATE SET world = EXCLUDED.world",
                SqlDialect.SQLITE.upsert("homes", keys, columns, List.of("world")));
        assertEquals("MERGE INTO homes (owner, name, world) KEY (owner, name) VALUES (?, ?, ?)",
                SqlDialect.H2.upsert("homes", keys, columns, List.of("world")));
        assertEquals("INSERT OR REPLACE INTO homes (owner, name, world) VALUES (?, ?, ?)",
                SqlDialect.SQLITE.replace("homes", keys, columns));
        assertEquals(SqlDialect.H2, SqlDialect.of(DatabaseType.H2));
        assertThrows(IllegalArgumentException.class, () -> SqlDialect.of(DatabaseType.YAML));
    }

    @Test
    void h2UpsertInsertsThenUpdates() throws SQLException {
        String sql = SqlDialect.H2.upsert("homes", List.of("owner", "name"), List.of("owner", "name", "world"), List.of("world"));
        for (String world : List.of("world", "nether")) {
            Jdbc.update(dataSource, sql, statement -> {
                statement.setString(1, "u1");
                statement.setString(2, "base");
                statement.setString(3, world);
            });
        }
        assertEquals("nether", Jdbc.query(dataSource, "SELECT world FROM homes", null,
                rows -> rows.next() ? rows.getString(1) : null));
        assertEquals(1, (int) Jdbc.query(dataSource, "SELECT COUNT(*) FROM homes", null,
                rows -> rows.next() ? rows.getInt(1) : 0));
    }

    @Test
    void closeH2WritesTheDatabaseAndClosesThePool(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws SQLException {
        String url = DataSources.h2Url(folder.resolve("close-test").toFile(), "DB_CLOSE_DELAY=-1");
        HikariDataSource first = DataSources.create(url, "sa", "", PoolOptions.named("close-test").maximumPoolSize(1).driver("org.h2.Driver"));
        try (Connection connection = first.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t (v INT)");
            statement.execute("INSERT INTO t VALUES (7)");
        }

        DataSources.closeH2(first);
        assertTrue(first.isClosed());

        HikariDataSource second = DataSources.create(url, "sa", "", PoolOptions.named("close-test").maximumPoolSize(1).driver("org.h2.Driver"));
        try (Connection connection = second.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT v FROM t")) {
            assertTrue(result.next());
            assertEquals(7, result.getInt(1));
        } finally {
            second.close();
        }
    }

    @Test
    void schemaChangesAreIdempotent() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(Schema.hasTable(connection, "homes"));
            assertFalse(Schema.hasTable(connection, "warps"));
            assertTrue(Schema.addColumnIfMissing(connection, "homes", "server", "VARCHAR(64)"));
            assertFalse(Schema.addColumnIfMissing(connection, "homes", "server", "VARCHAR(64)"));
            assertTrue(Schema.hasColumn(connection, "homes", "SERVER"));
            Schema.createIndexIfMissing(connection, "idx_homes_server", "homes", "server");
            Schema.createIndexIfMissing(connection, "idx_homes_server", "homes", "server");

            Schema.VersionTable versions = new Schema.VersionTable("schema_version", SqlDialect.H2);
            versions.ensure(connection);
            assertEquals(0, versions.read(connection));
            versions.write(connection, 2);
            versions.write(connection, 3);
            assertEquals(3, versions.read(connection));
        }
    }

    @Test
    void failedTransactionsRollBack() throws SQLException {
        assertThrows(SQLException.class, () -> Jdbc.inTransaction(dataSource, connection -> {
            Jdbc.update(connection, "INSERT INTO homes VALUES ('u1', 'a', 'w')", null);
            Jdbc.update(connection, "INSERT INTO missing_table VALUES (1)", null);
            return null;
        }));
        assertNull(Jdbc.query(dataSource, "SELECT name FROM homes", null, rows -> rows.next() ? rows.getString(1) : null));
    }

    @Test
    void hintsExplainCommonConnectionFailures() {
        assertTrue(ConnectionHints.describe(new SQLException("Access denied for user"), "mysql").contains("mysql.username"));
        assertTrue(ConnectionHints.describe(new SQLException("Unknown database 'x'"), "mysql").contains("does not exist"));
        assertEquals("See the cause below.", ConnectionHints.describe(new SQLException("weird"), "mysql"));
    }

    @Test
    void uuidHelpersHandleNullAndMalformedValues() throws SQLException {
        Jdbc.update(dataSource, "CREATE TABLE ids (id VARCHAR(36))", null);
        UUID id = UUID.randomUUID();
        Jdbc.update(dataSource, "INSERT INTO ids VALUES (?)", statement -> Jdbc.setUuid(statement, 1, id));
        Jdbc.update(dataSource, "INSERT INTO ids VALUES (?)", statement -> Jdbc.setUuid(statement, 1, null));
        Jdbc.update(dataSource, "INSERT INTO ids VALUES ('not-a-uuid')", null);
        List<UUID> read = Jdbc.query(dataSource, "SELECT id FROM ids", null, rows -> {
            java.util.ArrayList<UUID> all = new java.util.ArrayList<>();
            while (rows.next()) all.add(Jdbc.getUuid(rows, "id"));
            return all;
        });
        assertEquals(id, read.get(0));
        assertNull(read.get(1));
        assertNull(read.get(2));
    }
}
