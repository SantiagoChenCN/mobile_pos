package com.espsa.mobilepos.app.sync;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.catalog.ProductCatalogService;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;
import com.espsa.mobilepos.core.model.Quantity;
import com.espsa.mobilepos.core.pricing.DefaultPriceCalculator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CB-03 durable lifecycle coverage.  Each recovery opens a new store over the same on-disk
 * state; this test deliberately uses only the public verified-pending promotion path.
 */
public final class CB03SnapshotLifecycleSmokeTest {
    private static final String FIRST = "ms2011-20260718T120000Z-0123456789ab";
    private static final String SECOND = "ms2011-20260718T120001Z-abcdefabcdef";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        assertInterruptedDownloadDoesNotStageCandidate();
        assertCompletedDownloadBeforeDurableStateKeepsOldActive();
        assertDurablePendingSurvivesRestartWithoutAutoActivation();
        assertDamagedActiveFallsBackToLastGoodThenLocal();
        assertNonEmptyCartWaitsAndCompletionOrCancellationSwitches();
        System.out.println("CB-03 snapshot lifecycle smoke test passed: " + assertions + " assertions");
    }

    private static void assertInterruptedDownloadDoesNotStageCandidate() throws Exception {
        Fixture fixture = new Fixture();
        try {
            File interrupted = fixture.cachePart(SECOND, "partial".getBytes(StandardCharsets.UTF_8));
            expectFailure("interrupted download is rejected before pending state", () ->
                    fixture.store.persistDownloaded(fixture.manifest(SECOND), fixture.manifestBytes(SECOND), interrupted));
            V2SnapshotStateStore.State state = fixture.reopen().readState();
            assertNull("interrupted download has no active", state.activeSnapshotId());
            assertNull("interrupted download has no pending", state.pendingSnapshotId());
            assertFalse("interrupted download has no immutable object", fixture.reopen().objectFileForTest(SECOND).exists());
            assertFalse("interrupted download has no recoverable candidate", new V2SnapshotReader(fixture.reopen()).recover().hasValidSnapshot());
        } finally { fixture.close(); }
    }

    private static void assertCompletedDownloadBeforeDurableStateKeepsOldActive() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.stageAndActivate(FIRST);
            fixture.cachePart(SECOND, fixture.database(SECOND)); // Process dies before persistDownloaded writes state.json.
            V2SnapshotStore reopened = fixture.reopen();
            V2SnapshotStateStore.State state = reopened.readState();
            V2SnapshotReader.RecoveryResult recovery = new V2SnapshotReader(reopened).recover();
            assertEquals("completed uncommitted download retains old active", FIRST, recovery.activeSnapshotId());
            assertFalse("old active is not a rollback", recovery.recoveredFromLastGood());
            assertNull("completed uncommitted download has no pending state", state.pendingSnapshotId());
            assertFalse("completed uncommitted download creates no immutable object", reopened.objectFileForTest(SECOND).exists());
        } finally { fixture.close(); }
    }

    private static void assertDurablePendingSurvivesRestartWithoutAutoActivation() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.stageAndActivate(FIRST);
            fixture.stage(SECOND);
            V2SnapshotStore reopened = fixture.reopen();
            V2SnapshotReader.RecoveryResult recovery = new V2SnapshotReader(reopened).recover();
            assertEquals("restart with pending retains active", FIRST, recovery.activeSnapshotId());
            assertFalse("restart does not promote pending", recovery.recoveredFromLastGood());
            assertEquals("restart preserves durable pending", SECOND, reopened.readState().pendingSnapshotId());
            assertTrue("restart preserves validated pending pair", reopened.validateStored(SECOND, reopened.readState().summary(SECOND)) != null);
        } finally { fixture.close(); }
    }

    private static void assertDamagedActiveFallsBackToLastGoodThenLocal() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.stageAndActivate(FIRST);
            fixture.stageAndActivate(SECOND);
            Files.write(fixture.store.objectFileForTest(SECOND).toPath(), "damaged".getBytes(StandardCharsets.UTF_8));
            V2SnapshotReader.RecoveryResult fallback = new V2SnapshotReader(fixture.reopen()).recover();
            assertEquals("damaged active falls back to last good", FIRST, fallback.activeSnapshotId());
            assertTrue("last good fallback is explicit", fallback.recoveredFromLastGood());
            Boundary boundary = fixture.boundary();
            assertEquals("boundary starts recovered last good", FIRST,
                    boundary.manager.startCartForRecoveredActiveOrLocal().pricingSnapshotRef().pricingSnapshotId());

            Files.write(fixture.store.objectFileForTest(FIRST).toPath(), "also-damaged".getBytes(StandardCharsets.UTF_8));
            V2SnapshotReader.RecoveryResult none = new V2SnapshotReader(fixture.reopen()).recover();
            assertFalse("two damaged protected snapshots have no v2 recovery", none.hasValidSnapshot());
            assertEquals("no verified v2 snapshot returns local safe cart", "local-library",
                    fixture.boundary().manager.startCartForRecoveredActiveOrLocal().pricingSnapshotRef().pricingSnapshotId());
        } finally { fixture.close(); }
    }

    private static void assertNonEmptyCartWaitsAndCompletionOrCancellationSwitches() throws Exception {
        Fixture cancellation = new Fixture();
        try {
            cancellation.stageAndActivate(FIRST);
            cancellation.stage(SECOND);
            Boundary boundary = cancellation.boundary();
            Cart inProgress = boundary.manager.startCartForRecoveredActiveOrLocal();
            boundary.checkout.addProductByBarcode(inProgress, "first", Quantity.one());
            assertSame("nonempty cart retains reference while pending", inProgress,
                    boundary.manager.activatePendingForEmptyCart(inProgress));
            assertEquals("nonempty cart leaves durable pending", SECOND, cancellation.reopen().readState().pendingSnapshotId());
            Cart afterCancellation = boundary.manager.onOrderFinishedOrCancelled(inProgress);
            assertEquals("cancellation switches only at boundary", SECOND, afterCancellation.pricingSnapshotRef().pricingSnapshotId());
            assertEquals("cancellation commits pending after prepared catalog", SECOND, cancellation.reopen().readState().activeSnapshotId());
            assertNull("cancellation clears pending after durable commit", cancellation.reopen().readState().pendingSnapshotId());
        } finally { cancellation.close(); }

        Fixture emptyCart = new Fixture();
        try {
            emptyCart.stageAndActivate(FIRST);
            emptyCart.stage(SECOND);
            Boundary boundary = emptyCart.boundary();
            Cart afterEmptyCartActivation = boundary.manager.activatePendingForEmptyCart(
                    boundary.manager.startCartForRecoveredActiveOrLocal());
            assertEquals("empty cart activates durable pending", SECOND, afterEmptyCartActivation.pricingSnapshotRef().pricingSnapshotId());
            assertEquals("empty cart durable activation commits second", SECOND, emptyCart.reopen().readState().activeSnapshotId());
            assertNull("empty cart durable activation clears pending", emptyCart.reopen().readState().pendingSnapshotId());
        } finally { emptyCart.close(); }

        Fixture completion = new Fixture();
        try {
            completion.stageAndActivate(FIRST);
            completion.stage(SECOND);
            Boundary boundary = completion.boundary();
            // Completion shares the same production order-boundary method as cancellation.
            Cart afterCompletion = boundary.manager.onOrderFinishedOrCancelled(boundary.manager.startCartForRecoveredActiveOrLocal());
            assertEquals("completion switches only at boundary", SECOND, afterCompletion.pricingSnapshotRef().pricingSnapshotId());
            assertEquals("completion uses verified durable pending", SECOND, completion.reopen().readState().activeSnapshotId());
        } finally { completion.close(); }
    }

    private static final class Fixture {
        final File root;
        final File files;
        final File cache;
        final V2SnapshotStore store;

        Fixture() throws Exception {
            root = Files.createTempDirectory("cb03-v2-").toFile();
            files = new File(root, "files");
            cache = new File(root, "cache");
            store = newStore();
        }

        V2SnapshotStore newStore() throws Exception {
            return new V2SnapshotStore(files, cache, new FakeInspector(), Fixture::decodeManifest);
        }

        V2SnapshotStore reopen() throws Exception { return newStore(); }

        void stage(String id) throws Exception {
            store.persistDownloaded(manifest(id), manifestBytes(id), cachePart(id, database(id)));
        }

        void stageAndActivate(String id) throws Exception {
            stage(id);
            store.activatePendingVerified(id);
        }

        File cachePart(String id, byte[] bytes) throws Exception {
            File part = new File(cache, "snapshot-v2-aa11bb22.part");
            Files.write(part.toPath(), bytes);
            return part;
        }

        byte[] database(String id) { return ("database-" + id).getBytes(StandardCharsets.UTF_8); }

        byte[] manifestBytes(String id) {
            byte[] database = database(id);
            String json = "{\"ok\":true,\"schemaVersion\":2,\"snapshotId\":\"" + id
                    + "\",\"sourceType\":\"ms2011_live\",\"createdAtUtc\":\"2026-07-18T12:00:00Z\",\"sizeBytes\":" + database.length
                    + ",\"sha256\":\"" + sha256(database) + "\",\"minimumAppVersion\":1,\"productCount\":1,\"categoryCount\":1,\"unitCount\":1,\"promotionCandidateCount\":1,\"verifiedPromotionCount\":0,\"validationIssueCount\":1,\"downloadPath\":\"/v2/snapshots/" + id + ".db\"}";
            return json.getBytes(StandardCharsets.UTF_8);
        }

        ComputerSyncManifestV2 manifest(String id) { return decodeManifest(manifestBytes(id)); }

        Boundary boundary() throws Exception {
            V2SnapshotStore durable = reopen();
            InMemoryProductRepository repository = new InMemoryProductRepository();
            CheckoutService checkout = new CheckoutService(repository, new DefaultPriceCalculator(), new InMemorySaleRepository());
            ProductCatalogService catalog = new ProductCatalogService(repository);
            Map<String, List<Product>> products = new LinkedHashMap<String, List<Product>>();
            products.put(FIRST, Collections.singletonList(product(1, "first", FIRST)));
            products.put(SECOND, Collections.singletonList(product(2, "second", SECOND)));
            ActiveSnapshotManager manager = new ActiveSnapshotManager(new DurableGateway(durable, products), catalog, repository, checkout);
            return new Boundary(manager, checkout);
        }

        void close() { deleteTree(root); }

        static ComputerSyncManifestV2 decodeManifest(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            int start = text.indexOf("ms2011-");
            int end = text.indexOf('"', start);
            if (start < 0 || end < start) throw new IllegalArgumentException("fixture manifest");
            String id = text.substring(start, end);
            byte[] database = ("database-" + id).getBytes(StandardCharsets.UTF_8);
            Map<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("ok", Boolean.TRUE); fields.put("schemaVersion", Integer.valueOf(2)); fields.put("snapshotId", id);
            fields.put("sourceType", "ms2011_live"); fields.put("createdAtUtc", "2026-07-18T12:00:00Z"); fields.put("sizeBytes", Long.valueOf(database.length));
            fields.put("sha256", sha256(database)); fields.put("minimumAppVersion", Integer.valueOf(1)); fields.put("productCount", Integer.valueOf(1));
            fields.put("categoryCount", Integer.valueOf(1)); fields.put("unitCount", Integer.valueOf(1)); fields.put("promotionCandidateCount", Integer.valueOf(1));
            fields.put("verifiedPromotionCount", Integer.valueOf(0)); fields.put("validationIssueCount", Integer.valueOf(1)); fields.put("downloadPath", "/v2/snapshots/" + id + ".db");
            try { return ComputerSyncManifestV2.fromFieldValues(fields); }
            catch (ComputerSyncException exception) { throw new IllegalArgumentException(exception); }
        }
    }

    private static final class DurableGateway implements ActiveSnapshotManager.SnapshotGateway {
        private final V2SnapshotStore store;
        private final Map<String, List<Product>> products;

        DurableGateway(V2SnapshotStore store, Map<String, List<Product>> products) {
            this.store = store;
            this.products = products;
        }

        @Override public ActiveSnapshotManager.SnapshotIds snapshotIds() {
            V2SnapshotStateStore.State state = store.readState();
            return state == null ? ActiveSnapshotManager.SnapshotIds.unreadable()
                    : ActiveSnapshotManager.SnapshotIds.readable(state.activeSnapshotId(), state.pendingSnapshotId());
        }

        @Override public String recoveredActiveSnapshotId() {
            V2SnapshotReader.RecoveryResult recovered = new V2SnapshotReader(store).recover();
            return recovered.hasValidSnapshot() ? recovered.activeSnapshotId() : null;
        }

        @Override public List<Product> readVerified(String id) throws Exception {
            V2SnapshotStateStore.State state = store.readState();
            if (state == null) throw new IllegalStateException("Missing durable v2 state");
            store.validateStored(id, state.summary(id));
            List<Product> result = products.get(id);
            if (result == null) throw new IllegalStateException("Missing fixture products");
            return result;
        }

        @Override public void activatePendingVerified(String id) throws Exception { store.activatePendingVerified(id); }
    }

    private static final class Boundary {
        final ActiveSnapshotManager manager;
        final CheckoutService checkout;
        Boundary(ActiveSnapshotManager manager, CheckoutService checkout) { this.manager = manager; this.checkout = checkout; }
    }

    private static final class FakeInspector implements V2SnapshotValidator.ReadOnlyDatabaseInspector {
        @Override public V2SnapshotValidator.DatabaseFacts inspect(File database) {
            Map<String, String> metadata = new LinkedHashMap<String, String>();
            metadata.put("schemaVersion", "2"); metadata.put("sourceHash", repeat('a', 64));
            try {
                String value = new String(Files.readAllBytes(database.toPath()), StandardCharsets.UTF_8);
                metadata.put("snapshotId", value.startsWith("database-") ? value.substring("database-".length()) : FIRST);
            } catch (Exception exception) { throw new IllegalStateException(exception); }
            return V2SnapshotValidator.DatabaseFacts.valid(metadata, V2SnapshotValidator.expectedCounts(1, 1, 1, 1, 0, 1));
        }
    }

    private static Product product(long gid, String barcode, String snapshotId) {
        String key = "ms2011:" + gid;
        return new Product(key, barcode, "Product " + gid, "", "un", Money.of("2099.99"), null, 0, false,
                ProductOrigin.MS2011_SYNC, key, snapshotId, false);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder();
            for (byte item : digest) out.append(String.format("%02x", item & 0xff));
            return out.toString();
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static String repeat(char value, int count) { char[] values = new char[count]; Arrays.fill(values, value); return new String(values); }
    private interface Checked { void run() throws Exception; }
    private static void expectFailure(String name, Checked checked) throws Exception { assertions++; try { checked.run(); throw new AssertionError(name); } catch (Exception expected) { } }
    private static void assertSame(String name, Object expected, Object actual) { assertions++; if (expected != actual) throw new AssertionError(name); }
    private static void assertTrue(String name, boolean value) { assertions++; if (!value) throw new AssertionError(name); }
    private static void assertFalse(String name, boolean value) { assertTrue(name, !value); }
    private static void assertNull(String name, Object value) { assertions++; if (value != null) throw new AssertionError(name); }
    private static void assertEquals(String name, Object expected, Object actual) { assertions++; if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name + " expected=" + expected + " actual=" + actual); }
    private static void deleteTree(File file) { if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child); } file.delete(); }
}
