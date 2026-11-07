package org.glstudio.nexus.modules.command;

import org.bukkit.Location;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandManagerTest {

    private final FakeCommandMap map = new FakeCommandMap();
    private final AtomicInteger refreshes = new AtomicInteger();
    private final CommandManager manager = new CommandManager(null, "GL-Test", () -> map, refreshes::incrementAndGet);

    @Test
    void registerAddsLabelAliasesAndNamespacedLabels() {
        assertTrue(manager.register(new TestCommand(manager, "home", List.of("h"))));

        assertTrue(map.getKnownCommands().containsKey("home"));
        assertTrue(map.getKnownCommands().containsKey("h"));
        assertTrue(map.getKnownCommands().containsKey("gl-test:home"));
        assertTrue(map.getKnownCommands().containsKey("gl-test:h"));
        assertSame(manager.getCommand("H"), manager.getCommand("home"));
    }

    @Test
    void unregisterAllRemovesEverythingThisManagerAdded() {
        manager.registerAll(new TestCommand(manager, "home", List.of("h")), new TestCommand(manager, "warp", List.of()));
        manager.unregisterAll();

        assertTrue(map.getKnownCommands().isEmpty());
        assertTrue(manager.getRegisteredCommands().isEmpty());
        assertTrue(manager.trackedLabels().isEmpty());
        assertEquals(1, refreshes.get());
    }

    @Test
    void anotherPluginsCommandWithTheSameLabelSurvives() {
        CommandManager other = new CommandManager(null, "Other", () -> map, () -> {
        });
        other.register(new TestCommand(other, "spawn", List.of()));
        org.bukkit.command.Command theirs = map.getCommand("spawn");

        assertTrue(manager.register(new TestCommand(manager, "spawn", List.of())));
        manager.unregisterAll();

        assertSame(theirs, map.getCommand("spawn"));
        assertTrue(map.getKnownCommands().containsKey("other:spawn"));
        assertFalse(map.getKnownCommands().containsKey("gl-test:spawn"));
    }

    @Test
    void unregisterOneCommandKeepsTheOthers() {
        manager.registerAll(new TestCommand(manager, "home", List.of("h")), new TestCommand(manager, "warp", List.of("w")));
        manager.unregister("HOME");

        assertNull(map.getCommand("home"));
        assertNull(map.getCommand("h"));
        assertTrue(map.getKnownCommands().containsKey("warp"));
        assertNull(manager.getCommand("h"));
        assertEquals(List.of("warp"), List.copyOf(manager.trackedLabels()));
    }

    @Test
    void messagesAndFormatArePerManager() {
        manager.textFormat(org.glstudio.nexus.utils.TextFormat.MINIMESSAGE)
                .messages("<red>nope</red>", "<red>players only</red>");
        assertEquals("<red>nope</red>", manager.getNoPermissionMessage());
        assertEquals("nope", org.glstudio.nexus.utils.CC.plain(manager.render(manager.getNoPermissionMessage())));
        manager.messages("  ", null);
        assertEquals("<red>nope</red>", manager.getNoPermissionMessage());
    }

    private static final class TestCommand extends Command {
        private final List<String> aliases;

        TestCommand(CommandManager manager, String name, List<String> aliases) {
            super(manager, name, "test." + name);
            this.aliases = aliases;
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }

        @Override
        public List<String> usage() {
            return List.of("/" + getName());
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
        }
    }

    /** Mirrors how Bukkit's SimpleCommandMap registers labels, aliases and namespaced labels. */
    private static final class FakeCommandMap implements CommandMap {
        private final Map<String, org.bukkit.command.Command> known = new HashMap<>();

        @Override
        public void registerAll(String fallbackPrefix, List<org.bukkit.command.Command> commands) {
            commands.forEach(command -> register(fallbackPrefix, command));
        }

        @Override
        public boolean register(String label, String fallbackPrefix, org.bukkit.command.Command command) {
            String name = label.toLowerCase(Locale.ROOT);
            String prefix = fallbackPrefix.toLowerCase(Locale.ROOT);
            boolean registered = known.putIfAbsent(name, command) == null;
            known.put(prefix + ":" + name, command);
            for (String alias : command.getAliases()) {
                String lower = alias.toLowerCase(Locale.ROOT);
                known.putIfAbsent(lower, command);
                known.put(prefix + ":" + lower, command);
            }
            command.register(this);
            return registered;
        }

        @Override
        public boolean register(String fallbackPrefix, org.bukkit.command.Command command) {
            return register(command.getName(), fallbackPrefix, command);
        }

        @Override
        public boolean dispatch(CommandSender sender, String cmdLine) {
            return false;
        }

        @Override
        public void clearCommands() {
            known.clear();
        }

        @Override
        public org.bukkit.command.Command getCommand(String name) {
            return known.get(name.toLowerCase(Locale.ROOT));
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String cmdLine) {
            return List.of();
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String cmdLine, Location location) {
            return List.of();
        }

        @Override
        public Map<String, org.bukkit.command.Command> getKnownCommands() {
            return known;
        }
    }
}
