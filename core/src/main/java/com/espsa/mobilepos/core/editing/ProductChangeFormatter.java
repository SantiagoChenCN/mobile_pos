package com.espsa.mobilepos.core.editing;

import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;

import java.util.ArrayList;
import java.util.List;

public final class ProductChangeFormatter {
    public List<ProductChange> diff(Product before, Product after) {
        List<ProductChange> changes = new ArrayList<ProductChange>();
        if (before == null || after == null) {
            return changes;
        }
        addTextChange(changes, "条码", "Codigo", before.barcode(), after.barcode());
        addTextChange(changes, "名称", "Nombre", before.name(), after.name());
        addMoneyChange(changes, "售价", "Precio", before.salePrice(), after.salePrice());
        addTextChange(changes, "分类", "Categoria", before.category(), after.category());
        addTextChange(changes, "单位", "Unidad", before.unitName(), after.unitName());
        addPromotionChange(changes, before, after);
        return changes;
    }

    public String formatZh(List<ProductChange> changes) {
        return format(changes, true);
    }

    public String formatEs(List<ProductChange> changes) {
        return format(changes, false);
    }

    private void addTextChange(List<ProductChange> changes, String labelZh, String labelEs, String before, String after) {
        if (!value(before).equals(value(after))) {
            changes.add(new ProductChange(labelZh, labelEs, value(before), value(after)));
        }
    }

    private void addMoneyChange(List<ProductChange> changes, String labelZh, String labelEs, Money before, Money after) {
        if (before == null && after == null) {
            return;
        }
        if (before == null || after == null || before.compareTo(after) != 0) {
            changes.add(new ProductChange(labelZh, labelEs, money(before), money(after)));
        }
    }

    private void addPromotionChange(List<ProductChange> changes, Product before, Product after) {
        String beforePromotion = promotionText(before);
        String afterPromotion = promotionText(after);
        if (!beforePromotion.equals(afterPromotion)) {
            changes.add(new ProductChange("促销", "Promocion", beforePromotion, afterPromotion));
        }
    }

    private String format(List<ProductChange> changes, boolean zh) {
        if (changes == null || changes.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ProductChange change : changes) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(zh ? change.fieldLabelZh() : change.fieldLabelEs())
                    .append(": ")
                    .append(change.oldValue())
                    .append(" -> ")
                    .append(change.newValue());
        }
        return builder.toString();
    }

    private String promotionText(Product product) {
        if (product == null || !product.hasQuantityPromotion()) {
            return "";
        }
        return money(product.promotionPrice()) + " x" + product.promotionMinQuantity();
    }

    private String money(Money money) {
        return money == null ? "" : "$" + money.canonicalText();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
