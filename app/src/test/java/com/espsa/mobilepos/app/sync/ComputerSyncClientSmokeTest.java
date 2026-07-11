package com.espsa.mobilepos.app.sync;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;

public final class ComputerSyncClientSmokeTest {
    public static void main(String[] args) throws Exception {
        ComputerSyncClient.validateHealthValues(
                Boolean.TRUE,
                "MobilePosSync",
                "1.0",
                "192.168.1.35",
                Integer.valueOf(8765)
        );

        expectReason("wrong sync app is rejected", ComputerSyncFailureReason.INVALID_RESPONSE, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncClient.validateHealthValues(
                        Boolean.TRUE,
                        "OtherService",
                        "1.0",
                        "192.168.1.35",
                        Integer.valueOf(8765)
                );
            }
        });
        expectReason("missing health field is rejected", ComputerSyncFailureReason.INVALID_RESPONSE, new SyncAction() {
            @Override
            public void run() throws ComputerSyncException {
                ComputerSyncClient.validateHealthValues(
                        Boolean.TRUE,
                        "MobilePosSync",
                        "1.0",
                        "192.168.1.35",
                        null
                );
            }
        });

        assertReason("HTTP 403 is invalid token", ComputerSyncFailureReason.INVALID_TOKEN,
                ComputerSyncClient.httpStatusException(403, "request"));
        assertReason("other HTTP status is HTTP error", ComputerSyncFailureReason.HTTP_ERROR,
                ComputerSyncClient.httpStatusException(500, "request"));
        assertReason("timeout is classified", ComputerSyncFailureReason.CONNECTION_TIMEOUT,
                ComputerSyncClient.connectionFailureFor(new SocketTimeoutException("timeout")));
        assertReason("refused connection is classified", ComputerSyncFailureReason.CONNECTION_REFUSED,
                ComputerSyncClient.connectionFailureFor(new ConnectException("refused")));
        assertReason("unknown host is classified", ComputerSyncFailureReason.UNKNOWN_HOST,
                ComputerSyncClient.connectionFailureFor(new UnknownHostException("host")));
        assertReason("cleartext block is classified", ComputerSyncFailureReason.CLEAR_TEXT_BLOCKED,
                ComputerSyncClient.connectionFailureFor(new UnknownServiceException("CLEARTEXT not permitted")));

        ComputerSyncException sanitized = ComputerSyncClient.connectionFailureFor(
                new IOException("request failed for token=secret-token")
        );
        assertTrue("connection error does not expose token", !sanitized.getMessage().contains("secret-token"));

        System.out.println("Computer sync client smoke test passed");
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
            assertReason(label, expectedReason, actual);
        }
    }

    private static void assertReason(
            String label,
            ComputerSyncFailureReason expectedReason,
            ComputerSyncException actual
    ) {
        assertTrue(label, actual.reason() == expectedReason);
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
