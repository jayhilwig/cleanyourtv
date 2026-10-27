package com.jayhilwig.aroundtown;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Read-only view of the same private weather cache and ZIP used by MainActivity. */
public final class WeatherProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"snapshot".equals(method)) return null;
        Bundle result = new Bundle();
        result.putString("zip", getContext().getSharedPreferences("MainActivity", 0).getString("zip", "98512"));
        result.putString("current", getContext().getSharedPreferences("MainActivity", 0).getString("current", ""));
        result.putString("forecast", getContext().getSharedPreferences("MainActivity", 0).getString("forecast", ""));
        return result;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
