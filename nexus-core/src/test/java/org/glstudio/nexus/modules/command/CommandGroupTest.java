package org.glstudio.nexus.modules.command;

import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandGroupTest {

    private static CommandSender senderWithEveryPermission() {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) ->
                        method.getName().equals("hasPermission") ? Boolean.TRUE : null);
    }

    private static final class Sub extends SubCommand<Void> {
        Sub(String name, String... aliases) {
            super(name, "", aliases);
        }

        @Override
        public void execute(CommandSender sender, Void context, String[] args) {
        }
    }

    private static final class Group extends CommandGroup<Void> {
        Group(CommandManager manager) {
            super(manager, "group", "");
            register(new Sub("alpha", "a"));
        }

        @Override
        public List<String> aliases() {
            return List.of();
        }

        @Override
        public List<String> usage() {
            return List.of();
        }

        @Override
        protected List<String> extraCompletions(CommandSender sender, Void context) {
            return List.of("help");
        }
    }

    private final CommandManager manager = new CommandManager(null, "test", () -> (CommandMap) null, () -> {
    });

    @Test
    void completesSubcommandNamesAndTheExtraOnes() {
        assertEquals(List.of("alpha", "help"), new Group(manager).onTabComplete(senderWithEveryPermission(), new String[]{""}));
    }

    @Test
    void extraCompletionsAreFilteredByWhatWasTyped() {
        assertEquals(List.of("help"), new Group(manager).onTabComplete(senderWithEveryPermission(), new String[]{"he"}));
    }

    @Test
    void aliasesStillDispatchButAreNotSuggestedByDefault() {
        Group group = new Group(manager);

        assertEquals("alpha", group.getSubCommand("a").getName());
    }
}
