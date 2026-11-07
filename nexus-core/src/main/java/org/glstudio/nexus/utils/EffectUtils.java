package org.glstudio.nexus.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Sound and particle shortcuts. For values read from configuration, use {@link SoundSpec} and
 * {@link ParticleSpec}.
 */
public class EffectUtils {
    /**
     * Plays a sound to a player at full volume and normal pitch.
     *
     * @param player the player
     * @param sound  the sound
     */
    public static void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, 1.0f);
    }
    /**
     * Plays a sound to a player.
     *
     * @param player the player
     * @param sound  the sound
     * @param volume the volume
     * @param pitch  the pitch
     */
    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }
    /**
     * Plays a sound to a player in a mixer category.
     *
     * @param player   the player
     * @param sound    the sound
     * @param category the category
     * @param volume   the volume
     * @param pitch    the pitch
     */
    public static void playSound(Player player, Sound sound, SoundCategory category, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, category, volume, pitch);
    }
    /**
     * Plays a sound at a location for everyone nearby.
     *
     * @param location the location
     * @param sound    the sound
     */
    public static void playSound(Location location, Sound sound) {
        World world = location.getWorld();
        if (world != null) world.playSound(location, sound, SoundCategory.MASTER, 1.0f, 1.0f);
    }
    /**
     * Plays a sound at a location for everyone nearby.
     *
     * @param location the location
     * @param sound    the sound
     * @param volume   the volume
     * @param pitch    the pitch
     */
    public static void playSound(Location location, Sound sound, float volume, float pitch) {
        World world = location.getWorld();
        if (world != null) world.playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }
    /**
     * Plays a sound at a location in a mixer category.
     *
     * @param location the location
     * @param sound    the sound
     * @param category the category
     * @param volume   the volume
     * @param pitch    the pitch
     */
    public static void playSound(Location location, Sound sound, SoundCategory category, float volume, float pitch) {
        World world = location.getWorld();
        if (world != null) world.playSound(location, sound, category, volume, pitch);
    }
    /**
     * Spawns one particle.
     *
     * @param location where
     * @param particle the particle
     */
    public static void spawnParticle(Location location, Particle particle) {
        spawnParticle(location, particle, 1, 0, 0, 0, 0);
    }
    /**
     * Spawns particles without spread.
     *
     * @param location where
     * @param particle the particle
     * @param count    how many
     */
    public static void spawnParticle(Location location, Particle particle, int count) {
        spawnParticle(location, particle, count, 0, 0, 0, 0);
    }
    /**
     * Spawns particles with a spread.
     *
     * @param location where
     * @param particle the particle
     * @param count    how many
     * @param offsetX  spread on X
     * @param offsetY  spread on Y
     * @param offsetZ  spread on Z
     */
    public static void spawnParticle(Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(location, particle, count, offsetX, offsetY, offsetZ, 0);
    }
    /**
     * Spawns particles with a spread and speed.
     *
     * @param location where
     * @param particle the particle
     * @param count    how many
     * @param offsetX  spread on X
     * @param offsetY  spread on Y
     * @param offsetZ  spread on Z
     * @param extra    speed
     */
    public static void spawnParticle(Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        World world = location.getWorld();
        if (world != null) world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
    }
    /**
     * Spawns particles that need data (colour, block...).
     *
     * @param location where
     * @param particle the particle
     * @param count    how many
     * @param offsetX  spread on X
     * @param offsetY  spread on Y
     * @param offsetZ  spread on Z
     * @param extra    speed
     * @param data     the particle data
     */
    public static void spawnParticle(Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        World world = location.getWorld();
        if (world != null) world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }
    /**
     * Starts a sound builder.
     *
     * @param sound the sound
     * @return the builder
     */
    public static SoundBuilder sound(Sound sound) {
        return new SoundBuilder(sound);
    }
    /**
     * Starts a particle builder.
     *
     * @param particle the particle
     * @return the builder
     */
    public static ParticleBuilder particle(Particle particle) {
        return new ParticleBuilder(particle);
    }
    /**
     * Fluent sound playback.
     */
    public static class SoundBuilder {

        private final Sound sound;
        private SoundCategory category = SoundCategory.MASTER;
        private float volume = 1.0f;
        private float pitch = 1.0f;
        /**
         * Starts with full volume, normal pitch, {@code MASTER} category.
         *
         * @param sound the sound
         */
        public SoundBuilder(Sound sound) {
            this.sound = sound;
        }
        /**
         * Sets the mixer category.
         *
         * @param category the category
         * @return this builder
         */
        public SoundBuilder category(SoundCategory category) {
            this.category = category;
            return this;
        }
        /**
         * Sets the volume.
         *
         * @param volume the volume
         * @return this builder
         */
        public SoundBuilder volume(float volume) {
            this.volume = volume;
            return this;
        }
        /**
         * Sets the pitch.
         *
         * @param pitch the pitch
         * @return this builder
         */
        public SoundBuilder pitch(float pitch) {
            this.pitch = pitch;
            return this;
        }
        /**
         * Plays the sound to one player at their location.
         *
         * @param player the player
         */
        public void play(Player player) {
            player.playSound(player.getLocation(), sound, category, volume, pitch);
        }
        /**
         * Plays the sound at a location for everyone nearby.
         *
         * @param location the location
         */
        public void play(Location location) {
            World world = location.getWorld();
            if (world != null) world.playSound(location, sound, category, volume, pitch);
        }
        /**
         * Plays the sound at a player's location for everyone nearby.
         *
         * @param player the player
         */
        public void playAt(Player player) {
            World world = player.getWorld();
            world.playSound(player.getLocation(), sound, category, volume, pitch);
        }
    }
    /**
     * Fluent particle spawning.
     */
    public static class ParticleBuilder {

        private final Particle particle;
        private int count = 1;
        private double offsetX = 0;
        private double offsetY = 0;
        private double offsetZ = 0;
        private double extra = 0;
        private Object data = null;
        /**
         * Starts with one particle, no spread, no speed.
         *
         * @param particle the particle
         */
        public ParticleBuilder(Particle particle) {
            this.particle = particle;
        }
        /**
         * Sets how many.
         *
         * @param count the count
         * @return this builder
         */
        public ParticleBuilder count(int count) {
            this.count = count;
            return this;
        }
        /**
         * Sets the spread.
         *
         * @param x spread on X
         * @param y spread on Y
         * @param z spread on Z
         * @return this builder
         */
        public ParticleBuilder offset(double x, double y, double z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }
        /**
         * Sets the speed.
         *
         * @param extra the speed
         * @return this builder
         */
        public ParticleBuilder extra(double extra) {
            this.extra = extra;
            return this;
        }
        /**
         * Sets the particle data.
         *
         * @param data the data
         * @return this builder
         */
        public ParticleBuilder data(Object data) {
            this.data = data;
            return this;
        }
        /**
         * Spawns at a location.
         *
         * @param location the location
         */
        public void spawn(Location location) {
            World world = location.getWorld();
            if (world == null) return;
            if (data != null) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
            } else {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            }
        }
        /**
         * Spawns at a player's location.
         *
         * @param player the player
         */
        public void spawn(Player player) {
            spawn(player.getLocation());
        }
    }
}