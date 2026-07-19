package com.espsa.mobilepos.core.model;

import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CartLine;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.ledger.SaleLine;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;

public final class QuantityContractTest {
    public static void main(String[] args) {
        assertEquals("1", Quantity.one().canonicalText(), "one");
        assertEquals("1.25", Quantity.of("1.2500").canonicalText(), "canonical quantity");
        assertEquals("2", Quantity.of("1.25").add(Quantity.of(new BigDecimal("0.75"))).canonicalText(),
                "exact quantity addition");
        assertTrue(Quantity.of("2.0000").isInteger(), "normalized integer detection");
        assertTrue(!Quantity.of("2.0001").isInteger(), "fractional detection");
        assertTrue(Quantity.of("1.0").equals(Quantity.of("1.00")), "numeric equality ignores scale");
        assertTrue(Quantity.of("1.0").hashCode() == Quantity.of("1.00").hashCode(),
                "equal quantities share hash");
        expectIllegal(() -> Quantity.of("0"), "zero rejects");
        expectIllegal(() -> Quantity.of("1.00001"), "scale above four rejects");
        expectIllegal(() -> Quantity.of("100000000000"), "integer digits above eleven reject");
        expectIllegal(() -> Quantity.of("1e2"), "exponent rejects");

        Product product = new Product(
                "q-1", "q-1", "Quantity product", "almacen", "un",
                Money.of("10"), null, 0, false
        );
        CartLine integer = new CartLine(product, Quantity.of("2"));
        assertEquals("2", integer.quantityValue().canonicalText(), "integer Quantity remains exact");
        CartLine fractional = integer.withQuantity(Quantity.of("1.5"));
        assertEquals("1.5", fractional.quantityValue().canonicalText(), "CartLine can carry fractional quantity");

        for (Method method : Quantity.class.getDeclaredMethods()) {
            assertTrue(method.getReturnType() != double.class && method.getReturnType() != Double.class,
                    "Quantity has no double return");
            for (Class<?> parameter : method.getParameterTypes()) {
                assertTrue(parameter != double.class && parameter != Double.class,
                        "Quantity has no double parameter");
            }
        }
        assertNoMethodNamed(Quantity.class, "legacyIntValueExact");
        assertNoMethodNamed(CartLine.class, "quantity");
        assertNoIntParameter(Cart.class, "addProduct");
        assertNoIntParameter(CartLine.class, "withQuantity");
        assertNoIntParameter(CheckoutService.class, "addProductByBarcode");
        assertNoIntParameter(CheckoutService.class, "addManualAlmacenItem");
        assertNoIntConstructor(CartLine.class);
        assertNoIntConstructor(SaleLine.class);
        System.out.println("Quantity contract test passed");
    }

    private static void assertNoMethodNamed(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            assertTrue(!methodName.equals(method.getName()),
                    type.getSimpleName() + " cannot retain " + methodName);
        }
    }

    private static void assertNoIntParameter(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            if (!methodName.equals(method.getName())) {
                continue;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                assertTrue(parameter != int.class,
                        type.getSimpleName() + "." + methodName + " cannot accept int quantity");
            }
        }
    }

    private static void assertNoIntConstructor(Class<?> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertTrue(parameter != int.class,
                        type.getSimpleName() + " cannot retain an int quantity constructor");
            }
        }
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
