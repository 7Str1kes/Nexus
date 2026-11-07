package org.glstudio.nexus.utils;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes YAML (or any text) so that a crash mid-write never leaves a truncated file behind.
 */
public final class YamlFiles {

    private YamlFiles() {
    }

    /**
     * Saves {@code config} to {@code target} atomically.
     *
     * @param config the configuration
     * @param target the destination
     * @throws IOException if the file cannot be written
     */
    public static void saveAtomically(YamlConfiguration config, File target) throws IOException {
        writeAtomically(target.toPath(), config.saveToString());
    }

    /**
     * Writes {@code content} to a temporary sibling of {@code target} and moves it into place. Falls
     * back to a plain replace on file systems without atomic moves.
     *
     * @param target  the destination
     * @param content the text, written as UTF-8
     * @throws IOException if the file cannot be written; the temporary file is removed
     */
    public static void writeAtomically(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
    }
}
