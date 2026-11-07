package org.glstudio.nexus.storage;

import org.glstudio.nexus.utils.Diagnostics;

import java.util.Locale;

/**
 * Turns database connection failures into a sentence an administrator can act on.
 */
public final class ConnectionHints {

    private ConnectionHints() {
    }

    /**
     * Explains a failure to open or use a database connection.
     *
     * @param error   the failure
     * @param section the config path the settings come from, e.g. {@code mysql}
     * @return the hint, or {@code "See the cause below."} if nothing specific applies
     */
    public static String describe(Throwable error, String section) {
        String message = String.valueOf(Diagnostics.rootCause(error).getMessage()).toLowerCase(Locale.ROOT);
        String prefix = section == null || section.isBlank() ? "" : section + ".";

        if (message.contains("access denied") || message.contains("password authentication failed")) {
            return "Check " + prefix + "username and " + prefix + "password, and that the user is "
                    + "allowed to connect from this host.";
        }
        if (message.contains("unknown database") || (message.contains("database") && message.contains("does not exist"))) {
            return "The database named in " + prefix + "database does not exist. Create it first; the plugin "
                    + "creates its tables but not the database itself.";
        }
        if (message.contains("communications link failure") || message.contains("connection refused")
                || message.contains("could not create connection") || message.contains("connect timed out")) {
            return "Could not reach the server at " + prefix + "host:" + prefix + "port. Check it is running and "
                    + "reachable from here.";
        }
        if (message.contains("no suitable driver") || message.contains("classnotfound")) {
            return "The JDBC driver was not loaded. It is downloaded at startup via the libraries block in "
                    + "plugin.yml, which needs the server to have internet access on first run.";
        }
        if (message.contains("public key retrieval")) {
            return "MySQL 8 refused the connection over plain TCP. Set " + prefix + "useSSL to true, or allow "
                    + "public key retrieval on the server.";
        }
        if (message.contains("database is locked") || message.contains("file is locked")
                || message.contains("locked by another process")) {
            return "The database file is in use by another process. Stop the other server or program using it.";
        }
        if (message.contains("syntax") || message.contains("errorcode=1064")) {
            return "The server rejected one of the plugin's own statements, which points at an unusually old "
                    + "database version. Report this with the statement named below.";
        }
        return "See the cause below.";
    }
}
