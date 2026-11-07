package org.glstudio.nexus.storage;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Opens MongoDB connections and checks they work before the plugin relies on them.
 */
public final class MongoConnector {

    private MongoConnector() {
    }

    /**
     * An open client and the database the plugin uses.
     *
     * @param client   the client; close it on shutdown
     * @param database the database
     */
    public record Handle(MongoClient client, MongoDatabase database) implements AutoCloseable {

        /**
         * Closes the client.
         */
        @Override
        public void close() {
            client.close();
        }
    }

    /**
     * Connects, pings the database and returns the handle.
     *
     * @param connectionString a {@code mongodb://} or {@code mongodb+srv://} URI
     * @param database         the database name
     * @param timeout          server selection and connect timeout
     * @return the handle
     * @throws StorageException if the server cannot be reached; the client is closed again
     */
    public static Handle connect(String connectionString, String database, Duration timeout) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout((int) timeout.toMillis(), TimeUnit.MILLISECONDS))
                .build();
        MongoClient client = MongoClients.create(settings);
        try {
            MongoDatabase db = client.getDatabase(database);
            db.runCommand(new Document("ping", 1));
            return new Handle(client, db);
        } catch (RuntimeException e) {
            client.close();
            throw new StorageException("Could not reach MongoDB database '" + database + "'", e);
        }
    }

    /**
     * Builds a connection string from parts, URL-encoding the credentials.
     *
     * @param host     the host
     * @param port     the port
     * @param username the user, blank for none
     * @param password the password
     * @param authDb   the authentication database, blank for {@code admin}
     * @return the URI
     */
    public static String uri(String host, int port, String username, String password, String authDb) {
        StringBuilder uri = new StringBuilder("mongodb://");
        if (username != null && !username.isBlank()) {
            uri.append(encode(username)).append(':').append(encode(password == null ? "" : password)).append('@');
        }
        uri.append(host).append(':').append(port).append('/');
        if (username != null && !username.isBlank()) {
            uri.append("?authSource=").append(authDb == null || authDb.isBlank() ? "admin" : encode(authDb));
        }
        return uri.toString();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
