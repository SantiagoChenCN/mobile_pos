package com.espsa.mobilepos.core.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ImportFormatRegistry {
    private final Map<ImportFormat, ProductImportAdapter> adapters =
            new EnumMap<ImportFormat, ProductImportAdapter>(ImportFormat.class);

    public ImportFormatRegistry(List<ProductImportAdapter> adapters) {
        if (adapters == null) {
            return;
        }
        for (ProductImportAdapter adapter : adapters) {
            register(adapter);
        }
    }

    public static ImportFormatRegistry coreDefaults() {
        List<ProductImportAdapter> adapters = new ArrayList<ProductImportAdapter>();
        adapters.add(new CsvProductImportAdapter());
        return new ImportFormatRegistry(adapters);
    }

    public ProductImportAdapter adapterFor(ImportFormat format) throws ProductImportException {
        ProductImportAdapter adapter = adapters.get(format);
        if (adapter == null) {
            throw new ProductImportException("Unsupported import format: " + format);
        }
        return adapter;
    }

    public List<ImportFormat> supportedFormats() {
        return Collections.unmodifiableList(new ArrayList<ImportFormat>(adapters.keySet()));
    }

    private void register(ProductImportAdapter adapter) {
        if (adapter != null) {
            adapters.put(adapter.format(), adapter);
        }
    }
}
