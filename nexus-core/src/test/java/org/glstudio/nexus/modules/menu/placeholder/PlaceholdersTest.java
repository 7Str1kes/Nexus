package org.glstudio.nexus.modules.menu.placeholder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaceholdersTest {

    @Test
    void mapTokensMatchBothSyntaxes() {
        Map<String, String> tokens = Map.of("page", "2", "max_page", "5");
        assertEquals("Page 2/5", Placeholders.apply("Page %page%/{max_page}", tokens));
        assertEquals("Page 2 of 5", Placeholders.apply("Page {page} of %max_page%", tokens));
    }

    @Test
    void unknownTokensStayAsWritten() {
        assertEquals("100% {missing} %x", Placeholders.apply("100% {missing} %x", Map.of("other", "1")));
    }

    @Test
    void pairsWithDelimitersMatchExactly() {
        assertEquals("Hello Steve, {name}", Placeholders.apply("Hello {player}, {name}", "{player}", "Steve"));
        assertEquals("Hello %player%", Placeholders.apply("Hello %player%", "{player}", "Steve"));
    }

    @Test
    void barePairsMatchBothSyntaxes() {
        assertEquals("Steve Steve", Placeholders.apply("{player} %player%", "player", "Steve"));
    }

    @Test
    void valuesAreNeverRescanned() {
        // A player-chosen value containing a token must not expand into another value.
        String result = Placeholders.apply("{name} owns {home}", "{name}", "{home}", "{home}", "base");
        assertEquals("{home} owns base", result);
    }

    @Test
    void longestTokenWins() {
        assertEquals("A-B", Placeholders.apply("{player}-{player_name}", "{player}", "A", "{player_name}", "B"));
    }

    @Test
    void nonStringValuesAreConverted() {
        assertEquals("3 homes", Placeholders.apply("{count} homes", "{count}", 3));
    }

    @Test
    void oddPairsAndNullTextAreSafe() {
        assertEquals("x {a}", Placeholders.apply("x {a}", "{a}"));
        assertNull(Placeholders.apply((String) null, "{a}", "b"));
        assertEquals(List.of("1", "2"), Placeholders.apply(List.of("{a}", "{b}"), "{a}", 1, "{b}", 2));
    }
}
