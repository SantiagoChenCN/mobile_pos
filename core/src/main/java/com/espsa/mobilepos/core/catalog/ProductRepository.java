package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findByBarcode(String barcode);

    List<Product> searchByName(String query, int limit);

    void replaceAll(List<Product> products);

    int count();
}

