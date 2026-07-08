package com.espsa.mobilepos.app;

import android.net.Uri;

import com.espsa.mobilepos.core.importer.ImportFormat;

public interface ImportGateway {
    void requestImportFile(ImportFormat format);

    void onImportFileSelected(Uri uri);
}
