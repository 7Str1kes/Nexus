package org.glstudio.nexus.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CenteredTextTest {

    private static int spaces(String centred, String original) {
        assertEquals(original, centred.substring(centred.length() - original.length()));
        return centred.length() - original.length();
    }

    @Test
    void measuresEachCharacterPlusOnePixel() {
        assertEquals(22, CenteredText.width("Hello"));
        assertEquals(4, CenteredText.width(" "));
        assertEquals(7, CenteredText.width("@"));
        assertEquals(0, CenteredText.width(""));
        assertEquals(0, CenteredText.width(null));
    }

    @Test
    void unknownCharactersCountAsFourPixels() {
        assertEquals(5, CenteredText.width("ᴀ"));
        assertEquals(6, CenteredText.width("&lᴀ"));
    }

    @Test
    void boldIsOnePixelWiderExceptSpaces() {
        assertEquals(10, CenteredText.width("&lHi"));
        assertEquals(10, CenteredText.width("<bold>Hi"));
        assertEquals(10, CenteredText.width("<b>Hi"));
        assertEquals(4, CenteredText.width("<b> "));
    }

    @Test
    void boldEndsOnResetOrClosingTagButNotOnAColour() {
        assertEquals(13, CenteredText.width("&lA&rA"));
        assertEquals(13, CenteredText.width("<b>A</b>A"));
        assertEquals(13, CenteredText.width("<bold>A<reset>A"));
        assertEquals(14, CenteredText.width("&lA&cA"));
    }

    @Test
    void tagsAndWhatTheyHoldTakeNoRoom() {
        assertEquals(12, CenteredText.width("<hover:show_text:'<red>x'>ab"));
        assertEquals(12, CenteredText.width("&#FF0000<click:open_url:'https://a.b/c?d>e'>ab"));
        assertEquals(12, CenteredText.width("<gradient:red:blue>ab</gradient>"));
    }

    @Test
    void anUnclosedAngleBracketIsText() {
        assertEquals(11, CenteredText.width("<3"));
    }

    @Test
    void centresWithSpacesInFront() {
        assertEquals(32, spaces(CenteredText.center("Hello World"), "Hello World"));
        assertEquals(24, spaces(CenteredText.center("<b>Hi</b> there <r>and &lbold&r again"),
                "<b>Hi</b> there <r>and &lbold&r again"));
    }

    @Test
    void centresLikeTheAnnouncementsItReplaces() {
        // Space counts of lines centred by the announcement plugin GL-Chat replaces.
        String title = "&6▪ &#FF6B6B&lʀ&#FF7878&lᴇ&#FF8585&lᴘ&#FF9393&lᴏ&#FFA0A0&lʀ&#FFA0A0&lᴛ&#FF9393&lᴀ&#FF8585&lʀ &r&6▪";
        String link = "&#5B6CF5<click:open_url:'https://discord.gg/goldenlyons'><hover:show_text:'<#A8B4FF>¡Haz clic para entrar!'>&6» &#A8B4FF/ᴅɪꜱᴄᴏʀᴅ";
        String text = "&f  equipo de &#FFE66D&lstaff &r&flo atenderá de &#FFC2C2inmediato&f.";
        assertEquals(31, spaces(CenteredText.center(title), title));
        assertEquals(33, spaces(CenteredText.center(link), link));
        assertEquals(11, spaces(CenteredText.center(text), text));
        assertEquals(38, spaces(CenteredText.center("&f "), "&f "));
        assertEquals(39, spaces(CenteredText.center("&f"), "&f"));
    }

    @Test
    void leavesEmptyAndTooLongLinesAlone() {
        assertEquals("", CenteredText.center(""));
        assertEquals("", CenteredText.center((String) null));
        String longLine = "This line is far too long to be centred in the chat box because it is much wider than it";
        assertEquals(longLine, CenteredText.center(longLine));
    }

    @Test
    void centresEveryLineOfAList() {
        List<String> centred = CenteredText.center(List.of("Hello", ""));
        assertEquals(2, centred.size());
        assertEquals("", centred.get(1));
        assertEquals(" ".repeat(36) + "Hello", centred.get(0));
    }
}
