package com.espsa.mobilepos.app.sync;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/** Owns the v2 filesDir layout; callers never choose a final object path. */
public final class V2SnapshotStore {
    // MB-03 creates this random app-cache name internally; final files are derived only from validated snapshotId.
    private static final Pattern CACHE_PART = Pattern.compile("^snapshot-v2-[a-z0-9]+\\.part$");
    private final File root, objects, manifests, temporary, trustedCacheTemporary;
    private final V2SnapshotStateStore stateStore;
    private final V2SnapshotValidator validator;
    private final ManifestDecoder manifestDecoder;

    interface ManifestDecoder { ComputerSyncManifestV2 decode(byte[] bytes) throws Exception; }

    public V2SnapshotStore(Context context) throws IOException {
        this(requireFiles(context), requireCache(context), new V2SnapshotValidator(), ComputerSyncManifestV2::fromUtf8Json);
    }

    V2SnapshotStore(File filesDirectory, File cacheTemporary, V2SnapshotValidator.ReadOnlyDatabaseInspector inspector) throws IOException {
        this(filesDirectory, cacheTemporary, new V2SnapshotValidator(inspector), ComputerSyncManifestV2::fromUtf8Json);
    }

    V2SnapshotStore(File filesDirectory, File cacheTemporary, V2SnapshotValidator.ReadOnlyDatabaseInspector inspector, ManifestDecoder decoder) throws IOException {
        this(filesDirectory, cacheTemporary, new V2SnapshotValidator(inspector), decoder);
    }

    private V2SnapshotStore(File filesDirectory, File cacheTemporary, V2SnapshotValidator validator, ManifestDecoder decoder) throws IOException {
        this.root = V2SnapshotStateStore.canonicalDirectory(new File(filesDirectory, "computer-sync-v2"));
        this.objects = V2SnapshotStateStore.canonicalDirectory(V2SnapshotStateStore.child(root, "objects"));
        this.manifests = V2SnapshotStateStore.canonicalDirectory(V2SnapshotStateStore.child(root, "manifests"));
        this.temporary = V2SnapshotStateStore.canonicalDirectory(V2SnapshotStateStore.child(root, "tmp"));
        this.trustedCacheTemporary = V2SnapshotStateStore.canonicalDirectory(cacheTemporary);
        if (decoder == null) throw new IOException("Missing manifest decoder");
        this.stateStore = new V2SnapshotStateStore(root); this.validator = validator; this.manifestDecoder = decoder;
    }

    /** Accepts only a cache temporary file produced by the MB-03 v2 download entry point. */
    public synchronized void persistDownloaded(ComputerSyncManifestV2 manifest, byte[] originalManifestBytes, File cacheTemporary) throws Exception {
        if (manifest == null || originalManifestBytes == null || originalManifestBytes.length == 0) throw new IllegalArgumentException("Missing v2 pair");
        ComputerSyncManifestV2 raw = manifestDecoder.decode(originalManifestBytes);
        if (!sameManifest(manifest, raw)) throw new IllegalArgumentException("Manifest object and original bytes differ");
        verifyTrustedCacheFile(cacheTemporary);
        V2SnapshotStateStore.SnapshotSummary summary = V2SnapshotValidator.summary(manifest);
        validator.validate(cacheTemporary, manifest, null);
        V2SnapshotStateStore.State state = stateStore.read();
        if (state == null) throw new IOException("Damaged v2 state cannot be replaced");
        File object = objectFile(summary.snapshotId); File manifestFile = manifestFile(summary.snapshotId);
        verifyExistingPairOrPartial(object, manifestFile, manifest, originalManifestBytes);
        // This durable pending reference precedes every final move, so a crash leaves only a recoverable pending pair.
        stateStore.write(state.withPending(summary));
        File stagedObject = null; File stagedManifest = null;
        try {
            if (!object.exists()) { stagedObject = staging(summary.snapshotId, "object", ".db.tmp"); copyAndSync(cacheTemporary, stagedObject); if (!V2SnapshotStateStore.isSafeRegularChild(temporary, stagedObject) || (object.exists() && !V2SnapshotStateStore.isSafeRegularChild(objects, object))) throw new IOException("Unsafe immutable object path"); atomicMove(stagedObject, object); }
            if (!manifestFile.exists()) { stagedManifest = staging(summary.snapshotId, "manifest", ".json.tmp"); copyAndSync(originalManifestBytes, stagedManifest); if (!V2SnapshotStateStore.isSafeRegularChild(temporary, stagedManifest) || (manifestFile.exists() && !V2SnapshotStateStore.isSafeRegularChild(manifests, manifestFile))) throw new IOException("Unsafe immutable manifest path"); atomicMove(stagedManifest, manifestFile); }
            validator.validate(object, manifest, summary);
        } finally { deleteOwnedTemporary(stagedObject); deleteOwnedTemporary(stagedManifest); }
    }

