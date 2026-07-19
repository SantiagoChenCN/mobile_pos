package com.espsa.mobilepos.app.sync;

import android.content.Context;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground-only v2 check/download coordinator. It persists new snapshots as pending and never enables them. */
public final class ComputerSyncCoordinator implements AutoCloseable {
    interface ConfigSource {
        ComputerSyncConfig load();
        void save(ComputerSyncConfig config);
    }

    interface SyncAction {
        SyncOutcome run(ComputerSyncConfig config) throws Exception;
    }

    static final class SyncOutcome {
        private final String pendingSnapshotId;

        private SyncOutcome(String pendingSnapshotId) { this.pendingSnapshotId = pendingSnapshotId; }
        static SyncOutcome unchanged() { return new SyncOutcome(null); }
        static SyncOutcome persistedPending(String snapshotId) {
            if (snapshotId == null || snapshotId.trim().isEmpty()) {
                throw new IllegalArgumentException("Pending snapshot ID is required");
            }
            return new SyncOutcome(snapshotId.trim());
        }
    }

    public static final class ListenerRegistration implements AutoCloseable {
        private final ComputerSyncCoordinator owner;
        private final ListenerSlot slot;

        private ListenerRegistration(ComputerSyncCoordinator owner, ListenerSlot slot) {
            this.owner = owner;
            this.slot = slot;
        }

        @Override
        public void close() { owner.remove(slot); }
    }

    private final ConfigSource configSource;
    private final SyncAction syncAction;
    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final AtomicBoolean singleFlight = new AtomicBoolean(false);
    private final Object foregroundLock = new Object();
    private final Object listenerLock = new Object();
    private final Map<ComputerSyncStateListener, ListenerSlot> listeners = new IdentityHashMap<ComputerSyncStateListener, ListenerSlot>();
    private long nextGeneration;
    private boolean foreground;
    private boolean closed;
    private ScheduledFuture<?> intervalFuture;
    private volatile ComputerSyncState state;

    public ComputerSyncCoordinator(Context context, ComputerSyncStore store, ComputerSyncService service) {
        this(new AndroidConfigSource(context, store), new AndroidSyncAction(context, service),
                Executors.newSingleThreadScheduledExecutor(), Clock.systemUTC());
    }

    ComputerSyncCoordinator(ConfigSource configSource, SyncAction syncAction,
                            ScheduledExecutorService executor, Clock clock) {
        if (configSource == null || syncAction == null || executor == null || clock == null) {
            throw new IllegalArgumentException("Coordinator dependencies are required");
        }
        this.configSource = configSource;
        this.syncAction = syncAction;
        this.executor = executor;
        this.clock = clock;
        this.state = ComputerSyncState.fromConfig(safeLoad());
    }

    public void startForeground() {
        synchronized (foregroundLock) {
            if (closed) return;
            foreground = true;
            cancelIntervalLocked();
            int seconds = safeLoad().foregroundIntervalSeconds();
            if (seconds != 0) {
                intervalFuture = executor.scheduleWithFixedDelay(
                        () -> trigger(ComputerSyncTrigger.INTERVAL), seconds, seconds, TimeUnit.SECONDS);
            }
        }
        trigger(ComputerSyncTrigger.APP_RESUME);
    }

    public void stopForeground() {
        synchronized (foregroundLock) {
            foreground = false;
            cancelIntervalLocked();
        }
    }

