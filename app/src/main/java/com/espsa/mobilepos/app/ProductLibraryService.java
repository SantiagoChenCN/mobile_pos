package com.espsa.mobilepos.app;

import android.content.Context;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.editing.ProductPersistenceException;
import com.espsa.mobilepos.core.editing.ProductPersistencePort;
import com.espsa.mobilepos.core.library.ImportSnapshotInfo;
import com.espsa.mobilepos.core.library.ProductLibraryMetadata;
import com.espsa.mobilepos.core.library.ProductLibraryState;
import com.espsa.mobilepos.core.model.Product;

import java.util.Collections;
import java.util.List;

public final class ProductLibraryService implements ProductPersistencePort {
    private final Context context;
    private final ProductLocalStore localStore;
    private final InMemoryProductRepository productRepository;

    public ProductLibraryService(
            Context context,
            ProductLocalStore localStore,
            InMemoryProductRepository productRepository
    ) {
        this.context = context == null ? null : context.getApplicationContext();
        this.localStore = localStore;
        this.productRepository = productRepository;
    }

    public ProductLibraryMetadata metadata() throws ProductStoreException {
        return localStore.loadMetadata(requireContext());
    }

    public List<ImportSnapshotInfo> recentImports() throws ProductStoreException {
        return localStore.listImportSnapshots(requireContext());
    }

    public ProductLibraryState restoreSnapshot(String snapshotId) throws ProductStoreException {
        ProductLibraryState state = localStore.restoreSnapshot(requireContext(), snapshotId);
        productRepository.replaceAll(state.products());
        return state;
    }

    public ProductLibraryState currentState() throws ProductStoreException {
        return localStore.loadState(requireContext());
    }

    public List<Product> latestImportSnapshotProducts() {
        try {
            if (context == null) {
                return Collections.emptyList();
            }
            List<ImportSnapshotInfo> imports = localStore.listImportSnapshots(context);
            if (imports.isEmpty()) {
                return Collections.emptyList();
            }
            return localStore.loadSnapshotProducts(context, imports.get(0).snapshotId());
        } catch (ProductStoreException ex) {
            return Collections.emptyList();
        }
    }

    public void markManualProductLibraryChange(List<Product> currentProducts) throws ProductStoreException {
        localStore.saveCurrentProducts(requireContext(), currentProducts, true);
    }

    @Override
    public void saveManualProducts(List<Product> products) throws ProductPersistenceException {
        try {
            markManualProductLibraryChange(products);
        } catch (ProductStoreException ex) {
            throw new ProductPersistenceException("保存本地商品库失败", ex);
        }
    }

    private Context requireContext() throws ProductStoreException {
        if (context == null) {
            throw new ProductStoreException("Android context is required");
        }
        return context;
    }
}
