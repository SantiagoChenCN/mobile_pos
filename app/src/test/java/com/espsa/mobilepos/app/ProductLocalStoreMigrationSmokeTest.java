package com.espsa.mobilepos.app;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

/** Host-only atomic-write and source-contract coverage; Android org.json runtime is not claimed here. */
public final class ProductLocalStoreMigrationSmokeTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("product-local-store-").toFile();
        try {
            File products = new File(root, "products.json");
            assertSourceContract();
            Files.write(products.toPath(), "old-products".getBytes(StandardCharsets.UTF_8));
            ProductLocalStore store = new ProductLocalStore();
            byte[] before = Files.readAllBytes(products.toPath());
            ProductLocalStore failing = new ProductLocalStore((source, destination) -> { throw new java.io.IOException("fixture atomic move failure"); });
            expectFailure("atomic replacement failure", () -> invokeAtomicWrite(failing, products, "must-not-replace"));
            assertArrayEquals("old products survive failed atomic replacement", before, Files.readAllBytes(products.toPath()));
            assertEquals("temporary files cleaned", 0, root.listFiles((dir, name) -> name.endsWith(".tmp")).length);
            invokeAtomicWrite(store, products, "new-products");
            assertEquals("atomic replacement success", "new-products", new String(Files.readAllBytes(products.toPath()), StandardCharsets.UTF_8));
            System.out.println("Product local store migration smoke test passed: " + assertions + " assertions");
        } finally {
            deleteTree(root);
        }
    }

    private static void invokeAtomicWrite(ProductLocalStore store, File target, String text) throws Exception {
        Method write = ProductLocalStore.class.getDeclaredMethod("writeUtf8", File.class, String.class);
        write.setAccessible(true);
        try {
            write.invoke(store, target, text);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw ex;
        }
    }

    private static void assertSourceContract() throws Exception {
        String text = new String(Files.readAllBytes(new File("app/src/main/java/com/espsa/mobilepos/app/ProductLocalStore.java").toPath()), StandardCharsets.UTF_8);
        assertTrue("legacy fallback exists", text.contains("ProductOrigin.LEGACY_IMPORT"));
        assertTrue("migration report path exists", text.contains("product_migration_report.json"));
        assertTrue("report records choices", text.contains("allowedChoices"));
        assertTrue("origin persisted", text.contains("item.put(\"origin\""));
        assertTrue("source key persisted", text.contains("item.put(\"sourceProductKey\""));
        assertTrue("stopped persisted", text.contains("item.put(\"stopped\""));
        assertTrue("flush precedes fd sync", text.indexOf("output.flush()") < text.indexOf("output.getFD().sync()"));
        assertTrue("write failures enter temporary cleanup", text.contains("try (FileOutputStream output"));
        assertTrue("atomic move exists", text.contains("StandardCopyOption.ATOMIC_MOVE"));
    }

    private interface Checked { void run() throws Exception; }
    private static void expectFailure(String name, Checked action) throws Exception { assertions++; try { action.run(); throw new AssertionError(name); } catch (Exception expected) { } }
    private static void assertTrue(String name, boolean value) { assertions++; if (!value) throw new AssertionError(name); }
    private static void assertEquals(String name, Object expected, Object actual) { assertions++; if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected " + expected + " actual " + actual); }
    private static void assertArrayEquals(String name, byte[] expected, byte[] actual) { assertions++; if (!java.util.Arrays.equals(expected, actual)) throw new AssertionError(name); }
    private static void deleteTree(File file) { if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child); } file.delete(); }
}
