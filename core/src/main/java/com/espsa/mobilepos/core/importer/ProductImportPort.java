package com.espsa.mobilepos.core.importer;

import java.io.InputStream;

public interface ProductImportPort {
    ProductImportResult importProducts(InputStream inputStream, String sourceFileName) throws ProductImportException;
}

