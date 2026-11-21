package org.glstudio.nexus.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmountsTest {

    @Test
    void amountsWithSuffixesAndSeparators() {
        assertEquals(2500, Amounts.parse("2.5k").orElseThrow());
        assertEquals(1_000_000, Amounts.parse("1M").orElseThrow());
        assertEquals(1234.5, Amounts.parse("1,234.5").orElseThrow());
        assertTrue(Amounts.parse("-5").isEmpty());
        assertTrue(Amounts.parse("abc").isEmpty());
        assertTrue(Amounts.parse("").isEmpty());
    }

    @Test
    void countsAreWhole() {
        assertEquals(10_000, Amounts.parseCount("10.000").orElseThrow());
        assertEquals(3000, Amounts.parseCount("3k").orElseThrow());
        assertTrue(Amounts.parseCount("1.5").isEmpty());
        assertTrue(Amounts.parseCount("3000000000").isEmpty());
    }
}
