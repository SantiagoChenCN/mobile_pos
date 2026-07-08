package com.espsa.mobilepos.core.importer;

import java.io.InputStream;

public interface ProductImportAdapter {
    ImportFormat format();

    ProductImportResult importProducts(InputStream inputStream, String sourceFileName)
            throws ProductImportException;
}
