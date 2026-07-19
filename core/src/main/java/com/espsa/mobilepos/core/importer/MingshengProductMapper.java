package com.espsa.mobilepos.core.importer;

import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;

import java.math.BigDecimal;
import java.util.Map;

public final class MingshengProductMapper {
    public Product fromGoodListRow(Map<String, String> row) {
        String id = firstNonBlank(row, "GNID", "GID");
        String barcode = text(row, "GBarcode");
        String name = firstNonBlank(row, "GNameX", "GYiNameJian");
        String category = firstNonBlank(row, "RTypeName", "GType");
        String unitName = firstNonBlank(row, "UName", "GUNIT");
        Money salePrice = parseMoney(text(row, "GSalePrice"));

        Money promotionAmount = parseMoney(text(row, "GHuiPrice"));
        int promotionMinQuantity = parseIntegerQuantity(text(row, "GHuiPriceCount"));
        Money promotionPrice = !promotionAmount.isZero() && promotionMinQuantity > 0 ? promotionAmount : null;

        return new Product(
                id,
                barcode,
                name,
                category,
                unitName,
                salePrice,
                promotionPrice,
                promotionMinQuantity,
                false
        );
    }

    private String firstNonBlank(Map<String, String> row, String first, String second) {
        String firstValue = text(row, first);
        if (!firstValue.isEmpty()) {
            return firstValue;
        }
        return text(row, second);
    }

    private String text(Map<String, String> row, String key) {
        if (row == null || key == null) {
            return "";
        }
        String value = row.get(key);
        return value == null ? "" : value.trim();
    }

    private Money parseMoney(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Money.ZERO;
        }
        return Money.of(value.trim());
    }

    private int parseIntegerQuantity(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        BigDecimal quantity = new BigDecimal(value.trim());
        return quantity.stripTrailingZeros().intValueExact();
    }
}
