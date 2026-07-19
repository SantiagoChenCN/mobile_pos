package com.espsa.mobilepos.core.model;

public final class DecimalValueContractTest {
    public static void main(String[] args) {
        assertCanonical("0", DecimalValue.Kind.MONEY, "0");
        assertCanonical("0000.0000", DecimalValue.Kind.MONEY, "0");
        assertCanonical("2099.99", DecimalValue.Kind.MONEY, "2099.99");
        assertCanonical("2099.9900", DecimalValue.Kind.MONEY, "2099.99");
        assertCanonical("999999999999999.9999", DecimalValue.Kind.MONEY, "999999999999999.9999");
        assertCanonical("1.2500", DecimalValue.Kind.QUANTITY, "1.25");
        assertCanonical("99999999999.9999", DecimalValue.Kind.QUANTITY, "99999999999.9999");

        expectInvalid("", DecimalValue.Kind.MONEY);
        expectInvalid(" ", DecimalValue.Kind.MONEY);
        expectInvalid("-1", DecimalValue.Kind.MONEY);
        expectInvalid("+1", DecimalValue.Kind.MONEY);
        expectInvalid("1e2", DecimalValue.Kind.MONEY);
        expectInvalid("NaN", DecimalValue.Kind.MONEY);
        expectInvalid("Infinity", DecimalValue.Kind.MONEY);
        expectInvalid("1.23456", DecimalValue.Kind.MONEY);
        expectInvalid("1000000000000000", DecimalValue.Kind.MONEY);
        expectInvalid("100000000000", DecimalValue.Kind.QUANTITY);

        DecimalValue quantity = DecimalValue.parse("1.25", DecimalValue.Kind.QUANTITY);
        DecimalValue money = DecimalValue.parse("2099.99", DecimalValue.Kind.MONEY);
        assertEquals("2624.9875", quantity.multiplyExact(money, DecimalValue.Kind.MONEY).canonicalText());
        assertTrue(DecimalValue.parse("1", DecimalValue.Kind.QUANTITY)
                .compareTo(DecimalValue.parse("1.0000", DecimalValue.Kind.QUANTITY)) == 0);

        System.out.println("DecimalValue contract test passed");
    }

    private static void assertCanonical(String raw, DecimalValue.Kind kind, String expected) {
        DecimalValue value = DecimalValue.parse(raw, kind);
        assertEquals(raw, value.rawText());
        assertEquals(expected, value.canonicalText());
        assertTrue(!value.canonicalText().toLowerCase().contains("e"));
    }

    private static void expectInvalid(String raw, DecimalValue.Kind kind) {
        try {
            DecimalValue.parse(raw, kind);
            throw new AssertionError("Expected invalid decimal: " + raw);
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition failed");
        }
    }
}
