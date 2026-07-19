package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.catalog.ProductRepository;
import com.espsa.mobilepos.core.ledger.Sale;
import com.espsa.mobilepos.core.ledger.SaleLine;
import com.espsa.mobilepos.core.ledger.SaleRepository;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.Quantity;
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
    private final ProductRepository productRepository;
    private final PriceCalculator priceCalculator;
    private final SaleRepository saleRepository;
    private final DateTimeFormatter saleIdTime;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public CheckoutService(ProductRepository productRepository, PriceCalculator priceCalculator, SaleRepository saleRepository) {
        this(productRepository, priceCalculator, saleRepository, ZoneId.systemDefault());
    }

    public CheckoutService(
            ProductRepository productRepository,
            PriceCalculator priceCalculator,
            SaleRepository saleRepository,
            ZoneId businessZone
    ) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository");
        this.priceCalculator = Objects.requireNonNull(priceCalculator, "priceCalculator");
        this.saleRepository = Objects.requireNonNull(saleRepository, "saleRepository");
        this.saleIdTime = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(businessZone == null ? ZoneId.systemDefault() : businessZone);
    }

    public Cart startCart() {
        return startCart(PricingSnapshotRef.localLibrary(productRepository));
    }

    /** CB-02 supplies the active snapshot identity and immutable lookup at the approved order boundary. */
    public Cart startCart(PricingSnapshotRef pricingSnapshotRef) {
        return new Cart(null, Objects.requireNonNull(pricingSnapshotRef, "pricingSnapshotRef"));
    }

    public CartLine addProductByBarcode(Cart cart, String barcode, Quantity quantity) throws ProductNotFoundException {
        Objects.requireNonNull(cart, "cart");
        Optional<Product> product = cart.pricingSnapshotRef().findByBarcode(barcode);
        if (!product.isPresent()) {
            throw new ProductNotFoundException(barcode);
        }
        return cart.addProduct(product.get(), quantity);
    }

    public CartLine addManualAlmacenItem(Cart cart, Money unitPrice, Quantity quantity) {
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
        if (price.total().compareTo(Money.ZERO) <= 0) {
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
                    linePrice.line().quantityValue(),
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
        return formatSaleId(Instant.now(), sequence.getAndIncrement());
    }

    String formatSaleId(Instant instant, int sequenceNumber) {
        return "SALE-" + saleIdTime.format(instant) + "-" + sequenceNumber;
    }
}
