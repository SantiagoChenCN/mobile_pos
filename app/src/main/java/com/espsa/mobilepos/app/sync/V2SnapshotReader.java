package com.espsa.mobilepos.app.sync;

/** Startup recovery only chooses a verified immutable pair; catalog loading remains MB-06 work. */
public final class V2SnapshotReader {
    private final V2SnapshotStore store;

    public V2SnapshotReader(V2SnapshotStore store) {
        if (store == null) throw new IllegalArgumentException("Missing v2 snapshot store");
        this.store = store;
    }

    public RecoveryResult recover() {
        V2SnapshotStateStore.State state = store.readState();
        if (state == null) return RecoveryResult.none();
        String active = verified(state.activeSnapshotId(), state);
        if (active != null) { cleanupIncompletePending(state); return RecoveryResult.active(active, false); }
        String rollback = verified(state.lastGoodSnapshotId(), state);
        cleanupIncompletePending(state);
        return rollback == null ? RecoveryResult.none() : RecoveryResult.active(rollback, true);
    }

    private String verified(String id, V2SnapshotStateStore.State state) {
        if (id == null) return null;
        try { return store.validateStored(id, state.summary(id)) == null ? null : id; }
        catch (Exception ignored) { return null; }
    }
    private void cleanupIncompletePending(V2SnapshotStateStore.State state) { if (state.pendingSnapshotId() != null) store.cleanupIncompletePending(state.pendingSnapshotId()); }

    public static final class RecoveryResult {
        private final String activeSnapshotId; private final boolean recoveredFromLastGood;
        private RecoveryResult(String id, boolean fallback) { this.activeSnapshotId=id;this.recoveredFromLastGood=fallback; }
        static RecoveryResult none(){return new RecoveryResult(null,false);} static RecoveryResult active(String id,boolean fallback){return new RecoveryResult(id,fallback);}
        public String activeSnapshotId(){return activeSnapshotId;} public boolean recoveredFromLastGood(){return recoveredFromLastGood;} public boolean hasValidSnapshot(){return activeSnapshotId!=null;}
    }
}