    /**
     * Promotes only the currently staged immutable pair.  All validation happens before the
     * atomic state replacement, so callers can retain their catalog and current cart on failure.
     */
    public synchronized void activatePendingVerified(String snapshotId) throws Exception {
        String id = com.espsa.mobilepos.core.model.V2Contract.validateSnapshotId(snapshotId);
        V2SnapshotStateStore.State state = stateStore.read();
        if (state == null || !id.equals(state.pendingSnapshotId())) {
            throw new IOException("Snapshot is not the current pending v2 pair");
        }
        validateStored(id, state.summary(id));
        stateStore.write(state.withActive(id));
    }

    V2SnapshotStateStore.State readState() { return stateStore.read(); }
    V2SnapshotStateStore.SnapshotSummary validateStored(String id, V2SnapshotStateStore.SnapshotSummary expected) throws Exception {
        File object = objectFile(id); File manifest = manifestFile(id);
        if (!V2SnapshotStateStore.isSafeRegularChild(objects, object) || !V2SnapshotStateStore.isSafeRegularChild(manifests, manifest) || manifest.length() > com.espsa.mobilepos.core.model.V2Contract.MANIFEST_SOFT_BYTES) throw new IOException("Stored v2 pair missing");
        ComputerSyncManifestV2 parsed = manifestDecoder.decode(Files.readAllBytes(manifest.toPath()));
        if (!id.equals(parsed.snapshotId())) throw new IOException("Manifest path mismatch");
        return validator.validate(object, parsed, expected);
    }
    /** Revalidates the immutable pair immediately before an MB-06 reader obtains its file. */
    File verifiedImmutableObjectFile(String id) throws Exception {
        V2SnapshotStateStore.State state = stateStore.read();
        if (state == null || state.summary(id) == null) throw new IOException("Missing verified snapshot state");
        validateStored(id, state.summary(id));
        File object = objectFile(id);
        if (!V2SnapshotStateStore.isSafeRegularChild(objects, object)) throw new IOException("Unsafe immutable object");
        return object;
    }
    void cleanupIncompletePending(String id) {
        try { V2SnapshotStateStore.State state=stateStore.read(); if(state==null||!id.equals(state.pendingSnapshotId())||id.equals(state.activeSnapshotId())||id.equals(state.lastGoodSnapshotId()))return; try{validateStored(id,state.summary(id));return;}catch(Exception ignored){} deletePendingFile(objectFile(id)); deletePendingFile(manifestFile(id)); File[] entries=temporary.listFiles(); if(entries!=null) for(File item:entries) if(item.getName().startsWith("pending-"+id+"-") && (item.getName().endsWith(".db.tmp")||item.getName().endsWith(".json.tmp"))) deletePendingFile(item); stateStore.write(state.withoutPending()); } catch(Exception ignored) { }
    }

    // Package-private seams are for host tests only; public production construction requires Context.
    File objectFileForTest(String id) throws Exception { return objectFile(id); } File manifestFileForTest(String id) throws Exception { return manifestFile(id); } File stateFileForTest(){return stateStore.stateFileForTest();} void activateForTest(String id) throws Exception { V2SnapshotStateStore.State state=stateStore.read(); if(state==null||state.summary(id)==null)throw new IOException("Unknown snapshot"); stateStore.write(state.withActive(id)); }

