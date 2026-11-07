package org.glstudio.nexus.utils;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;

/**
 * A sound read from configuration: key, volume and pitch.
 *
 * <p>Two configuration shapes are supported: an inline string ({@code block.note_block.pling:1:1.2},
 * volume and pitch optional) and a section with {@code enabled}, a sound key, {@code volume} and
 * {@code pitch}. The sound is kept as a key, so resource-pack sounds in their own namespace work too.
 * Pitch is clamped to 0.5-2, the range the client plays.
 *
 * @param key      the sound key, or {@code null} for no sound
 * @param volume   the volume, at least 0
 * @param pitch    the pitch, 0.5 to 2
 * @param category the mixer category
 */
public record SoundSpec(String key, float volume, float pitch, SoundCategory category) {

    /** No sound. */
    public static final SoundSpec NONE = new SoundSpec(null, 0f, 1f, SoundCategory.MASTER);

    /**
     * Creates a spec, clamping volume and pitch and defaulting the category to {@code MASTER}.
     *
     * @param key      the sound key, or {@code null}
     * @param volume   the volume
     * @param pitch    the pitch
     * @param category the category, {@code null} for {@code MASTER}
     */
    public SoundSpec {
        volume = Math.max(0f, volume);
        pitch = Math.max(0.5f, Math.min(2f, pitch));
        category = category == null ? SoundCategory.MASTER : category;
    }

    /**
     * Creates a spec in the {@code MASTER} category.
     *
     * @param key    the sound key
     * @param volume the volume
     * @param pitch  the pitch
     * @return the spec
     */
    public static SoundSpec of(String key, float volume, float pitch) {
        return new SoundSpec(key, volume, pitch, SoundCategory.MASTER);
    }

    /**
     * Parses {@code <sound>[:<volume>[:<pitch>]]}. The sound may be namespaced
     * ({@code minecraft:entity.player.levelup}) or enum style ({@code ENTITY_PLAYER_LEVELUP}).
     *
     * @param raw the configured value; {@code null}, blank or {@code NONE} give {@link #NONE}
     * @return the spec; an unknown sound keeps its normalised key and plays nothing on the client
     */
    public static SoundSpec parse(String raw) {
        return parse(raw, null, null);
    }

    /**
     * Parses like {@link #parse(String)}, recording a problem for an unknown sound or bad numbers.
     *
     * @param raw      the configured value
     * @param problems where to record problems, may be {@code null}
     * @param path     the key, used in problem messages
     * @return the spec
     */
    public static SoundSpec parse(String raw, ConfigProblems problems, String path) {
        if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("NONE")) return NONE;

        String[] parts = raw.trim().split(":");
        int numeric = 0;
        for (int i = parts.length - 1; i >= 1 && numeric < 2; i--) {
            if (!isNumber(parts[i])) break;
            numeric++;
        }
        int nameParts = parts.length - numeric;
        String name = String.join(":", Arrays.copyOfRange(parts, 0, nameParts));
        float volume = numeric >= 1 ? Float.parseFloat(parts[nameParts]) : 1f;
        float pitch = numeric == 2 ? Float.parseFloat(parts[nameParts + 1]) : 1f;
        if (name.isBlank()) return NONE;
        return of(resolveKey(name, problems, path), volume, pitch);
    }

    /**
     * Reads a section with {@code enabled} (default {@code true}), {@code soundKey}, {@code volume}
     * and {@code pitch}. Missing keys keep the fallback's values.
     *
     * @param section  the section, may be {@code null}
     * @param soundKey the key holding the sound name, e.g. {@code sound}
     * @param fallback used when the section is missing, and for missing keys
     * @param problems where to record problems, may be {@code null}
     * @param path     the section path, used in problem messages
     * @return the spec, {@link #NONE} if disabled
     */
    public static SoundSpec fromSection(ConfigurationSection section, String soundKey, SoundSpec fallback,
                                        ConfigProblems problems, String path) {
        if (section == null) return fallback;
        if (!section.getBoolean("enabled", true)) return NONE;
        String key = fallback.key();
        if (section.isSet(soundKey)) {
            String raw = section.getString(soundKey, "");
            key = raw.isBlank() || raw.equalsIgnoreCase("NONE") ? null : resolveKey(raw, problems, path + "." + soundKey);
        }
        float volume = (float) section.getDouble("volume", fallback.volume());
        float pitch = (float) section.getDouble("pitch", fallback.pitch());
        if (problems != null && section.isSet("pitch") && (pitch < 0.5f || pitch > 2f)) {
            problems.add(path + ".pitch is " + pitch + "; Minecraft only plays 0.5 to 2.0. Clamping.");
        }
        if (problems != null && volume < 0) problems.add(path + ".volume is negative; using 0.");
        return new SoundSpec(key, volume, pitch, fallback.category());
    }

    private static String resolveKey(String name, ConfigProblems problems, String path) {
        return Registries.soundKey(name).orElseGet(() -> {
            if (problems != null) {
                problems.add((path == null ? "sound" : path) + " is '" + name + "', which this server does not know; no sound will play.");
            }
            return name.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        });
    }

    private static boolean isNumber(String value) {
        try {
            Float.parseFloat(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Returns whether this spec plays nothing.
     *
     * @return {@code true} for no sound
     */
    public boolean isNone() {
        return key == null || key.isEmpty();
    }

    /**
     * Plays the sound to {@code player} only, at their location.
     *
     * @param player the listener, ignored if {@code null} or offline
     */
    public void play(Player player) {
        if (isNone() || player == null || !player.isOnline()) return;
        player.playSound(player.getLocation(), key, category, volume, pitch);
    }

    /**
     * Plays the sound to {@code player} only, at {@code location}.
     *
     * @param player   the listener
     * @param location where it comes from
     */
    public void play(Player player, Location location) {
        if (isNone() || player == null || !player.isOnline() || location == null) return;
        player.playSound(location, key, category, volume, pitch);
    }

    /**
     * Plays the sound at {@code location} for everyone nearby.
     *
     * @param location where it comes from
     */
    public void play(Location location) {
        if (isNone() || location == null) return;
        World world = location.getWorld();
        if (world != null) world.playSound(location, key, category, volume, pitch);
    }
}
