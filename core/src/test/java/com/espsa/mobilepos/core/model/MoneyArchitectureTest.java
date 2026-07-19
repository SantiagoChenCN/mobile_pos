package com.espsa.mobilepos.core.model;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.editing.ProductDraft;
import com.espsa.mobilepos.core.editing.ProductEditingService;
import com.espsa.mobilepos.core.editing.ProductOptionProvider;
import com.espsa.mobilepos.core.editing.ProductValidationResult;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class MoneyArchitectureTest {
    private static final Pattern NUMERIC_MONEY_FACTORY = Pattern.compile("Money\\.of\\(\\s*[0-9]+[lL]?\\s*\\)");
    private static final Pattern LONG_MONEY_VARIABLE = Pattern.compile("\\blong\\s+\\w*(?:amount|price|total|discount)\\w*", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws Exception {
        assertNoLegacyMoneyMethods();
        assertProductEditingAcceptsExactDecimal();
        assertProductionSourcesAvoidLegacyMoneyPaths();
        System.out.println("Money architecture test passed");
    }

    private static void assertNoLegacyMoneyMethods() {
        for (Method method : Money.class.getDeclaredMethods()) {
            assertTrue(!"amount".equals(method.getName()), "Money.amount must be removed");
            assertTrue(!"legacyLongValueExact".equals(method.getName()), "legacyLongValueExact must be removed");
            if ("of".equals(method.getName())) {
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertTrue(parameter != long.class && parameter != Long.class, "Money.of(long) must be removed");
                }
            }
        }
    }

    private static void assertProductEditingAcceptsExactDecimal() {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        ProductEditingService editing = new ProductEditingService(
                repository,
                products -> { },
                new ProductOptionProvider(repository::all)
        );
        ProductValidationResult validation = editing.validateForCreate(new ProductDraft(
                "1001", "Decimal local", "almacen", "un", "2099,99", "1499.5", "2"
        ));
        assertTrue(validation.valid(), "product editing must accept point/comma decimal money");
        assertTrue("2099.99".equals(validation.parsedDraft().salePrice().canonicalText()),
                "sale price remains exact original currency");
        assertTrue("1499.5".equals(validation.parsedDraft().promotionPrice().canonicalText()),
                "promotion price remains exact original currency");
    }

    private static void assertProductionSourcesAvoidLegacyMoneyPaths() throws Exception {
        Path project = findProjectRoot();
        List<Path> roots = new ArrayList<Path>();
        roots.add(project.resolve("core/src/main/java"));
        roots.add(project.resolve("app/src/main/java"));
        List<String> violations = new ArrayList<String>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (java.util.stream.Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                    try {
                        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                        for (int index = 0; index < lines.size(); index++) {
                            String line = lines.get(index);
                            boolean pixelRounding = line.contains("displayMetrics") || line.contains("getDisplayMetrics");
                            if (line.contains("legacyLongValueExact(")
                                    || line.contains(".amount()")
                                    || line.contains("Double.parseDouble(")
                                    || (line.contains("Math.round(") && !pixelRounding)
                                    || NUMERIC_MONEY_FACTORY.matcher(line).find()
                                    || LONG_MONEY_VARIABLE.matcher(line).find()) {
                                violations.add(project.relativize(path) + ":" + (index + 1) + ":" + line.trim());
                            }
                        }
                    } catch (Exception exc) {
                        throw new RuntimeException(exc);
                    }
                });
            }
        }
        assertTrue(violations.isEmpty(), "legacy money production paths: " + violations);

        String store = Files.readString(
                project.resolve("app/src/main/java/com/espsa/mobilepos/app/ProductLocalStore.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(store.contains("product.salePrice().canonicalText()"),
                "new product JSON must persist canonical decimal strings");
    }

    private static Path findProjectRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path[] candidates = new Path[] {
                cwd,
                cwd.resolve("android-emergency-pos"),
                Paths.get("E:/AndroidEmergencyPos")
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve("core/src/main/java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Android project root not found from " + cwd);
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
