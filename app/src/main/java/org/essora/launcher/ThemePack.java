package org.essora.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Local, non-executable Essora themes. No external storage permission or network access. */
final class ThemePack {
    static final String LICENSE = "GPL-3.0-only";
    static final int FORMAT = 1;
    private static final int MAX_ENTRIES = 220;
    private static final long MAX_ARCHIVE = 16L * 1024 * 1024;
    private static final long MAX_ENTRY = 4L * 1024 * 1024;
    private static final int MAX_EDGE = 8192;

    static final class Theme {
        String id = UUID.randomUUID().toString();
        String name = "", author = "", version = "1.0", description = "";
        String originalAuthor = "", modifiedBy = "", website = "";
        String wallpaperCredit = "", iconsCredit = "";
        // External icon-pack reference; images are never embedded in .essoratheme.
        String iconPackPackage = "";
        int border = 0xffc58b53;
        int background = 0xcc15100c;
        int dock = 0xd518110d;
        int text = 0xfffff5e6;
        int battery = 0xffc58b53;
        int batteryOutline = 0xffffe3bd;
        int accent = 0xffc58b53;
        File directory;
        final LinkedHashMap<String, String> icons = new LinkedHashMap<>();

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("format", "essoratheme");
            json.put("formatVersion", FORMAT);
            json.put("license", LICENSE);
            json.put("name", name.trim());
            json.put("author", author.trim());
            json.put("version", version.trim());
            json.put("description", description.trim());
            json.put("originalAuthor", originalAuthor.trim());
            json.put("modifiedBy", modifiedBy.trim());
            json.put("website", website.trim());
            json.put("wallpaperCredit", wallpaperCredit.trim());
            json.put("iconsCredit", iconsCredit.trim());
            json.put("iconPackPackage", iconPackPackage.trim());
            JSONObject colors = new JSONObject();
            colors.put("border", border);
            colors.put("background", background);
            colors.put("dock", dock);
            colors.put("text", text);
            colors.put("battery", battery);
            colors.put("batteryOutline", batteryOutline);
            colors.put("accent", accent);
            json.put("colors", colors);
            JSONObject mapped = new JSONObject();
            for (Map.Entry<String, String> item : icons.entrySet()) mapped.put(item.getKey(), item.getValue());
            json.put("icons", mapped);
            return json;
        }

