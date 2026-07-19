package com.espsa.mobilepos.ui;

import com.espsa.mobilepos.core.model.Quantity;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class QuantityTextContractTest {
    public static void main(String[] args) throws Exception {
        assertQuantity("1.25", QuantityText.parse("1.2500"), "point input");
        assertQuantity("1.25", QuantityText.parse("1,2500"), "comma input");
        assertQuantity("0.0001", QuantityText.parse("0,0001"), "CT-01 scale boundary");
        assertQuantity("99999999999.9999", QuantityText.parse("99999999999.9999"),
                "CT-01 maximum 11+4 boundary");
        assertQuantity("1", QuantityText.parse(repeat('0', 31) + "1"),
                "exactly 32 input characters accept");
        assertEquals("2147483648", QuantityText.format(Quantity.of("2147483648")),
                "display does not narrow to int");
        expectNullPointer(() -> QuantityText.format(null), "null display fails fast");

        assertTrue(QuantityText.parseOrNull("1.00001") == null, "excess scale rejects");
        assertTrue(QuantityText.parseOrNull("1,000.00") == null, "mixed separators reject");
        assertTrue(QuantityText.parseOrNull("NaN") == null, "NaN rejects");
        assertTrue(QuantityText.parseOrNull("1e2") == null, "exponent rejects");
        assertTrue(QuantityText.parseOrNull("0") == null, "zero rejects");
        assertTrue(QuantityText.parseOrNull("-1") == null, "negative rejects");
        assertTrue(QuantityText.parseOrNull("100000000000") == null,
                "integer digits above eleven reject");
        assertTrue(QuantityText.parseOrNull(repeat('0', 32) + "1") == null,
                "input length above 32 rejects");

        final int[] callbackCount = new int[]{0};
        final Quantity[] captured = new Quantity[]{null};
        boolean invalidApplied = QuantityText.applyIfValid("1.00001", quantity -> {
            callbackCount[0]++;
            captured[0] = quantity;
        });
        assertTrue(!invalidApplied, "invalid submission returns false");
        assertTrue(callbackCount[0] == 0, "invalid submission invokes callback zero times");
        assertTrue(captured[0] == null, "invalid submission captures no quantity");

        boolean validApplied = QuantityText.applyIfValid("1,2500", quantity -> {
            callbackCount[0]++;
            captured[0] = quantity;
        });
        assertTrue(validApplied, "valid submission returns true");
        assertTrue(callbackCount[0] == 1, "valid submission invokes callback exactly once");
        assertQuantity("1.25", captured[0], "valid submission captures exact Quantity");
        expectNullPointer(() -> QuantityText.applyIfValid("1", null),
                "null submission callback fails fast");

        for (Method method : QuantityText.class.getDeclaredMethods()) {
            assertTrue(method.getReturnType() != double.class && method.getReturnType() != float.class
                            && method.getReturnType() != int.class,
                    "QuantityText cannot expose primitive numeric return types: " + method);
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertTrue(parameterType != double.class && parameterType != float.class
                                && parameterType != int.class,
                        "QuantityText cannot accept primitive numeric parameters: " + method);
            }
        }

        String checkoutSource = readCheckoutSource();
        assertContains(checkoutSource, "修改数量", "line action offers quantity edit");
        assertContains(checkoutSource, "Cambiar cantidad", "quantity edit has Spanish label");
        assertContains(checkoutSource, "Quantity.one()", "default add paths use explicit Quantity one");
        assertContains(checkoutSource, "QuantityText.format", "checkout displays canonical quantity text");
        assertContains(checkoutSource, "QuantityText.applyIfValid",
                "quantity dialog delegates executable submission decision");
        assertContains(checkoutSource, "setOnShowListener", "invalid quantity can keep dialog open");
        assertContains(checkoutSource, "setError", "invalid quantity shows an inline error");
        assertNotContains(checkoutSource, "changeLineQuantity", "integer plus/minus path is removed");
        assertNotContains(checkoutSource, ".quantity()", "checkout does not use legacy int getter");
        assertNotContains(checkoutSource, "addProduct(product, 1)", "search does not use int quantity");

        System.out.println("Quantity text/checkout contract test passed");
    }

    private static String readCheckoutSource() throws Exception {
        Path modulePath = Paths.get(
                "app", "src", "main", "java", "com", "espsa", "mobilepos", "ui", "screens",
                "CheckoutScreen.java"
        );
        Path workspacePath = Paths.get("android-emergency-pos").resolve(modulePath);
        Path source = Files.exists(modulePath) ? modulePath : workspacePath;
        return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static void assertQuantity(String expected, Quantity actual, String label) {
        if (actual == null || !expected.equals(actual.canonicalText())) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertContains(String actual, String expected, String label) {
        assertTrue(actual.contains(expected), label + ": missing=" + expected);
    }

    private static void assertNotContains(String actual, String forbidden, String label) {
        assertTrue(!actual.contains(forbidden), label + ": forbidden=" + forbidden);
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void expectNullPointer(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (NullPointerException expected) {
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