    private static File requireFiles(Context context) throws IOException { if(context==null||context.getFilesDir()==null)throw new IOException("Missing app files directory"); return context.getFilesDir(); }
    private static File requireCache(Context context) throws IOException { if(context==null||context.getCacheDir()==null)throw new IOException("Missing app cache directory"); return V2SnapshotStateStore.child(V2SnapshotStateStore.canonicalDirectory(new File(context.getCacheDir(),"computer-sync-v2")),"tmp"); }
    private File objectFile(String id) throws Exception { com.espsa.mobilepos.core.model.V2Contract.validateSnapshotId(id); return V2SnapshotStateStore.child(objects,id+".db"); }
    private File manifestFile(String id) throws Exception { com.espsa.mobilepos.core.model.V2Contract.validateSnapshotId(id); return V2SnapshotStateStore.child(manifests,id+".json"); }
    private File staging(String id,String kind,String suffix) throws Exception { com.espsa.mobilepos.core.model.V2Contract.validateSnapshotId(id); return V2SnapshotStateStore.child(temporary,"pending-"+id+"-"+UUID.randomUUID().toString().replace("-","")+"-"+kind+suffix); }
    private void verifyTrustedCacheFile(File source) throws Exception { if(source==null||!CACHE_PART.matcher(source.getName()).matches()||!V2SnapshotStateStore.isSafeRegularChild(trustedCacheTemporary, source))throw new IOException("Untrusted v2 cache file"); }
    private void verifyExistingPairOrPartial(File object, File manifestFile, ComputerSyncManifestV2 manifest, byte[] rawManifest) throws Exception { if (object.exists() && !V2SnapshotStateStore.isSafeRegularChild(objects, object)) throw new IOException("Unsafe immutable object conflict"); if (manifestFile.exists() && (!V2SnapshotStateStore.isSafeRegularChild(manifests, manifestFile) || !java.util.Arrays.equals(Files.readAllBytes(manifestFile.toPath()), rawManifest))) throw new IOException("Refusing immutable manifest conflict"); if (object.exists()) validator.validate(object, manifest, null); }
    private static void copyAndSync(File source,File destination) throws IOException { try(FileInputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(destination)){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1)output.write(b,0,n);output.flush();output.getFD().sync();} }
    private static void copyAndSync(byte[] source,File destination) throws IOException { try(FileOutputStream output=new FileOutputStream(destination)){output.write(source);output.flush();output.getFD().sync();} }
    private static void atomicMove(File source,File destination) throws IOException { Files.move(source.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE); }
    private void deleteOwnedTemporary(File file){try{if(file!=null&&V2SnapshotStateStore.isSafeRegularChild(temporary,file))Files.deleteIfExists(file.toPath());}catch(Exception ignored){}}
    private void deletePendingFile(File file){try{File parent=file==null?null:file.getParentFile();if(parent!=null&&V2SnapshotStateStore.isSafeRegularChild(parent,file))Files.deleteIfExists(file.toPath());}catch(Exception ignored){}}
    private static boolean sameManifest(ComputerSyncManifestV2 a,ComputerSyncManifestV2 b){return a.snapshotId().equals(b.snapshotId())&&a.createdAtUtc().equals(b.createdAtUtc())&&a.sizeBytes()==b.sizeBytes()&&a.sha256().equals(b.sha256())&&a.minimumAppVersion()==b.minimumAppVersion()&&a.productCount()==b.productCount()&&a.categoryCount()==b.categoryCount()&&a.unitCount()==b.unitCount()&&a.promotionCandidateCount()==b.promotionCandidateCount()&&a.verifiedPromotionCount()==b.verifiedPromotionCount()&&a.validationIssueCount()==b.validationIssueCount()&&a.downloadPath().equals(b.downloadPath());}
}
