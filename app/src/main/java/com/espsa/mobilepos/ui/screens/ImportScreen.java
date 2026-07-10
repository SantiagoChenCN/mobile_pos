package com.espsa.mobilepos.ui.screens;

import android.app.Activity;
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
import com.espsa.mobilepos.app.ScanGateway;
import com.espsa.mobilepos.app.sync.ComputerSyncConfig;
import com.espsa.mobilepos.app.sync.ComputerSyncManifest;
import com.espsa.mobilepos.core.importer.ImportFormat;
import com.espsa.mobilepos.core.importer.ProductImportException;
import com.espsa.mobilepos.core.importer.ProductImportResult;
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
    private final ScanGateway scanGateway;
    private final Runnable refresh;

    public ImportScreen(
            Context context,
            AppServices services,
            AppLanguage language,
            ImportGateway importGateway,
            ScanGateway scanGateway,
            Runnable refresh
    ) {
        this.context = context;
        this.services = services;
        this.language = language;
        this.importGateway = importGateway;
        this.scanGateway = scanGateway;
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
        page.addView(computerSyncCard(), Views.cardParams(context));
        page.addView(recentSnapshotsCard(), Views.cardParams(context));
        scroll.addView(page);
        return scroll;
    }

    public void addScannedBarcode(String value) {
        try {
            ComputerSyncConfig config = services.computerSync().configureFromSetupUri(context, value);
            Toast.makeText(context, UiText.choose(language, "电脑同步配置已保存", "Configuracion guardada"), Toast.LENGTH_SHORT).show();
            runBackground(
                    null,
                    "",
                    () -> services.computerSync().testConnection(context),
                    ok -> {
                        showMessage(
                                UiText.choose(language, "连接成功", "Conexion correcta"),
                                config.baseUrl()
                        );
                        refresh.run();
                    }
            );
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private View currentLibraryPanel() {
        LinearLayout panel = Views.card(context);
        addCardTitle(panel, UiText.choose(language, "当前商品库", "Productos actuales"));
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

    private View computerSyncCard() {
        LinearLayout card = Views.card(context);
        addCardTitle(card, UiText.choose(language, "电脑同步", "Sincronizacion con PC"));

        TextView description = Views.text(
                context,
                UiText.choose(
                        language,
                        "从收银电脑同步工具获取最新鸣盛数据库副本。同步前会确认，不会自动覆盖商品库。",
                        "Obtiene la ultima copia Ming Sheng desde la herramienta de PC. Siempre pide confirmacion antes de importar."
                ),
                14,
                StyleGuide.MUTED
        );
        description.setSingleLine(false);
        description.setPadding(0, Views.dp(context, 6), 0, Views.dp(context, 8));
        card.addView(description, Views.matchWrap());

        ComputerSyncConfig config = services.computerSync().config(context);
        card.addView(info(UiText.choose(language, "状态", "Estado"), config.configured()
                ? UiText.choose(language, "已配置", "Configurado")
                : UiText.choose(language, "未配置", "Sin configurar")));
        card.addView(info(UiText.choose(language, "地址", "Direccion"), config.configured() ? config.baseUrl() : "-"));
        card.addView(info(UiText.choose(language, "上次检查", "Ultima revision"), emptyText(config.lastCheckedAt())));
        card.addView(info(UiText.choose(language, "上次同步", "Ultima sync"), emptyText(config.lastSyncedAt())));

        Button scan = Views.button(context, UiText.choose(language, "扫码连接电脑工具", "Escanear herramienta de PC"));
        scan.setOnClickListener(v -> scanGateway.requestBarcodeScan());
        card.addView(scan, Views.matchWrap());

        Button test = Views.button(context, UiText.choose(language, "测试连接", "Probar conexion"));
        test.setOnClickListener(v -> runBackground(
                test,
                UiText.choose(language, "测试中...", "Probando..."),
                () -> services.computerSync().testConnection(context),
                ok -> showMessage(UiText.choose(language, "连接成功", "Conexion correcta"), services.computerSync().config(context).baseUrl())
        ));
        card.addView(test, Views.matchWrap());

        Button check = Views.button(context, UiText.choose(language, "检查新版本", "Buscar nueva version"));
        check.setOnClickListener(v -> runBackground(
                check,
                UiText.choose(language, "检查中...", "Revisando..."),
                () -> services.computerSync().checkManifest(context),
                manifest -> handleManifest(manifest, false)
        ));
        card.addView(check, Views.matchWrap());

        Button sync = Views.button(context, UiText.choose(language, "立即同步", "Sincronizar ahora"));
        sync.setOnClickListener(v -> runBackground(
                sync,
                UiText.choose(language, "检查中...", "Revisando..."),
                () -> services.computerSync().checkManifest(context),
                manifest -> handleManifest(manifest, true)
        ));
        card.addView(sync, Views.matchWrap());
        return card;
    }

    private View recentSnapshotsCard() {
        LinearLayout card = Views.card(context);
        addSnapshotRows(card);
        return card;
    }

    private void handleManifest(ComputerSyncManifest manifest, boolean syncWhenNew) {
        if (manifest == null || !manifest.ok()) {
            showError(manifest == null ? UiText.choose(language, "电脑端未返回 manifest", "La PC no devolvio manifest") : manifest.error());
            return;
        }
        if (!services.computerSync().hasNewVersion(context, manifest)) {
            showManifestMessage(UiText.choose(language, "已是最新版本", "Ya esta actualizado"), manifest);
            return;
        }
        if (syncWhenNew) {
            confirmSync(manifest);
        } else {
            showManifestMessage(UiText.choose(language, "发现新版本", "Nueva version disponible"), manifest);
        }
    }

    private void confirmSync(ComputerSyncManifest manifest) {
        String message = manifestSummary(manifest)
                + "\n\n"
                + UiText.choose(language, "导入后会替换当前手机商品库。", "La importacion reemplazara los productos actuales.");
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "导入电脑商品库？", "Importar productos desde PC?"))
                .setMessage(message)
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "导入", "Importar"), (dialog, which) -> {
                    if (hasManualChanges()) {
                        confirmSyncWithLocalChanges(manifest);
                    } else {
                        syncNow(manifest);
                    }
                })
                .show();
    }

    private void confirmSyncWithLocalChanges(ComputerSyncManifest manifest) {
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "确认覆盖本地修改", "Confirmar reemplazo local"))
                .setMessage(UiText.choose(
                        language,
                        "手机上有本地手动修改或自建商品。继续导入会覆盖这些修改。",
                        "Hay cambios locales o productos creados en el telefono. La importacion los reemplazara."
                ))
                .setNegativeButton(UiText.choose(language, "取消", "Cancelar"), null)
                .setPositiveButton(UiText.choose(language, "继续覆盖并导入", "Reemplazar e importar"), (dialog, which) -> syncNow(manifest))
                .show();
    }

    private void syncNow(ComputerSyncManifest manifest) {
        runBackground(
                null,
                "",
                () -> services.syncProductsFromComputer(context, manifest),
                result -> {
                    showImportSuccess(result);
                    refresh.run();
                }
        );
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
        return Views.infoBlock(context, label, value);
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

    private boolean hasManualChanges() {
        try {
            return services.productLibrary().metadata().manuallyModified();
        } catch (ProductStoreException ex) {
            return false;
        }
    }

    private void showManifestMessage(String title, ComputerSyncManifest manifest) {
        showMessage(title, manifestSummary(manifest));
    }

    private String manifestSummary(ComputerSyncManifest manifest) {
        return UiText.choose(language, "文件", "Archivo") + ": " + emptyText(manifest.fileName())
                + "\n" + UiText.choose(language, "时间", "Fecha") + ": " + emptyText(manifest.createdAt())
                + "\n" + UiText.choose(language, "大小", "Tamano") + ": " + formatBytes(manifest.sizeBytes())
                + "\nSHA-256: " + shortHash(manifest.sha256());
    }

    private void showImportSuccess(ProductImportResult result) {
        String message = UiText.choose(language, "商品", "Productos") + ": " + result.productCount()
                + "\n" + UiText.choose(language, "促销", "Promociones") + ": " + result.promotionCount()
                + "\n" + UiText.choose(language, "警告", "Advertencias") + ": " + result.warnings().size()
                + "\n" + UiText.choose(language, "文件", "Archivo") + ": " + emptyText(result.sourceFileName());
        showMessage(UiText.choose(language, "同步完成", "Sincronizacion completa"), message);
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(emptyText(message))
                .setPositiveButton("OK", null)
                .show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(context)
                .setTitle(UiText.choose(language, "电脑同步失败", "Error de sincronizacion"))
                .setMessage(emptyText(message))
                .setPositiveButton("OK", null)
                .show();
    }

    private <T> void runBackground(Button button, String loadingLabel, SyncWork<T> work, SyncSuccess<T> success) {
        String originalLabel = button == null ? "" : button.getText().toString();
        if (button != null) {
            button.setEnabled(false);
            if (loadingLabel != null && !loadingLabel.trim().isEmpty()) {
                button.setText(loadingLabel);
            }
        }
        new Thread(() -> {
            try {
                T result = work.run();
                runOnUiThread(() -> {
                    restoreButton(button, originalLabel);
                    success.onSuccess(result);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    restoreButton(button, originalLabel);
                    showError(ex.getMessage());
                });
            }
        }).start();
    }

    private void restoreButton(Button button, String label) {
        if (button != null) {
            button.setEnabled(true);
            button.setText(label);
        }
    }

    private void runOnUiThread(Runnable action) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(action);
        } else {
            action.run();
        }
    }

    private void addCardTitle(LinearLayout card, String title) {
        TextView titleView = Views.text(context, title, 18, StyleGuide.INK);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(titleView, Views.matchWrap());
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

    private String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "-";
        }
        if (bytes >= 1024L * 1024L) {
            return (bytes / (1024L * 1024L)) + " MB";
        }
        if (bytes >= 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return bytes + " B";
    }

    private String shortHash(String hash) {
        if (hash == null || hash.trim().isEmpty()) {
            return "-";
        }
        String clean = hash.trim();
        return clean.length() <= 12 ? clean : clean.substring(0, 12) + "...";
    }

    private interface SyncWork<T> {
        T run() throws Exception;
    }

    private interface SyncSuccess<T> {
        void onSuccess(T result);
    }
}
