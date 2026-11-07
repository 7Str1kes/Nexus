package org.glstudio.nexus.utils;

import java.sql.SQLException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Turns exceptions, especially the ones that come out of {@code CompletableFuture} chains, into
 * console output that says what actually failed.
 */
public final class Diagnostics {

    private static final int MAX_DEPTH = 10;

    private Diagnostics() {
    }

    /**
     * Strips the {@link CompletionException} and {@link ExecutionException} wrappers that futures
     * add around the real failure.
     *
     * @param error the failure as received from a future
     * @return the first throwable that is not one of those wrappers
     */
    public static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Follows the cause chain down to the original failure.
     *
     * @param error the failure
     * @return the deepest cause, at most {@value #MAX_DEPTH} levels down, guarding against cycles
     */
    public static Throwable rootCause(Throwable error) {
        Throwable current = unwrap(error);
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Throwable cause = current.getCause();
            if (cause == null || cause == current) return current;
            current = cause;
        }
        return current;
    }

    /**
     * Describes the root cause in a single line, suitable for a chat message or a status command.
     *
     * @param error the failure
     * @return the root cause message, or its class name if it has no message
     */
    public static String describeRootCause(Throwable error) {
        Throwable cause = rootCause(error);
        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) return cause.getClass().getSimpleName();
        return detail.replace('\n', ' ').replace('\r', ' ');
    }

    /**
     * Describes the whole cause chain in one line, e.g.
     * {@code "StorageException: save failed <- SQLException: timeout [SQLState=08S01, errorCode=0]"}.
     *
     * @param error the failure
     * @return the chain, outermost first
     */
    public static String describeChain(Throwable error) {
        Throwable current = unwrap(error);
        StringBuilder chain = new StringBuilder();
        for (int depth = 0; depth < MAX_DEPTH && current != null; depth++) {
            if (depth > 0) chain.append(" <- ");
            chain.append(describe(current));
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return chain.toString();
    }

    /**
     * Logs the cause chain in one line and the stack trace of the root cause.
     *
     * @param context where it happened, usually {@code "Class#method"}
     * @param error   the failure, possibly wrapped by a future
     */
    public static void logFailure(String context, Throwable error) {
        LoggerUtils.logError(context + ": " + describeChain(error));
        LoggerUtils.logException(context, rootCause(error));
    }

    private static String describe(Throwable error) {
        String message = error.getMessage() == null ? "(no message)" : error.getMessage();
        String label = error.getClass().getSimpleName() + ": " + message;
        if (error instanceof SQLException sql) {
            return label + " [SQLState=" + sql.getSQLState() + ", errorCode=" + sql.getErrorCode() + "]";
        }
        return label;
    }
}
