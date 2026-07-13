package org.glstudio.nexus.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class EffectUtils {

    public static void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }

    public static void playSound(Player player, Sound sound, SoundCategory category, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, category, volume, pitch);
    }

    public static void playSound(Location location, Sound sound) {
        World world = location.getWorld();
        if (world != null) world.playSound(location, sound, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    public static void playSound(Location location, Sound sound, float volume, float pitch) {
        World world = location.getWorld();
        if (world != null) world.playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }

    public static void playSound(Location location, Sound sound, SoundCategory category, float volume, float pitch) {
        World world = location.getWorld();
        if (world != null) world.playSound(location, sound, category, volume, pitch);
    }

    public static void spawnParticle(Location location, Particle particle) {
        spawnParticle(location, particle, 1, 0, 0, 0, 0);
    }

    public static void spawnParticle(Location location, Particle particle, int count) {
        spawnParticle(location, particle, count, 0, 0, 0, 0);
    }

    public static void spawnParticle(Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(location, particle, count, offsetX, offsetY, offsetZ, 0);
    }

    public static void spawnParticle(Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        World world = location.getWorld();
        if (world != null) world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    public static void spawnParticle(Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        World world = location.getWorld();
        if (world != null) world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }

    public static SoundBuilder sound(Sound sound) {
        return new SoundBuilder(sound);
    }

    public static ParticleBuilder particle(Particle particle) {
        return new ParticleBuilder(particle);
    }

    public static class SoundBuilder {

        private final Sound sound;
        private SoundCategory category = SoundCategory.MASTER;
        private float volume = 1.0f;
        private float pitch = 1.0f;

        public SoundBuilder(Sound sound) {
            this.sound = sound;
        }

        public SoundBuilder category(SoundCategory category) {
            this.category = category;
            return this;
        }

        public SoundBuilder volume(float volume) {
            this.volume = volume;
            return this;
        }

        public SoundBuilder pitch(float pitch) {
            this.pitch = pitch;
            return this;
        }

        public void play(Player player) {
            player.playSound(player.getLocation(), sound, category, volume, pitch);
        }

        public void play(Location location) {
            World world = location.getWorld();
            if (world != null) world.playSound(location, sound, category, volume, pitch);
        }

        public void playAt(Player player) {
            World world = player.getWorld();
            world.playSound(player.getLocation(), sound, category, volume, pitch);
        }
    }

    public static class ParticleBuilder {

        private final Particle particle;
        private int count = 1;
        private double offsetX = 0;
        private double offsetY = 0;
        private double offsetZ = 0;
        private double extra = 0;
        private Object data = null;

        public ParticleBuilder(Particle particle) {
            this.particle = particle;
        }

        public ParticleBuilder count(int count) {
            this.count = count;
            return this;
        }

        public ParticleBuilder offset(double x, double y, double z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        public ParticleBuilder extra(double extra) {
            this.extra = extra;
            return this;
        }

        public ParticleBuilder data(Object data) {
            this.data = data;
            return this;
        }

        public void spawn(Location location) {
            World world = location.getWorld();
            if (world == null) return;
            if (data != null) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
            } else {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            }
        }

        public void spawn(Player player) {
            spawn(player.getLocation());
        }
    }
}