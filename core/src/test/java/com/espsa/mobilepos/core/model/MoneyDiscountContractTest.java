package com.espsa.mobilepos.core.model;

import java.lang.reflect.Method;
import java.math.BigDecimal;

public final class MoneyDiscountContractTest {
    public static void main(String[] args) {
        Money exact = Money.of("2099.9900");
        assertEquals("2099.99", exact.canonicalText(), "canonical money text");
        assertTrue(exact.equals(Money.of(new BigDecimal("2099.99"))), "string and BigDecimal agree");
        assertTrue(Money.of("1.0").equals(Money.of("1.00")), "numeric equality ignores scale");
        assertTrue(Money.of("1.0").hashCode() == Money.of("1.00").hashCode(), "equal hash ignores scale");
        assertEquals("2100.24", exact.plus(Money.of("0.25")).canonicalText(), "exact addition");
        assertEquals("0", Money.of("1.25").minusCapped(Money.of("2")).canonicalText(), "capped subtraction");
        assertEquals("6299.97", exact.times(3).canonicalText(), "exact multiplication");

        expectIllegal(() -> Money.of("1.23456"), "money scale is bounded");
        expectIllegal(() -> Money.of("-1"), "negative money rejects");
        expectIllegal(() -> Money.of("1e2"), "string exponent rejects");

        Discount exactPercent = Discount.percent("10.50");
        assertEquals("10.5", exactPercent.canonicalValue(), "exact original percentage");
        assertEquals("10.5", exactPercent.calculateAmount(Money.of("100")).canonicalText(), "exact percentage amount");
        assertTrue(exactPercent.equals(Discount.percent(new BigDecimal("10.500"))), "discount numeric equality");
        assertTrue(exactPercent.hashCode() == Discount.percent("10.500").hashCode(), "discount hash ignores scale");
        assertEquals("25.25", Discount.fixedAmount(Money.of("25.25")).calculateAmount(Money.of("100")).canonicalText(), "fixed exact amount");
        expectIllegal(() -> Discount.percent("100.01"), "percent above 100 rejects");

        for (Method method : Money.class.getDeclaredMethods()) {
            assertTrue(method.getReturnType() != double.class && method.getReturnType() != Double.class,
                    "Money has no double return");
            for (Class<?> parameter : method.getParameterTypes()) {
                assertTrue(parameter != double.class && parameter != Double.class,
                        "Money has no double parameter");
            }
        }

        System.out.println("Money/Discount contract test passed");
    }

    private static void expectIllegal(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
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
}
