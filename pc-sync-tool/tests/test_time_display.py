from __future__ import annotations

import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from time_display import format_argentina_time, parse_iso_datetime


class TimeDisplayTest(unittest.TestCase):
    def test_utc_z_timestamp_converts_to_argentina_time(self):
        self.assertEqual(
            "2026-07-11 01:10:00 ART",
            format_argentina_time("2026-07-11T04:10:00Z"),
        )

    def test_utc_offset_timestamp_converts_to_argentina_time(self):
        self.assertEqual(
            "2026-07-11 01:10:00 ART",
            format_argentina_time("2026-07-11T04:10:00+00:00"),
        )

    def test_argentina_offset_timestamp_keeps_local_time(self):
        self.assertEqual(
            "2026-07-11 01:10:00 ART",
            format_argentina_time("2026-07-11T01:10:00-03:00"),
        )

    def test_aware_datetime_is_supported(self):
        value = datetime(2026, 7, 11, 4, 10, tzinfo=timezone.utc)

        self.assertEqual("2026-07-11 01:10:00 ART", format_argentina_time(value))

    def test_naive_datetime_is_interpreted_as_utc(self):
        value = datetime(2026, 7, 11, 4, 10)

        self.assertEqual("2026-07-11 01:10:00 ART", format_argentina_time(value))
        self.assertEqual(timezone.utc, parse_iso_datetime(value).tzinfo)

    def test_utc_date_boundary_moves_to_previous_argentina_date(self):
        self.assertEqual(
            "2026-07-10 21:00:00 ART",
            format_argentina_time("2026-07-11T00:00:00Z"),
        )

    def test_empty_values_use_empty_text(self):
        self.assertEqual("-", format_argentina_time(None))
        self.assertEqual("-", format_argentina_time(""))
        self.assertEqual("N/A", format_argentina_time(" ", empty_text="N/A"))

    def test_invalid_text_falls_back_to_original_value(self):
        self.assertEqual("not-a-timestamp", format_argentina_time("not-a-timestamp"))
        self.assertIsNone(parse_iso_datetime("not-a-timestamp"))


if __name__ == "__main__":
    unittest.main()
