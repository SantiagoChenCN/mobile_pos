package com.espsa.mobilepos.core.editing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProductValidationResult {
    private final List<String> errorsZh;
    private final List<String> errorsEs;
    private final ParsedProductDraft parsedDraft;

    public ProductValidationResult(List<String> errorsZh, List<String> errorsEs, ParsedProductDraft parsedDraft) {
        this.errorsZh = errorsZh == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(errorsZh));
        this.errorsEs = errorsEs == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(errorsEs));
        this.parsedDraft = parsedDraft;
    }

    public static ProductValidationResult valid(ParsedProductDraft parsedDraft) {
        return new ProductValidationResult(Collections.<String>emptyList(), Collections.<String>emptyList(), parsedDraft);
    }

    public boolean valid() {
        return errorsZh.isEmpty() && errorsEs.isEmpty() && parsedDraft != null;
    }

    public List<String> errorsZh() {
        return errorsZh;
    }

    public List<String> errorsEs() {
        return errorsEs;
    }

    public ParsedProductDraft parsedDraft() {
        return parsedDraft;
    }
}
