package org.glstudio.nexus.action;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.glstudio.nexus.condition.Condition;
import org.glstudio.nexus.condition.Period;
import org.glstudio.nexus.hooks.economy.EconomyResult;
import org.glstudio.nexus.hooks.economy.VaultEconomy;
import org.glstudio.nexus.modules.menu.placeholder.Placeholders;
import org.glstudio.nexus.proxy.ProxyMessenger;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.DurationFormat;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.nexus.utils.TextFormat;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs lists of actions written as {@code [type] value}. Besides the types of Nexus ({@code console},
 * {@code player}, {@code message}, {@code broadcast}, {@code actionbar}, {@code title},
 * {@code sound}) it knows:
 * <ul>
 *   <li>{@code server <name>}: sends the player to another server of the proxy;</li>
 *   <li>{@code perm-command <permission>[,<permission>...] <command>}: a command as the player, with
 *       those permissions for that one command ({@code auto} is the command's own permission). The
 *       permissions only live in memory, so nothing is left behind if the server stops;</li>
 *   <li>{@code wait <ticks>}: runs the rest later;</li>
 *   <li>{@code random}: runs one of the actions after it, picked at random, and ends the list;</li>
 *   <li>{@code block-until-done}: the player cannot start the list again until it ends; read by
 *       whoever runs the list, see {@link #blocks(List)}.</li>
 * </ul>
 * And checks, which stop the list, sending the message after {@code |} when there is one:
 * <ul>
 *   <li>{@code permission <node>}: for players without the permission;</li>
 *   <li>{@code chance <percent>}: the rest of the time;</li>
 *   <li>{@code item <material> [amount]}: for players who do not carry that;</li>
 *   <li>{@code take <material> [amount]}: the same, and takes the items when they do;</li>
 *   <li>{@code if <a> <operator> <b>}: when the comparison does not hold (see {@link Condition});</li>
 *   <li>{@code limit <times> <period> [name]}: once the player used it that many times in the period
 *       (see {@link Period}); {@code {left}} in the message is the time until it starts again. The
 *       lists run with one scope share their count, unless named; a name is shared by every list
 *       that uses it;</li>
 *   <li>{@code cost <amount>}: for players who cannot pay, through Vault; it takes the money when
 *       they can. {@code {cost}} in the message is the amount.</li>
 * </ul>
 * Checks written one after another are a block: they are all checked first, and only when every one
 * passes are items taken, money paid and uses counted, so a player is never charged for something a
 * later check refuses. Values get {@code {player}}, {@code {uuid}} and the pairs given, then
 * PlaceholderAPI. Main thread only.
 */
public class ActionRunner {

    /** The types this runner handles itself, besides those of Nexus. */
    public static final Set<String> OWN = Set.of("server", "perm-command", "wait", "permission", "chance", "item", "take",
            "if", "limit", "cost", "random", "block-until-done");
    /** The checks: they stop the list, and run as a block when written one after another. */
    private static final Set<String> CHECKS = Set.of("permission", "chance", "item", "take", "if", "limit", "cost");
    /** A type with nothing after it, which Nexus reads as nothing at all. */
    private static final Pattern BARE = Pattern.compile("\\[([a-z][a-z-]*)]", Pattern.CASE_INSENSITIVE);

    private final JavaPlugin plugin;
    private final ActionRegistry registry = ActionRegistry.withDefaults(TextFormat.MINIMESSAGE);
    private final ProxyMessenger proxy;
    private final UsageLimits limits;
    private final BukkitTask saver;
    private final Map<String, ScopedAction> extra = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private ZoneId zone = ZoneId.systemDefault();

    /**
     * An action a plugin adds, which knows what list it runs in.
     */
    @FunctionalInterface
    public interface ScopedAction {
        /**
         * Runs the action.
         *
         * @param player the player
         * @param scope  what the list belongs to, such as {@code hologram:welcome}
         * @param value  the value, filled in; empty for a type written alone
         */
        void run(Player player, String scope, String value);
    }

    /**
     * Creates the runner, with the uses of {@code [limit]} kept in {@code data/limits.yml}, and opens
     * the channel to the proxy.
     *
     * @param plugin the plugin
     * @param files  where {@code data/limits.yml} is written, in order
     */
    public ActionRunner(JavaPlugin plugin, Executor files) {
        this(plugin, yamlLimits(plugin, files));
    }

    /**
     * Creates the runner, with the uses of {@code [limit]} kept where the plugin wants, and opens the
     * channel to the proxy.
     *
     * @param plugin the plugin
     * @param limits where the uses of {@code [limit]} are kept
     */
    public ActionRunner(JavaPlugin plugin, UsageLimits limits) {
        this.plugin = plugin;
        this.proxy = new ProxyMessenger(plugin);
        proxy.register();
        registry.register("server", proxy::connect);
        this.limits = limits;
        this.saver = Bukkit.getScheduler().runTaskTimer(plugin, limits::flush, 600L, 600L);
    }

    private static UsageLimits yamlLimits(JavaPlugin plugin, Executor files) {
        YamlUsageLimits limits = new YamlUsageLimits(new File(plugin.getDataFolder(), "data/limits.yml"), files);
        limits.load();
        return limits;
    }

    /**
     * Adds an action of this plugin's own.
     *
     * @param type   its type, lower case
     * @param action what it does
     */
    public void register(String type, ScopedAction action) {
        extra.put(type, action);
    }

    /**
     * Sets the time zone {@code [limit]} periods are counted in.
     *
     * @param zone the time zone
     */
    public void setZone(ZoneId zone) {
        this.zone = zone;
    }

    /**
     * Returns the uses of {@code [limit]}.
     *
     * @return the store
     */
    public UsageLimits limits() {
        return limits;
    }

    /**
     * Returns whether a type is known.
     *
     * @param type the type, any case
     * @return {@code true} if it can run
     */
    public boolean isKnown(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        return OWN.contains(lower) || extra.containsKey(lower) || registry.isKnown(lower);
    }

    /**
     * Returns whether a line is an action this runner can run.
     *
     * @param line the line as written, such as {@code [message] Hi}
     * @return {@code true} if it can run
     */
    public boolean accepts(String line) {
        Optional<ActionLine> parsed = parse(line);
        if (parsed.isEmpty() || !isKnown(parsed.get().type())) return false;
        String type = parsed.get().type();
        String value = parsed.get().value();
        return switch (type) {
            case "if" -> Condition.parse(value.split("\\|", 2)[0]).isPresent();
            case "limit" -> limit(value.split("\\|", 2)[0]) != null;
            case "perm-command" -> value.trim().split("\\s+", 2).length == 2;
            default -> true;
        };
    }

    /**
     * Returns whether a list holds {@code [block-until-done]}.
     *
     * @param lines the actions
     * @return {@code true} if the player must wait for it to end before starting it again
     */
    public static boolean blocks(List<String> lines) {
        for (String line : lines) {
            Optional<ActionLine> parsed = parse(line);
            if (parsed.isPresent() && parsed.get().type().equals("block-until-done")) return true;
        }
        return false;
    }

    /**
     * Runs a list for a player.
     *
     * @param player the player
     * @param lines  the actions
     * @param pairs  placeholder pairs
     * @return completes when the list ends, waits included, or stops
     */
    public CompletableFuture<Void> run(Player player, List<String> lines, Object... pairs) {
        return run(player, "", lines, pairs);
    }

    /**
     * Runs a list for a player.
     *
     * @param player the player
     * @param scope  what the list belongs to, such as {@code npc:guard}: an unnamed {@code [limit]}
     *               counts the uses of each scope apart
     * @param lines  the actions
     * @param pairs  placeholder pairs, such as {@code "{npc}", id}
     * @return completes when the list ends, waits included, or stops
     */
    public CompletableFuture<Void> run(Player player, String scope, List<String> lines, Object... pairs) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        run(player, scope, List.copyOf(lines), 0, pairs, done);
        return done;
    }

    /**
     * Writes the uses of {@code [limit]} and closes the channel to the proxy.
     */
    public void close() {
        saver.cancel();
        limits.flushNow();
        proxy.close();
    }

    private void run(Player player, String scope, List<String> lines, int from, Object[] pairs,
                     CompletableFuture<Void> done) {
        for (int index = from; index < lines.size(); index++) {
            Optional<ActionLine> parsed = parse(lines.get(index));
            if (parsed.isEmpty()) continue;
            ActionLine action = parsed.get();
            if (CHECKS.contains(action.type())) {
                index = checks(player, scope, lines, index, pairs);
                if (index < 0) {
                    done.complete(null);
                    return;
                }
                continue;
            }
            if (action.type().equals("random")) {
                List<String> rest = lines.subList(index + 1, lines.size());
                if (!rest.isEmpty()) {
                    run(player, scope, List.of(rest.get(ThreadLocalRandom.current().nextInt(rest.size()))), 0, pairs, done);
                    return;
                }
                break;
            }
            if (action.type().equals("block-until-done")) continue;
            String value = fill(player, action.value(), pairs);
            switch (action.type()) {
                case "wait" -> {
                    long ticks = Math.max(1L, Math.round(number(value, 20)));
                    int next = index + 1;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            run(player, scope, lines, next, pairs, done);
                        } else {
                            done.complete(null);
                        }
                    }, ticks);
                    return;
                }
                case "perm-command" -> runWithPermissions(player, value);
                default -> {
                    ScopedAction own = extra.get(action.type());
                    if (own != null) {
                        own.run(player, scope, value);
                    } else {
                        registry.run(player, new ActionLine(action.type(), value));
                    }
                }
            }
        }
        done.complete(null);
    }

    /**
     * Runs the block of checks starting at {@code from}: all are checked, then all take effect.
     *
     * @return the index of the block's last line, or {@code -1} if a check stopped the list
     */
    private int checks(Player player, String scope, List<String> lines, int from, Object[] pairs) {
        List<Check> block = new ArrayList<>();
        int index = from;
        for (; index < lines.size(); index++) {
            Optional<ActionLine> parsed = parse(lines.get(index));
            if (parsed.isEmpty() || !CHECKS.contains(parsed.get().type())) break;
            block.add(new Check(parsed.get().type(), parsed.get().value()));
        }
        long now = System.currentTimeMillis();
        for (Check check : block) {
            String failure = failure(player, scope, check, pairs, now);
            if (failure != null) {
                if (!failure.isBlank()) player.sendMessage(CC.miniMessage(failure));
                return -1;
            }
        }
        for (Check check : block) {
            String failure = apply(player, scope, check, pairs);
            if (failure != null) {
                if (!failure.isBlank()) player.sendMessage(CC.miniMessage(failure));
                return -1;
            }
        }
        return index - 1;
    }

    /** Whether a check fails: the message to send (blank for none), or {@code null} if it passes. */
    private @Nullable String failure(Player player, String scope, Check check, Object[] pairs, long now) {
        String[] parts = check.value().split("\\|", 2);
        String message = parts.length > 1 ? parts[1].trim() : "";
        String value = parts[0];
        switch (check.type()) {
            case "permission" -> {
                return player.hasPermission(fill(player, value, pairs).trim()) ? null : fill(player, message, pairs);
            }
            case "chance" -> {
                double percent = number(fill(player, value, pairs).replace("%", ""), 100);
                return ThreadLocalRandom.current().nextDouble(100) < percent ? null : fill(player, message, pairs);
            }
            case "item", "take" -> {
                return carries(player, fill(player, value, pairs)) ? null : fill(player, message, pairs);
            }
            case "if" -> {
                Optional<Condition> condition = Condition.parse(value);
                boolean holds = condition.isPresent() && condition.get().test(side -> fill(player, side, pairs));
                return holds ? null : fill(player, message, pairs);
            }
            case "limit" -> {
                Limit limit = limit(fill(player, value, pairs));
                if (limit == null) {
                    warnOnce("limit:" + value, "[limit] '" + value + "' is not <times> <period> [name]; the list stops.");
                    return "";
                }
                UsageLimits.Uses uses = limits.current(limit.key(scope), player.getUniqueId(), now);
                if (uses == null || uses.count() < limit.times()) return null;
                String left = uses.expires() == Long.MAX_VALUE ? "-"
                        : DurationFormat.compact(Duration.ofSeconds((uses.expires() - now + 999) / 1000));
                return fill(player, message.replace("{left}", left), pairs);
            }
            case "cost" -> {
                double amount = number(fill(player, value, pairs), -1);
                if (amount < 0) {
                    warnOnce("cost:" + value, "[cost] '" + value + "' is not an amount; the list stops.");
                    return "";
                }
                VaultEconomy economy = VaultEconomy.get();
                if (!economy.isAvailable()) {
                    warnOnce("cost:vault", "[cost] needs Vault and an economy plugin; lists with it stop.");
                    return "";
                }
                if (economy.has(player, amount)) return null;
                return fill(player, message.replace("{cost}", economy.format(amount)), pairs);
            }
            default -> {
                return null;
            }
        }
    }

    /** Makes a passed check take effect: the message if it cannot after all, or {@code null}. */
    private @Nullable String apply(Player player, String scope, Check check, Object[] pairs) {
        String[] parts = check.value().split("\\|", 2);
        String message = parts.length > 1 ? parts[1].trim() : "";
        switch (check.type()) {
            case "take" -> take(player, fill(player, parts[0], pairs));
            case "limit" -> {
                Limit limit = limit(fill(player, parts[0], pairs));
                if (limit != null) limits.use(limit.key(scope), player.getUniqueId(), limit.period(), ZonedDateTime.now(zone));
            }
            case "cost" -> {
                double amount = number(fill(player, parts[0], pairs), 0);
                EconomyResult paid = VaultEconomy.get().withdraw(player, amount);
                if (!paid.success()) return fill(player, message.replace("{cost}", VaultEconomy.get().format(amount)), pairs);
            }
            default -> {
                // Nothing to take.
            }
        }
        return null;
    }

    /** Whether the player carries the items of {@code <material> [amount]}. */
    private boolean carries(Player player, String spec) {
        ItemStack wanted = items(spec);
        return wanted != null && player.getInventory().containsAtLeast(new ItemStack(wanted.getType()), wanted.getAmount());
    }

    private void take(Player player, String spec) {
        ItemStack wanted = items(spec);
        if (wanted != null) player.getInventory().removeItem(wanted);
    }

    private @Nullable ItemStack items(String spec) {
        String[] words = spec.trim().split("\\s+");
        Material material = Material.matchMaterial(words[0]);
        if (material == null || !material.isItem()) {
            warnOnce("item:" + words[0], "[item] '" + words[0] + "' is not an item.");
            return null;
        }
        int amount = words.length > 1 ? (int) Math.max(1, number(words[1], 1)) : 1;
        return new ItemStack(material, amount);
    }

    /** {@code <times> <period> [name]}, or {@code null} if it is not that. */
    private static @Nullable Limit limit(String value) {
        String[] words = value.trim().split("\\s+");
        if (words.length < 2 || words.length > 3) return null;
        int times = (int) number(words[0], 0);
        Optional<Period> period = Period.parse(words[1]);
        if (times < 1 || period.isEmpty()) return null;
        // A dot would split the name in data/limits.yml.
        return new Limit(times, period.get(), words.length == 3 ? words[2].toLowerCase(Locale.ROOT).replace('.', '_') : "");
    }

    /**
     * Runs a command as the player with permissions only for that command. The attachment is never
     * written anywhere, so a crash mid-command leaves nothing behind.
     */
    private void runWithPermissions(Player player, String value) {
        String[] words = value.trim().split("\\s+", 2);
        if (words.length < 2) {
            warnOnce("perm-command:" + value, "[perm-command] '" + value + "' is not <permissions> <command>.");
            return;
        }
        String command = words[1].startsWith("/") ? words[1].substring(1) : words[1];
        Set<String> nodes = new LinkedHashSet<>();
        for (String node : words[0].split(",")) {
            if (node.equalsIgnoreCase("auto")) {
                nodes.addAll(commandPermissions(command));
            } else if (!node.isBlank()) {
                nodes.add(node.trim());
            }
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        try {
            nodes.forEach(node -> attachment.setPermission(node, true));
            player.performCommand(command);
        } finally {
            player.removeAttachment(attachment);
        }
    }

    private List<String> commandPermissions(String command) {
        String label = command.split("\\s+", 2)[0];
        Command known = Bukkit.getCommandMap().getCommand(label);
        if (known == null || known.getPermission() == null || known.getPermission().isBlank()) {
            warnOnce("auto:" + label, "[perm-command] auto: /" + label + " names no permission; write the ones it checks.");
            return List.of();
        }
        return List.of(known.getPermission().split(";"));
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) LoggerUtils.logWarn(message);
    }

    /** Reads a line, also a type written alone such as {@code [random]}. */
    private static Optional<ActionLine> parse(String line) {
        String trimmed = line.trim();
        Matcher bare = BARE.matcher(trimmed);
        if (bare.matches()) return Optional.of(new ActionLine(bare.group(1).toLowerCase(Locale.ROOT), ""));
        return ActionLine.parse(trimmed);
    }

    private static String fill(Player player, String text, Object[] pairs) {
        List<Object> all = new ArrayList<>(List.of(pairs));
        all.addAll(List.of("{player}", player.getName(), "{uuid}", player.getUniqueId()));
        return Placeholders.papi(player, Placeholders.apply(text, all.toArray()));
    }

    private static double number(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** A check as written: its type and its value with the message. */
    private record Check(String type, String value) {
    }

    /** A {@code [limit]}: how many uses in what period, and its name. */
    private record Limit(int times, Period period, String name) {

        /** Named limits are shared; unnamed ones belong to the scope the list runs in. */
        String key(String scope) {
            if (!name.isEmpty()) return name;
            return scope.isEmpty() ? "default" : scope;
        }
    }
}
