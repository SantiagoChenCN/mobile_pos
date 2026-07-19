package com.espsa.mobilepos.app.sync;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Host-only coordinator contract coverage; network and Android storage remain behind existing v2 seams. */
public final class ComputerSyncCoordinatorSmokeTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        validatesForegroundIntervalContract();
        persistsOnlyCoordinatorOutcomeAcrossSuccessAndFailure();
        suppressesConcurrentTriggersAndLeavesActiveWorkRunningOnStop();
        ignoresUnregisteredListenerCallbacks();
        System.out.println("Computer sync coordinator smoke test passed: " + assertions + " assertions");
    }

    private static void validatesForegroundIntervalContract() {
        assertEquals("default interval", 30, ComputerSyncConfig.empty().foregroundIntervalSeconds());
        assertEquals("disabled interval", 0, ComputerSyncConfig.empty().withForegroundIntervalSeconds(0).foregroundIntervalSeconds());
        assertEquals("minimum interval", 5, ComputerSyncConfig.empty().withForegroundIntervalSeconds(5).foregroundIntervalSeconds());
        assertEquals("maximum interval", 86400, ComputerSyncConfig.empty().withForegroundIntervalSeconds(86400).foregroundIntervalSeconds());
        expectFailure("interval below minimum", () -> ComputerSyncConfig.empty().withForegroundIntervalSeconds(4));
        expectFailure("interval above maximum", () -> ComputerSyncConfig.empty().withForegroundIntervalSeconds(86401));
    }

    private static void persistsOnlyCoordinatorOutcomeAcrossSuccessAndFailure() throws Exception {
        MemoryConfigSource source = new MemoryConfigSource(config());
        AtomicInteger calls = new AtomicInteger();
        ComputerSyncCoordinator.SyncAction action = current -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return ComputerSyncCoordinator.SyncOutcome.persistedPending("ms2011-20260718T120000Z-0123456789ab");
            }
            if (call == 2) return ComputerSyncCoordinator.SyncOutcome.unchanged();
            throw new ComputerSyncException(ComputerSyncFailureReason.CONNECTION_REFUSED, "offline");
        };
        ComputerSyncCoordinator coordinator = new ComputerSyncCoordinator(source, action,
                Executors.newSingleThreadScheduledExecutor(), new TickingClock());
        try {
            coordinator.trigger(ComputerSyncTrigger.MANUAL);
            awaitState("first check", coordinator, state -> !state.snapshotId().isEmpty());
            ComputerSyncState success = coordinator.state();
            assertEquals("successful snapshot id", "ms2011-20260718T120000Z-0123456789ab", success.snapshotId());
            assertEquals("success resets failures", 0, success.consecutiveFailures());
            assertEquals("success check time", "2026-07-18T12:00:00Z", success.lastCheckAtUtc());
            assertEquals("success time", "2026-07-18T12:00:00Z", success.lastSuccessAtUtc());
            coordinator.trigger(ComputerSyncTrigger.NEW_CART);
            awaitState("unchanged check", coordinator, state -> "2026-07-18T12:00:01Z".equals(state.lastCheckAtUtc()));
            ComputerSyncState unchanged = coordinator.state();
            assertEquals("unchanged check preserves last sync", success.lastSuccessAtUtc(), unchanged.lastSuccessAtUtc());
            assertEquals("unchanged check preserves pending snapshot", success.snapshotId(), unchanged.snapshotId());
            coordinator.trigger(ComputerSyncTrigger.MANUAL);
            awaitState("failure check", coordinator, state -> state.consecutiveFailures() == 1);
            ComputerSyncState failure = coordinator.state();
            assertEquals("failure increments count", 1, failure.consecutiveFailures());
            assertEquals("failure retains last-good snapshot", success.snapshotId(), failure.snapshotId());
            assertEquals("failure retains last success", success.lastSuccessAtUtc(), failure.lastSuccessAtUtc());
            assertEquals("state is persisted", 1, source.config.coordinatorConsecutiveFailures());
            assertEquals("no invented state fields", 4, source.config.coordinatorStateFieldCount());
        } finally { coordinator.close(); }
    }

    private static void suppressesConcurrentTriggersAndLeavesActiveWorkRunningOnStop() throws Exception {
        MemoryConfigSource source = new MemoryConfigSource(config().withForegroundIntervalSeconds(0));
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        ComputerSyncCoordinator.SyncAction action = current -> {
            calls.incrementAndGet(); started.countDown(); await("release active check", release, 1, TimeUnit.SECONDS);
            completed.countDown(); return ComputerSyncCoordinator.SyncOutcome.unchanged();
        };
        ComputerSyncCoordinator coordinator = coordinator(source, action);
        try {
            coordinator.startForeground();
            await("active check starts", started, 1, TimeUnit.SECONDS);
            coordinator.trigger(ComputerSyncTrigger.NEW_CART);
            coordinator.stopForeground();
            assertEquals("single flight retains one active request", 1, calls.get());
            release.countDown();
            await("active check completes after stop", completed, 1, TimeUnit.SECONDS);
            assertEquals("stop does not cancel active request", 1, calls.get());
        } finally { coordinator.close(); }
    }

    private static void ignoresUnregisteredListenerCallbacks() throws Exception {
        MemoryConfigSource source = new MemoryConfigSource(config());
        AtomicInteger callbacks = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);
        ComputerSyncCoordinator coordinator = coordinator(source, current -> {
            completed.countDown(); return ComputerSyncCoordinator.SyncOutcome.unchanged();
        });
        try {
            ComputerSyncCoordinator.ListenerRegistration registration = coordinator.addListener(state -> callbacks.incrementAndGet());
            registration.close();
            coordinator.trigger(ComputerSyncTrigger.MANUAL);
            await("unregistered listener work", completed, 1, TimeUnit.SECONDS);
            assertEquals("unregistered listener is ignored", 0, callbacks.get());
        } finally { coordinator.close(); }
    }

    private static ComputerSyncCoordinator coordinator(MemoryConfigSource source, ComputerSyncCoordinator.SyncAction action) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        return new ComputerSyncCoordinator(source, action, executor,
                Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC));
    }

    private static ComputerSyncConfig config() { return new ComputerSyncConfig("192.168.1.20", 8765, "token", "", "", "", ""); }
    private static void await(String label, CountDownLatch latch, long timeout, TimeUnit unit) throws Exception { if (!latch.await(timeout, unit)) throw new AssertionError(label + " timed out"); assertions++; }
    private static void awaitState(String label, ComputerSyncCoordinator coordinator, StateCondition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (condition.matches(coordinator.state())) { assertions++; return; }
            Thread.sleep(10L);
        }
        throw new AssertionError(label + " timed out");
    }
    private static void expectFailure(String label, ThrowingAction action) { try { action.run(); throw new AssertionError(label); } catch (IllegalArgumentException expected) { assertions++; } catch (Exception unexpected) { throw new AssertionError(label, unexpected); } }
    private static void assertEquals(String label, Object expected, Object actual) { if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(label + " expected=" + expected + " actual=" + actual); assertions++; }
    private interface ThrowingAction { void run() throws Exception; }
    private interface StateCondition { boolean matches(ComputerSyncState state); }
    private static final class TickingClock extends Clock {
        private final AtomicInteger seconds = new AtomicInteger();
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.parse("2026-07-18T12:00:00Z").plusSeconds(seconds.getAndIncrement()); }
    }
    private static final class MemoryConfigSource implements ComputerSyncCoordinator.ConfigSource {
        private ComputerSyncConfig config; private MemoryConfigSource(ComputerSyncConfig value) { config = value; }
        @Override public ComputerSyncConfig load() { return config; }
        @Override public void save(ComputerSyncConfig value) { config = value; }
    }
}
