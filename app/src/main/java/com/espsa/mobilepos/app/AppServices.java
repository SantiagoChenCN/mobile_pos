package com.espsa.mobilepos.app;

import android.content.Context;
import android.net.Uri;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.catalog.ProductCatalogService;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.importer.ProductImportException;
import com.espsa.mobilepos.core.importer.ProductImportResult;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;
import com.espsa.mobilepos.core.ledger.LedgerService;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.pricing.DefaultPriceCalculator;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AppServices {
    private final ProductCatalogService catalog;
    private final CheckoutService checkout;
    private final LedgerService ledger;
    private final InMemoryProductRepository productRepository;
    private final InMemorySaleRepository saleRepository;
    private final ProductLocalStore productLocalStore;
    private String lastImportMessage = "";
    private Cart currentCart;

    private AppServices(
            ProductCatalogService catalog,
            CheckoutService checkout,
            LedgerService ledger,
            InMemoryProductRepository productRepository,
            InMemorySaleRepository saleRepository,
            ProductLocalStore productLocalStore
    ) {
        this.catalog = catalog;
        this.checkout = checkout;
        this.ledger = ledger;
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.productLocalStore = productLocalStore;
        this.currentCart = checkout.startCart();
    }

    public static AppServices create(Context context) {
        InMemoryProductRepository productRepository = new InMemoryProductRepository();
        ProductLocalStore productLocalStore = new ProductLocalStore();
        List<Product> storedProducts = productLocalStore.load(context);
        productRepository.replaceAll(storedProducts.isEmpty() ? demoProducts() : storedProducts);

        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        ProductCatalogService catalog = new ProductCatalogService(productRepository);
        CheckoutService checkout = new CheckoutService(productRepository, new DefaultPriceCalculator(), saleRepository);
        LedgerService ledger = new LedgerService(saleRepository, ZoneId.systemDefault());
        return new AppServices(catalog, checkout, ledger, productRepository, saleRepository, productLocalStore);
    }

    public static AppServices createDemoServices() {
        InMemoryProductRepository productRepository = new InMemoryProductRepository();
        productRepository.replaceAll(demoProducts());

        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        ProductCatalogService catalog = new ProductCatalogService(productRepository);
        CheckoutService checkout = new CheckoutService(productRepository, new DefaultPriceCalculator(), saleRepository);
        LedgerService ledger = new LedgerService(saleRepository, ZoneId.systemDefault());
        return new AppServices(catalog, checkout, ledger, productRepository, saleRepository, new ProductLocalStore());
    }

    private static List<Product> demoProducts() {
        return new ArrayList<Product>(Arrays.asList(
                new Product("demo-1", "7790580000001", "Yerba Oferta", "almacen", "un", Money.of(2000), Money.of(1499), 2, false),
                new Product("demo-2", "7790895000012", "Aceite girasol", "almacen", "un", Money.of(2800), null, 0, false),
                new Product("demo-3", "7791234000019", "Leche entera", "lacteos", "un", Money.of(1250), Money.of(1100), 6, false)
        ));
    }

    public ProductCatalogService catalog() {
        return catalog;
    }

    public CheckoutService checkout() {
        return checkout;
    }

    public LedgerService ledger() {
        return ledger;
    }

    public InMemoryProductRepository productRepository() {
        return productRepository;
    }

    public InMemorySaleRepository saleRepository() {
        return saleRepository;
    }

    public Cart currentCart() {
        return currentCart;
    }

    public Cart resetCart() {
        currentCart = checkout.startCart();
        return currentCart;
    }

    public ProductImportResult importMingshengDatabase(Context context, Uri uri) throws ProductImportException {
        ProductImportResult result = new AndroidDbProductImporter().importFromUri(context, uri);
        catalog.applyImport(result);
        try {
            productLocalStore.save(context, result.products());
        } catch (Exception ex) {
            throw new ProductImportException("商品已读取，但保存到手机本地失败", ex);
        }
        lastImportMessage = "products=" + result.productCount() + ", promotions=" + result.promotionCount();
        return result;
    }

    public String lastImportMessage() {
        return lastImportMessage;
    }
}
