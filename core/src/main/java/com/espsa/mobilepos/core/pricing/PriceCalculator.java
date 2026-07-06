package com.espsa.mobilepos.core.pricing;

import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CartLine;

public interface PriceCalculator {
    LinePriceResult calculateLine(CartLine line);

    CartPriceResult calculateCart(Cart cart);
}

