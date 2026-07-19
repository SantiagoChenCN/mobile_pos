package com.espsa.mobilepos.app.sync;

import com.espsa.mobilepos.core.model.V2Contract;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict, bounded state for v2 objects. It never contains catalog or promotion payloads. */
public final class V2SnapshotStateStore {
    static final int MAX_STATE_BYTES = 16 * 1024;
    private static final int MAX_FAILURES = 1_000_000;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private final File root;
    private final File stateFile;
    private final AtomicMover mover;

    interface AtomicMover { void move(File source, File destination) throws IOException; }

    V2SnapshotStateStore(File root) throws IOException { this(root, V2SnapshotStateStore::atomicMove); }

    V2SnapshotStateStore(File root, AtomicMover mover) throws IOException {
        if (root == null || mover == null) throw new IOException("Missing v2 state root");
        this.root = canonicalDirectory(root);
        this.stateFile = child(this.root, "state.json");
        this.mover = mover;
    }

    State read() {
        try {
            if (!stateFile.exists()) return State.empty();
            if (!isSafeRegularChild(root, stateFile) || stateFile.length() <= 0 || stateFile.length() > MAX_STATE_BYTES) return null;
            return State.fromJson(new String(Files.readAllBytes(stateFile.toPath()), StandardCharsets.UTF_8));
        } catch (Exception ignored) { return null; }
    }

