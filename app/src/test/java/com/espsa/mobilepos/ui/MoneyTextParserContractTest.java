package com.espsa.mobilepos.ui;

import com.espsa.mobilepos.core.model.Money;

import java.math.BigDecimal;

public final class MoneyTextParserContractTest {
    public static void main(String[] args) {
        assertMoney("2099.99", NumberTextParser.parseMoney("2099.9900"), "point input");
        assertMoney("2099.99", NumberTextParser.parseMoney("2099,9900"), "comma input");
        assertMoney("0.0001", NumberTextParser.parseMoney("0,0001"), "CT-01 scale boundary");
        assertTrue(NumberTextParser.parseMoneyOrNull("1.00001") == null, "excess scale rejects");
        assertTrue(NumberTextParser.parseMoneyOrNull("1,000.00") == null, "mixed separators reject");
        assertTrue(NumberTextParser.parseMoneyOrNull("NaN") == null, "NaN rejects");
        assertTrue(NumberTextParser.parseMoneyOrNull("1e2") == null, "exponent rejects");
        assertTrue(NumberTextParser.parseMoneyOrNull("-1") == null, "negative rejects");
        assertTrue(NumberTextParser.parseMoneyOrNull("1000000000000000") == null, "integer digits bounded");

        BigDecimal percentage = NumberTextParser.parsePercentageOrNull("10,50");
        assertTrue(percentage != null && percentage.compareTo(new BigDecimal("10.5")) == 0,
                "exact percentage accepts comma");
        assertTrue(NumberTextParser.parsePercentageOrNull("100.01") == null, "percentage upper bound");
        assertTrue(NumberTextParser.parsePercentageOrNull("0") == null, "zero percentage rejects");

        assertEquals("$2099.99", MoneyText.currency(Money.of("2099.9900")), "currency display");
        assertEquals("0", MoneyText.format(Money.ZERO), "zero display");
        expectNullPointer(() -> MoneyText.format(null), "null format fails fast");
        expectNullPointer(() -> MoneyText.currency(null), "null currency fails fast");
        System.out.println("Money text/parser contract test passed");
    }

    private static void assertMoney(String expected, Money actual, String label) {
        if (actual == null || !expected.equals(actual.canonicalText())) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void expectNullPointer(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (NullPointerException expected) {
        }
    }
}
