package com.espsa.mobilepos.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

public final class AndroidFileNameResolver {
    public String displayName(Context context, Uri uri) {
        if (context == null || uri == null) {
            return "";
        }
        String displayName = queryDisplayName(context, uri);
        if (!displayName.isEmpty()) {
            return displayName;
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? uri.toString() : fallback;
    }

    private String queryDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) {
                return "";
            }
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex < 0 || cursor.isNull(nameIndex)) {
                return "";
            }
            String value = cursor.getString(nameIndex);
            return value == null ? "" : value.trim();
        } catch (Exception ex) {
            return "";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