        static Theme fromJson(JSONObject json) throws IOException {
            if (!"essoratheme".equals(json.optString("format")) || json.optInt("formatVersion") != FORMAT)
                throw new IOException("Formato de tema no compatible");
            if (!LICENSE.equals(json.optString("license")))
                throw new IOException("Solo se aceptan temas GNU GPL-3.0");
            Theme theme = new Theme();
            theme.name = bounded(json.optString("name"), 75);
            theme.author = bounded(json.optString("author"), 75);
            theme.version = bounded(json.optString("version", "1.0"), 30);
            theme.description = bounded(json.optString("description"), 400);
            theme.originalAuthor = bounded(json.optString("originalAuthor"), 75);
            theme.modifiedBy = bounded(json.optString("modifiedBy"), 75);
            theme.website = bounded(json.optString("website"), 200);
            theme.wallpaperCredit = bounded(json.optString("wallpaperCredit"), 160);
            theme.iconsCredit = bounded(json.optString("iconsCredit"), 160);
            theme.iconPackPackage = bounded(json.optString("iconPackPackage"), 160);
            if (!theme.iconPackPackage.isEmpty() &&
                    !theme.iconPackPackage.matches("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+"))
                throw new IOException("Referencia de paquete de iconos inválida");
            if (theme.name.trim().isEmpty() || theme.author.trim().isEmpty())
                throw new IOException("Nombre y autor son obligatorios");
            JSONObject colors = json.optJSONObject("colors");
            if (colors == null) throw new IOException("Faltan los colores");
            theme.border = colors.optInt("border", theme.border);
            theme.background = colors.optInt("background", theme.background);
            theme.dock = colors.optInt("dock", theme.dock);
            theme.text = colors.optInt("text", theme.text);
            theme.battery = colors.optInt("battery", theme.battery);
            theme.batteryOutline = colors.optInt("batteryOutline", theme.batteryOutline);
            theme.accent = colors.optInt("accent", theme.accent);
            JSONObject icons = json.optJSONObject("icons");
            if (icons != null) {
                Iterator<String> keys = icons.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String entry = icons.optString(key);
                    if (key.length() > 240 || !validIconPath(entry)) throw new IOException("Referencia de icono inválida");
                    if (theme.icons.size() >= 180) throw new IOException("Demasiados iconos");
                    theme.icons.put(key, entry);
                }
            }
            return theme;
        }
    }

    static File root(Context context) {
        File root = new File(context.getFilesDir(), "essora_themes");
        if (!root.exists()) root.mkdirs();
        return root;
    }

    static File freshWork(Context context) throws IOException {
        File directory = new File(context.getCacheDir(), "essora_theme_work_" + UUID.randomUUID());
        if (!directory.mkdirs()) throw new IOException("No se puede crear el borrador");
        return directory;
    }

    static Theme startNew(Context context) throws IOException {
        Theme t = new Theme();
        t.directory = freshWork(context);
        return t;
    }

    static Theme edit(Context context, Theme existing) throws IOException {
        Theme draft = Theme.fromJson(readJson(new File(existing.directory, "theme.json")));
        // An already-published author is never silently replaced by an editor.
        if (draft.originalAuthor.isEmpty()) {
            draft.originalAuthor = draft.author;
            if (draft.modifiedBy.isEmpty()) draft.modifiedBy = draft.author;
        }
        draft.id = existing.id;
        draft.directory = freshWork(context);
        copyTree(existing.directory, draft.directory);
        return draft;
    }

    static Theme duplicate(Context context, Theme existing) throws IOException {
        Theme draft = edit(context, existing);
        draft.id = UUID.randomUUID().toString();
        draft.originalAuthor = existing.originalAuthor.isEmpty() ? existing.author : existing.originalAuthor;
        draft.modifiedBy = "";
        draft.name += " (copia)";
        return draft;
    }

    static void save(Context context, Theme t) throws Exception {
        if (t.directory == null || t.name.trim().isEmpty() || t.author.trim().isEmpty())
            throw new IOException("Nombre y autor son obligatorios");
        if (!safeId(t.id)) throw new IOException("Identificador inválido");
        File out = new File(root(context), t.id);
        File stage = new File(root(context), t.id + ".stage");
        deleteTree(stage);
        if (!stage.mkdirs()) throw new IOException("No se puede guardar el tema");
        copyTree(t.directory, stage);
        writeJson(new File(stage, "theme.json"), t.toJson());
        File backup = new File(root(context), t.id + ".backup");
        deleteTree(backup);
        if (out.exists() && !out.renameTo(backup)) throw new IOException("No se pudo preparar la copia de seguridad");
        if (!stage.renameTo(out)) {
            if (backup.exists()) backup.renameTo(out);
            throw new IOException("No se puede finalizar el guardado");
        }
        deleteTree(backup);
        File previousDir = t.directory;
        t.directory = out;
        if (previousDir != null && !previousDir.equals(out)
                && previousDir.getParentFile() != null
                && previousDir.getParentFile().equals(context.getCacheDir())) {
            deleteTree(previousDir);
        }
    }

    static Theme load(Context context, String id) {
        if (!safeId(id)) return null;
        File directory = new File(root(context), id);
        try {
            Theme theme = Theme.fromJson(readJson(new File(directory, "theme.json")));
            theme.id = id;
            theme.directory = directory;
            return theme;
        } catch (Exception ignored) { return null; }
    }

    static List<Theme> list(Context context) {
        ArrayList<Theme> result = new ArrayList<>();
        File[] dirs = root(context).listFiles();
        if (dirs != null) for (File dir : dirs) if (dir.isDirectory()) {
            Theme theme = load(context, dir.getName());
            if (theme != null) result.add(theme);
        }
        Collections.sort(result, Comparator.comparing(t -> t.name.toLowerCase(java.util.Locale.ROOT)));
        return result;
    }

    static void delete(Context context, Theme theme) {
        if (safeId(theme.id)) deleteTree(new File(root(context), theme.id));
    }

    static File wallpaper(Theme theme) { return new File(theme.directory, "wallpaper.webp"); }
    static File preview(Theme theme) { return new File(theme.directory, "preview.webp"); }

    static File iconFile(Theme theme, String component) {
        if (theme == null || theme.directory == null) return null;
        String name = theme.icons.get(component);
        if (name == null && component.contains("/"))
            name = theme.icons.get(component.substring(0, component.indexOf('/')));
        return validIconPath(name) ? new File(theme.directory, name) : null;
    }

    static boolean hasWallpaper(Theme t) {
        return t != null && t.directory != null && wallpaper(t).isFile();
    }

    static void clearWallpaper(Theme t) {
        if (t == null || t.directory == null) return;
        wallpaper(t).delete();
        preview(t).delete();
    }

    static void setWallpaper(Context context, Theme t, Uri uri) throws Exception {
        importImage(context, uri, wallpaper(t), 1920);
        writePreviewFromWallpaper(t);
    }

    static void setWallpaperFromResource(Context context, Theme t, int drawableResId) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), drawableResId, bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1)
            throw new IOException("Recurso de fondo no válido");
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1920 * 1.5f && sample < 128) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap image = BitmapFactory.decodeResource(context.getResources(), drawableResId, options);
        if (image == null) throw new IOException("No se pudo cargar el fondo integrado");
        Bitmap scaled = image;
        if (Math.max(image.getWidth(), image.getHeight()) > 1920) {
            float factor = 1920f / (float) Math.max(image.getWidth(), image.getHeight());
            scaled = Bitmap.createScaledBitmap(image,
                    Math.max(1, Math.round(image.getWidth() * factor)),
                    Math.max(1, Math.round(image.getHeight() * factor)), true);
        }
        try (OutputStream out = new FileOutputStream(wallpaper(t))) {
            if (!scaled.compress(Bitmap.CompressFormat.WEBP, 80, out))
                throw new IOException("No se pudo guardar el fondo del tema");
        } finally {
            if (scaled != image) scaled.recycle();
            image.recycle();
        }
        writePreviewFromWallpaper(t);
    }

    private static void writePreviewFromWallpaper(Theme t) throws IOException {
        Bitmap bmp = decode(previewSource(t), 380, 230);
        if (bmp != null) {
            try (FileOutputStream stream = new FileOutputStream(preview(t))) {
                bmp.compress(Bitmap.CompressFormat.WEBP, 76, stream);
            } finally {
                bmp.recycle();
            }
        }
    }

    private static File previewSource(Theme t) { return wallpaper(t); }

    static void setIcon(Context context, Theme t, String component, Uri uri) throws Exception {
        String filename = "icons/" + sha256(component).substring(0, 24) + ".webp";
        File file = new File(t.directory, filename);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("No se puede crear la carpeta de iconos");
        importImage(context, uri, file, 128);
        t.icons.put(component, filename);
    }

    static void removeIcon(Theme t, String component) {
        String old = t.icons.remove(component);
        if (validIconPath(old)) new File(t.directory, old).delete();
    }

    private static void importImage(Context context, Uri uri, File output, int edge) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > MAX_EDGE || bounds.outHeight > MAX_EDGE)
            throw new IOException("Imagen no válida o demasiado grande");
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > edge * 1.5f && sample < 128) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap image;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            image = BitmapFactory.decodeStream(in, null, options);
        }
        if (image == null) throw new IOException("No se pudo leer la imagen");
        Bitmap scaled = image;
        if (Math.max(image.getWidth(), image.getHeight()) > edge) {
            float factor = edge / (float) Math.max(image.getWidth(), image.getHeight());
            scaled = Bitmap.createScaledBitmap(image, Math.max(1, Math.round(image.getWidth() * factor)),
                    Math.max(1, Math.round(image.getHeight() * factor)), true);
        }
        try (OutputStream out = new FileOutputStream(output)) {
            if (!scaled.compress(Bitmap.CompressFormat.WEBP, edge < 200 ? 85 : 78, out))
                throw new IOException("No se pudo convertir la imagen");
        } finally {
            if (scaled != image) scaled.recycle();
            image.recycle();
        }
    }

    static Bitmap decode(File file, int targetWidth, int targetHeight) {
        if (file == null || !file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > MAX_EDGE || bounds.outHeight > MAX_EDGE)
            return null;
        int sample = 1;
        while ((bounds.outWidth / sample > Math.max(targetWidth, 1) * 1.6f ||
                bounds.outHeight / sample > Math.max(targetHeight, 1) * 1.6f) && sample < 128) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try { return BitmapFactory.decodeFile(file.getAbsolutePath(), options); }
        catch (OutOfMemoryError ignored) { return null; }
    }

    /** Export a GPLv3 theme ZIP. Assets and icon mappings are data only, never executable. */
    static void exportTheme(Context context, Theme theme, Uri target) throws Exception {
        try (OutputStream stream = context.getContentResolver().openOutputStream(target)) {
            if (stream == null) throw new IOException("No se puede crear el archivo");
            writeThemeArchive(context, theme, stream);
        }
    }

    /** Generate one self-contained archive in internal cache, usable through a read-only content URI. */
    static File exportForSharing(Context context, Theme theme) throws Exception {
        File folder = new File(context.getCacheDir(), ThemeShareProvider.DIR);
        if (!folder.isDirectory() && !folder.mkdirs())
            throw new IOException("No se puede preparar el archivo para compartir");
        // Clean only old share files, never delete one that may still be open by another app.
        File[] previous = folder.listFiles();
        if (previous != null) for (File f : previous) {
            if (f.isFile() && f.getName().endsWith(".essoratheme")
                    && System.currentTimeMillis() - f.lastModified() > 7L * 24 * 60 * 60 * 1000)
                f.delete();
        }
        String clean = theme.name.replaceAll("[^a-zA-Z0-9_-]+", "-");
        if (clean.length() > 55) clean = clean.substring(0, 55);
        if (clean.isEmpty()) clean = "Essora-Theme";
        File output = new File(folder, clean + "-" + UUID.randomUUID() + ".essoratheme");
        try (OutputStream stream = new FileOutputStream(output)) {
            writeThemeArchive(context, theme, stream);
        } catch (Exception error) {
            output.delete();
            throw error;
        }
        return output;
    }

    private static void writeThemeArchive(Context context, Theme theme, OutputStream stream) throws Exception {
        if (theme.name.trim().isEmpty() || theme.author.trim().isEmpty()) throw new IOException("Nombre o autor vacío");
        try (ZipOutputStream zip = new ZipOutputStream(stream)) {
            put(zip, "theme.json", theme.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            try (InputStream license = context.getAssets().open("essora/GPL-3.0.txt")) {
                putStream(zip, "LICENSE", license, 64 * 1024);
            }
            put(zip, "CREDITS.txt", credits(theme).getBytes(StandardCharsets.UTF_8));
            addAsset(zip, wallpaper(theme), "wallpaper.webp");
            addAsset(zip, preview(theme), "preview.webp");
            HashSet<String> included = new HashSet<>();
            for (String relative : theme.icons.values()) {
                if (validIconPath(relative) && included.add(relative))
                    addAsset(zip, new File(theme.directory, relative), relative);
            }
        }
    }

    static String credits(Theme t) {
        return "Essora Theme - GNU GPL-3.0\n" + "Tema: " + t.name + "\n" +
                "Autor: " + t.author + "\n" +
                "Autor original: " + (t.originalAuthor.isEmpty() ? t.author : t.originalAuthor) + "\n" +
                "Modificado por: " + t.modifiedBy + "\n" +
                "Wallpaper: " + t.wallpaperCredit + "\n" +
                "Iconos: " + t.iconsCredit + "\n" +
                "Paquete externo de iconos: " + t.iconPackPackage + "\n" +
                "La APK del pack debe instalarse por separado y conserva su propia licencia.\n" +
                "Licencia: " + LICENSE + "\n" +
                "Los recursos de terceros deben tener permisos de redistribución compatibles.\n";
    }

    /** Only accept whitelisted paths, limited archive size and GPL-3.0-only metadata. */
    static Theme importTheme(Context context, Uri inputUri) throws Exception {
        File work = freshWork(context);
        try {
            unpack(context, inputUri, work, false);
            Theme theme = Theme.fromJson(readJson(new File(work, "theme.json")));
            validateLicense(new File(work, "LICENSE"));
            theme.id = UUID.randomUUID().toString(); // never overwrite an installed theme
            if (theme.originalAuthor.isEmpty()) theme.originalAuthor = theme.author;
            theme.directory = work;
            ensureIconFiles(theme);
            return theme; // caller confirms before saving
        } catch (Exception error) {
            deleteTree(work);
            throw error;
        }
    }

    static String importIconPack(Context context, Theme target, Uri inputUri) throws Exception {
        File work = freshWork(context);
        try {
            unpack(context, inputUri, work, true);
            JSONObject json = readJson(new File(work, "icons.json"));
            if (!"essoraicons".equals(json.optString("format")) || json.optInt("formatVersion") != FORMAT ||
                    !LICENSE.equals(json.optString("license")))
                throw new IOException("Pack de iconos incompatible o sin GPL-3.0");
            String author = bounded(json.optString("author"), 75);
            if (author.trim().isEmpty()) throw new IOException("El pack necesita autor");
            validateLicense(new File(work, "LICENSE"));
            JSONObject mapping = json.optJSONObject("icons");
            if (mapping == null || mapping.length() == 0) throw new IOException("El pack no tiene iconos");
            int applied = 0;
            Iterator<String> keys = mapping.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String relative = mapping.optString(key);
                if (key.length() > 240 || !validIconPath(relative)) throw new IOException("Ruta de icono inválida");
                File source = new File(work, relative);
                if (!source.isFile()) throw new IOException("Falta un icono del pack");
                String destName = "icons/" + sha256(key).substring(0, 24) + ".webp";
                File dest = new File(target.directory, destName);
                File folder = dest.getParentFile();
                if (!folder.exists() && !folder.mkdirs()) throw new IOException("Error creando carpeta de iconos");
                Bitmap bmp = decode(source, 128, 128);
                if (bmp == null) throw new IOException("Icono inválido");
                try (FileOutputStream out = new FileOutputStream(dest)) {
                    bmp.compress(Bitmap.CompressFormat.WEBP, 84, out);
                } finally { bmp.recycle(); }
                if (!target.icons.containsKey(key) && target.icons.size() >= 180)
                    throw new IOException("El tema admite como máximo 180 iconos");
                target.icons.put(key, destName);
                applied++;
            }
            String credit = target.iconsCredit.trim().isEmpty() ? author : target.iconsCredit + ", " + author;
            target.iconsCredit = credit.length() > 160 ? credit.substring(0, 160) : credit;
            return applied + " iconos importados";
        } finally { deleteTree(work); }
    }

    private static void validateLicense(File file) throws IOException {
        if (!file.isFile() || file.length() > 64 * 1024 || file.length() < 100)
            throw new IOException("Falta LICENSE GNU GPL-3.0");
        byte[] sample = new byte[(int) Math.min(file.length(), 8192)];
        try (InputStream input = new FileInputStream(file)) {
            int count = input.read(sample);
            String header = new String(sample, 0, Math.max(0, count), StandardCharsets.UTF_8);
            if (!header.contains("GNU GENERAL PUBLIC LICENSE") || !header.contains("Version 3"))
                throw new IOException("LICENSE no contiene GNU GPL versión 3");
        }
    }

    private static void ensureIconFiles(Theme t) throws IOException {
        for (String relative : t.icons.values()) {
            File image = new File(t.directory, relative);
            Bitmap bmp = decode(image, 128, 128);
            if (!image.isFile() || bmp == null)
                throw new IOException("Falta un icono o es inválido");
            bmp.recycle();
        }
        File wallpaper = wallpaper(t);
        if (wallpaper.isFile()) {
            Bitmap bmp = decode(wallpaper, 500, 800);
            if (bmp == null) throw new IOException("Wallpaper inválido");
            bmp.recycle();
        }
    }

    private static void unpack(Context context, Uri uri, File target, boolean iconPack) throws Exception {
        int entries = 0;
        long total = 0;
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = input == null ? null : new ZipInputStream(input)) {
            if (zip == null) throw new IOException("No se pudo abrir el ZIP");
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("Demasiados archivos");
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!"icons/".equals(name)) throw new IOException("Carpeta no permitida");
                    continue;
                }
                boolean baseFile = (iconPack ? "icons.json" : "theme.json").equals(name) ||
                        "LICENSE".equals(name) || "CREDITS.txt".equals(name);
                boolean extraFile = !iconPack && ("wallpaper.webp".equals(name) || "preview.webp".equals(name));
                if (!baseFile && !extraFile && !validIconPath(name)) throw new IOException("Archivo no permitido: " + name);
                if (entry.getSize() > MAX_ENTRY) throw new IOException("Archivo demasiado grande");
                File file = new File(target, name);
                if (file.exists()) throw new IOException("Archivo duplicado");
                File parent = file.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) throw new IOException("Error creando carpeta");
                long count = 0;
                try (FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        count += n;
                        total += n;
                        if (count > MAX_ENTRY || total > MAX_ARCHIVE) throw new IOException("Paquete demasiado grande");
                        out.write(buffer, 0, n);
                    }
                }
                if (("theme.json".equals(name) || "icons.json".equals(name)) && count > 128 * 1024)
                    throw new IOException("Archivo JSON demasiado grande");
                zip.closeEntry();
            }
        }
    }

    private static boolean validIconPath(String relative) {
        return relative != null && relative.matches("icons/[a-zA-Z0-9_-]{1,80}\\.(png|webp)");
    }
    private static boolean safeId(String id) { return id != null && id.matches("[a-fA-F0-9-]{36}"); }
    private static String bounded(String s, int limit) throws IOException {
        if (s == null) return "";
        if (s.length() > limit) throw new IOException("Campo demasiado largo");
        return s;
    }
    private static JSONObject readJson(File file) throws IOException {
        if (!file.isFile() || file.length() > 128 * 1024) throw new IOException("JSON inexistente o demasiado grande");
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (JSONException error) { throw new IOException("JSON incorrecto", error); }
    }
    private static void writeJson(File file, JSONObject json) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (JSONException error) { throw new IOException("JSON incorrecto", error); }
    }
    private static void addAsset(ZipOutputStream zip, File file, String name) throws IOException {
        if (!file.isFile()) return;
        try (FileInputStream input = new FileInputStream(file)) { putStream(zip, name, input, MAX_ENTRY); }
    }
    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
    private static void putStream(ZipOutputStream zip, String name, InputStream input, long max) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        byte[] buffer = new byte[8192];
        int n;
        long total = 0;
        while ((n = input.read(buffer)) != -1) {
            total += n;
            if (total > max) throw new IOException("Archivo demasiado grande");
            zip.write(buffer, 0, n);
        }
        zip.closeEntry();
    }
    private static void copyTree(File source, File target) throws IOException {
        if (!source.exists()) return;
        File[] files = source.listFiles();
        if (files == null) return;
        for (File from : files) {
            File to = new File(target, from.getName());
            if (from.isDirectory()) {
                if (!to.exists() && !to.mkdirs()) throw new IOException("No se puede crear carpeta");
                copyTree(from, to);
            } else {
                try (FileInputStream input = new FileInputStream(from);
                     FileOutputStream output = new FileOutputStream(to)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
            }
        }
    }
    static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File child : files) deleteTree(child);
        }
        file.delete();
    }
    private static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
