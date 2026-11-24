package org.glstudio.nexus.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Centres chat lines by putting spaces in front of them, measuring each character in pixels of the
 * default Minecraft font.
 *
 * <p>Lines may mix both formats the plugins use: legacy {@code &} codes (with {@code &#rrggbb} hex)
 * and MiniMessage tags. They are measured as {@link CC#miniMessage(String)} shows them: tags take no
 * room, nor does what they hold (a hover text, a URL), and bold text, from {@code <bold>},
 * {@code <b>} or {@code &l} until {@code </bold>}, {@code </b>}, {@code <reset>}, {@code <r>} or
 * {@code &r}, is one pixel wider per character. Every character is followed by one pixel of space;
 * characters the table does not know count as 4 pixels.
 *
 * <p>The spaces go in front of the line as written, so the result is parsed as usual afterwards.
 * Fill in placeholders before centring, so the line is measured as it will be shown.
 */
public final class CenteredText {

    /** Where lines are centred: half the width, in pixels, of the chat box with default settings. */
    public static final int CHAT_CENTER = 154;

    private static final int DEFAULT_WIDTH = 4;
    private static final int SPACE_WIDTH = 3;
    private static final int[] WIDTHS = new int[128];

    static {
        Arrays.fill(WIDTHS, DEFAULT_WIDTH);
        set("abcdeghjmnopqrsuvwxyzABCDEFGHJKLMNOPQRSTUVWXYZ0123456789#$%^&*-_+=?/\\~", 5);
        set("fkt<>(){}", 4);
        set("I[]\"", 3);
        set("`", 2);
        set("il!:;'|.,", 1);
        set("@", 6);
        set(" ", SPACE_WIDTH);
    }

    private CenteredText() {
    }

    private static void set(String characters, int width) {
        for (char c : characters.toCharArray()) WIDTHS[c] = width;
    }

    /**
     * Centres a line in the chat.
     *
     * @param text the line, may be {@code null}
     * @return the line with the spaces that centre it in front; empty for {@code null}, and a line
     * already wider than half the chat unchanged
     */
    public static String center(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        int missing = CHAT_CENTER - width(text) / 2;
        int spaceWithGap = SPACE_WIDTH + 1;
        int spaces = 0;
        for (int filled = 0; filled < missing; filled += spaceWithGap) spaces++;
        return " ".repeat(spaces) + text;
    }

    /**
     * Centres every line.
     *
     * @param lines the lines
     * @return a new list with each line centred by {@link #center(String)}
     */
    public static List<String> center(List<String> lines) {
        List<String> centred = new ArrayList<>(lines.size());
        for (String line : lines) centred.add(center(line));
        return centred;
    }

    /**
     * Measures a line as the chat shows it, without its tags.
     *
     * @param text the line, may be {@code null}
     * @return the width in pixels, counting one pixel after each character; 0 for {@code null}
     */
    public static int width(String text) {
        if (text == null || text.isEmpty()) return 0;
        String tagged = CC.legacyToMiniMessage(text);
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < tagged.length(); i++) {
            char c = tagged.charAt(i);
            if (c == '<') {
                int end = tagEnd(tagged, i);
                if (end != -1) {
                    String name = tagName(tagged.substring(i + 1, end));
                    if (name.equals("bold") || name.equals("b")) {
                        bold = true;
                    } else if (name.equals("/bold") || name.equals("/b") || name.equals("reset") || name.equals("r")) {
                        bold = false;
                    }
                    i = end;
                    continue;
                }
            }
            width += charWidth(c, bold) + 1;
        }
        return width;
    }

    private static int charWidth(char c, boolean bold) {
        int width = c < WIDTHS.length ? WIDTHS[c] : DEFAULT_WIDTH;
        return bold && c != ' ' ? width + 1 : width;
    }

    /** The {@code >} closing the tag opened at {@code start}, or -1; a {@code >} inside quotes does not count. */
    private static int tagEnd(String text, int start) {
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '\'' || c == '"') && text.charAt(i - 1) != '\\') {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (c == quote) {
                    quoted = false;
                }
            }
            if (quoted) continue;
            if (c == '<') {
                depth++;
            } else if (c == '>' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String tagName(String tag) {
        int colon = tag.indexOf(':');
        return (colon == -1 ? tag : tag.substring(0, colon)).toLowerCase(Locale.ROOT);
    }
}
