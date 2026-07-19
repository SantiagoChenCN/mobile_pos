package com.espsa.mobilepos.core.model;

import java.time.Instant;

public final class V2ContractTest {
    public static void main(String[] args) {
        assertEquals("ms2011:7318", V2Contract.sourceProductKey(7318));
        assertEquals("pc-1f03e4ee190b6df903dc7ddf", V2Contract.derivedId("candidate", "MS_GOODLIST", "7318"));
        assertEquals("map-217f524fa87a376937c89acb", V2Contract.derivedId("mapping", "MS_CUXIAO_GOOD", "6:7318"));
        assertEquals("tier-ebb77aad935bde305a09c124", V2Contract.derivedId("tier", "MS_SALE_CXDETAIL1", "6:1"));
        assertEquals("schedule-3328dff18c0d065278d2e40e", V2Contract.derivedId("schedule", "MS_SALE_CXTABLEDING", "6:1"));
        assertEquals("group-775bf544c18dadcea83c48c1", V2Contract.derivedId("group", "MS_SALE_CXMASTERFOUR", "1:A"));

        final String hash = "abcdef1234560000000000000000000000000000000000000000000000000000";
        final String snapshot = V2Contract.snapshotId(Instant.parse("2026-07-17T18:00:01Z"), hash);
        assertEquals("ms2011-20260717T180001Z-abcdef123456", snapshot);
        assertEquals("/v2/snapshots/" + snapshot + ".db", V2Contract.downloadPath(snapshot));
        expectInvalid(new Runnable() { public void run() { V2Contract.validateSnapshotId("../snapshot"); } });
        expectInvalid(new Runnable() { public void run() { V2Contract.validateSnapshotId("ms2011-20261317T180001Z-abcdef123456"); } });
        String[] invalidCalendarSnapshotIds = {
                "ms2011-20260230T120000Z-abcdef123456",
                "ms2011-20250229T120000Z-abcdef123456",
                "ms2011-20260228T240000Z-abcdef123456",
                "ms2011-20260228T126000Z-abcdef123456",
                "ms2011-20260228T125960Z-abcdef123456"
        };
        for (final String invalidSnapshotId : invalidCalendarSnapshotIds) {
            expectInvalid(new Runnable() {
                public void run() {
                    V2Contract.validateSnapshotId(invalidSnapshotId);
                }
            });
        }

        V2Manifest manifest = new V2Manifest(
                true, 2, snapshot, "ms2011_live", "2026-07-17T18:00:01Z",
                1048576L, hash, 1, 11195, 128, 12, 41, 0, 13,
                "/v2/snapshots/" + snapshot + ".db"
        );
        assertEquals(snapshot, manifest.snapshotId());
        V2Manifest minimumBoundaryManifest = new V2Manifest(
                true, 2, snapshot, "ms2011_live", "2026-07-17T18:00:01Z",
                0L, hash, 1L, 0, 0L, 0L, 0, 0, 0,
                "/v2/snapshots/" + snapshot + ".db"
        );
        assertEquals(1, minimumBoundaryManifest.minimumAppVersion());
        assertEquals(0, minimumBoundaryManifest.categoryCount());
        assertEquals(0, minimumBoundaryManifest.unitCount());
        V2Manifest maximumBoundaryManifest = new V2Manifest(
                true, 2, snapshot, "ms2011_live", "2026-07-17T18:00:01Z",
                0L, hash, 2147483647L, 0, 2147483647L, 2147483647L, 0, 0, 0,
                "/v2/snapshots/" + snapshot + ".db"
        );
        assertEquals(2147483647, maximumBoundaryManifest.minimumAppVersion());
        assertEquals(2147483647, maximumBoundaryManifest.categoryCount());
        assertEquals(2147483647, maximumBoundaryManifest.unitCount());
        expectInvalid(new Runnable() {
            public void run() {
                new V2Manifest(true, 2, snapshot, "ms2011_live", "2026-07-17T18:00:01Z",
                        0L, hash, 2147483648L, 0, 0L, 0L, 0, 0, 0,
                        "/v2/snapshots/" + snapshot + ".db");
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new V2Manifest(true, 2, snapshot, "ms2011_live", "2026-07-17T18:00:01Z",
                        0L, hash, 1L, 0, 2147483648L, 0L, 0, 0, 0,
                        "/v2/snapshots/" + snapshot + ".db");
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new V2Manifest(true, 2, snapshot, "ms2011_live", "2026-07-17T18:00:01Z",
                        0L, hash, 1L, 0, 0L, 2147483648L, 0, 0, 0,
                        "/v2/snapshots/" + snapshot + ".db");
            }
        });
        expectInvalid(new Runnable() {
            public void run() {
                new V2Manifest(true, 2, snapshot, "ms2011_live", "2026-07-17T15:00:01-03:00",
                        1, hash, 1, 1, 1, 1, 0, 0, 0, "/v2/snapshots/" + snapshot + ".db");
            }
        });
        V2Contract.requireDownloadSpace(100, 25, 25, 25, 25);
        expectInvalid(new Runnable() { public void run() { V2Contract.requireDownloadSpace(99, 25, 25, 25, 25); } });
        System.out.println("V2 contract test passed");
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected contract failure");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
