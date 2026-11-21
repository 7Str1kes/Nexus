package org.glstudio.nexus.condition;

import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A comparison such as {@code %vault_eco_balance% >= 100}, used by {@code [if]} actions and by
 * conditions written in config files. Each side is filled in (placeholders, {@code {player}})
 * separately, so a value with spaces or an operator inside does not change how it is read. Two
 * numbers compare as numbers; anything else compares as text, ignoring case. A side alone, with no
 * operator, is true when it reads {@code true}, {@code yes}, {@code on} or {@code 1}.
 *
 * @param left     the left side, as written
 * @param operator how they compare
 * @param right    the right side, as written; empty when there is no operator
 */
public record Condition(String left, Operator operator, String right) {

    // Spaces around the operator, so "a>=b" inside a value is not taken for one.
    private static final Pattern OPERATOR = Pattern.compile("\\s+(>=|<=|!=|==|=|>|<|!contains|contains)\\s+",
            Pattern.CASE_INSENSITIVE);

    /** How the two sides compare. */
    public enum Operator {
        /** {@code >=} */
        AT_LEAST,
        /** {@code <=} */
        AT_MOST,
        /** {@code >} */
        MORE,
        /** {@code <} */
        LESS,
        /** {@code ==} or {@code =} */
        EQUALS,
        /** {@code !=} */
        NOT_EQUALS,
        /** {@code contains} */
        CONTAINS,
        /** {@code !contains} */
        NOT_CONTAINS,
        /** No operator: the left side alone. */
        TRUTHY
    }

    /**
     * Reads a condition.
     *
     * @param raw the text, such as {@code %player_level% >= 10}
     * @return the condition, or empty if it is blank
     */
    public static Optional<Condition> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String text = raw.trim();
        Matcher matcher = OPERATOR.matcher(text);
        if (!matcher.find()) return Optional.of(new Condition(text, Operator.TRUTHY, ""));
        Operator operator = switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
            case ">=" -> Operator.AT_LEAST;
            case "<=" -> Operator.AT_MOST;
            case ">" -> Operator.MORE;
            case "<" -> Operator.LESS;
            case "!=" -> Operator.NOT_EQUALS;
            case "contains" -> Operator.CONTAINS;
            case "!contains" -> Operator.NOT_CONTAINS;
            default -> Operator.EQUALS;
        };
        return Optional.of(new Condition(text.substring(0, matcher.start()).trim(), operator,
                text.substring(matcher.end()).trim()));
    }

    /**
     * Tests the condition.
     *
     * @param fill fills in one side: placeholders and {@code {player}}
     * @return whether it holds
     */
    public boolean test(UnaryOperator<String> fill) {
        String a = fill.apply(left).trim();
        if (operator == Operator.TRUTHY) {
            return switch (a.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on", "1" -> true;
                default -> false;
            };
        }
        String b = fill.apply(right).trim();
        Double x = number(a);
        Double y = number(b);
        boolean numbers = x != null && y != null;
        return switch (operator) {
            case AT_LEAST -> numbers && x >= y;
            case AT_MOST -> numbers && x <= y;
            case MORE -> numbers && x > y;
            case LESS -> numbers && x < y;
            case EQUALS -> numbers ? x.doubleValue() == y.doubleValue() : a.equalsIgnoreCase(b);
            case NOT_EQUALS -> numbers ? x.doubleValue() != y.doubleValue() : !a.equalsIgnoreCase(b);
            case CONTAINS -> a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
            case NOT_CONTAINS -> !a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
            case TRUTHY -> false;
        };
    }

    /** A number as placeholders print it: {@code 1,234.5} or {@code 1234.5}. */
    static Double number(String text) {
        if (text.isEmpty()) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(text.replace(",", ""));
            } catch (NumberFormatException again) {
                return null;
            }
        }
    }
}
