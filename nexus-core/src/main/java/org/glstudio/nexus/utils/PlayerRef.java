package org.glstudio.nexus.utils;

import java.util.Objects;
import java.util.UUID;

/**
 * A player identified by UUID, with the name last known for them.
 *
 * @param uuid the player's UUID
 * @param name the last known name
 */
public record PlayerRef(UUID uuid, String name) {

    /**
     * Creates a reference, rejecting {@code null}.
     *
     * @param uuid the player's UUID
     * @param name the last known name
     */
    public PlayerRef {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
    }
}
