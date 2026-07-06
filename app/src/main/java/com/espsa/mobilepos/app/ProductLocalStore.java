package com.espsa.mobilepos.app;

import android.content.Context;

import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ProductLocalStore {
    private static final String FILE_NAME = "products.json";

    public List<Product> load(Context context) {
        File file = file(context);
        if (!file.exists()) {
            return new ArrayList<Product>();
        }
        try {
            byte[] bytes = readAllBytes(file);
            JSONArray array = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
            List<Product> products = new ArrayList<Product>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                long promotionPrice = item.optLong("promotionPrice", 0);
                products.add(new Product(
                        item.getString("id"),
                        item.optString("barcode", ""),
                        item.getString("name"),
                        item.optString("category", Product.MANUAL_ALMACEN_CATEGORY),
                        item.optString("unitName", ""),
                        Money.of(item.getLong("salePrice")),
                        promotionPrice > 0 ? Money.of(promotionPrice) : null,
                        item.optInt("promotionMinQuantity", 0),
                        item.optBoolean("manualPriceProduct", false)
                ));
            }
            return products;
        } catch (Exception ex) {
            return new ArrayList<Product>();
        }
    }

    public void save(Context context, List<Product> products) throws Exception {
        JSONArray array = new JSONArray();
        if (products != null) {
            for (Product product : products) {
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
                array.put(item);
            }
        }
        FileOutputStream output = new FileOutputStream(file(context), false);
        try {
            output.write(array.toString().getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }

    private File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private byte[] readAllBytes(File file) throws Exception {
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
            return bytes;
        } finally {
            input.close();
        }
    }
}

