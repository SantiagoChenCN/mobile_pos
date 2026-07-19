package com.espsa.mobilepos.app.sync;

import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Host-only mapping coverage; Android SQLite runtime behavior remains a device/runtime evidence item. */
public final class V2ProductSnapshotReaderSmokeTest {
    private static final String SNAPSHOT = "ms2011-20260718T120000Z-0123456789ab";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        List<Product> products = V2ProductSnapshotReader.mapRowsForTest(Arrays.asList(
                row("ms2011:7", "7", "779", "Exact product", null, null, "2099.99", "0"),
                row("ms2011:8", "8", "", "Stopped product", "cat", "un", "0.125", "-1")
        ), SNAPSHOT);
        assertEquals("two immutable mapped products", 2, products.size());
        assertEquals("sync origin", ProductOrigin.MS2011_SYNC, products.get(0).origin());
        assertEquals("stable source key", "ms2011:7", products.get(0).sourceProductKey());
        assertEquals("source snapshot id", SNAPSHOT, products.get(0).sourceSnapshotId());
        assertEquals("exact decimal mapping", "2099.99", products.get(0).salePrice().canonicalText());
        assertEquals("empty category remains empty", "", products.get(0).category());
        assertEquals("empty unit remains empty", "", products.get(0).unitName());
        assertNull("simple price stays unset", products.get(0).promotionPrice());
        assertEquals("simple threshold stays zero", 0, products.get(0).promotionMinQuantity());
        assertTrue("nonzero stop flag maps stopped", products.get(1).stopped());
        assertEquals("empty barcode remains a source value", "", products.get(1).barcode());
        expectFailure("list is immutable", () -> products.add(products.get(0)));
        expectFailure("bad gid", () -> V2ProductSnapshotReader.mapRowsForTest(Collections.singletonList(row("ms2011:0", "0", "x", "x", "", "", "1", "0")), SNAPSHOT));
        expectFailure("source key must equal gid", () -> V2ProductSnapshotReader.mapRowsForTest(Collections.singletonList(row("ms2011:8", "7", "x", "x", "", "", "1", "0")), SNAPSHOT));
        expectFailure("duplicate source key", () -> V2ProductSnapshotReader.mapRowsForTest(Arrays.asList(row("ms2011:7", "7", "a", "a", "", "", "1", "0"), row("ms2011:7", "7", "b", "b", "", "", "1", "0")), SNAPSHOT));
        expectFailure("invalid decimal", () -> V2ProductSnapshotReader.mapRowsForTest(Collections.singletonList(row("ms2011:7", "7", "x", "x", "", "", "1,2", "0")), SNAPSHOT));
        expectFailure("invalid stop flag", () -> V2ProductSnapshotReader.mapRowsForTest(Collections.singletonList(row("ms2011:7", "7", "x", "x", "", "", "1", "1.0")), SNAPSHOT));
        assertSourceSafetyContract();
        System.out.println("V2 product snapshot reader smoke test passed: " + assertions + " assertions");
    }

    private static V2ProductSnapshotReader.ProductRow row(String key, String gid, String barcode, String name, String category, String unit, String price, String stopped) {
        return new V2ProductSnapshotReader.ProductRow(key, gid, barcode, name, category, unit, price, stopped);
    }

    private static void assertSourceSafetyContract() throws Exception {
        String text = new String(Files.readAllBytes(new File("app/src/main/java/com/espsa/mobilepos/app/sync/V2ProductSnapshotReader.java").toPath()), StandardCharsets.UTF_8);
        assertTrue("reader uses read-only open", text.contains("SQLiteDatabase.OPEN_READONLY"));
        assertFalse("reader never uses read-write open", text.contains("OPEN_READWRITE"));
        assertFalse("reader never creates database", text.contains("CREATE_IF_NECESSARY"));
        assertFalse("reader never executes SQL writes", text.contains("execSQL"));
        assertTrue("reader ignores simple promotion columns", !text.contains("simple_price_decimal") && !text.contains("simple_threshold_decimal"));
        String appServices = new String(Files.readAllBytes(new File("app/src/main/java/com/espsa/mobilepos/app/AppServices.java").toPath()), StandardCharsets.UTF_8);
        assertTrue("startup boundary manager is wired", appServices.contains("createActiveSnapshotManager"));
    }

    private interface Checked { void run() throws Exception; }
    private static void expectFailure(String name, Checked checked) { assertions++; try { checked.run(); throw new AssertionError(name); } catch (Exception expected) { } }
    private static void assertTrue(String name, boolean value) { assertions++; if (!value) throw new AssertionError(name); }
    private static void assertFalse(String name, boolean value) { assertTrue(name, !value); }
    private static void assertNull(String name, Object value) { assertions++; if (value != null) throw new AssertionError(name); }
    private static void assertEquals(String name, Object expected, Object actual) { assertions++; if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual); }
}
