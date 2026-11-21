package org.glstudio.nexus.condition;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * When something is shown: a list of windows, any of which is enough. A window is
 * <ul>
 *   <li>{@code HH:mm-HH:mm}: every day, between those hours of the server's clock (it may run past
 *       midnight, such as {@code 22:00-02:00});</li>
 *   <li>{@code <days> HH:mm-HH:mm}: only on those days, such as {@code mon-fri 18:00-22:00} or
 *       {@code sat,sun 10:00-14:00};</li>
 *   <li>{@code <days>}: all day on those days;</li>
 *   <li>{@code day} or {@code night}: by the time of the world, night being 13000 to 23000.</li>
 * </ul>
 * An empty list is always active.
 *
 * @param windows the windows
 */
public record Schedule(List<Window> windows) {

    /** A schedule with no windows: always active. */
    public static final Schedule ALWAYS = new Schedule(List.of());

    /**
     * One window of a schedule.
     *
     * @param days  the days it applies to
     * @param from  when it opens, or {@code null} for all day
     * @param to    when it closes
     * @param world {@code day}, {@code night}, or {@code null} for the server's clock
     */
    public record Window(Set<DayOfWeek> days, LocalTime from, LocalTime to, String world) {
    }

    /**
     * Creates the schedule.
     */
    public Schedule {
        windows = List.copyOf(windows);
    }

    /**
     * Reads a schedule.
     *
     * @param lines    one window each
     * @param problems where windows that cannot be read are added, one line each
     * @return the schedule of the windows that could be read
     */
    public static Schedule parse(List<String> lines, List<String> problems) {
        List<Window> windows = new ArrayList<>();
        for (String line : lines) {
            Window window = window(line.trim().toLowerCase(Locale.ROOT));
            if (window == null) {
                problems.add("'" + line + "' is not HH:mm-HH:mm, <days> HH:mm-HH:mm, <days>, day or night");
            } else {
                windows.add(window);
            }
        }
        return windows.isEmpty() ? ALWAYS : new Schedule(windows);
    }

    /**
     * Returns whether the schedule is active.
     *
     * @param now       the server's time, in the time zone schedules are read in
     * @param worldTime the time of the world, 0 to 23999
     * @return {@code true} if a window is open, or there are none
     */
    public boolean active(ZonedDateTime now, long worldTime) {
        if (windows.isEmpty()) return true;
        for (Window window : windows) {
            if (window.world() != null) {
                boolean night = worldTime >= 13000 && worldTime < 23000;
                if (window.world().equals("night") == night) return true;
                continue;
            }
            LocalTime time = now.toLocalTime();
            if (window.from() == null) {
                if (window.days().contains(now.getDayOfWeek())) return true;
            } else if (!window.from().isAfter(window.to())) {
                if (window.days().contains(now.getDayOfWeek()) && !time.isBefore(window.from()) && time.isBefore(window.to())) {
                    return true;
                }
            } else {
                // Past midnight: the evening of a listed day, or the morning after one.
                if (window.days().contains(now.getDayOfWeek()) && !time.isBefore(window.from())) return true;
                if (window.days().contains(now.getDayOfWeek().minus(1)) && time.isBefore(window.to())) return true;
            }
        }
        return false;
    }

    /**
     * Returns whether there are windows.
     *
     * @return {@code false} for a schedule that is always active
     */
    public boolean limited() {
        return !windows.isEmpty();
    }

    /**
     * Writes the windows back as they are read.
     *
     * @return one line each
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (Window window : windows) {
            if (window.world() != null) {
                lines.add(window.world());
                continue;
            }
            String days = window.days().size() == 7 ? "" : days(window.days());
            String hours = window.from() == null ? "" : window.from() + "-" + window.to();
            lines.add((days + " " + hours).trim());
        }
        return lines;
    }

    private static Window window(String text) {
        if (text.equals("day") || text.equals("night")) return new Window(EnumSet.allOf(DayOfWeek.class), null, null, text);
        String[] words = text.split("\\s+");
        if (words.length > 2) return null;
        String hours = words[words.length - 1].contains(":") ? words[words.length - 1] : null;
        String dayText = words.length == 2 ? words[0] : hours == null ? words[0] : null;
        if (words.length == 2 && hours == null) return null;
        Set<DayOfWeek> days = dayText == null ? EnumSet.allOf(DayOfWeek.class) : days(dayText);
        if (days == null) return null;
        if (hours == null) return new Window(days, null, null, null);
        String[] range = hours.split("-");
        if (range.length != 2) return null;
        try {
            LocalTime from = LocalTime.parse(pad(range[0]));
            LocalTime to = LocalTime.parse(pad(range[1]));
            if (from.equals(to)) return null;
            return new Window(days, from, to, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** {@code mon}, {@code mon-fri}, {@code sat,sun} or a mix of them. */
    private static Set<DayOfWeek> days(String text) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String part : text.split(",")) {
            String[] range = part.split("-");
            DayOfWeek first = day(range[0]);
            DayOfWeek last = range.length == 2 ? day(range[1]) : first;
            if (first == null || last == null || range.length > 2) return null;
            for (DayOfWeek day = first; ; day = day.plus(1)) {
                days.add(day);
                if (day == last) break;
            }
        }
        return days;
    }

    private static DayOfWeek day(String text) {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().toLowerCase(Locale.ROOT).startsWith(text) && text.length() >= 3) return day;
        }
        return null;
    }

    /** Three days or more in a row are written as a range, such as {@code mon-fri}. */
    private static String days(Set<DayOfWeek> days) {
        List<String> parts = new ArrayList<>();
        DayOfWeek[] week = DayOfWeek.values();
        for (int start = 0; start < week.length; start++) {
            if (!days.contains(week[start])) continue;
            int end = start;
            while (end + 1 < week.length && days.contains(week[end + 1])) end++;
            if (end - start >= 2) {
                parts.add(name(week[start]) + "-" + name(week[end]));
            } else {
                for (int day = start; day <= end; day++) parts.add(name(week[day]));
            }
            start = end;
        }
        return String.join(",", parts);
    }

    private static String name(DayOfWeek day) {
        return day.name().substring(0, 3).toLowerCase(Locale.ROOT);
    }

    // "9:00" is read as "09:00".
    private static String pad(String time) {
        return time.length() == 4 ? "0" + time : time;
    }
}
