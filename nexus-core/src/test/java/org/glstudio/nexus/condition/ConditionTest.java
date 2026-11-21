package org.glstudio.nexus.condition;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionTest {

    private static final Map<String, String> VALUES = Map.of("%balance%", "1,250.5", "%level%", "9",
            "%world%", "world_nether", "%flying%", "yes", "%rank%", "VIP > MVP");

    private static boolean holds(String raw) {
        UnaryOperator<String> fill = side -> {
            String out = side;
            for (Map.Entry<String, String> entry : VALUES.entrySet()) out = out.replace(entry.getKey(), entry.getValue());
            return out;
        };
        return Condition.parse(raw).orElseThrow().test(fill);
    }

    @Test
    void numbersCompareAsNumbers() {
        assertTrue(holds("%balance% >= 1000"), "thousands separators are read");
        assertFalse(holds("%balance% < 1000"));
        assertTrue(holds("%level% < 10"));
        assertTrue(holds("%level% == 9.0"));
        assertTrue(holds("%level% != 8"));
        assertFalse(holds("%level% > 9"));
    }

    @Test
    void textComparesIgnoringCase() {
        assertTrue(holds("%world% == WORLD_NETHER"));
        assertTrue(holds("%world% contains nether"));
        assertTrue(holds("%world% !contains end"));
        assertFalse(holds("%world% > 3"), "text is never more than a number");
    }

    @Test
    void sidesAreFilledAfterReadingTheOperator() {
        assertTrue(holds("%rank% contains MVP"), "an operator inside a value is not read as one");
    }

    @Test
    void aSideAloneIsTruthy() {
        assertTrue(holds("%flying%"));
        assertFalse(holds("%world%"));
        assertTrue(Condition.parse("  ").isEmpty());
        assertEquals(Condition.Operator.TRUTHY, Condition.parse("%flying%").orElseThrow().operator());
    }
}