    void write(State state) throws IOException {
        if (state == null) throw new IOException("Missing v2 state");
        String json = state.toJson();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_STATE_BYTES) throw new IOException("v2 state exceeds limit");
        File temporary = child(root, "state-" + UUID.randomUUID().toString().replace("-", "") + ".tmp");
        try {
            if (temporary.exists() || !isSafeChild(root, temporary)) throw new IOException("Unsafe state temporary path");
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.flush();
                FileDescriptor descriptor = output.getFD();
                descriptor.sync();
            }
            if (stateFile.exists() && !isSafeRegularChild(root, stateFile)) throw new IOException("Refusing non-file state target");
            if (!isSafeRegularChild(root, temporary)) throw new IOException("Unsafe state temporary file");
            mover.move(temporary, stateFile);
        } finally {
            if (temporary.exists() && isSafeRegularChild(root, temporary)) Files.deleteIfExists(temporary.toPath());
        }
    }

    File stateFileForTest() { return stateFile; }

    private static void atomicMove(File source, File destination) throws IOException {
        Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
    }

    static File canonicalDirectory(File directory) throws IOException {
        if (directory == null) throw new IOException("Missing v2 directory");
        File raw = directory.getAbsoluteFile();
        rejectSymlinkChain(raw);
        if (!raw.exists() && !raw.mkdirs()) throw new IOException("Cannot create v2 directory");
        rejectSymlinkChain(raw);
        if (!raw.isDirectory() || Files.isSymbolicLink(raw.toPath())) throw new IOException("Unsafe v2 directory");
        return raw;
    }

    static File child(File parent, String name) throws IOException {
        if (name == null || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..")) throw new IOException("Unsafe v2 filename");
        File safeParent = canonicalDirectory(parent);
        File result = new File(safeParent, name).getAbsoluteFile();
        if (!safeParent.equals(result.getParentFile()) || Files.isSymbolicLink(result.toPath())) throw new IOException("v2 path escape");
        return result;
    }

    static boolean isRegularFile(File file) {
        return file != null && Files.isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(file.toPath());
    }
    static boolean isSafeChild(File parent, File child) {
        try { File safeParent=canonicalDirectory(parent); File raw=child==null?null:child.getAbsoluteFile(); return raw!=null && safeParent.equals(raw.getParentFile()) && !Files.isSymbolicLink(raw.toPath()); } catch (Exception ignored) { return false; }
    }
    static boolean isSafeRegularChild(File parent, File child) { return isSafeChild(parent, child) && isRegularFile(child); }
    private static void rejectSymlinkChain(File target) throws IOException { for (File item=target; item!=null; item=item.getParentFile()) if (Files.isSymbolicLink(item.toPath())) throw new IOException("v2 symlink path rejected"); }

    static final class SnapshotSummary {
        final String snapshotId; final String sha256; final long sizeBytes; final int schemaVersion; final int minimumAppVersion;
        final int productCount; final int categoryCount; final int unitCount; final int promotionCandidateCount; final int verifiedPromotionCount; final int validationIssueCount;

        SnapshotSummary(String snapshotId, String sha256, long sizeBytes, int schemaVersion, int minimumAppVersion,
                        int productCount, int categoryCount, int unitCount, int promotionCandidateCount,
                        int verifiedPromotionCount, int validationIssueCount) {
            try { V2Contract.validateSnapshotId(snapshotId); } catch (Exception exception) { throw new IllegalArgumentException("Invalid snapshot summary", exception); }
            if (sha256 == null || !SHA256.matcher(sha256).matches() || sizeBytes < 0 || sizeBytes > V2Contract.SNAPSHOT_SOFT_BYTES
                    || schemaVersion != V2Contract.SCHEMA_VERSION || minimumAppVersion < 1 || minimumAppVersion > Integer.MAX_VALUE
                    || productCount < 0 || productCount > V2Contract.PRODUCT_SOFT_COUNT || categoryCount < 0 || categoryCount > Integer.MAX_VALUE
                    || unitCount < 0 || unitCount > Integer.MAX_VALUE || promotionCandidateCount < 0 || promotionCandidateCount > V2Contract.PROMOTION_SOFT_COUNT
                    || verifiedPromotionCount < 0 || verifiedPromotionCount > promotionCandidateCount || validationIssueCount < 0 || validationIssueCount > V2Contract.ISSUE_SOFT_COUNT) throw new IllegalArgumentException("Invalid snapshot summary");
            this.snapshotId=snapshotId; this.sha256=sha256; this.sizeBytes=sizeBytes; this.schemaVersion=schemaVersion; this.minimumAppVersion=minimumAppVersion;
            this.productCount=productCount; this.categoryCount=categoryCount; this.unitCount=unitCount; this.promotionCandidateCount=promotionCandidateCount;
            this.verifiedPromotionCount=verifiedPromotionCount; this.validationIssueCount=validationIssueCount;
        }

        boolean matches(SnapshotSummary other) {
            return other != null && snapshotId.equals(other.snapshotId) && sha256.equals(other.sha256) && sizeBytes == other.sizeBytes
                    && schemaVersion == other.schemaVersion && minimumAppVersion == other.minimumAppVersion && productCount == other.productCount
                    && categoryCount == other.categoryCount && unitCount == other.unitCount && promotionCandidateCount == other.promotionCandidateCount
                    && verifiedPromotionCount == other.verifiedPromotionCount && validationIssueCount == other.validationIssueCount;
        }
    }

    static final class State {
        private final String activeSnapshotId, pendingSnapshotId, lastGoodSnapshotId;
        private final Map<String, SnapshotSummary> snapshots;
        private final int consecutiveFailures;

        private State(String active, String pending, String lastGood, Map<String, SnapshotSummary> summaries, int failures) {
            if (failures < 0 || failures > MAX_FAILURES || summaries == null || summaries.size() > 3) throw new IllegalArgumentException("Invalid v2 state bounds");
            this.activeSnapshotId = nullableId(active); this.pendingSnapshotId = nullableId(pending); this.lastGoodSnapshotId = nullableId(lastGood);
            LinkedHashMap<String, SnapshotSummary> copy = new LinkedHashMap<String, SnapshotSummary>();
            for (Map.Entry<String, SnapshotSummary> item : summaries.entrySet()) {
                if (item.getValue() == null || !item.getKey().equals(item.getValue().snapshotId)) throw new IllegalArgumentException("Invalid snapshot state mapping");
                copy.put(item.getKey(), item.getValue());
            }
            if (pendingSnapshotId != null && (pendingSnapshotId.equals(activeSnapshotId) || pendingSnapshotId.equals(lastGoodSnapshotId))) throw new IllegalArgumentException("Pending snapshot cannot be protected");
            requirePresent(copy, activeSnapshotId); requirePresent(copy, pendingSnapshotId); requirePresent(copy, lastGoodSnapshotId);
            if (copy.size() != referencedCount(activeSnapshotId, pendingSnapshotId, lastGoodSnapshotId)) throw new IllegalArgumentException("State cannot retain orphan snapshots");
            this.snapshots = Collections.unmodifiableMap(copy); this.consecutiveFailures = failures;
        }
        static State empty() { return new State(null, null, null, Collections.<String, SnapshotSummary>emptyMap(), 0); }
        String activeSnapshotId() { return activeSnapshotId; } String pendingSnapshotId() { return pendingSnapshotId; } String lastGoodSnapshotId() { return lastGoodSnapshotId; }
        SnapshotSummary summary(String id) { return snapshots.get(id); }
        State withFailures(int failures) { return new State(activeSnapshotId, pendingSnapshotId, lastGoodSnapshotId, snapshots, failures); }
        State withPending(SnapshotSummary value) { if (value == null || value.snapshotId.equals(activeSnapshotId) || value.snapshotId.equals(lastGoodSnapshotId)) throw new IllegalArgumentException("Pending snapshot cannot replace protected snapshot"); if (pendingSnapshotId != null && !pendingSnapshotId.equals(value.snapshotId)) throw new IllegalArgumentException("A different pending snapshot already exists"); if (pendingSnapshotId != null && !value.matches(summary(pendingSnapshotId))) throw new IllegalArgumentException("Pending snapshot summary conflict"); return updated(activeSnapshotId, value.snapshotId, lastGoodSnapshotId, value); }
        State withActive(String id) { return updated(id, null, activeSnapshotId, summary(id)); }
        State withoutPending() { return new State(activeSnapshotId, null, lastGoodSnapshotId, without(pendingSnapshotId), consecutiveFailures); }
        private State updated(String active, String pending, String lastGood, SnapshotSummary extra) {
            LinkedHashMap<String, SnapshotSummary> next = new LinkedHashMap<String, SnapshotSummary>(snapshots);
            if (extra != null) next.put(extra.snapshotId, extra);
            retain(next, active, pending, lastGood);
            return new State(active, pending, lastGood, next, consecutiveFailures);
        }
        private Map<String, SnapshotSummary> without(String id) { LinkedHashMap<String, SnapshotSummary> next = new LinkedHashMap<String, SnapshotSummary>(snapshots); if (id != null) next.remove(id); return next; }
        private static void retain(Map<String, SnapshotSummary> values, String... ids) { values.keySet().retainAll(java.util.Arrays.asList(ids)); }
        private static int referencedCount(String... ids) { java.util.HashSet<String> distinct = new java.util.HashSet<String>(); for (String id:ids) if (id != null) distinct.add(id); return distinct.size(); }
        private static String nullableId(String value) { if (value == null) return null; V2Contract.validateSnapshotId(value); return value; }
        private static void requirePresent(Map<String, SnapshotSummary> values, String id) { if (id != null && !values.containsKey(id)) throw new IllegalArgumentException("Referenced snapshot summary missing"); }

        String toJson() {
            StringBuilder out = new StringBuilder("{\"activeSnapshotId\":").append(json(activeSnapshotId)).append(",\"pendingSnapshotId\":").append(json(pendingSnapshotId)).append(",\"lastGoodSnapshotId\":").append(json(lastGoodSnapshotId)).append(",\"snapshots\":{");
            boolean first=true; for (SnapshotSummary value:snapshots.values()) { if(!first)out.append(','); first=false; out.append(json(value.snapshotId)).append(":{").append("\"snapshotId\":").append(json(value.snapshotId)).append(",\"sha256\":").append(json(value.sha256)).append(",\"sizeBytes\":").append(value.sizeBytes).append(",\"schemaVersion\":").append(value.schemaVersion).append(",\"minimumAppVersion\":").append(value.minimumAppVersion).append(",\"productCount\":").append(value.productCount).append(",\"categoryCount\":").append(value.categoryCount).append(",\"unitCount\":").append(value.unitCount).append(",\"promotionCandidateCount\":").append(value.promotionCandidateCount).append(",\"verifiedPromotionCount\":").append(value.verifiedPromotionCount).append(",\"validationIssueCount\":").append(value.validationIssueCount).append('}'); }
            return out.append("},\"consecutiveFailures\":").append(consecutiveFailures).append('}').toString();
        }
        @SuppressWarnings("unchecked") static State fromJson(String text) {
            Object raw = new Json(text).value(); if (!(raw instanceof Map)) throw new IllegalArgumentException("Invalid state JSON");
            Map<String,Object> root=(Map<String,Object>)raw; exact(root,"activeSnapshotId","pendingSnapshotId","lastGoodSnapshotId","snapshots","consecutiveFailures");
            String active=nullableString(root.get("activeSnapshotId")), pending=nullableString(root.get("pendingSnapshotId")), good=nullableString(root.get("lastGoodSnapshotId"));
            if (!(root.get("snapshots") instanceof Map)) throw new IllegalArgumentException("Invalid snapshot map"); Map<String,Object> rawSnapshots=(Map<String,Object>)root.get("snapshots"); LinkedHashMap<String,SnapshotSummary> summaries=new LinkedHashMap<String,SnapshotSummary>();
            for (Map.Entry<String,Object> entry:rawSnapshots.entrySet()) { if (!(entry.getValue() instanceof Map)) throw new IllegalArgumentException("Invalid snapshot summary"); Map<String,Object> item=(Map<String,Object>)entry.getValue(); exact(item,"snapshotId","sha256","sizeBytes","schemaVersion","minimumAppVersion","productCount","categoryCount","unitCount","promotionCandidateCount","verifiedPromotionCount","validationIssueCount"); SnapshotSummary summary=new SnapshotSummary(string(item,"snapshotId"),string(item,"sha256"),number(item,"sizeBytes"),integer(item,"schemaVersion"),integer(item,"minimumAppVersion"),integer(item,"productCount"),integer(item,"categoryCount"),integer(item,"unitCount"),integer(item,"promotionCandidateCount"),integer(item,"verifiedPromotionCount"),integer(item,"validationIssueCount")); if(!entry.getKey().equals(summary.snapshotId)) throw new IllegalArgumentException("Snapshot key mismatch"); summaries.put(entry.getKey(),summary); }
            return new State(active,pending,good,summaries,integer(root,"consecutiveFailures"));
        }
        private static void exact(Map<String,Object> map,String... names){ if(map.size()!=names.length || !map.keySet().equals(new java.util.HashSet<String>(java.util.Arrays.asList(names))))throw new IllegalArgumentException("Unexpected state fields"); }
        private static String string(Map<String,Object> map,String key){Object value=map.get(key);if(!(value instanceof String)||((String)value).length()>128)throw new IllegalArgumentException("Invalid state string");return(String)value;}
        private static String nullableString(Object value){if(value==null)return null;if(!(value instanceof String)||((String)value).length()>128)throw new IllegalArgumentException("Invalid nullable state string");return(String)value;}
        private static int integer(Map<String,Object> map,String key){long value=number(map,key);if(value>Integer.MAX_VALUE)throw new IllegalArgumentException("State integer overflow");return(int)value;}
        private static long number(Map<String,Object> map,String key){Object value=map.get(key);if(!(value instanceof Long)||((Long)value)<0)return -1L;return((Long)value).longValue();}
        private static String json(String value){if(value==null)return"null";return '"'+value.replace("\\","\\\\").replace("\"","\\\"")+'"';}
    }

    /** Minimal JSON parser: exact object/string/non-negative integer/null types only. */
    private static final class Json { private final String text; private int at; Json(String text){if(text==null||text.length()>MAX_STATE_BYTES)throw new IllegalArgumentException("state length");this.text=text;} Object value(){ws();Object result=item();ws();if(at!=text.length())bad();return result;} private Object item(){ws();if(at>=text.length())bad();char c=text.charAt(at);if(c=='{')return object();if(c=='\"')return string();if(c=='n'){word("null");return null;}if(c>='0'&&c<='9')return number();bad();return null;} private Map<String,Object> object(){at++;ws();LinkedHashMap<String,Object> out=new LinkedHashMap<String,Object>();if(peek('}')){at++;return out;}while(true){ws();String key=string();ws();take(':');Object value=item();if(out.containsKey(key))bad();out.put(key,value);ws();if(peek('}')){at++;return out;}take(',');}} private String string(){take('\"');StringBuilder out=new StringBuilder();while(at<text.length()){char c=text.charAt(at++);if(c=='\"')return out.toString();if(c=='\\'){if(at>=text.length())bad();char esc=text.charAt(at++);if(esc!='\\'&&esc!='\"')bad();out.append(esc);}else{if(c<0x20)bad();out.append(c);}if(out.length()>512)bad();}bad();return null;} private Long number(){int start=at;while(at<text.length()&&Character.isDigit(text.charAt(at)))at++;if(at-start>10)bad();try{return Long.valueOf(text.substring(start,at));}catch(Exception e){bad();return null;}} private void word(String value){if(!text.startsWith(value,at))bad();at+=value.length();} private boolean peek(char value){return at<text.length()&&text.charAt(at)==value;} private void take(char value){if(!peek(value))bad();at++;} private void ws(){while(at<text.length()&&Character.isWhitespace(text.charAt(at)))at++;} private void bad(){throw new IllegalArgumentException("Invalid state JSON");} }
}
