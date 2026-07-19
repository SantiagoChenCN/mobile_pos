package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<Product> all();

    Optional<Product> findById(String productId);

    Optional<Product> findByBarcode(String barcode);

    List<Product> findAllByBarcode(String barcode);

    List<Product> searchByName(String query, int limit);

    void replaceAll(List<Product> products);

    void upsert(Product product);

    Optional<Product> deleteById(String productId);

    boolean barcodeExists(String barcode, String excludedProductId);

    boolean exactNameExists(String name, String excludedProductId);

    int count();
}
