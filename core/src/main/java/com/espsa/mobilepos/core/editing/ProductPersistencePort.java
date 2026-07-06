package com.espsa.mobilepos.core.editing;

import com.espsa.mobilepos.core.model.Product;

import java.util.List;

public interface ProductPersistencePort {
    void saveManualProducts(List<Product> products) throws ProductPersistenceException;
}
