package com.espsa.mobilepos;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.espsa.mobilepos.ui.StyleGuide;
import com.espsa.mobilepos.ui.Views;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Arrays;
import java.util.List;

public final class ScannerActivity extends Activity {
    public static final String EXTRA_BARCODE = "barcode";
    private static final int CAMERA_PERMISSION_REQUEST = 301;

    private DecoratedBarcodeView barcodeView;
    private boolean handled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        setContentView(scannerLayout());
        if (hasCameraPermission()) {
            startScanner();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(StyleGuide.INK);
        window.setNavigationBarColor(StyleGuide.INK);
        window.getDecorView().setSystemUiVisibility(0);
    }

    private View scannerLayout() {
        LinearLayout root = Views.vertical(this);
        root.setBackgroundColor(StyleGuide.INK);
        root.setPadding(16, systemBarHeight("status_bar") + 10, 16, systemBarHeight("navigation_bar") + 12);

        TextView title = Views.text(this, "对准商品条码 / Apunte al codigo", 20, StyleGuide.SURFACE);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, Views.matchWrap());

        barcodeView = new DecoratedBarcodeView(this);
        barcodeView.setStatusText("");
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(supportedFormats()));
        root.addView(barcodeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        Button cancel = Views.button(this, "取消 / Cancelar");
        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        root.addView(cancel, Views.matchWrap());
        return root;
    }

    private List<BarcodeFormat> supportedFormats() {
        return Arrays.asList(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.ITF,
                BarcodeFormat.QR_CODE
        );
    }

    private void startScanner() {
        try {
            barcodeView.decodeContinuous(new BarcodeCallback() {
                @Override
                public void barcodeResult(BarcodeResult result) {
                    if (handled || result == null || result.getText() == null || result.getText().trim().isEmpty()) {
                        return;
                    }
                    handled = true;
                    Intent data = new Intent();
                    data.putExtra(EXTRA_BARCODE, result.getText().trim());
                    setResult(RESULT_OK, data);
                    finish();
                }
            });
            barcodeView.resume();
        } catch (RuntimeException ex) {
            Toast.makeText(this, "无法打开相机 / No se puede abrir la camara", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeView != null && hasCameraPermission()) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (barcodeView != null) {
            barcodeView.pause();
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner();
            } else {
                Toast.makeText(this, "需要相机权限 / Permiso de camara requerido", Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private int systemBarHeight(String resourceName) {
        int resourceId = getResources().getIdentifier(resourceName, "dimen", "android");
        if (resourceId == 0) {
            return 0;
        }
        return getResources().getDimensionPixelSize(resourceId);
    }
}
