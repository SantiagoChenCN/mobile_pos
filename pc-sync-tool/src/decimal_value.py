from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal
from enum import Enum


_PLAIN_DECIMAL = re.compile(r"^(?:0|[0-9]+)(?:\.[0-9]+)?$")
MAX_CANONICAL_LENGTH = 32


class DecimalKind(str, Enum):
    MONEY = "MONEY"
    QUANTITY = "QUANTITY"

    @property
    def max_integer_digits(self) -> int:
        return 15 if self is DecimalKind.MONEY else 11

    @property
    def max_fraction_digits(self) -> int:
        return 4


@dataclass(frozen=True)
class DecimalValue:
    raw_text: str
    canonical_text: str
    value: Decimal
    kind: DecimalKind

    @classmethod
    def parse(cls, raw_text: str, kind: DecimalKind | str) -> "DecimalValue":
        if not isinstance(raw_text, str):
            raise ValueError("decimal text must be a string")
        try:
            parsed_kind = kind if isinstance(kind, DecimalKind) else DecimalKind(kind)
        except (TypeError, ValueError) as exc:
            raise ValueError("unknown decimal kind") from exc
        if not _PLAIN_DECIMAL.fullmatch(raw_text):
            raise ValueError("decimal text must be an unsigned plain decimal")

        value = Decimal(raw_text)
        canonical = _canonical_text(value)
        integer_part, _, fraction_part = canonical.partition(".")
        if len(integer_part) > parsed_kind.max_integer_digits:
            raise ValueError("decimal integer digits exceed the business limit")
        if len(fraction_part) > parsed_kind.max_fraction_digits:
            raise ValueError("decimal fraction digits exceed the business limit")
        if len(canonical) > MAX_CANONICAL_LENGTH:
            raise ValueError("canonical decimal text is too long")
        return cls(raw_text, canonical, value, parsed_kind)

    def compare_to(self, other: "DecimalValue") -> int:
        if not isinstance(other, DecimalValue):
            raise TypeError("other must be DecimalValue")
        return (self.value > other.value) - (self.value < other.value)

    def multiply_exact(
        self,
        other: "DecimalValue",
        result_kind: DecimalKind | str = DecimalKind.MONEY,
    ) -> "DecimalValue":
        if not isinstance(other, DecimalValue):
            raise TypeError("other must be DecimalValue")
        product = _canonical_text(self.value * other.value)
        return DecimalValue.parse(product, result_kind)


def _canonical_text(value: Decimal) -> str:
    if not value.is_finite() or value < 0:
        raise ValueError("decimal value must be finite and non-negative")
    if value == 0:
        return "0"
    plain = format(value, "f")
    if "." in plain:
        plain = plain.rstrip("0").rstrip(".")
    return plain
