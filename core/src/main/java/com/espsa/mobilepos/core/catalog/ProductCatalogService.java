package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.importer.ProductImportResult;
import com.espsa.mobilepos.core.model.Product;

import java.util.List;
import java.util.Optional;

public final class ProductCatalogService {
    private final ProductRepository productRepository;

    public ProductCatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Optional<Product> findByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode);
    }

    public List<Product> searchByName(String query, int limit) {
        return productRepository.searchByName(query, limit);
    }

    public List<Product> searchByName(String query) {
        return productRepository.searchByName(query, Integer.MAX_VALUE);
    }

    public void applyImport(ProductImportResult importResult) {
        productRepository.replaceAll(importResult.products());
    }

    public int productCount() {
        return productRepository.count();
    }
}
