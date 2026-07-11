package com.espsa.mobilepos.app.sync;

public final class ComputerSyncServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        expectReason("empty host is rejected", ComputerSyncFailureReason.INVALID_CONFIG, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncService.validateManualConfig("", 8765, "TOKEN");
            }
        });
        expectReason("zero port is rejected", ComputerSyncFailureReason.INVALID_CONFIG, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncService.validateManualConfig("192.168.1.35", 0, "TOKEN");
            }
        });
        expectReason("large port is rejected", ComputerSyncFailureReason.INVALID_CONFIG, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncService.validateManualConfig("192.168.1.35", 65536, "TOKEN");
            }
        });
        expectReason("empty token is rejected", ComputerSyncFailureReason.INVALID_CONFIG, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncService.validateManualConfig("192.168.1.35", 8765, "");
            }
        });
        expectReason("loopback address is rejected", ComputerSyncFailureReason.INVALID_CONFIG, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncService.validateManualConfig("127.0.0.1", 8765, "TOKEN");
            }
        });
        expectReason("localhost is rejected", ComputerSyncFailureReason.INVALID_CONFIG, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncService.validateManualConfig("localhost", 8765, "TOKEN");
            }
        });
        expectReason("wildcard address is rejected", ComputerSyncFailureReason.INVALID_CONFIG, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncService.validateManualConfig("0.0.0.0", 8765, "TOKEN");
            }
        });
        expectReason("invalid IPv4 is rejected", ComputerSyncFailureReason.INVALID_CONFIG, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncService.validateManualConfig("192.168.1.256", 8765, "TOKEN");
            }
        });

        ComputerSyncConfig config = ComputerSyncService.validateManualConfig(" 192.168.1.35 ", 8765, " TOKEN ");
        assertTrue("valid config is configured", config.configured());
        assertTrue("host is trimmed", "192.168.1.35".equals(config.host()));
        assertTrue("token is trimmed", "TOKEN".equals(config.token()));
        assertTrue("base url is correct", "http://192.168.1.35:8765".equals(config.baseUrl()));

        System.out.println("Computer sync service smoke test passed");
    }

    private static void expectReason(
            String label,
            ComputerSyncFailureReason expectedReason,
            SyncAction action
    ) throws Exception {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (ComputerSyncException actual) {
            assertTrue(label + " reason", actual.reason() == expectedReason);
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private interface SyncAction {
        void run() throws ComputerSyncException;
    }
}
