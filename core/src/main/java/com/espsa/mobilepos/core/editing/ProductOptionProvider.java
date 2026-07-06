package com.espsa.mobilepos.core.editing;

import com.espsa.mobilepos.core.model.Product;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ProductOptionProvider {
    private final Supplier<List<Product>> latestSnapshotProducts;

    public ProductOptionProvider(Supplier<List<Product>> latestSnapshotProducts) {
        this.latestSnapshotProducts = latestSnapshotProducts;
    }

    public List<String> categoryOptions(Product currentProductOrNull) {
        List<String> options = extractCategoriesFromLatestSnapshot();
        if (options.isEmpty()) {
            options = fallbackCategories();
        }
        return normalizedOptions(options, currentProductOrNull == null ? "" : currentProductOrNull.category());
    }

    public List<String> unitOptions(Product currentProductOrNull) {
        List<String> options = extractUnitsFromLatestSnapshot();
        if (options.isEmpty()) {
            options = fallbackUnits();
        }
        return normalizedOptions(options, currentProductOrNull == null ? "" : currentProductOrNull.unitName());
    }

    private List<String> extractCategoriesFromLatestSnapshot() {
        List<Product> products = snapshotProducts();
        List<String> categories = new ArrayList<String>();
        for (Product product : products) {
            categories.add(product.category());
        }
        return categories;
    }

    private List<String> extractUnitsFromLatestSnapshot() {
        List<Product> products = snapshotProducts();
        List<String> units = new ArrayList<String>();
        for (Product product : products) {
            units.add(product.unitName());
        }
        return units;
    }

    private List<Product> snapshotProducts() {
        if (latestSnapshotProducts == null) {
            return Collections.emptyList();
        }
        List<Product> products = latestSnapshotProducts.get();
        return products == null ? Collections.<Product>emptyList() : products;
    }

    private List<String> fallbackCategories() {
        return Arrays.asList(
                "Almacen",
                "Bebidas",
                "Limpieza",
                "Perfumeria",
                "Lacteos",
                "Fiambres",
                "Panaderia",
                "Verduleria",
                "Carniceria",
                "Congelados",
                "Mascotas",
                "Bazar",
                "Otros"
        );
    }

    private List<String> fallbackUnits() {
        return Arrays.asList("un", "kg", "g", "L", "ml", "pack", "caja", "bolsa", "docena");
    }

    private List<String> normalizedOptions(List<String> options, String currentValue) {
        Set<String> unique = new LinkedHashSet<String>();
        unique.add("");
        for (String option : options) {
            addIfPresent(unique, option);
        }
        addIfPresent(unique, currentValue);
        List<String> result = new ArrayList<String>(unique);
        if (result.size() > 1) {
            List<String> tail = new ArrayList<String>(result.subList(1, result.size()));
            Collections.sort(tail, String.CASE_INSENSITIVE_ORDER);
            result = new ArrayList<String>();
            result.add("");
            result.addAll(tail);
        }
        return result;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.trim().isEmpty()) {
            values.add(value.trim());
        }
    }
}
