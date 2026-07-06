package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.catalog.ProductRepository;
import com.espsa.mobilepos.core.ledger.Sale;
import com.espsa.mobilepos.core.ledger.SaleLine;
import com.espsa.mobilepos.core.ledger.SaleRepository;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.SaleStatus;
import com.espsa.mobilepos.core.pricing.CartPriceResult;
import com.espsa.mobilepos.core.pricing.LinePriceResult;
import com.espsa.mobilepos.core.pricing.PriceCalculator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class CheckoutService {
    private static final DateTimeFormatter SALE_ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final ProductRepository productRepository;
    private final PriceCalculator priceCalculator;
    private final SaleRepository saleRepository;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public CheckoutService(ProductRepository productRepository, PriceCalculator priceCalculator, SaleRepository saleRepository) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository");
        this.priceCalculator = Objects.requireNonNull(priceCalculator, "priceCalculator");
        this.saleRepository = Objects.requireNonNull(saleRepository, "saleRepository");
    }

    public Cart startCart() {
        return new Cart();
    }

    public CartLine addProductByBarcode(Cart cart, String barcode, int quantity) throws ProductNotFoundException {
        Optional<Product> product = productRepository.findByBarcode(barcode);
        if (!product.isPresent()) {
            throw new ProductNotFoundException(barcode);
        }
        return cart.addProduct(product.get(), quantity);
    }

    public CartLine addManualAlmacenItem(Cart cart, Money unitPrice, int quantity) {
        Product manual = Product.manualAlmacen("manual-" + Instant.now().toEpochMilli(), unitPrice);
        return cart.addProduct(manual, quantity);
    }

    public CartPriceResult preview(Cart cart) {
        return priceCalculator.calculateCart(cart);
    }

    public Sale checkout(Cart cart, PaymentMethod paymentMethod) {
        if (cart == null || cart.isEmpty()) {
            throw new IllegalArgumentException("Cannot checkout an empty cart");
        }
        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        CartPriceResult price = priceCalculator.calculateCart(cart);
        if (price.total().amount() <= 0) {
            throw new IllegalArgumentException("Sale total must be greater than zero");
        }

        List<SaleLine> saleLines = new ArrayList<SaleLine>();
        for (LinePriceResult linePrice : price.lines()) {
            Product product = linePrice.line().product();
            saleLines.add(new SaleLine(
                    product.id(),
                    product.barcode(),
                    product.name(),
                    product.category(),
                    linePrice.line().quantity(),
                    linePrice.originalUnitPrice(),
                    linePrice.appliedUnitPrice(),
                    linePrice.grossSubtotal(),
                    linePrice.lineDiscount(),
                    linePrice.lineDiscountAmount(),
                    linePrice.finalSubtotal(),
                    linePrice.automaticPromotionApplied(),
                    linePrice.manualPriceApplied(),
                    product.isManualPriceProduct()
            ));
        }

        Sale sale = new Sale(
                nextSaleId(),
                Instant.now(),
                paymentMethod,
                price.subtotal(),
                price.cartDiscount(),
                price.cartDiscountAmount(),
                price.total(),
                SaleStatus.NORMAL,
                saleLines
        );
        return saleRepository.save(sale);
    }

    public Sale voidSale(String saleId) {
        return saleRepository.voidSale(saleId);
    }

    private String nextSaleId() {
        return "SALE-" + SALE_ID_TIME.format(Instant.now()) + "-" + sequence.getAndIncrement();
    }
}

