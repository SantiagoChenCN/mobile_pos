package com.espsa.mobilepos.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NormalizedPromotionRuleContractTest {
    public static void main(String[] args) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> empty = new ArrayList<Map<String, Object>>();
        NormalizedPromotionRule rule = new NormalizedPromotionRule(
                "rule-simple-v1",
                "pc-1f03e4ee190b6df903dc7ddf",
                "FIXTURE_ONLY",
                "fixture-only-v1",
                "abcdef1234560000000000000000000000000000000000000000000000000000",
                parameters, empty, empty, empty, null, "UNVERIFIED"
        );
        assertEquals("pc-1f03e4ee190b6df903dc7ddf", rule.candidateId());
        assertTrue(rule.schedules().isEmpty());
        try {
            rule.parameters().put("formula", "invented");
            throw new AssertionError("Parameters must be immutable");
        } catch (UnsupportedOperationException expected) {
        }
        System.out.println("Normalized promotion rule contract test passed");
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition failed");
        }
    }
}
