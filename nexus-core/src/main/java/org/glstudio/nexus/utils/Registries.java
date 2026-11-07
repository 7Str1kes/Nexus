package org.glstudio.nexus.utils;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves names written in configuration files against the server registries, accepting every
 * spelling administrators use: {@code minecraft:block.note_block.pling},
 * {@code block.note_block.pling}, {@code BLOCK_NOTE_BLOCK_PLING} or {@code block_note_block_pling}.
 *
 * <p>Lookups never throw; an unknown name gives an empty result.
 */
public final class Registries {

    private static final Map<Registry<?>, Map<String, Object>> SQUASHED = new ConcurrentHashMap<>();

    private Registries() {
    }

    /**
     * Finds a sound event.
     *
     * @param raw the configured name
     * @return the sound, or empty
     */
    public static Optional<Sound> sound(String raw) {
        return lookup(Registry.SOUND_EVENT, raw);
    }

    /**
     * Returns the key to play {@code raw} with. Known sounds resolve to their registry key; a key in a
     * namespace other than {@code minecraft} (a resource pack sound) is kept as written.
     *
     * @param raw the configured name
     * @return the key, e.g. {@code minecraft:entity.player.levelup}, or empty if unknown
     */
    public static Optional<String> soundKey(String raw) {
        Optional<Sound> sound = sound(raw);
        if (sound.isPresent()) return Optional.of(keyOf(sound.get()).toString());
        if (raw == null) return Optional.empty();
        NamespacedKey custom = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
        if (custom != null && !NamespacedKey.MINECRAFT.equals(custom.getNamespace()) && raw.indexOf(':') > 0) {
            return Optional.of(custom.toString());
        }
        return Optional.empty();
    }

    /**
     * Finds a particle type.
     *
     * @param raw the configured name
     * @return the particle, or empty
     */
    public static Optional<Particle> particle(String raw) {
        return lookup(Registry.PARTICLE_TYPE, raw);
    }

    /**
     * Finds a potion effect type.
     *
     * @param raw the configured name, e.g. {@code speed}
     * @return the effect, or empty
     */
    public static Optional<PotionEffectType> effect(String raw) {
        return lookup(Registry.MOB_EFFECT, raw);
    }

    /**
     * Finds an enchantment.
     *
     * @param raw the configured name, e.g. {@code sharpness}
     * @return the enchantment, or empty
     */
    public static Optional<Enchantment> enchantment(String raw) {
        return lookup(Registry.ENCHANTMENT, raw);
    }

    /**
     * Finds an entity type.
     *
     * @param raw the configured name, e.g. {@code zombie}
     * @return the type, or empty
     */
    public static Optional<EntityType> entityType(String raw) {
        return lookup(Registry.ENTITY_TYPE, raw);
    }

    /**
     * Finds an attribute.
     *
     * @param raw the configured name, e.g. {@code max_health} or {@code generic.max_health}
     * @return the attribute, or empty
     */
    public static Optional<Attribute> attribute(String raw) {
        Optional<Attribute> found = lookup(Registry.ATTRIBUTE, raw);
        if (found.isPresent() || raw == null) return found;
        // Pre-1.21.3 names carried a "generic." prefix that the registry dropped.
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("generic.") ? lookup(Registry.ATTRIBUTE, lower.substring(8)) : found;
    }

    /**
     * Finds a material, accepting {@code STONE}, {@code stone} and {@code minecraft:stone}.
     *
     * @param raw the configured name
     * @return the material, or empty
     */
    public static Optional<Material> material(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        return Optional.ofNullable(Material.matchMaterial(raw.trim().replace('-', '_')));
    }

    private static <T extends Keyed> Optional<T> lookup(Registry<T> registry, String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        try {
            NamespacedKey key = NamespacedKey.fromString(lower);
            if (key != null) {
                T direct = registry.get(key);
                if (direct != null) return Optional.of(direct);
            }
        } catch (IllegalArgumentException ignored) {
            // Not a valid key (e.g. upper-case enum style with odd characters): try the squashed form.
        }
        @SuppressWarnings("unchecked")
        T squashed = (T) squashedIndex(registry).get(squash(lower));
        return Optional.ofNullable(squashed);
    }

    private static <T extends Keyed> Map<String, Object> squashedIndex(Registry<T> registry) {
        return SQUASHED.computeIfAbsent(registry, ignored -> {
            Map<String, Object> index = new HashMap<>();
            for (T entry : registry) {
                NamespacedKey key = keyOf(entry);
                index.putIfAbsent(squash(key.getKey()), entry);
                index.putIfAbsent(squash(key.toString()), entry);
            }
            return index;
        });
    }

    @SuppressWarnings("deprecation")
    private static NamespacedKey keyOf(Keyed keyed) {
        return keyed.getKey();
    }

    private static String squash(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("minecraft:")) lower = lower.substring(10);
        return lower.replace("_", "").replace("-", "").replace(".", "").replace(":", "");
    }
}
