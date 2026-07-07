package com.espsa.mobilepos.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ListView;

import com.espsa.mobilepos.core.model.Product;

import java.util.List;

public final class ProductSearchResultDialog {
    private ProductSearchResultDialog() {
    }

    public static AlertDialog show(
            Context context,
            AppLanguage language,
            List<Product> products,
            ProductSelectedCallback callback
    ) {
        ListView listView = new ListView(context);
        ProductSearchResultAdapter adapter = new ProductSearchResultAdapter(context, products);
        listView.setAdapter(adapter);
        listView.setDividerHeight(1);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "选择商品", "Elegir producto") + " (" + adapter.getCount() + ")")
                .setView(listView)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .show();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            callback.onProductSelected(adapter.getItem(position));
        });
        return dialog;
    }

    public interface ProductSelectedCallback {
        void onProductSelected(Product product);
    }
}
