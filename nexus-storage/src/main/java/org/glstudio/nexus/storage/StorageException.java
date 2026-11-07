package org.glstudio.nexus.storage;

/**
 * Unchecked wrapper for a storage failure, so it can travel through a {@code CompletableFuture}.
 */
public class StorageException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what failed
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * Creates the exception.
     *
     * @param message what failed
     * @param cause   the underlying failure
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
