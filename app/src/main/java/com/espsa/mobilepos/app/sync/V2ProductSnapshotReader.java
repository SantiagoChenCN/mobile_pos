package com.espsa.mobilepos.app.sync;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;
import com.espsa.mobilepos.core.model.V2Contract;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Maps only a store-verified recovered v2 object; it never mutates a catalog or repository. */
public final class V2ProductSnapshotReader {
    private static final String PRODUCT_QUERY = "SELECT source_product_key,gid,barcode,name,category_code,unit_code,sale_price_decimal,stop_flag FROM products ORDER BY source_product_key";

    interface ProductRowSource { List<ProductRow> read(File database) throws Exception; }

    static final class ProductRow {
        final String sourceProductKey, gid, barcode, name, categoryCode, unitCode, salePriceDecimal, stopFlag;
        ProductRow(String sourceProductKey, String gid, String barcode, String name, String categoryCode, String unitCode, String salePriceDecimal, String stopFlag) {
            this.sourceProductKey = sourceProductKey; this.gid = gid; this.barcode = barcode; this.name = name;
            this.categoryCode = categoryCode; this.unitCode = unitCode; this.salePriceDecimal = salePriceDecimal; this.stopFlag = stopFlag;
        }
    }

    private final V2SnapshotStore store;
    private final ProductRowSource rowSource;

    public V2ProductSnapshotReader(V2SnapshotStore store) {
        this(store, new AndroidProductRowSource());
    }

    V2ProductSnapshotReader(V2SnapshotStore store, ProductRowSource rowSource) {
        if (store == null || rowSource == null) throw new IllegalArgumentException("Verified store and row source are required");
        this.store = store;
        this.rowSource = rowSource;
    }

    public List<Product> readRecovered(V2SnapshotReader.RecoveryResult recovery) throws Exception {
        if (recovery == null || !recovery.hasValidSnapshot()) throw new IllegalArgumentException("No verified recovered snapshot");
        return readVerifiedSnapshot(recovery.activeSnapshotId());
    }

    /** Maps one store-verified immutable snapshot; callers cannot supply an arbitrary file. */
    public List<Product> readVerifiedSnapshot(String snapshotId) throws Exception {
        String id = V2Contract.validateSnapshotId(snapshotId);
        return mapRows(rowSource.read(store.verifiedImmutableObjectFile(id)), id);
    }

    /** Package-private host seam for mapping tests; production callers must use readRecovered. */
    static List<Product> mapRowsForTest(List<ProductRow> rows, String snapshotId) {
        return mapRows(rows, V2Contract.validateSnapshotId(snapshotId));
    }

    private static List<Product> mapRows(List<ProductRow> rows, String snapshotId) {
        if (rows == null) throw new IllegalArgumentException("Snapshot rows are required");
        List<Product> products = new ArrayList<Product>();
        Set<String> sourceKeys = new HashSet<String>();
        Set<String> ids = new HashSet<String>();
        for (ProductRow row : rows) {
            if (row == null) throw new IllegalArgumentException("Snapshot contains null product row");
            long gid = parsePositiveGid(row.gid);
            String expectedKey = V2Contract.sourceProductKey(gid);
            if (!expectedKey.equals(row.sourceProductKey)) throw new IllegalArgumentException("source_product_key does not match GID");
            if (!sourceKeys.add(row.sourceProductKey) || !ids.add(expectedKey)) throw new IllegalArgumentException("Duplicate synchronized product identity");
            products.add(new Product(expectedKey, nullable(row.barcode), required(row.name, "name"),
                    nullable(row.categoryCode), nullable(row.unitCode), Money.of(required(row.salePriceDecimal, "sale_price_decimal")),
                    null, 0, false, ProductOrigin.MS2011_SYNC, expectedKey, snapshotId, parseStopFlag(row.stopFlag)));
        }
        return Collections.unmodifiableList(products);
    }

    private static long parsePositiveGid(String value) {
        String text = required(value, "gid");
        if (!text.matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid GID");
        try { return Long.parseLong(text); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid GID", error); }
    }

    private static boolean parseStopFlag(String value) {
        String text = required(value, "stop_flag");
        if (!text.matches("-?[0-9]+")) throw new IllegalArgumentException("Invalid stop_flag");
        try { return Long.parseLong(text) != 0L; }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid stop_flag", error); }
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String nullable(String value) { return value == null ? "" : value.trim(); }

    private static final class AndroidProductRowSource implements ProductRowSource {
        @Override public List<ProductRow> read(File database) {
            if (database == null) throw new IllegalArgumentException("Verified database is required");
            SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            try {
                Cursor cursor = db.rawQuery(PRODUCT_QUERY, null);
                try {
                    List<ProductRow> rows = new ArrayList<ProductRow>();
                    int sourceKey = cursor.getColumnIndexOrThrow("source_product_key");
                    int gid = cursor.getColumnIndexOrThrow("gid");
                    int barcode = cursor.getColumnIndexOrThrow("barcode");
                    int name = cursor.getColumnIndexOrThrow("name");
                    int category = cursor.getColumnIndexOrThrow("category_code");
                    int unit = cursor.getColumnIndexOrThrow("unit_code");
                    int price = cursor.getColumnIndexOrThrow("sale_price_decimal");
                    int stopped = cursor.getColumnIndexOrThrow("stop_flag");
                    while (cursor.moveToNext()) rows.add(new ProductRow(cursor.getString(sourceKey), cursor.getString(gid), cursor.getString(barcode), cursor.getString(name), cursor.getString(category), cursor.getString(unit), cursor.getString(price), cursor.getString(stopped)));
                    return Collections.unmodifiableList(rows);
                } finally { cursor.close(); }
            } finally { db.close(); }
        }
    }
}
