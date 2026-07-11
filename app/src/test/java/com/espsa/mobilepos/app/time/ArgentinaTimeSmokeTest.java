package com.espsa.mobilepos.app.time;

import java.time.LocalDate;

public final class ArgentinaTimeSmokeTest {
    public static void main(String[] args) {
        assertEquals(
                "UTC converts to Argentina time",
                "2026-07-11 01:10:00 ART",
                ArgentinaTime.formatIso("2026-07-11T04:10:00Z")
        );
        assertEquals(
                "explicit UTC offset converts to Argentina time",
                "2026-07-11 01:10:00 ART",
                ArgentinaTime.formatIso("2026-07-11T04:10:00+00:00")
        );
        assertEquals(
                "Argentina offset remains unchanged",
                "2026-07-11 01:10:00 ART",
                ArgentinaTime.formatIso("2026-07-11T01:10:00-03:00")
        );
        assertEquals(
                "UTC date boundary is converted correctly",
                "2026-07-10 23:10:00 ART",
                ArgentinaTime.formatIso("2026-07-11T02:10:00Z")
        );
        assertEquals(
                "timezone-less legacy value is treated as UTC",
                "2026-07-11 01:10:00 ART",
                ArgentinaTime.formatIso("2026-07-11T04:10:00")
        );
        assertEquals("empty value uses fallback", "-", ArgentinaTime.formatIso("  "));
        assertEquals("null value uses fallback", "-", ArgentinaTime.formatIso(null));
        assertEquals("invalid value is returned for diagnosis", "not-a-time", ArgentinaTime.formatIso("not-a-time"));
        assertEquals("today uses Argentina zone", LocalDate.now(ArgentinaTime.ZONE), ArgentinaTime.today());

        System.out.println("Argentina time smoke test passed");
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }
}
