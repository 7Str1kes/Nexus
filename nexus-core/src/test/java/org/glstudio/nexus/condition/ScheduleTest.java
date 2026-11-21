package org.glstudio.nexus.condition;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTest {

    // 2026-11-20 is a Friday.
    private static ZonedDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).atZone(ZoneId.of("Europe/Madrid"));
    }

    private static Schedule parse(String... lines) {
        List<String> problems = new ArrayList<>();
        Schedule schedule = Schedule.parse(List.of(lines), problems);
        assertEquals(List.of(), problems);
        return schedule;
    }

    @Test
    void hoursOfEveryDay() {
        Schedule schedule = parse("18:00-22:00");
        assertTrue(schedule.active(at(20, 18, 0), 0));
        assertTrue(schedule.active(at(20, 21, 59), 0));
        assertFalse(schedule.active(at(20, 22, 0), 0));
        assertFalse(schedule.active(at(20, 9, 0), 0));
    }

    @Test
    void pastMidnightBelongsToTheDayItStarted() {
        Schedule schedule = parse("fri 22:00-02:00");
        assertTrue(schedule.active(at(20, 23, 0), 0), "Friday night");
        assertTrue(schedule.active(at(21, 1, 30), 0), "Saturday morning, after Friday");
        assertFalse(schedule.active(at(22, 1, 30), 0), "Sunday morning, after Saturday");
    }

    @Test
    void daysAndRanges() {
        Schedule weekdays = parse("mon-fri");
        assertTrue(weekdays.active(at(20, 12, 0), 0));
        assertFalse(weekdays.active(at(21, 12, 0), 0));
        Schedule weekend = parse("sat,sun 10:00-14:00", "wed");
        assertTrue(weekend.active(at(22, 11, 0), 0));
        assertFalse(weekend.active(at(22, 15, 0), 0));
        assertTrue(weekend.active(at(18, 23, 0), 0), "any time on Wednesday");
        assertEquals(List.of("sat,sun 10:00-14:00", "wed"), weekend.lines());
    }

    @Test
    void worldTime() {
        Schedule night = parse("night");
        assertTrue(night.active(at(20, 12, 0), 18000));
        assertFalse(night.active(at(20, 12, 0), 6000));
        assertTrue(parse("day").active(at(20, 0, 0), 1000));
    }

    @Test
    void badWindowsAreReported() {
        List<String> problems = new ArrayList<>();
        Schedule schedule = Schedule.parse(List.of("someday", "25:00-26:00", "9:00-17:00"), problems);
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(schedule.active(at(20, 9, 30), 0), "9:00 is read as 09:00");
        assertTrue(Schedule.parse(List.of(), problems).active(at(20, 3, 0), 0), "no windows: always");
    }
}
