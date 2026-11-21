package org.glstudio.nexus.modules.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Helpers for commands: tab-completion filtering and joining arguments.
 */
public final class Completions {

    private Completions() {
    }

    /**
     * Keeps the options that start with what was typed, ignoring case.
     *
     * @param options the options
     * @param typed   what was typed so far, may be {@code null}
     * @return the matching options, in their order
     */
    public static List<String> filter(Collection<String> options, String typed) {
        if (typed == null || typed.isEmpty()) return List.copyOf(options);
        String lower = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    /**
     * Joins the arguments from one on, with spaces.
     *
     * @param args the arguments
     * @param from the first one to join
     * @return the text, empty when there are none
     */
    public static String join(String[] args, int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }
}
