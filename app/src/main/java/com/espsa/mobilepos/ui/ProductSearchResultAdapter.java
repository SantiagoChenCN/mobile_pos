package com.espsa.mobilepos.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.espsa.mobilepos.core.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProductSearchResultAdapter extends BaseAdapter {
    private final Context context;
    private final List<Product> products;

    public ProductSearchResultAdapter(Context context, List<Product> products) {
        this.context = context;
        this.products = products == null
                ? Collections.<Product>emptyList()
                : Collections.unmodifiableList(new ArrayList<Product>(products));
    }

    @Override
    public int getCount() {
        return products.size();
    }

    @Override
    public Product getItem(int position) {
        return products.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null ? createRow() : convertView;
        bindRow(row, getItem(position));
        return row;
    }

    private View createRow() {
        LinearLayout row = Views.vertical(context);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView primary = Views.text(context, "", 17, StyleGuide.INK);
        primary.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        primary.setSingleLine(false);
        row.addView(primary, Views.matchWrap());

        TextView secondary = Views.text(context, "", 13, StyleGuide.MUTED);
        secondary.setSingleLine(false);
        row.addView(secondary, Views.matchWrap());

        row.setTag(new RowHolder(primary, secondary));
        return row;
    }

    private void bindRow(View row, Product product) {
        RowHolder holder = (RowHolder) row.getTag();
        holder.primary.setText(product.name());
        holder.secondary.setText(secondaryLine(product));
    }

    private String secondaryLine(Product product) {
        return product.barcode()
                + "  " + MoneyText.currency(product.salePrice())
                + "  " + product.category()
                + unitSuffix(product);
    }

    private String unitSuffix(Product product) {
        return product.unitName().isEmpty() ? "" : "  " + product.unitName();
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class RowHolder {
        private final TextView primary;
        private final TextView secondary;

        private RowHolder(TextView primary, TextView secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }
    }
}
