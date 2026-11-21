package org.glstudio.nexus.modules.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompletionsTest {

    @Test
    void filtersByPrefixIgnoringCase() {
        List<String> options = List.of("Alice", "alex", "Bob");
        assertEquals(List.of("Alice", "alex"), Completions.filter(options, "AL"));
        assertEquals(options, Completions.filter(options, ""));
        assertEquals(options, Completions.filter(options, null));
        assertEquals(List.of(), Completions.filter(options, "z"));
    }

    @Test
    void joinsFromAnArgument() {
        assertEquals("b c", Completions.join(new String[]{"a", "b", "c"}, 1));
        assertEquals("", Completions.join(new String[]{"a"}, 1));
    }
}