    public void trigger(ComputerSyncTrigger trigger) {
        if (trigger == null || closed || !singleFlight.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> runCheck(trigger));
        } catch (RejectedExecutionException ignored) {
            singleFlight.set(false);
        }
    }

    public ComputerSyncState state() { return state; }

    public ListenerRegistration addListener(ComputerSyncStateListener listener) {
        if (listener == null) throw new IllegalArgumentException("State listener is required");
        synchronized (listenerLock) {
            ListenerSlot slot = new ListenerSlot(listener, ++nextGeneration);
            listeners.put(listener, slot);
            return new ListenerRegistration(this, slot);
        }
    }

    @Override
    public void close() {
        synchronized (foregroundLock) {
            if (closed) return;
            closed = true;
            foreground = false;
            cancelIntervalLocked();
        }
        executor.shutdown();
        synchronized (listenerLock) { listeners.clear(); }
    }

    private void runCheck(ComputerSyncTrigger trigger) {
        if (trigger == ComputerSyncTrigger.INTERVAL && !isForeground()) {
            singleFlight.set(false);
            return;
        }
        ComputerSyncConfig config = safeLoad();
        String checkedAt = nowIso();
        try {
            SyncOutcome outcome = syncAction.run(config);
            String snapshotId = outcome.pendingSnapshotId == null ? config.coordinatorSnapshotId() : outcome.pendingSnapshotId;
            String lastSuccess = outcome.pendingSnapshotId == null
                    ? config.coordinatorLastSuccessAtUtc() : checkedAt;
            publishAndPersist(config.withCoordinatorState(0, checkedAt, lastSuccess, snapshotId));
        } catch (Exception ignored) {
            publishAndPersist(config.withCoordinatorState(increment(config.coordinatorConsecutiveFailures()), checkedAt,
                    config.coordinatorLastSuccessAtUtc(), config.coordinatorSnapshotId()));
        } finally {
            singleFlight.set(false);
        }
    }

    private void publishAndPersist(ComputerSyncConfig next) {
        try {
            configSource.save(next);
        } catch (Exception ignored) {
            // SharedPreferences is best effort. The in-memory state still preserves last known good data.
        }
        ComputerSyncState nextState = ComputerSyncState.fromConfig(next);
        state = nextState;
        notifyListeners(nextState);
    }

    private ComputerSyncConfig safeLoad() {
        try {
            ComputerSyncConfig loaded = configSource.load();
            return loaded == null ? ComputerSyncConfig.empty() : loaded;
        } catch (Exception ignored) {
            return ComputerSyncConfig.empty();
        }
    }

    private String nowIso() { return Instant.now(clock).toString(); }
    private static int increment(int value) { return value == Integer.MAX_VALUE ? value : value + 1; }

    private void cancelIntervalLocked() {
        if (intervalFuture != null) {
            intervalFuture.cancel(false);
            intervalFuture = null;
        }
    }

    private boolean isForeground() {
        synchronized (foregroundLock) { return foreground && !closed; }
    }

    private void remove(ListenerSlot slot) {
        synchronized (listenerLock) {
            if (slot != null && listeners.get(slot.listener) == slot) listeners.remove(slot.listener);
        }
    }

    private void notifyListeners(ComputerSyncState next) {
        List<ListenerSlot> snapshot;
        synchronized (listenerLock) { snapshot = new ArrayList<ListenerSlot>(listeners.values()); }
        for (ListenerSlot slot : snapshot) {
            synchronized (listenerLock) {
                ListenerSlot current = listeners.get(slot.listener);
                if (current != slot || current.generation != slot.generation) continue;
                try { slot.listener.onComputerSyncState(next); } catch (RuntimeException ignored) { }
            }
        }
    }

    private static final class ListenerSlot {
        private final ComputerSyncStateListener listener;
        private final long generation;
        private ListenerSlot(ComputerSyncStateListener listener, long generation) {
            this.listener = listener;
            this.generation = generation;
        }
    }

    private static final class AndroidConfigSource implements ConfigSource {
        private final Context applicationContext;
        private final ComputerSyncStore store;
        private AndroidConfigSource(Context context, ComputerSyncStore store) {
            if (context == null || store == null) throw new IllegalArgumentException("Application context and store are required");
            this.applicationContext = context.getApplicationContext();
            this.store = store;
        }
        @Override public ComputerSyncConfig load() { return store.load(applicationContext); }
        @Override public void save(ComputerSyncConfig config) { store.save(applicationContext, config); }
    }

    private static final class AndroidSyncAction implements SyncAction {
        private final Context applicationContext;
        private final ComputerSyncService service;
        private AndroidSyncAction(Context context, ComputerSyncService service) {
            if (context == null || service == null) throw new IllegalArgumentException("Application context and service are required");
            this.applicationContext = context.getApplicationContext();
            this.service = service;
        }
        @Override public SyncOutcome run(ComputerSyncConfig config) throws Exception {
            if (!config.configured()) throw new ComputerSyncException(ComputerSyncFailureReason.INVALID_CONFIG, "电脑同步连接未配置");
            ComputerSyncManifestV2 manifest = service.checkManifestV2(applicationContext);
            if (manifest.snapshotId().equals(config.coordinatorSnapshotId())) return SyncOutcome.unchanged();
            File temporary = service.downloadV2Database(applicationContext, manifest);
            try {
                new V2SnapshotStore(applicationContext).persistDownloaded(manifest, manifest.originalUtf8Bytes(), temporary);
                return SyncOutcome.persistedPending(manifest.snapshotId());
            } finally {
                if (temporary != null && temporary.isFile()) temporary.delete();
            }
        }
    }
}
