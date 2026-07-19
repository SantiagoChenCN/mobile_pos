package com.espsa.mobilepos.core.pricing;

import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CartLine;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.Quantity;

import java.util.ArrayList;
import java.util.List;

public final class DefaultPriceCalculator implements PriceCalculator {
    @Override
    public LinePriceResult calculateLine(CartLine line) {
        Product product = line.product();
        Money originalUnitPrice = product.salePrice();
        boolean manualPriceApplied = line.manualUnitPrice() != null;
        boolean automaticPromotionApplied = false;

        Money appliedUnitPrice;
        if (manualPriceApplied) {
            appliedUnitPrice = line.manualUnitPrice();
        } else if (product.hasQuantityPromotion()
                && line.quantityValue().isInteger()
                && line.quantityValue().value().compareTo(
                        java.math.BigDecimal.valueOf(product.promotionMinQuantity())
                ) >= 0) {
            appliedUnitPrice = product.promotionPrice();
            automaticPromotionApplied = true;
        } else {
            appliedUnitPrice = originalUnitPrice;
        }

        Quantity quantity = line.quantityValue();
        Money grossSubtotal = Money.of(appliedUnitPrice.value().multiply(quantity.value()));
        Discount lineDiscount = line.lineDiscount();
        Money lineDiscountAmount = lineDiscount.calculateAmount(grossSubtotal);
        Money finalSubtotal = grossSubtotal.minusCapped(lineDiscountAmount);

        return new LinePriceResult(
                line,
                originalUnitPrice,
                appliedUnitPrice,
                grossSubtotal,
                lineDiscount,
                lineDiscountAmount,
                finalSubtotal,
                automaticPromotionApplied,
                manualPriceApplied
        );
    }

    @Override
    public CartPriceResult calculateCart(Cart cart) {
        List<LinePriceResult> lineResults = new ArrayList<LinePriceResult>();
        Money subtotal = Money.ZERO;
        for (CartLine line : cart.lines()) {
            LinePriceResult lineResult = calculateLine(line);
            lineResults.add(lineResult);
            subtotal = subtotal.plus(lineResult.finalSubtotal());
        }

        Discount cartDiscount = cart.cartDiscount();
        Money cartDiscountAmount = cartDiscount.calculateAmount(subtotal);
        Money total = subtotal.minusCapped(cartDiscountAmount);
        return new CartPriceResult(lineResults, subtotal, cartDiscount, cartDiscountAmount, total);
    }
}
