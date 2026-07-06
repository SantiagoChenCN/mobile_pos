package com.espsa.mobilepos.core.importer;

import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class MingshengProductMapper {
    public Product fromGoodListRow(Map<String, String> row) {
        String id = firstNonBlank(row, "GNID", "GID");
        String barcode = text(row, "GBarcode");
        String name = firstNonBlank(row, "GNameX", "GYiNameJian");
        String category = firstNonBlank(row, "RTypeName", "GType");
        String unitName = firstNonBlank(row, "UName", "GUNIT");
        Money salePrice = Money.of(parseAmount(text(row, "GSalePrice")));

        long promotionAmount = parseAmount(text(row, "GHuiPrice"));
        int promotionMinQuantity = (int) parseAmount(text(row, "GHuiPriceCount"));
        Money promotionPrice = promotionAmount > 0 && promotionMinQuantity > 0 ? Money.of(promotionAmount) : null;

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

    private long parseAmount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return new BigDecimal(value.trim()).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException ex) {
            return Math.round(Double.parseDouble(value.trim()));
        }
    }
}
