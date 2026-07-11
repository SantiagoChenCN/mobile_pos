package com.espsa.mobilepos.app.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

public final class ArgentinaTime {
    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss 'ART'", Locale.ROOT)
            .withZone(ZONE);

    private ArgentinaTime() {
    }

    public static String formatInstant(Instant instant) {
        return instant == null ? "-" : DISPLAY_FORMAT.format(instant);
    }

    public static String formatIso(String isoValue) {
        if (isoValue == null || isoValue.trim().isEmpty()) {
            return "-";
        }
        String raw = isoValue.trim();
        try {
            TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parse(normalizeUtc(raw));
            Instant instant = parsed.isSupported(ChronoField.INSTANT_SECONDS)
                    ? Instant.from(parsed)
                    : LocalDateTime.from(parsed).toInstant(ZoneOffset.UTC);
            return formatInstant(instant);
        } catch (DateTimeParseException | ArithmeticException ex) {
            return raw;
        }
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static String normalizeUtc(String value) {
        if (value.endsWith("Z") || value.endsWith("z")) {
            return value.substring(0, value.length() - 1) + "+00:00";
        }
        return value;
    }
}
