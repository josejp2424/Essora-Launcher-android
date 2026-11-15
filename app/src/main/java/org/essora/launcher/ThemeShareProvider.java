package org.essora.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only access to specifically exported .essoratheme archives in app cache. */
public final class ThemeShareProvider extends ContentProvider {
    static final String DIR = "shared_themes";
    static final String MIME = "application/zip";

    static Uri uriForFile(android.content.Context context, File file) throws IOException {
        File folder = new File(context.getCacheDir(), DIR).getCanonicalFile();
        if (!folder.isDirectory() || !file.getCanonicalFile().getParentFile().equals(folder)
                || !validName(file.getName()) || !file.isFile()) {
            throw new IOException("No se pudo preparar el archivo para compartir");
        }
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + ".themes")
                .appendPath(file.getName()).build();
    }

    private static boolean validName(String name) {
        return name != null && name.length() > 12 && name.length() <= 160
                && name.matches("[A-Za-z0-9_-]+\\.essoratheme");
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri == null || !"content".equals(uri.getScheme())
                || !(getContext().getPackageName() + ".themes").equals(uri.getAuthority())
                || uri.getPathSegments().size() != 1) throw new FileNotFoundException();
        String name = uri.getLastPathSegment();
        if (!validName(name)) throw new FileNotFoundException();
        try {
            File folder = new File(getContext().getCacheDir(), DIR).getCanonicalFile();
            File target = new File(folder, name).getCanonicalFile();
            if (!folder.equals(target.getParentFile()) || !target.isFile()) throw new FileNotFoundException();
            return target;
        } catch (IOException error) {
            throw new FileNotFoundException();
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return MIME; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                   String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null
                    ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor result = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
            }
            result.addRow(row);
            return result;
        } catch (FileNotFoundException ignored) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
