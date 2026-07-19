package com.espsa.mobilepos.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class NormalizedPromotionRule {
    private static final Pattern CANDIDATE_ID = Pattern.compile("^pc-[0-9a-f]{24}$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final String ruleId;
    private final String candidateId;
    private final String ruleType;
    private final String ruleVersion;
    private final String evidenceHash;
    private final Map<String, Object> parameters;
    private final List<Map<String, Object>> tiers;
    private final List<Map<String, Object>> schedules;
    private final List<Map<String, Object>> groups;
    private final Integer priorityOrder;
    private final String stackMode;

    public NormalizedPromotionRule(
            String ruleId, String candidateId, String ruleType, String ruleVersion,
            String evidenceHash, Map<String, Object> parameters,
            List<Map<String, Object>> tiers, List<Map<String, Object>> schedules,
            List<Map<String, Object>> groups, Integer priorityOrder, String stackMode
    ) {
        this.ruleId = requireText(ruleId, "ruleId");
        if (candidateId == null || !CANDIDATE_ID.matcher(candidateId).matches()) {
            throw new IllegalArgumentException("Invalid candidateId");
        }
        this.candidateId = candidateId;
        this.ruleType = requireText(ruleType, "ruleType");
        this.ruleVersion = requireText(ruleVersion, "ruleVersion");
        if (evidenceHash == null || !SHA256.matcher(evidenceHash).matches()) {
            throw new IllegalArgumentException("Invalid evidenceHash");
        }
        this.evidenceHash = evidenceHash;
        if (parameters == null || tiers == null || schedules == null || groups == null) {
            throw new IllegalArgumentException("Rule collections cannot be null");
        }
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(parameters));
        this.tiers = immutableRows(tiers);
        this.schedules = immutableRows(schedules);
        this.groups = immutableRows(groups);
        this.priorityOrder = priorityOrder;
        this.stackMode = requireText(stackMode, "stackMode");
    }

    public String ruleId() { return ruleId; }
    public String candidateId() { return candidateId; }
    public String ruleType() { return ruleType; }
    public String ruleVersion() { return ruleVersion; }
    public String evidenceHash() { return evidenceHash; }
    public Map<String, Object> parameters() { return parameters; }
    public List<Map<String, Object>> tiers() { return tiers; }
    public List<Map<String, Object>> schedules() { return schedules; }
    public List<Map<String, Object>> groups() { return groups; }
    public Integer priorityOrder() { return priorityOrder; }
    public String stackMode() { return stackMode; }

    private static List<Map<String, Object>> immutableRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row == null) {
                throw new IllegalArgumentException("Rule child row cannot be null");
            }
            result.add(Collections.unmodifiableMap(new LinkedHashMap<String, Object>(row)));
        }
        return Collections.unmodifiableList(result);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return value;
    }
}
