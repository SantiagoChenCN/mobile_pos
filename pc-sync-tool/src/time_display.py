from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Optional

try:
    from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
except ImportError:  # pragma: no cover - Python 3.9+ provides zoneinfo
    ZoneInfo = None  # type: ignore[assignment,misc]
    ZoneInfoNotFoundError = OSError  # type: ignore[assignment,misc]


_FALLBACK_ARGENTINA_TIMEZONE = timezone(timedelta(hours=-3), name="ART")


def _argentina_timezone():
    if ZoneInfo is None:
        return _FALLBACK_ARGENTINA_TIMEZONE
    try:
        return ZoneInfo("America/Argentina/Buenos_Aires")
    except ZoneInfoNotFoundError:
        return _FALLBACK_ARGENTINA_TIMEZONE


ARGENTINA_TIMEZONE = _argentina_timezone()


def parse_iso_datetime(value: object) -> Optional[datetime]:
    """Parse an ISO timestamp without consulting the computer local timezone."""
    if isinstance(value, datetime):
        parsed = value
    elif isinstance(value, str) and value.strip():
        text = value.strip()
        if text.endswith(("Z", "z")):
            text = text[:-1] + "+00:00"
        try:
            parsed = datetime.fromisoformat(text)
        except ValueError:
            return None
    else:
        return None

    # Legacy timestamps without an offset were generated as UTC by this tool.
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed


def format_argentina_time(value: object, empty_text: str = "-") -> str:
    """Convert a UTC/offset ISO timestamp to ``yyyy-MM-dd HH:mm:ss ART``."""
    if value is None or (isinstance(value, str) and not value.strip()):
        return empty_text

    parsed = parse_iso_datetime(value)
    if parsed is None:
        return str(value)
    return parsed.astimezone(ARGENTINA_TIMEZONE).strftime("%Y-%m-%d %H:%M:%S ART")
