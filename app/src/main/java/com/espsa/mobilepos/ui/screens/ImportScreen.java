package com.espsa.mobilepos.ui.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.espsa.mobilepos.app.AppServices;
import com.espsa.mobilepos.app.ImportGateway;
import com.espsa.mobilepos.app.ProductStoreException;
import com.espsa.mobilepos.core.importer.ImportFormat;
import com.espsa.mobilepos.core.library.ImportSnapshotInfo;
import com.espsa.mobilepos.core.library.ProductLibraryMetadata;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.UiText;
import com.espsa.mobilepos.ui.Views;

import java.util.List;

public final class ImportScreen {
    private final Context context;
    private final AppServices services;
    private final AppLanguage language;
    private final ImportGateway importGateway;
    private final Runnable refresh;

    public ImportScreen(
            Context context,
            AppServices services,
            AppLanguage language,
            ImportGateway importGateway,
            Runnable refresh
    ) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.importGateway = importGateway;
        this.refresh = refresh;
    }

    public View render() {
        ScrollView scroll = new ScrollView(context);
        LinearLayout page = Views.vertical(context);
        page.setPadding(16, 8, 16, 16);

        TextView title = Views.text(context, UiText.choose(language, "导入商品库 / Import", "Importar productos"), 24, StyleGuide.INK);
        StyleGuide.pageTitle(title);
        page.addView(title, Views.matchWrap());

        page.addView(currentLibraryPanel(), Views.cardParams(context));
        page.addView(formatCard(
                ImportFormat.MINGSHENG_DB,
                UiText.choose(language, "鸣盛数据库", "Base Ming Sheng"),
                UiText.choose(language, "适合从鸣盛收银软件导出的商品库。", "Para bases de productos exportadas desde Ming Sheng."),
                UiText.choose(language, "选择 .db 文件", "Elegir archivo .db")
        ), Views.cardParams(context));
        page.addView(formatCard(
                ImportFormat.GENERIC_CSV,
                UiText.choose(language, "通用 CSV 商品表", "CSV generico de productos"),
                UiText.choose(language, "适合包含 barcode、name、price 字段的商品表。", "Para tablas con campos barcode, name y price."),
                UiText.choose(language, "选择 .csv 文件", "Elegir archivo .csv")
        ), Views.cardParams(context));
        page.addView(recentSnapshotsCard(), Views.cardParams(context));
        scroll.addView(page);
        return scroll;
    }

    private View currentLibraryPanel() {
        LinearLayout panel = Views.card(context);
        TextView heading = Views.text(context, UiText.choose(language, "当前商品库", "Productos actuales"), 18, StyleGuide.INK);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        panel.addView(heading, Views.matchWrap());
        try {
            ProductLibraryMetadata metadata = services.productLibrary().metadata();
            panel.addView(info(UiText.choose(language, "当前商品数", "Productos actuales"), Integer.toString(services.catalog().productCount())));
            panel.addView(info(UiText.choose(language, "最近导入时间", "Ultima importacion"), emptyText(metadata.lastImportTimeIso())));
            panel.addView(info(UiText.choose(language, "最近导入文件", "Archivo"), emptyText(metadata.lastImportFileName())));
            panel.addView(info(
                    UiText.choose(language, "本地手动修改", "Cambios locales"),
                    metadata.manuallyModified()
                            ? UiText.choose(language, "有", "Si")
                            : UiText.choose(language, "无", "No")
            ));
        } catch (ProductStoreException ex) {
            panel.addView(info(UiText.choose(language, "商品库状态", "Estado"), ex.getMessage()));
        }
        return panel;
    }

    private View formatCard(ImportFormat format, String title, String description, String actionLabel) {
        return Views.actionCard(
                context,
                title,
                description + "\n" + UiText.choose(language, "导入会替换当前商品库，并清除本地手动修改。", "La importacion reemplaza los productos actuales y borra cambios locales."),
                UiText.choose(language, "支持: ", "Soporta: ") + extensionsText(format),
                actionLabel,
                () -> confirmImport(format, title)
        );
    }

    private View recentSnapshotsCard() {
        LinearLayout card = Views.card(context);
        addSnapshotRows(card);
        return card;
    }

    private void addSnapshotRows(LinearLayout list) {
        TextView title = Views.text(context, UiText.choose(language, "最近 5 次导入", "Ultimas 5 importaciones"), 18, StyleGuide.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 14, 0, 8);
        list.addView(title, Views.matchWrap());
        try {
            List<ImportSnapshotInfo> snapshots = services.productLibrary().recentImports();
            if (snapshots.isEmpty()) {
                TextView empty = Views.text(context, UiText.choose(language, "还没有导入快照", "Sin snapshots"), 16, StyleGuide.MUTED);
                empty.setPadding(0, 24, 0, 24);
                list.addView(empty, Views.matchWrap());
                return;
            }
            for (ImportSnapshotInfo snapshot : snapshots) {
                list.addView(snapshotRow(snapshot), Views.matchWrap());
                list.addView(Views.divider(context));
            }
        } catch (ProductStoreException ex) {
            list.addView(info(UiText.choose(language, "读取快照失败", "Error al leer snapshots"), ex.getMessage()));
        }
    }

    private View snapshotRow(ImportSnapshotInfo snapshot) {
        LinearLayout row = Views.vertical(context);
        row.setPadding(0, 12, 0, 12);

        TextView file = Views.text(context, emptyText(snapshot.fileName()), 17, StyleGuide.INK);
        file.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(file, Views.matchWrap());

        TextView meta = Views.text(
                context,
                snapshot.importedAtIso()
                        + "\n" + UiText.choose(language, "商品", "Productos") + ": " + snapshot.productCount()
                        + "  " + UiText.choose(language, "促销", "Promo") + ": " + snapshot.promotionCount(),
                14,
                StyleGuide.MUTED
        );
        row.addView(meta, Views.matchWrap());

        Button restore = Views.button(context, UiText.choose(language, "回滚到这个版本", "Restaurar esta version"));
        restore.setOnClickListener(v -> confirmRestoreSnapshot(snapshot));
        row.addView(restore, Views.matchWrap());
        return row;
    }

    private View info(String label, String value) {
        LinearLayout row = Views.vertical(context);
        row.setPadding(0, 8, 0, 8);
        TextView labelView = Views.text(context, label, 13, StyleGuide.MUTED);
        row.addView(labelView, Views.matchWrap());
        TextView valueView = Views.text(context, value, 20, StyleGuide.INK);
        valueView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(valueView, Views.matchWrap());
        return row;
    }

    private void confirmImport(ImportFormat format, String title) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(UiText.choose(
                        language,
                        "继续导入会清空当前本地修改和自建商品。",
                        "Se reemplazaran los productos actuales y los cambios locales."
                ))
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "继续导入", "Importar"), (dialog, which) -> importGateway.requestImportFile(format))
                .show();
    }

    private void confirmRestoreSnapshot(ImportSnapshotInfo snapshot) {
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "回滚商品库？", "Restaurar productos?"))
                .setMessage(snapshot.fileName() + "\n\n" + UiText.choose(
                        language,
                        "回滚会替换当前商品库，并清空当前本地修改和自建商品。",
                        "La restauracion reemplaza los productos actuales y borra cambios locales."
                ))
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "确认回滚", "Restaurar"), (dialog, which) -> restoreSnapshot(snapshot.snapshotId()))
                .show();
    }

    private void restoreSnapshot(String snapshotId) {
        try {
            services.productLibrary().restoreSnapshot(snapshotId);
            Toast.makeText(context, UiText.choose(language, "商品库已回滚", "Productos restaurados"), Toast.LENGTH_LONG).show();
            refresh.run();
        } catch (ProductStoreException ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String emptyText(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private String extensionsText(ImportFormat format) {
        StringBuilder builder = new StringBuilder();
        String[] extensions = format.extensions();
        for (int i = 0; i < extensions.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(extensions[i]);
        }
        return builder.toString();
    }
}
