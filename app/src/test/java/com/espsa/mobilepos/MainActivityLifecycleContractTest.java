package com.espsa.mobilepos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Host-only source contract for MainActivity's narrow foreground-sync ownership. */
public final class MainActivityLifecycleContractTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        String source = readMainActivitySource();
        String onCreate = methodBody(source, "onCreate(Bundle savedInstanceState)");
        String onResume = methodBody(source, "onResume()");
        String onPause = methodBody(source, "onPause()");

        assertTrue("services initialize during onCreate", onCreate.contains("services = ((MobilePosApplication) getApplication()).services();"));
        assertOrdered("onResume calls Activity super before foreground start", onResume,
                "super.onResume();", "services.computerSyncCoordinator().startForeground();");
        assertOrdered("onPause stops foreground work before Activity super", onPause,
                "services.computerSyncCoordinator().stopForeground();", "super.onPause();");
        assertOccurrences("onResume starts exactly once", onResume,
                "services.computerSyncCoordinator().startForeground();", 1);
        assertOccurrences("onPause stops exactly once", onPause,
                "services.computerSyncCoordinator().stopForeground();", 1);

        assertNotContains("activity does not create coordinator", source, "new ComputerSyncCoordinator");
        assertNotContains("activity does not retain coordinator", source, "ComputerSyncCoordinator ");
        assertNotContains("activity does not create an executor", source, "Executor");
        assertNotContains("activity does not perform SQL work", source, "SQL");
        assertNotContains("activity does not parse SQLite", source, "SQLite");
        assertNotContains("activity does not switch active snapshots", source, "activateForTest");
        assertNotContains("activity does not reset carts from lifecycle", onResume + onPause, "resetCart(");
        assertNotContains("activity does not read carts from lifecycle", onResume + onPause, "currentCart(");

        System.out.println("MainActivity lifecycle contract test passed: " + assertions + " assertions");
    }

    private static String readMainActivitySource() throws Exception {
        Path modulePath = Paths.get("app", "src", "main", "java", "com", "espsa", "mobilepos", "MainActivity.java");
        Path workspacePath = Paths.get("android-emergency-pos").resolve(modulePath);
        Path source = Files.exists(modulePath) ? modulePath : workspacePath;
        return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        if (signatureIndex < 0) throw new AssertionError("missing method signature: " + signature);
        int openBrace = source.indexOf('{', signatureIndex);
        if (openBrace < 0) throw new AssertionError("missing method body: " + signature);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(openBrace + 1, index);
        }
        throw new AssertionError("unclosed method body: " + signature);
    }

    private static void assertOrdered(String label, String source, String first, String second) {
        assertions++;
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) throw new AssertionError(label);
    }

    private static void assertOccurrences(String label, String source, String token, int expected) {
        int count = 0;
        for (int index = source.indexOf(token); index >= 0; index = source.indexOf(token, index + token.length())) count++;
        assertions++;
        if (count != expected) throw new AssertionError(label + ": expected=" + expected + " actual=" + count);
    }

    private static void assertNotContains(String label, String source, String token) {
        assertions++;
        if (source.contains(token)) throw new AssertionError(label + ": " + token);
    }

    private static void assertTrue(String label, boolean value) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }
}
