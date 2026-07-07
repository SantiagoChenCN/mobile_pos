package com.espsa.mobilepos.app;

import android.content.Context;
import android.net.Uri;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.catalog.ProductCatalogService;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.editing.ProductEditingService;
import com.espsa.mobilepos.core.editing.ProductOptionProvider;
import com.espsa.mobilepos.core.importer.ProductImportException;
import com.espsa.mobilepos.core.importer.ProductImportResult;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;
import com.espsa.mobilepos.core.ledger.LedgerService;
import com.espsa.mobilepos.core.library.ProductLibraryState;
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
    private final ProductEditingService productEditing;
    private final ProductLibraryService productLibrary;
    private final UserPreferencesStore preferencesStore;
    private final AndroidFileNameResolver fileNameResolver;
    private final SearchTaskRunner searchTaskRunner;
    private String lastImportMessage = "";
    private Cart currentCart;

    private AppServices(
            ProductCatalogService catalog,
            CheckoutService checkout,
            LedgerService ledger,
            InMemoryProductRepository productRepository,
            InMemorySaleRepository saleRepository,
            ProductLocalStore productLocalStore,
            ProductEditingService productEditing,
            ProductLibraryService productLibrary,
            UserPreferencesStore preferencesStore,
            AndroidFileNameResolver fileNameResolver,
            SearchTaskRunner searchTaskRunner
    ) {
        this.catalog = catalog;
        this.checkout = checkout;
        this.ledger = ledger;
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.productLocalStore = productLocalStore;
        this.productEditing = productEditing;
        this.productLibrary = productLibrary;
        this.preferencesStore = preferencesStore;
        this.fileNameResolver = fileNameResolver;
        this.searchTaskRunner = searchTaskRunner;
        this.currentCart = checkout.startCart();
    }

    public static AppServices create(Context context) {
        InMemoryProductRepository productRepository = new InMemoryProductRepository();
        ProductLocalStore productLocalStore = new ProductLocalStore();
        ProductLibraryState state = loadInitialProductState(context, productLocalStore);
        productRepository.replaceAll(state.products());

        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        ProductCatalogService catalog = new ProductCatalogService(productRepository);
        CheckoutService checkout = new CheckoutService(productRepository, new DefaultPriceCalculator(), saleRepository);
        LedgerService ledger = new LedgerService(saleRepository, ZoneId.systemDefault());
        ProductLibraryService productLibrary = new ProductLibraryService(context, productLocalStore, productRepository);
        ProductOptionProvider optionProvider = new ProductOptionProvider(productLibrary::latestImportSnapshotProducts);
        ProductEditingService productEditing = new ProductEditingService(productRepository, productLibrary, optionProvider);
        return new AppServices(
                catalog,
                checkout,
                ledger,
                productRepository,
                saleRepository,
                productLocalStore,
                productEditing,
                productLibrary,
                new UserPreferencesStore(),
                new AndroidFileNameResolver(),
                new SearchTaskRunner()
        );
    }

    public static AppServices createDemoServices() {
        InMemoryProductRepository productRepository = new InMemoryProductRepository();
        productRepository.replaceAll(demoProducts());

        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        ProductCatalogService catalog = new ProductCatalogService(productRepository);
        CheckoutService checkout = new CheckoutService(productRepository, new DefaultPriceCalculator(), saleRepository);
        LedgerService ledger = new LedgerService(saleRepository, ZoneId.systemDefault());
        ProductLocalStore productLocalStore = new ProductLocalStore();
        ProductLibraryService productLibrary = new ProductLibraryService(null, productLocalStore, productRepository);
        ProductOptionProvider optionProvider = new ProductOptionProvider(productLibrary::latestImportSnapshotProducts);
        ProductEditingService productEditing = new ProductEditingService(productRepository, productLibrary, optionProvider);
        return new AppServices(
                catalog,
                checkout,
                ledger,
                productRepository,
                saleRepository,
                productLocalStore,
                productEditing,
                productLibrary,
                new UserPreferencesStore(),
                new AndroidFileNameResolver(),
                new SearchTaskRunner()
        );
    }

    private static ProductLibraryState loadInitialProductState(Context context, ProductLocalStore productLocalStore) {
        try {
            return productLocalStore.loadState(context);
        } catch (ProductStoreException ex) {
            return ProductLibraryState.empty();
        }
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

    public ProductEditingService productEditing() {
        return productEditing;
    }

    public ProductLibraryService productLibrary() {
        return productLibrary;
    }

    public UserPreferencesStore preferencesStore() {
        return preferencesStore;
    }

    public SearchTaskRunner searchTaskRunner() {
        return searchTaskRunner;
    }

    public Cart currentCart() {
        return currentCart;
    }

    public Cart resetCart() {
        currentCart = checkout.startCart();
        return currentCart;
    }

    public ProductImportResult importMingshengDatabase(Context context, Uri uri) throws ProductImportException {
        String fileName = fileNameResolver.displayName(context, uri);
        ProductImportResult result = new AndroidDbProductImporter().importFromUri(context, uri);
        try {
            ProductLibraryState state = productLocalStore.saveImportResult(context, result, fileName);
            productRepository.replaceAll(state.products());
        } catch (ProductStoreException ex) {
            throw new ProductImportException("商品已读取，但保存到手机本地失败", ex);
        }
        lastImportMessage = "products=" + result.productCount() + ", promotions=" + result.promotionCount();
        return result;
    }

    public String lastImportMessage() {
        return lastImportMessage;
    }
}
