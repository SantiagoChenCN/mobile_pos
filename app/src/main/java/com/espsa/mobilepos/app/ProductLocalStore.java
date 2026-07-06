package com.espsa.mobilepos.app;

import android.content.Context;

import com.espsa.mobilepos.core.importer.ProductImportResult;
import com.espsa.mobilepos.core.library.ImportSnapshotInfo;
import com.espsa.mobilepos.core.library.ProductLibraryMetadata;
import com.espsa.mobilepos.core.library.ProductLibraryState;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProductLocalStore {
    private static final String PRODUCTS_FILE_NAME = "products.json";
    private static final String METADATA_FILE_NAME = "product_library_meta.json";
    private static final String SNAPSHOT_DIRECTORY_NAME = "import_snapshots";
    private static final int MAX_RECENT_IMPORTS = 5;

    public ProductLibraryState loadState(Context context) throws ProductStoreException {
        File productsFile = currentProductsFile(context);
        if (!productsFile.exists()) {
            return new ProductLibraryState(Collections.<Product>emptyList(), loadMetadata(context));
        }
        return new ProductLibraryState(readProducts(productsFile), loadMetadata(context));
    }

    public void saveCurrentProducts(
            Context context,
            List<Product> products,
            boolean manuallyModified
    ) throws ProductStoreException {
        writeProducts(currentProductsFile(context), products);
        ProductLibraryMetadata metadata = loadMetadata(context).withManualModification(manuallyModified);
        writeMetadata(metadataFile(context), metadata);
    }

    public ProductLibraryState saveImportResult(
            Context context,
            ProductImportResult result,
            String originalFileName
    ) throws ProductStoreException {
        if (result == null) {
            throw new ProductStoreException("Import result is required");
        }
        ImportSnapshotInfo snapshot = createSnapshotInfo(originalFileName, result);
        writeSnapshot(context, snapshot, result.products());
        writeProducts(currentProductsFile(context), result.products());
        ProductLibraryMetadata metadata = metadataForImport(context, snapshot);
        writeMetadata(metadataFile(context), metadata);
        pruneOldSnapshots(context, metadata.recentImports());
        return new ProductLibraryState(result.products(), metadata);
    }

    public List<ImportSnapshotInfo> listImportSnapshots(Context context) throws ProductStoreException {
        return loadMetadata(context).recentImports();
    }

    public ProductLibraryState restoreSnapshot(Context context, String snapshotId) throws ProductStoreException {
        ImportSnapshotInfo snapshot = findSnapshotInfo(context, snapshotId);
        List<Product> products = readProducts(snapshotFile(context, snapshot.snapshotId()));
        writeProducts(currentProductsFile(context), products);
        ProductLibraryMetadata metadata = metadataForRestore(context, snapshot);
        writeMetadata(metadataFile(context), metadata);
        return new ProductLibraryState(products, metadata);
    }

    public ProductLibraryMetadata loadMetadata(Context context) throws ProductStoreException {
        File file = metadataFile(context);
        if (!file.exists()) {
            return ProductLibraryMetadata.empty();
        }
        try {
            return metadataFromJson(new JSONObject(readUtf8(file)));
        } catch (Exception ex) {
            throw new ProductStoreException("读取商品库元数据失败", ex);
        }
    }

    public List<Product> load(Context context) {
        try {
            return loadState(context).products();
        } catch (ProductStoreException ex) {
            return new ArrayList<Product>();
        }
    }

    public void save(Context context, List<Product> products) throws Exception {
        saveCurrentProducts(context, products, true);
    }

    public List<Product> loadSnapshotProducts(Context context, String snapshotId) throws ProductStoreException {
        return readProducts(snapshotFile(context, snapshotId));
    }

    private ProductLibraryMetadata metadataForImport(Context context, ImportSnapshotInfo snapshot) throws ProductStoreException {
        List<ImportSnapshotInfo> recent = new ArrayList<ImportSnapshotInfo>();
        recent.add(snapshot);
        for (ImportSnapshotInfo existing : loadMetadata(context).recentImports()) {
            if (!existing.snapshotId().equals(snapshot.snapshotId()) && recent.size() < MAX_RECENT_IMPORTS) {
                recent.add(existing);
            }
        }
        return new ProductLibraryMetadata(
                snapshot.fileName(),
                snapshot.importedAtIso(),
                snapshot.productCount(),
                snapshot.promotionCount(),
                false,
                recent
        );
    }

    private ProductLibraryMetadata metadataForRestore(Context context, ImportSnapshotInfo snapshot) throws ProductStoreException {
        return new ProductLibraryMetadata(
                snapshot.fileName(),
                snapshot.importedAtIso(),
                snapshot.productCount(),
                snapshot.promotionCount(),
                false,
                loadMetadata(context).recentImports()
        );
    }

    private ImportSnapshotInfo findSnapshotInfo(Context context, String snapshotId) throws ProductStoreException {
        for (ImportSnapshotInfo snapshot : loadMetadata(context).recentImports()) {
            if (snapshot.snapshotId().equals(snapshotId)) {
                return snapshot;
            }
        }
        throw new ProductStoreException("找不到导入快照");
    }

    private ImportSnapshotInfo createSnapshotInfo(String fileName, ProductImportResult result) {
        String importedAtIso = result.importedAt().toString();
        String snapshotId = "snapshot-" + importedAtIso.replace("-", "")
                .replace(":", "")
                .replace(".", "")
                .replace("Z", "Z");
        return new ImportSnapshotInfo(
                snapshotId,
                fileName == null || fileName.trim().isEmpty() ? result.sourceFileName() : fileName,
                importedAtIso,
                result.productCount(),
                result.promotionCount()
        );
    }

    private void writeSnapshot(Context context, ImportSnapshotInfo info, List<Product> products) throws ProductStoreException {
        File directory = snapshotDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new ProductStoreException("创建导入快照目录失败");
        }
        writeProducts(snapshotFile(context, info.snapshotId()), products);
    }

    private void pruneOldSnapshots(Context context, List<ImportSnapshotInfo> recentImports) {
        File directory = snapshotDirectory(context);
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        List<String> keep = new ArrayList<String>();
        for (ImportSnapshotInfo snapshot : recentImports) {
            keep.add(snapshot.snapshotId() + ".json");
        }
        for (File file : files) {
            if (file.isFile() && !keep.contains(file.getName())) {
                file.delete();
            }
        }
    }

    private List<Product> readProducts(File file) throws ProductStoreException {
        try {
            JSONArray array = new JSONArray(readUtf8(file));
            List<Product> products = new ArrayList<Product>();
            for (int i = 0; i < array.length(); i++) {
                products.add(productFromJson(array.getJSONObject(i)));
            }
            return products;
        } catch (Exception ex) {
            throw new ProductStoreException("读取商品库失败", ex);
        }
    }

    private void writeProducts(File file, List<Product> products) throws ProductStoreException {
        try {
            JSONArray array = new JSONArray();
            if (products != null) {
                for (Product product : products) {
                    array.put(productToJson(product));
                }
            }
            writeUtf8(file, array.toString());
        } catch (Exception ex) {
            throw new ProductStoreException("保存商品库失败", ex);
        }
    }

    private Product productFromJson(JSONObject item) throws Exception {
        long promotionPrice = item.optLong("promotionPrice", 0);
        return new Product(
                item.getString("id"),
                item.optString("barcode", ""),
                item.getString("name"),
                item.optString("category", Product.MANUAL_ALMACEN_CATEGORY),
                item.optString("unitName", ""),
                Money.of(item.getLong("salePrice")),
                promotionPrice > 0 ? Money.of(promotionPrice) : null,
                item.optInt("promotionMinQuantity", 0),
                item.optBoolean("manualPriceProduct", false)
        );
    }

    private JSONObject productToJson(Product product) throws Exception {
        JSONObject item = new JSONObject();
        item.put("id", product.id());
        item.put("barcode", product.barcode());
        item.put("name", product.name());
        item.put("category", product.category());
        item.put("unitName", product.unitName());
        item.put("salePrice", product.salePrice().amount());
        item.put("promotionPrice", product.promotionPrice() == null ? 0 : product.promotionPrice().amount());
        item.put("promotionMinQuantity", product.promotionMinQuantity());
        item.put("manualPriceProduct", product.isManualPriceProduct());
        return item;
    }

    private ProductLibraryMetadata metadataFromJson(JSONObject object) {
        JSONArray array = object.optJSONArray("recentImports");
        List<ImportSnapshotInfo> recentImports = new ArrayList<ImportSnapshotInfo>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    recentImports.add(snapshotFromJson(item));
                }
            }
        }
        return new ProductLibraryMetadata(
                object.optString("lastImportFileName", ""),
                object.optString("lastImportTimeIso", ""),
                object.optInt("lastImportProductCount", 0),
                object.optInt("lastImportPromotionCount", 0),
                object.optBoolean("manuallyModified", false),
                recentImports
        );
    }

    private JSONObject metadataToJson(ProductLibraryMetadata metadata) throws Exception {
        JSONObject object = new JSONObject();
        object.put("lastImportFileName", metadata.lastImportFileName());
        object.put("lastImportTimeIso", metadata.lastImportTimeIso());
        object.put("lastImportProductCount", metadata.lastImportProductCount());
        object.put("lastImportPromotionCount", metadata.lastImportPromotionCount());
        object.put("manuallyModified", metadata.manuallyModified());
        JSONArray array = new JSONArray();
        for (ImportSnapshotInfo snapshot : metadata.recentImports()) {
            array.put(snapshotToJson(snapshot));
        }
        object.put("recentImports", array);
        return object;
    }

    private ImportSnapshotInfo snapshotFromJson(JSONObject item) {
        return new ImportSnapshotInfo(
                item.optString("snapshotId", ""),
                item.optString("fileName", ""),
                item.optString("importedAtIso", ""),
                item.optInt("productCount", 0),
                item.optInt("promotionCount", 0)
        );
    }

    private JSONObject snapshotToJson(ImportSnapshotInfo snapshot) throws Exception {
        JSONObject item = new JSONObject();
        item.put("snapshotId", snapshot.snapshotId());
        item.put("fileName", snapshot.fileName());
        item.put("importedAtIso", snapshot.importedAtIso());
        item.put("productCount", snapshot.productCount());
        item.put("promotionCount", snapshot.promotionCount());
        return item;
    }

    private void writeMetadata(File file, ProductLibraryMetadata metadata) throws ProductStoreException {
        try {
            writeUtf8(file, metadataToJson(metadata).toString());
        } catch (Exception ex) {
            throw new ProductStoreException("保存商品库元数据失败", ex);
        }
    }

    private File currentProductsFile(Context context) {
        return new File(context.getFilesDir(), PRODUCTS_FILE_NAME);
    }

    private File metadataFile(Context context) {
        return new File(context.getFilesDir(), METADATA_FILE_NAME);
    }

    private File snapshotDirectory(Context context) {
        return new File(context.getFilesDir(), SNAPSHOT_DIRECTORY_NAME);
    }

    private File snapshotFile(Context context, String snapshotId) {
        return new File(snapshotDirectory(context), snapshotId + ".json");
    }

    private String readUtf8(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private void writeUtf8(File file, String value) throws Exception {
        FileOutputStream output = new FileOutputStream(file, false);
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }
}
