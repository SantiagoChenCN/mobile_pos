package com.espsa.mobilepos.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public final class DecimalUiArchitectureTest {
    private static final String RUNNER_CLASS = "com.espsa.mobilepos.ui.AstScannerRunner";
    private static final String AST_SCANNER_SOURCE = """
package com.espsa.mobilepos.ui;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class AstScannerRunner {
    private static final String MONEY_TYPE = "com.espsa.mobilepos.core.model.Money";
    private static final String QUANTITY_TYPE = "com.espsa.mobilepos.core.model.Quantity";
    private static final String MONEY_TEXT_TYPE = "com.espsa.mobilepos.ui.MoneyText";
    private static final String QUANTITY_TEXT_TYPE = "com.espsa.mobilepos.ui.QuantityText";
    private static final String SALE_LINE_TYPE = "com.espsa.mobilepos.core.ledger.SaleLine";
    private static final Pattern NUMERIC_CURRENCY_LITERAL = Pattern.compile(
            "(?i)(?:\\\\$\\\\s*[0-9]|\\\\bARS\\\\s*[0-9])"
    );

    private AstScannerRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("UI source root argument is required");
        }
        verifySyntheticVariantCoverage();

        Path uiRoot = Paths.get(args[0]);
        List<Path> javaFiles = javaFilesBelow(uiRoot);
        AnalysisResult result = analyzePaths(javaFiles);
        List<String> failures = new ArrayList<>(result.compilerErrors);
        failures.addAll(result.violations);
        if (!result.checkoutZeroFormatterSeen) {
            failures.add("CheckoutScreen must contain a resolved MoneyText.currency(Money.ZERO) call");
        }
        if (!result.salesQuantityFormatterSeen) {
            failures.add("SalesScreen must contain a resolved QuantityText.format(SaleLine.quantity()) call");
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Decimal UI architecture violations (" + failures.size() + "):\\n - "
                    + String.join("\\n - ", failures));
        }

        System.out.println("Decimal UI architecture test passed; scanned UI Java=" + javaFiles.size()
                + "; AST synthetic variants=" + syntheticForbiddenCases().length);
    }

    private static void verifySyntheticVariantCoverage() throws Exception {
        List<String> failures = new ArrayList<>();
        String[][] forbidden = syntheticForbiddenCases();
        for (int index = 0; index < forbidden.length; index++) {
            String className = "SyntheticViolation" + index;
            AnalysisResult result = analyzeMemory(
                    className,
                    syntheticSource(className, forbidden[index][1], forbidden[index][2])
            );
            if (!result.compilerErrors.isEmpty()) {
                failures.add(forbidden[index][0] + " did not attribute: " + result.compilerErrors);
            } else if (result.violations.isEmpty()) {
                failures.add("missed " + forbidden[index][0]);
            }
        }

        AnalysisResult safe = analyzeMemory(
                "SyntheticSafeFormatter",
                syntheticSource(
                        "SyntheticSafeFormatter",
                        "",
                        "String a = MoneyText.currency(salePrice());"
                                + " String q = QuantityText.format(quantity());"
                                + " StringBuilder b = new StringBuilder()"
                                + ".append(MoneyText.currency(promotionPrice()));"
                                + " String marker = \\"$\\";"
                )
        );
        if (!safe.compilerErrors.isEmpty() || !safe.violations.isEmpty()) {
            failures.add("approved formatter calls failed: errors=" + safe.compilerErrors
                    + ", violations=" + safe.violations);
        }

        if (!failures.isEmpty()) {
            throw new AssertionError("AST synthetic architecture coverage failures: " + failures);
        }
    }

    private static String[][] syntheticForbiddenCases() {
        return new String[][]{
                {"append Quantity", "", "new StringBuilder().append(quantity());"},
                {"label plus Money", "", "String value = \\"total \\" + salePrice();"},
                {"currency plus Money", "", "String value = \\"$\\" + promotionPrice();"},
                {"String.valueOf Quantity", "", "String value = String.valueOf(quantity());"},
                {"Money local plus String", "", "Money local = salePrice(); String value = \\"$\\" + local;"},
                {"Quantity local plus String", "", "Quantity initial = quantity(); String value = \\"x\\" + initial;"},
                {"String.format Money", "", "String value = String.format(\\"%s\\", salePrice());"},
                {"String.formatted Money", "", "String value = \\"%s\\".formatted(salePrice());"},
                {"Objects.toString Money", "", "String value = java.util.Objects.toString(salePrice());"},
                {"MessageFormat.format Money", "",
                        "String value = java.text.MessageFormat.format(\\"{0}\\", salePrice());"},
                {"Money.toString", "", "String value = salePrice().toString();"},
                {"new BigDecimal", "", "java.math.BigDecimal value = new java.math.BigDecimal(\\"1\\");"},
                {"Money.of", "", "Money value = Money.of(\\"1\\");"},
                {"Quantity.of", "", "Quantity value = Quantity.of(\\"1\\");"},
                {"spaced primitive parser", "", "double value = Double . parseDouble ( \\"1\\" );"},
                {"wildcard primitive parser", "import static java.lang.Double.*;",
                        "double value = parseDouble(\\"1\\");"},
                {"wildcard Money factory", "import static com.espsa.mobilepos.core.model.Money.*;",
                        "Money value = of(\\"1\\");"},
                {"primitive parser method reference", "",
                        "java.util.function.Function<String, Double> f = Double::parseDouble;"},
                {"Money factory method reference", "",
                        "java.util.function.Function<String, Money> f = Money::of;"},
                {"BigDecimal constructor reference", "",
                        "java.util.function.Function<String, java.math.BigDecimal> f = java.math.BigDecimal::new;"},
                {"nested raw concat in formatter", "",
                        "String value = MoneyText.currency(select(Money.ZERO, \\"$\\" + salePrice()));"},
                {"hardcoded dollar literal", "", "String value = \\"$ 0\\";"},
                {"hardcoded ARS literal", "", "String value = \\"ARS 0\\";"},
                {"hardcoded currency concat", "", "String value = \\"$\\" + 0;"}
        };
    }

    private static String syntheticSource(String className, String extraImports, String body) {
        return "package com.espsa.mobilepos.ui;"
                + " import com.espsa.mobilepos.core.model.Money;"
                + " import com.espsa.mobilepos.core.model.Quantity;"
                + extraImports
                + " final class " + className + " {"
                + " private Money salePrice() { return Money.ZERO; }"
                + " private Money promotionPrice() { return Money.ZERO; }"
                + " private Quantity quantity() { return Quantity.one(); }"
                + " private Money select(Money first, Object ignored) { return first; }"
                + " void verify() { " + body + " }"
                + " }";
    }

    private static AnalysisResult analyzePaths(List<Path> paths) throws Exception {
        JavaCompiler compiler = requireCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            return analyze(
                    compiler,
                    fileManager,
                    diagnostics,
                    fileManager.getJavaFileObjectsFromPaths(paths)
            );
        }
    }

    private static AnalysisResult analyzeMemory(String className, String source) throws Exception {
        JavaCompiler compiler = requireCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            return analyze(
                    compiler,
                    fileManager,
                    diagnostics,
                    List.of(new MemoryJavaSource(className, source))
            );
        }
    }

    private static JavaCompiler requireCompiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("MF-01 architecture test requires a full JDK 17");
        }
        return compiler;
    }

    private static AnalysisResult analyze(
            JavaCompiler compiler,
            StandardJavaFileManager fileManager,
            DiagnosticCollector<JavaFileObject> diagnostics,
            Iterable<? extends JavaFileObject> sources
    ) throws Exception {
        List<String> options = Arrays.asList(
                "-proc:none",
                "-implicit:none",
                "-encoding", "UTF-8",
                "-source", "17",
                "-target", "17",
                "-classpath", System.getProperty("java.class.path")
        );
        JavacTask task = (JavacTask) compiler.getTask(
                null, fileManager, diagnostics, options, null, sources
        );
        List<CompilationUnitTree> units = new ArrayList<>();
        for (CompilationUnitTree unit : task.parse()) {
            units.add(unit);
        }
        task.analyze();

        AnalysisResult result = new AnalysisResult();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                String sourceName = diagnostic.getSource() == null
                        ? "<unknown>"
                        : simpleSourceName(diagnostic.getSource().getName());
                result.addCompilerError(sourceName + ":" + diagnostic.getLineNumber() + ": "
                        + diagnostic.getMessage(Locale.ROOT));
            }
        }
        if (!result.compilerErrors.isEmpty()) {
            return result;
        }

        Trees trees = Trees.instance(task);
        Types types = task.getTypes();
        Elements elements = task.getElements();
        TypeElement moneyElement = elements.getTypeElement(MONEY_TYPE);
        TypeElement quantityElement = elements.getTypeElement(QUANTITY_TYPE);
        if (moneyElement == null || quantityElement == null) {
            result.addCompilerError("Money/Quantity types are unavailable on the architecture-test classpath");
            return result;
        }

        TypeMirror moneyType = types.erasure(moneyElement.asType());
        TypeMirror quantityType = types.erasure(quantityElement.asType());
        for (CompilationUnitTree unit : units) {
            new UiAstScanner(
                    unit,
                    trees,
                    types,
                    moneyType,
                    quantityType,
                    result
            ).scan(unit, null);
        }
        return result;
    }

    private static final class UiAstScanner extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final Trees trees;
        private final Types types;
        private final TypeMirror moneyType;
        private final TypeMirror quantityType;
        private final AnalysisResult result;
        private final String sourceName;
        private final boolean decimalInfrastructure;
        private final boolean formatterClass;

        private UiAstScanner(
                CompilationUnitTree unit,
                Trees trees,
                Types types,
                TypeMirror moneyType,
                TypeMirror quantityType,
                AnalysisResult result
        ) {
            this.unit = unit;
            this.trees = trees;
            this.types = types;
            this.moneyType = moneyType;
            this.quantityType = quantityType;
            this.result = result;
            this.sourceName = simpleSourceName(unit.getSourceFile().getName());
            this.decimalInfrastructure = "MoneyText.java".equals(sourceName)
                    || "QuantityText.java".equals(sourceName)
                    || "NumberTextParser.java".equals(sourceName);
            this.formatterClass = "MoneyText.java".equals(sourceName)
                    || "QuantityText.java".equals(sourceName);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            ExecutableElement method = executableAt(getCurrentPath(), node);
            if (method != null) {
                String owner = ownerName(method);
                String name = method.getSimpleName().toString();

                if (!decimalInfrastructure && isPrimitiveParser(owner, name)) {
                    addViolation(node, "primitive numeric parser is forbidden in UI pages");
                }
                if (!decimalInfrastructure && isDomainFactory(owner, name)) {
                    addViolation(node, "UI pages must parse Money/Quantity through shared text parsers");
                }
                if (!formatterClass && "canonicalText".equals(name)
                        && (MONEY_TYPE.equals(owner) || QUANTITY_TYPE.equals(owner))) {
                    addViolation(node, "canonicalText must stay inside formatter classes");
                }
                if (isRawConversionSink(owner, name)) {
                    for (ExpressionTree argument : node.getArguments()) {
                        if (containsRawBusiness(argument)) {
                            addViolation(node, "business Money/Quantity display must use MoneyText/QuantityText");
                            break;
                        }
                    }
                }
                if ("toString".equals(name) && node.getArguments().isEmpty()
                        && node.getMethodSelect() instanceof MemberSelectTree) {
                    ExpressionTree receiver = ((MemberSelectTree) node.getMethodSelect()).getExpression();
                    if (isBusinessExpression(pathFor(receiver))) {
                        addViolation(node, "business Money/Quantity toString is forbidden in UI pages");
                    }
                }

                updateRequiredCallEvidence(node, owner, name);
            }
            return super.visitMethodInvocation(node, unused);
        }

        @Override
        public Void visitBinary(BinaryTree node, Void unused) {
            if (node.getKind() == Tree.Kind.PLUS) {
                if (containsRawBusiness(node.getLeftOperand())
                        || containsRawBusiness(node.getRightOperand())) {
                    addViolation(node, "business Money/Quantity concatenation must use a formatter");
                }
                if (isHardcodedCurrencyConcat(node)) {
                    addViolation(node, "numeric currency display must use MoneyText.currency");
                }
            }
            return super.visitBinary(node, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void unused) {
            if (!decimalInfrastructure && "java.math.BigDecimal".equals(ownerName(elementAt(getCurrentPath())))) {
                addViolation(node, "UI pages must not construct BigDecimal directly");
            }
            return super.visitNewClass(node, unused);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree node, Void unused) {
            Element element = elementAt(getCurrentPath());
            if (element instanceof ExecutableElement) {
                ExecutableElement method = (ExecutableElement) element;
                String owner = ownerName(method);
                String name = method.getSimpleName().toString();
                if (!decimalInfrastructure && isPrimitiveParser(owner, name)) {
                    addViolation(node, "primitive numeric parser reference is forbidden in UI pages");
                }
                if (!decimalInfrastructure && isDomainFactory(owner, name)) {
                    addViolation(node, "Money/Quantity factory reference is forbidden in UI pages");
                }
                if (!decimalInfrastructure && "java.math.BigDecimal".equals(owner)
                        && method.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR) {
                    addViolation(node, "BigDecimal constructor reference is forbidden in UI pages");
                }
                if (!formatterClass && "canonicalText".equals(name)
                        && (MONEY_TYPE.equals(owner) || QUANTITY_TYPE.equals(owner))) {
                    addViolation(node, "canonicalText reference must stay inside formatter classes");
                }
            }
            return super.visitMemberReference(node, unused);
        }

        @Override
        public Void visitLiteral(LiteralTree node, Void unused) {
            Object value = node.getValue();
            if (value instanceof String
                    && NUMERIC_CURRENCY_LITERAL.matcher((String) value).find()) {
                addViolation(node, "numeric currency display must use MoneyText.currency");
            }
            return super.visitLiteral(node, unused);
        }

        private void updateRequiredCallEvidence(MethodInvocationTree node, String owner, String name) {
            if ("CheckoutScreen.java".equals(sourceName)
                    && MONEY_TEXT_TYPE.equals(owner)
                    && "currency".equals(name)
                    && node.getArguments().size() == 1
                    && isMoneyZero(node.getArguments().get(0))) {
                result.checkoutZeroFormatterSeen = true;
            }
            if ("SalesScreen.java".equals(sourceName)
                    && QUANTITY_TEXT_TYPE.equals(owner)
                    && "format".equals(name)
                    && node.getArguments().size() == 1
                    && isSaleLineQuantityCall(node.getArguments().get(0))) {
                result.salesQuantityFormatterSeen = true;
            }
        }

        private boolean isMoneyZero(ExpressionTree expression) {
            Element element = elementAt(pathFor(expression));
            return element != null
                    && "ZERO".contentEquals(element.getSimpleName())
                    && MONEY_TYPE.equals(ownerName(element));
        }

        private boolean isSaleLineQuantityCall(ExpressionTree expression) {
            if (!(expression instanceof MethodInvocationTree)) {
                return false;
            }
            ExecutableElement method = executableAt(pathFor(expression), (MethodInvocationTree) expression);
            return method != null
                    && "quantity".contentEquals(method.getSimpleName())
                    && SALE_LINE_TYPE.equals(ownerName(method));
        }

        private boolean containsRawBusiness(Tree tree) {
            return Boolean.TRUE.equals(new TreeScanner<Boolean, Void>() {
                @Override
                public Boolean scan(Tree candidate, Void unused) {
                    if (candidate == null) {
                        return false;
                    }
                    TreePath path = pathFor(candidate);
                    if (path == null) {
                        result.addCompilerError(sourceName + ": unable to locate AST path for " + candidate);
                        return false;
                    }
                    if (candidate instanceof MethodInvocationTree && isApprovedFormatter(path)) {
                        return false;
                    }
                    if (candidate instanceof ExpressionTree && isBusinessExpression(path)) {
                        return true;
                    }
                    return super.scan(candidate, unused);
                }

                @Override
                public Boolean reduce(Boolean left, Boolean right) {
                    return Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right);
                }
            }.scan(tree, null));
        }

        private boolean isApprovedFormatter(TreePath path) {
            if (!(path.getLeaf() instanceof MethodInvocationTree)) {
                return false;
            }
            ExecutableElement method = executableAt(path, (MethodInvocationTree) path.getLeaf());
            if (method == null) {
                return false;
            }
            String owner = ownerName(method);
            String name = method.getSimpleName().toString();
            return MONEY_TEXT_TYPE.equals(owner) && ("currency".equals(name) || "format".equals(name))
                    || QUANTITY_TEXT_TYPE.equals(owner) && "format".equals(name);
        }

        private boolean isBusinessExpression(TreePath path) {
            if (path == null) {
                result.addCompilerError(sourceName + ": missing AST path for business expression");
                return false;
            }
            TypeMirror mirror = trees.getTypeMirror(path);
            if (mirror == null || mirror.getKind() == TypeKind.ERROR) {
                result.addCompilerError(sourceName + ": unresolved type for expression " + path.getLeaf());
                return false;
            }
            if (mirror.getKind() != TypeKind.DECLARED) {
                return false;
            }
            TypeMirror erased = types.erasure(mirror);
            return types.isSameType(erased, moneyType) || types.isSameType(erased, quantityType);
        }

        private boolean isHardcodedCurrencyConcat(BinaryTree node) {
            return isCurrencyMarker(node.getLeftOperand()) && isNumericLiteral(node.getRightOperand())
                    || isCurrencyMarker(node.getRightOperand()) && isNumericLiteral(node.getLeftOperand());
        }

        private boolean isCurrencyMarker(ExpressionTree expression) {
            if (!(expression instanceof LiteralTree)) {
                return false;
            }
            Object value = ((LiteralTree) expression).getValue();
            if (!(value instanceof String)) {
                return false;
            }
            String marker = ((String) value).trim();
            return "$".equals(marker) || "ARS".equalsIgnoreCase(marker);
        }

        private boolean isNumericLiteral(ExpressionTree expression) {
            return expression instanceof LiteralTree
                    && ((LiteralTree) expression).getValue() instanceof Number;
        }

        private ExecutableElement executableAt(TreePath path, MethodInvocationTree node) {
            Element element = elementAt(path);
            if (element instanceof ExecutableElement) {
                return (ExecutableElement) element;
            }
            TreePath selectPath = new TreePath(path, node.getMethodSelect());
            element = elementAt(selectPath);
            if (element instanceof ExecutableElement) {
                return (ExecutableElement) element;
            }
            result.addCompilerError(sourceName + ": unresolved method invocation " + node);
            return null;
        }

        private Element elementAt(TreePath path) {
            if (path == null) {
                return null;
            }
            return trees.getElement(path);
        }

        private TreePath pathFor(Tree tree) {
            return trees.getPath(unit, tree);
        }

        private void addViolation(Tree tree, String message) {
            long position = trees.getSourcePositions().getStartPosition(unit, tree);
            long line = position < 0 ? 0 : unit.getLineMap().getLineNumber(position);
            result.addViolation(sourceName + ":" + line + ": " + message);
        }
    }

    private static boolean isPrimitiveParser(String owner, String name) {
        return "java.lang.Long".equals(owner) && "parseLong".equals(name)
                || "java.lang.Double".equals(owner) && "parseDouble".equals(name)
                || "java.lang.Float".equals(owner) && "parseFloat".equals(name);
    }

    private static boolean isDomainFactory(String owner, String name) {
        return "of".equals(name) && (MONEY_TYPE.equals(owner) || QUANTITY_TYPE.equals(owner));
    }

    private static boolean isRawConversionSink(String owner, String name) {
        return ("java.lang.StringBuilder".equals(owner) || "java.lang.StringBuffer".equals(owner))
                && "append".equals(name)
                || "java.lang.String".equals(owner)
                && ("valueOf".equals(name) || "format".equals(name) || "formatted".equals(name))
                || "java.util.Objects".equals(owner) && "toString".equals(name)
                || "java.text.MessageFormat".equals(owner) && "format".equals(name);
    }

    private static String ownerName(Element element) {
        Element current = element;
        while (current != null && !(current instanceof TypeElement)) {
            current = current.getEnclosingElement();
        }
        return current instanceof TypeElement
                ? ((TypeElement) current).getQualifiedName().toString()
                : "";
    }

    private static String simpleSourceName(String name) {
        String normalized = name.replace('\\\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private static Path findUiSourceRoot() {
        Path moduleRoot = Paths.get(
                "app", "src", "main", "java", "com", "espsa", "mobilepos", "ui"
        );
        if (Files.isDirectory(moduleRoot)) {
            return moduleRoot;
        }
        Path workspaceRoot = Paths.get("android-emergency-pos").resolve(moduleRoot);
        if (Files.isDirectory(workspaceRoot)) {
            return workspaceRoot;
        }
        throw new IllegalStateException("Android UI source root not found");
    }

    private static List<Path> javaFilesBelow(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(result::add);
        }
        return result;
    }

    private static final class AnalysisResult {
        private final List<String> violations = new ArrayList<>();
        private final List<String> compilerErrors = new ArrayList<>();
        private boolean checkoutZeroFormatterSeen;
        private boolean salesQuantityFormatterSeen;

        private void addViolation(String violation) {
            if (!violations.contains(violation)) {
                violations.add(violation);
            }
        }

        private void addCompilerError(String error) {
            if (!compilerErrors.contains(error)) {
                compilerErrors.add(error);
            }
        }
    }

    private static final class MemoryJavaSource extends SimpleJavaFileObject {
        private final String source;

        private MemoryJavaSource(String className, String source) {
            super(URI.create("string:///" + className + ".java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
""";

    private DecimalUiArchitectureTest() {
    }

    public static void main(String[] args) throws Exception {
        Path uiRoot = findUiSourceRoot().toAbsolutePath().normalize();
        Path temporaryRoot = Files.createTempDirectory("mf01-decimal-ui-ast-").toAbsolutePath().normalize();
        try {
            Path sourceRoot = temporaryRoot.resolve("src");
            Path classesRoot = temporaryRoot.resolve("classes");
            Path packageRoot = sourceRoot.resolve(Paths.get("com", "espsa", "mobilepos", "ui"));
            Files.createDirectories(packageRoot);
            Files.createDirectories(classesRoot);
            Path runnerSource = packageRoot.resolve("AstScannerRunner.java");
            Files.write(runnerSource, AST_SCANNER_SOURCE.getBytes(StandardCharsets.UTF_8));

            String currentClassPath = System.getProperty("java.class.path");
            Path javac = jdkTool("javac");
            Process compile = new ProcessBuilder(
                    javac.toString(),
                    "-proc:none",
                    "-implicit:none",
                    "-encoding", "UTF-8",
                    "-source", "17",
                    "-target", "17",
                    "-classpath", currentClassPath,
                    "-d", classesRoot.toString(),
                    runnerSource.toString()
            ).redirectErrorStream(true).start();
            String compileOutput = readAll(compile.getInputStream());
            int compileExit = compile.waitFor();
            if (compileExit != 0) {
                throw new AssertionError("MF-01 AST runner compilation failed (exit=" + compileExit + "):\n"
                        + compileOutput);
            }

            Path java = jdkTool("java");
            String runnerClassPath = classesRoot + File.pathSeparator + currentClassPath;
            Process run = new ProcessBuilder(
                    java.toString(),
                    "-classpath", runnerClassPath,
                    RUNNER_CLASS,
                    uiRoot.toString()
            ).redirectErrorStream(true).start();
            String runOutput = readAll(run.getInputStream());
            int runExit = run.waitFor();
            if (runExit != 0) {
                throw new AssertionError("MF-01 AST architecture scan failed (exit=" + runExit + "):\n"
                        + runOutput);
            }
            System.out.print(runOutput);
        } finally {
            deleteTemporaryTree(temporaryRoot);
        }
    }

    private static Path jdkTool(String name) {
        String executable = isWindows() ? name + ".exe" : name;
        Path tool = Paths.get(System.getProperty("java.home"), "bin", executable)
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(tool)) {
            throw new IllegalStateException("MF-01 architecture test requires full JDK tool: " + tool);
        }
        return tool;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Path findUiSourceRoot() {
        Path moduleRoot = Paths.get("app", "src", "main", "java", "com", "espsa", "mobilepos", "ui");
        if (Files.isDirectory(moduleRoot)) {
            return moduleRoot;
        }
        Path workspaceRoot = Paths.get("android-emergency-pos").resolve(moduleRoot);
        if (Files.isDirectory(workspaceRoot)) {
            return workspaceRoot;
        }
        throw new IllegalStateException("Android UI source root not found");
    }

    private static void deleteTemporaryTree(Path root) {
        if (root == null || !root.isAbsolute()
                || !root.getFileName().toString().startsWith("mf01-decimal-ui-ast-")) {
            throw new IllegalArgumentException("Refusing to delete unexpected temporary path: " + root);
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exc) {
                    throw new IllegalStateException("Unable to delete temporary test path: " + path, exc);
                }
            });
        } catch (IOException exc) {
            throw new IllegalStateException("Unable to traverse temporary test path: " + root, exc);
        }
    }
}
