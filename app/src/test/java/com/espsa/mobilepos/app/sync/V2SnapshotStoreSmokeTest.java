package com.espsa.mobilepos.app.sync;

import com.espsa.mobilepos.core.model.V2Manifest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Host-only seam coverage. It does not claim Android SQLite runtime verification. */
public final class V2SnapshotStoreSmokeTest {
    private static final String FIRST = "ms2011-20260718T120000Z-0123456789ab";
    private static final String SECOND = "ms2011-20260718T120001Z-abcdefabcdef";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("v2-snapshot-store-").toFile();
        try {
            Fixture fixture = new Fixture(root);
            fixture.stage(FIRST);
            assertEquals("pending is staged", FIRST, fixture.store.readState().pendingSnapshotId());
            assertNull("pending is never activated by recovery", new V2SnapshotReader(fixture.store).recover().activeSnapshotId());

            fixture.store.activatePendingVerified(FIRST);
            assertEquals("normal active recovery", FIRST, new V2SnapshotReader(fixture.store).recover().activeSnapshotId());

            assertPendingActivationRejectsUnsafePairs(new Fixture(new File(root, "pending-activation")));

            Fixture fallback = new Fixture(new File(root, "fallback"));
            fallback.stage(FIRST); fallback.store.activateForTest(FIRST); fallback.stage(SECOND); fallback.store.activateForTest(SECOND); fallback.corruptObject(SECOND);
            assertEquals("damaged active falls back to last good", FIRST, new V2SnapshotReader(fallback.store).recover().activeSnapshotId());

            assertCrashRecoveryStates(root);
            assertValidatorRejections(fixture);
            assertFinalSafetyRejections(fixture, root);
            assertPendingWritePrecedesFinalMove();

            fixture.stage(SECOND);
            fixture.deleteObject(SECOND);
            assertEquals("active survives incomplete pending cleanup", FIRST, new V2SnapshotReader(fixture.store).recover().activeSnapshotId());
            assertFalse("incomplete pending object is absent", fixture.store.objectFileForTest(SECOND).exists());

            fixture.corruptObject(FIRST);
            fixture.stageAndActivate(SECOND);
            fixture.corruptObject(SECOND);
            assertNull("all invalid snapshots recover to none", new V2SnapshotReader(fixture.store).recover().activeSnapshotId());

            expectFailure("snapshot traversal", () -> fixture.store.objectFileForTest("../outside"));
            expectFailure("manifest mismatch", () -> fixture.store.persistDownloaded(fixture.manifest(FIRST), "{}".getBytes(StandardCharsets.UTF_8), fixture.cachePart(FIRST)));
            expectFailure("outside-cache source", () -> fixture.store.persistDownloaded(fixture.manifest(SECOND), fixture.manifestBytes(SECOND), new File(root, "outside.part")));
            File wrongName = new File(fixture.cache, "wrong.part"); Files.write(wrongName.toPath(), database(SECOND));
            expectFailure("wrong cache source name", () -> fixture.store.persistDownloaded(fixture.manifest(SECOND), fixture.manifestBytes(SECOND), wrongName));

            File state = fixture.store.stateFileForTest();
            Files.write(state.toPath(), "{\"activeSnapshotId\":123}".getBytes(StandardCharsets.UTF_8));
            assertNull("damaged state fails closed", new V2SnapshotReader(fixture.store).recover().activeSnapshotId());
            Files.write(state.toPath(), new byte[V2SnapshotStateStore.MAX_STATE_BYTES + 1]);
            assertNull("oversized state fails closed", new V2SnapshotReader(fixture.store).recover().activeSnapshotId());
            Files.write(state.toPath(), "{\"activeSnapshotId\":null,\"activeSnapshotId\":null}".getBytes(StandardCharsets.UTF_8));
            assertNull("duplicate null key fails closed", new V2SnapshotReader(fixture.store).recover().activeSnapshotId());

            expectFailure("product count overflow", () -> new V2SnapshotStateStore.SnapshotSummary(FIRST, sha256(database(FIRST)), database(FIRST).length, 2, 1, 250001, 1, 1, 1, 0, 1));

            V2SnapshotStateStore.State oldState = V2SnapshotStateStore.State.empty();
            new V2SnapshotStateStore(root).write(oldState);
            byte[] oldBytes = Files.readAllBytes(new File(root, "state.json").toPath());
            V2SnapshotStateStore stateStore = new V2SnapshotStateStore(root, (source, destination) -> { throw new java.io.IOException("fixture"); });
            expectFailure("atomic state replacement failure", () -> stateStore.write(oldState.withFailures(1)));
            assertArrayEquals("failed atomic state write preserves old state", oldBytes, Files.readAllBytes(new File(root, "state.json").toPath()));

            System.out.println("V2 snapshot store smoke test passed: " + assertions + " assertions");
        } finally {
            deleteTree(root);
        }
    }

    private static void assertCrashRecoveryStates(File root) throws Exception {
        Fixture crash = new Fixture(new File(root, "crash"));
        V2SnapshotStateStore state = new V2SnapshotStateStore(new File(crash.root, "computer-sync-v2"));
        V2SnapshotStateStore.SnapshotSummary first = V2SnapshotValidator.summary(crash.manifest(FIRST));
        state.write(V2SnapshotStateStore.State.empty().withPending(first));
        assertNull("pending before object move never activates", new V2SnapshotReader(crash.store).recover().activeSnapshotId());
        assertNull("pending before object move is cleared", crash.store.readState().pendingSnapshotId());
        state.write(V2SnapshotStateStore.State.empty().withPending(first));
        Files.write(crash.store.objectFileForTest(FIRST).toPath(), database(FIRST));
        assertNull("pending between pair moves never activates", new V2SnapshotReader(crash.store).recover().activeSnapshotId());
        assertFalse("partial object is cleaned", crash.store.objectFileForTest(FIRST).exists());
        crash.stage(FIRST);
        assertNull("complete pending remains pending", new V2SnapshotReader(crash.store).recover().activeSnapshotId());
        crash.stage(FIRST);
        assertTrue("pending retry is idempotent", crash.store.objectFileForTest(FIRST).exists() && crash.store.manifestFileForTest(FIRST).exists());
        crash.store.activateForTest(FIRST);
        expectFailure("pending cannot equal active", () -> crash.store.readState().withPending(first));
        Files.write(crash.store.stateFileForTest().toPath(), crash.store.readState().toJson().replace("\"pendingSnapshotId\":null", "\"pendingSnapshotId\":\"" + FIRST + "\"").getBytes(StandardCharsets.UTF_8));
        new V2SnapshotReader(crash.store).recover();
        assertTrue("invalid protected-pending state never deletes active", crash.store.objectFileForTest(FIRST).exists());
    }

    private static void assertPendingActivationRejectsUnsafePairs(Fixture fixture) throws Exception {
        fixture.stage(FIRST);
        fixture.store.activatePendingVerified(FIRST);
        fixture.stage(SECOND);
        V2SnapshotStateStore.State beforeWrongId = fixture.store.readState();
        expectFailure("activation requires the current pending id", () -> fixture.store.activatePendingVerified(FIRST));
        assertEquals("wrong activation id preserves active", beforeWrongId.activeSnapshotId(), fixture.store.readState().activeSnapshotId());
        assertEquals("wrong activation id preserves pending", beforeWrongId.pendingSnapshotId(), fixture.store.readState().pendingSnapshotId());

        fixture.corruptObject(SECOND);
        V2SnapshotStateStore.State beforeCorruption = fixture.store.readState();
        expectFailure("damaged pending pair is not activated", () -> fixture.store.activatePendingVerified(SECOND));
        assertEquals("damaged pair preserves active", beforeCorruption.activeSnapshotId(), fixture.store.readState().activeSnapshotId());
        assertEquals("damaged pair preserves pending", beforeCorruption.pendingSnapshotId(), fixture.store.readState().pendingSnapshotId());
    }

    private static void assertValidatorRejections(Fixture fixture) throws Exception {
        File database = fixture.cachePart(FIRST); ComputerSyncManifestV2 manifest = fixture.manifest(FIRST);
        V2SnapshotValidator.DatabaseFacts valid = V2SnapshotValidator.DatabaseFacts.valid(metadata(FIRST), V2SnapshotValidator.expectedCounts(1, 1, 1, 1, 0, 1));
        assertValidatorFailure("user version", database, manifest, new V2SnapshotValidator.DatabaseFacts(1, true, true, valid.tables, valid.columns, valid.metadata, valid.counts));
        assertValidatorFailure("integrity", database, manifest, new V2SnapshotValidator.DatabaseFacts(2, false, true, valid.tables, valid.columns, valid.metadata, valid.counts));
        assertValidatorFailure("foreign keys", database, manifest, new V2SnapshotValidator.DatabaseFacts(2, true, false, valid.tables, valid.columns, valid.metadata, valid.counts));
        java.util.Set<String> missingTable = new java.util.HashSet<String>(valid.tables); missingTable.remove("products");
        assertValidatorFailure("tables", database, manifest, new V2SnapshotValidator.DatabaseFacts(2, true, true, missingTable, valid.columns, valid.metadata, valid.counts));
        for (String table : valid.columns.keySet()) { java.util.Map<String, java.util.Set<String>> columns = copyColumns(valid.columns); columns.get(table).clear(); assertValidatorFailure("required columns " + table, database, manifest, new V2SnapshotValidator.DatabaseFacts(2, true, true, valid.tables, columns, valid.metadata, valid.counts)); }
        java.util.Map<String,String> badMetadata = new LinkedHashMap<String,String>(valid.metadata); badMetadata.put("schemaVersion", "3"); assertValidatorFailure("metadata schema", database, manifest, new V2SnapshotValidator.DatabaseFacts(2, true, true, valid.tables, valid.columns, badMetadata, valid.counts));
        badMetadata = new LinkedHashMap<String,String>(valid.metadata); badMetadata.put("snapshotId", SECOND); assertValidatorFailure("metadata id", database, manifest, new V2SnapshotValidator.DatabaseFacts(2, true, true, valid.tables, valid.columns, badMetadata, valid.counts));
        for (String count : valid.counts.keySet()) { java.util.Map<String,Long> counts = new LinkedHashMap<String,Long>(valid.counts); counts.put(count, Long.valueOf(9)); assertValidatorFailure("count " + count, database, manifest, new V2SnapshotValidator.DatabaseFacts(2, true, true, valid.tables, valid.columns, valid.metadata, counts)); }
        Files.write(database.toPath(), "wrong-size".getBytes(StandardCharsets.UTF_8)); assertValidatorFailure("hash and size", database, manifest, valid);
        Files.write(database.toPath(), database(FIRST));
    }

    private static void assertFinalSafetyRejections(Fixture fixture, File root) throws Exception {
        File database = fixture.cachePart(FIRST); ComputerSyncManifestV2 manifest = fixture.manifest(FIRST);
        V2SnapshotValidator.DatabaseFacts valid = V2SnapshotValidator.DatabaseFacts.valid(metadata(FIRST), V2SnapshotValidator.expectedCounts(1, 1, 1, 1, 0, 1));
        Map<String,String> bad = new LinkedHashMap<String,String>(valid.metadata); bad.remove("sourceHash"); assertValidatorFailure("missing source hash", database, manifest, new V2SnapshotValidator.DatabaseFacts(2,true,true,valid.tables,valid.columns,bad,valid.counts));
        bad = new LinkedHashMap<String,String>(valid.metadata); bad.put("sourceHash", "not-a-hash"); assertValidatorFailure("invalid source hash", database, manifest, new V2SnapshotValidator.DatabaseFacts(2,true,true,valid.tables,valid.columns,bad,valid.counts));
        V2SnapshotValidator.SchemaFacts schema = valid.schema;
        java.util.List<V2SnapshotValidator.ForeignKeyFact> keys = new java.util.ArrayList<V2SnapshotValidator.ForeignKeyFact>(schema.foreignKeys); keys.remove(0); assertSchemaFailure("missing foreign key", database, manifest, valid, schema(schema, null, keys, null, true, true));
        keys = new java.util.ArrayList<V2SnapshotValidator.ForeignKeyFact>(schema.foreignKeys); V2SnapshotValidator.ForeignKeyFact oldKey = keys.get(0); keys.set(0, new V2SnapshotValidator.ForeignKeyFact(oldKey.table,oldKey.from,oldKey.referenceTable,oldKey.to,oldKey.onUpdate,"CASCADE",oldKey.match)); assertSchemaFailure("wrong on delete", database, manifest, valid, schema(schema, null, keys, null, true, true));
        keys = new java.util.ArrayList<V2SnapshotValidator.ForeignKeyFact>(schema.foreignKeys); keys.add(new V2SnapshotValidator.ForeignKeyFact("products","barcode","products","barcode","NO ACTION","RESTRICT","NONE")); assertSchemaFailure("extra foreign key", database, manifest, valid, schema(schema, null, keys, null, true, true));
        java.util.Map<String,V2SnapshotValidator.IndexFact> indexes = new LinkedHashMap<String,V2SnapshotValidator.IndexFact>(schema.indexes); indexes.remove("idx_products_barcode"); assertSchemaFailure("missing index", database, manifest, valid, schema(schema, null, null, indexes, true, true));
        indexes = new LinkedHashMap<String,V2SnapshotValidator.IndexFact>(schema.indexes); V2SnapshotValidator.IndexFact index = indexes.get("idx_candidate_products_candidate"); indexes.put(index.name, new V2SnapshotValidator.IndexFact(index.table,index.name,index.unique,java.util.Arrays.asList("mapping_order","candidate_id"))); assertSchemaFailure("wrong index order", database, manifest, valid, schema(schema, null, null, indexes, true, true));
        indexes = new LinkedHashMap<String,V2SnapshotValidator.IndexFact>(schema.indexes); index = indexes.get("idx_products_barcode"); indexes.put(index.name, new V2SnapshotValidator.IndexFact(index.table,index.name,true,index.columns)); assertSchemaFailure("wrong index unique", database, manifest, valid, schema(schema, null, null, indexes, true, true));
        java.util.Map<String,java.util.List<V2SnapshotValidator.ColumnFact>> factColumns = copyFactColumns(schema.columns); V2SnapshotValidator.ColumnFact col = factColumns.get("products").get(0); factColumns.get("products").set(0,new V2SnapshotValidator.ColumnFact(col.cid,col.name,"INTEGER",col.notNull,col.defaultValue,col.pk)); assertSchemaFailure("wrong type", database, manifest, valid, schema(schema, factColumns, null, null, true, true));
        factColumns = copyFactColumns(schema.columns); col = factColumns.get("products").get(1); factColumns.get("products").set(1,new V2SnapshotValidator.ColumnFact(col.cid,col.name,col.type,0,col.defaultValue,col.pk)); assertSchemaFailure("wrong nullability", database, manifest, valid, schema(schema, factColumns, null, null, true, true));
        factColumns = copyFactColumns(schema.columns); col = factColumns.get("products").get(0); factColumns.get("products").set(0,new V2SnapshotValidator.ColumnFact(col.cid,col.name,col.type,col.notNull,col.defaultValue,0)); assertSchemaFailure("wrong primary key", database, manifest, valid, schema(schema, factColumns, null, null, true, true));
        assertSchemaFailure("missing unique", database, manifest, valid, schema(schema, null, null, null, true, false));
        assertSchemaFailure("wrong check", database, manifest, valid, schema(schema, null, null, null, false, true));
        File external = new File(root.getParentFile(), "v2-external-target"); Files.write(external.toPath(), new byte[] { 1 }); File link = new File(fixture.root, "symlink-leaf"); File directoryTarget = new File(root, "directory-target"); directoryTarget.mkdirs(); File directoryLink = new File(fixture.root, "symlink-dir");
        try { Files.createSymbolicLink(link.toPath(), external.toPath()); Files.createSymbolicLink(directoryLink.toPath(), directoryTarget.toPath()); expectFailure("symlink child", () -> V2SnapshotStateStore.child(fixture.root, "symlink-leaf")); expectFailure("symlink directory", () -> V2SnapshotStateStore.canonicalDirectory(directoryLink)); assertFalse("symlink is not regular child", V2SnapshotStateStore.isSafeRegularChild(fixture.root, link)); fixture.stage(SECOND); byte[] activeObject = Files.readAllBytes(fixture.store.objectFileForTest(FIRST).toPath()); byte[] activeManifest = Files.readAllBytes(fixture.store.manifestFileForTest(FIRST).toPath()); Files.delete(fixture.store.objectFileForTest(SECOND).toPath()); Files.delete(fixture.store.manifestFileForTest(SECOND).toPath()); Files.createSymbolicLink(fixture.store.objectFileForTest(SECOND).toPath(), fixture.store.objectFileForTest(FIRST).toPath()); Files.createSymbolicLink(fixture.store.manifestFileForTest(SECOND).toPath(), fixture.store.manifestFileForTest(FIRST).toPath()); assertEquals("active survives linked pending cleanup", FIRST, new V2SnapshotReader(fixture.store).recover().activeSnapshotId()); assertArrayEquals("linked pending cleanup preserves active object", activeObject, Files.readAllBytes(fixture.store.objectFileForTest(FIRST).toPath())); assertArrayEquals("linked pending cleanup preserves active manifest", activeManifest, Files.readAllBytes(fixture.store.manifestFileForTest(FIRST).toPath())); Files.deleteIfExists(fixture.store.objectFileForTest(SECOND).toPath()); Files.deleteIfExists(fixture.store.manifestFileForTest(SECOND).toPath()); }
        catch (UnsupportedOperationException | java.io.IOException denied) { assertFalse("pure policy rejects outside regular child", V2SnapshotStateStore.isSafeRegularChild(fixture.root, external)); }
    }

    private static void assertPendingWritePrecedesFinalMove() throws Exception {
        File source = new File("app/src/main/java/com/espsa/mobilepos/app/sync/V2SnapshotStore.java");
        String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
        assertTrue("pending state is committed before final object move", text.indexOf("stateStore.write(state.withPending(summary))") < text.indexOf("atomicMove(stagedObject, object)"));
    }

    private static Map<String,String> metadata(String id) { Map<String,String> result = new LinkedHashMap<String,String>(); result.put("schemaVersion", "2"); result.put("snapshotId", id); result.put("sourceHash", repeat('a', 64)); return result; }
    private static V2SnapshotValidator.SchemaFacts schema(V2SnapshotValidator.SchemaFacts base, Map<String,java.util.List<V2SnapshotValidator.ColumnFact>> columns, java.util.List<V2SnapshotValidator.ForeignKeyFact> keys, Map<String,V2SnapshotValidator.IndexFact> indexes, boolean check, boolean unique) { return new V2SnapshotValidator.SchemaFacts(columns == null ? base.columns : columns, keys == null ? base.foreignKeys : keys, indexes == null ? base.indexes : indexes, check, unique); }
    private static Map<String,java.util.List<V2SnapshotValidator.ColumnFact>> copyFactColumns(Map<String,java.util.List<V2SnapshotValidator.ColumnFact>> source) { Map<String,java.util.List<V2SnapshotValidator.ColumnFact>> result = new LinkedHashMap<String,java.util.List<V2SnapshotValidator.ColumnFact>>(); for (Map.Entry<String,java.util.List<V2SnapshotValidator.ColumnFact>> item : source.entrySet()) result.put(item.getKey(), new java.util.ArrayList<V2SnapshotValidator.ColumnFact>(item.getValue())); return result; }
    private static void assertSchemaFailure(String name, File database, ComputerSyncManifestV2 manifest, V2SnapshotValidator.DatabaseFacts valid, V2SnapshotValidator.SchemaFacts schema) throws Exception { assertValidatorFailure(name, database, manifest, new V2SnapshotValidator.DatabaseFacts(2,true,true,schema,valid.tables,valid.columns,valid.metadata,valid.counts)); }
    private static String repeat(char value, int count) { StringBuilder out = new StringBuilder(count); for (int i=0;i<count;i++) out.append(value); return out.toString(); }
    private static Map<String, java.util.Set<String>> copyColumns(Map<String, java.util.Set<String>> source) { Map<String, java.util.Set<String>> result = new LinkedHashMap<String, java.util.Set<String>>(); for (Map.Entry<String, java.util.Set<String>> item : source.entrySet()) result.put(item.getKey(), new java.util.HashSet<String>(item.getValue())); return result; }
    private static void assertValidatorFailure(String name, File database, ComputerSyncManifestV2 manifest, V2SnapshotValidator.DatabaseFacts facts) throws Exception { V2SnapshotValidator validator = new V2SnapshotValidator(file -> facts); expectFailure(name, () -> validator.validate(database, manifest, null)); }

    private static final class Fixture {
        final File root;
        final File cache;
        final V2SnapshotStore store;

        Fixture(File root) throws Exception {
            this.root = root;
            this.cache = new File(root, "cache/computer-sync-v2/tmp");
            if (!cache.mkdirs()) throw new AssertionError("cache");
            this.store = new V2SnapshotStore(root, cache, new FakeInspector(), Fixture::decodeManifest);
        }

        void stage(String id) throws Exception { store.persistDownloaded(manifest(id), manifestBytes(id), cachePart(id)); }
        void stageAndActivate(String id) throws Exception { stage(id); store.activateForTest(id); }
        File cachePart(String id) throws Exception {
            File result = new File(cache, "snapshot-v2-aa11bb22.part");
            Files.write(result.toPath(), database(id));
            return result;
        }
        void deleteObject(String id) throws Exception { store.objectFileForTest(id).delete(); }
        void corruptObject(String id) throws Exception { Files.write(store.objectFileForTest(id).toPath(), "bad".getBytes(StandardCharsets.UTF_8)); }
        ComputerSyncManifestV2 manifest(String id) { return decodeManifest(manifestBytes(id)); }
        static ComputerSyncManifestV2 decodeManifest(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            int start = text.indexOf("ms2011-"); int end = text.indexOf('"', start);
            if (start < 0 || end < start) throw new IllegalArgumentException("fixture manifest");
            String id = text.substring(start, end);
            Map<String, Object> fields = new LinkedHashMap<String, Object>();
            byte[] database = database(id);
            fields.put("ok", Boolean.TRUE); fields.put("schemaVersion", Integer.valueOf(2)); fields.put("snapshotId", id);
            fields.put("sourceType", "ms2011_live"); fields.put("createdAtUtc", "2026-07-18T12:00:00Z"); fields.put("sizeBytes", Long.valueOf(database.length));
            fields.put("sha256", sha256(database)); fields.put("minimumAppVersion", Integer.valueOf(1)); fields.put("productCount", Integer.valueOf(1)); fields.put("categoryCount", Integer.valueOf(1)); fields.put("unitCount", Integer.valueOf(1)); fields.put("promotionCandidateCount", Integer.valueOf(1)); fields.put("verifiedPromotionCount", Integer.valueOf(0)); fields.put("validationIssueCount", Integer.valueOf(1)); fields.put("downloadPath", "/v2/snapshots/" + id + ".db");
            try { return ComputerSyncManifestV2.fromFieldValues(fields); } catch (ComputerSyncException exception) { throw new IllegalArgumentException(exception); }
        }
        byte[] manifestBytes(String id) {
            byte[] database = database(id);
            String json = "{\"ok\":true,\"schemaVersion\":2,\"snapshotId\":\"" + id + "\",\"sourceType\":\"ms2011_live\",\"createdAtUtc\":\"2026-07-18T12:00:00Z\",\"sizeBytes\":" + database.length + ",\"sha256\":\"" + sha256(database) + "\",\"minimumAppVersion\":1,\"productCount\":1,\"categoryCount\":1,\"unitCount\":1,\"promotionCandidateCount\":1,\"verifiedPromotionCount\":0,\"validationIssueCount\":1,\"downloadPath\":\"/v2/snapshots/" + id + ".db\"}";
            return json.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class FakeInspector implements V2SnapshotValidator.ReadOnlyDatabaseInspector {
        @Override public V2SnapshotValidator.DatabaseFacts inspect(File database) {
            Map<String, String> metadata = new LinkedHashMap<String, String>();
            metadata.put("schemaVersion", "2"); metadata.put("sourceHash", repeat('a', 64));
            try {
                String content = new String(Files.readAllBytes(database.toPath()), StandardCharsets.UTF_8);
                metadata.put("snapshotId", content.substring("database-".length()));
            } catch (Exception exception) { throw new AssertionError(exception); }
            Map<String, Long> counts = V2SnapshotValidator.expectedCounts(1, 1, 1, 1, 0, 1);
            return V2SnapshotValidator.DatabaseFacts.valid(metadata, counts);
        }
    }

    private static byte[] database(String id) { return ("database-" + id).getBytes(StandardCharsets.UTF_8); }
    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder();
            for (byte item : digest) out.append(String.format("%02x", item & 0xff));
            return out.toString();
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private interface Checked { void run() throws Exception; }
    private static void expectFailure(String name, Checked checked) throws Exception { assertions++; try { checked.run(); throw new AssertionError(name); } catch (Exception expected) { } }
    private static void assertEquals(String name, Object expected, Object actual) { assertions++; if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(name); }
    private static void assertNull(String name, Object actual) { assertions++; if (actual != null) throw new AssertionError(name); }
    private static void assertFalse(String name, boolean actual) { assertions++; if (actual) throw new AssertionError(name); }
    private static void assertTrue(String name, boolean actual) { assertions++; if (!actual) throw new AssertionError(name); }
    private static void assertArrayEquals(String name, byte[] expected, byte[] actual) { assertions++; if (!java.util.Arrays.equals(expected, actual)) throw new AssertionError(name); }
    private static void deleteTree(File path) { if (path.isDirectory()) { File[] items = path.listFiles(); if (items != null) for (File item : items) deleteTree(item); } path.delete(); }
}
