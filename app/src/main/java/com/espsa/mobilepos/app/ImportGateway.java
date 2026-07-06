package com.espsa.mobilepos.app;

import android.net.Uri;

public interface ImportGateway {
    void requestImportFile();

    void onImportFileSelected(Uri uri);
}

