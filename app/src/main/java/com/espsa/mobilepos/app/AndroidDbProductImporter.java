package com.espsa.mobilepos.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.espsa.mobilepos.core.importer.MingshengProductMapper;
import com.espsa.mobilepos.core.importer.ProductImportException;
import com.espsa.mobilepos.core.importer.ProductImportResult;
import com.espsa.mobilepos.core.model.Product;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AndroidDbProductImporter {
    private static final String PRODUCT_TABLE = "CJQ_GOODLIST";

    private final MingshengProductMapper mapper = new MingshengProductMapper();

    public ProductImportResult importFromUri(Context context, Uri uri) throws ProductImportException {
        if (context == null || uri == null) {
            throw new ProductImportException("Import file is required");
        }

        File copiedDb = copyToCache(context, uri);
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(copiedDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            return readProducts(database, uri.toString());
        } catch (Exception ex) {
            throw new ProductImportException("不是支持的鸣盛商品数据库 .db", ex);
        } finally {
            if (database != null) {
                database.close();
            }
            copiedDb.delete();
        }
    }

    private File copyToCache(Context context, Uri uri) throws ProductImportException {
        File target = new File(context.getCacheDir(), "import-products.db");
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                throw new ProductImportException("无法打开选择的文件");
            }
            output = new FileOutputStream(target, false);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return target;
        } catch (ProductImportException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProductImportException("复制导入文件失败", ex);
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private ProductImportResult readProducts(SQLiteDatabase database, String sourceName) throws ProductImportException {
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT * FROM " + PRODUCT_TABLE, null);
            List<Product> products = new ArrayList<Product>();
            List<String> warnings = new ArrayList<String>();
            int promotionCount = 0;
            int rowIndex = 0;
            while (cursor.moveToNext()) {
                rowIndex++;
                try {
                    Map<String, String> row = cursorRow(cursor);
                    Product product = mapper.fromGoodListRow(row);
                    products.add(product);
                    if (product.hasQuantityPromotion()) {
                        promotionCount++;
                    }
                } catch (Exception ex) {
                    warnings.add("Row " + rowIndex + ": " + ex.getMessage());
                }
            }
            if (products.isEmpty()) {
                throw new ProductImportException("商品表为空或无法读取商品");
            }
            return new ProductImportResult(sourceName, Instant.now(), products, promotionCount, warnings);
        } catch (ProductImportException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProductImportException("读取商品表失败：" + PRODUCT_TABLE, ex);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private Map<String, String> cursorRow(Cursor cursor) {
        Map<String, String> row = new HashMap<String, String>();
        String[] columnNames = cursor.getColumnNames();
        for (int i = 0; i < columnNames.length; i++) {
            row.put(columnNames[i], cursor.isNull(i) ? "" : cursor.getString(i));
        }
        return row;
    }

    private void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}

