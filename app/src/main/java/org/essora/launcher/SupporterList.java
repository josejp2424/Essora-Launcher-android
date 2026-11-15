package org.essora.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Read-only public supporters list, maintained by the Essora developer on GitHub.
 * No PayPal API credentials, device identifiers or transactions are processed here.
 * Network requests occur only when the user opens/reloads the supporters screen.
 */
final class SupporterList {
    private static final String ENDPOINT =
            "https://raw.githubusercontent.com/josejp2424/Essora-Launcher-android/main/supporters.json";
    private static final String CACHE = "public-supporters-cache.json";
    private static final int MAX_BYTES = 128 * 1024;
    private static final int MAX_SUPPORTERS = 200;

    interface ResultCallback { void completed(boolean updated); }

    static final class Supporter {
        final String name;
        final double amount;
        final String currency;
        final String note;
        final double amountUsd;

        Supporter(String name, double amount, String currency, String note, double amountUsd) {
            this.name = name;
            this.amount = amount;
            this.currency = currency;
            this.note = note;
            this.amountUsd = amountUsd;
        }

        String formattedAmount() {
            if (Math.abs(amount - Math.rint(amount)) < 0.005) {
                return String.format(Locale.US, "%s %.0f", currency, amount);
            }
            return String.format(Locale.US, "%s %.2f", currency, amount);
        }
    }

    static List<Supporter> loadSorted(Context context) {
        byte[] cached = readCache(context);
        if (cached != null) {
            try { return parse(cached); }
            catch (Exception ignored) { /* use bundled offline list */ }
        }
        try (InputStream in = context.getAssets().open("essora/supporters.json")) {
            return parse(readBounded(in));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    /** Downloads only the published JSON; commits are never made from the device. */
    static void refreshAsync(Context context, ResultCallback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean success = refresh(app);
            new Handler(Looper.getMainLooper()).post(() -> callback.completed(success));
        }, "EssoraSupportersFetch").start();
    }

    private static boolean refresh(Context context) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(ENDPOINT);
            // Pin this read-only request to the expected HTTPS GitHub content host.
            if (!"https".equals(url.getProtocol()) ||
                    !"raw.githubusercontent.com".equals(url.getHost())) return false;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return false;
            if (connection.getContentLength() > MAX_BYTES) return false;
            byte[] bytes;
            try (InputStream in = connection.getInputStream()) {
                bytes = readBounded(in);
            }
            parse(bytes);  // Reject malformed/oversized JSON before touching last good cache.
            writeCache(context, bytes);
            return true;
        } catch (Exception ignored) {
            return false; // Offline/error: last successfully fetched version remains usable.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static List<Supporter> parse(byte[] bytes) throws Exception {
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        JSONArray arr = root.getJSONArray("supporters");
        if (arr.length() > MAX_SUPPORTERS) throw new IllegalArgumentException("Too many supporters");
        ArrayList<Supporter> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null || !item.optBoolean("public_consent", false)) continue;
            String name = item.optString("name", "").trim();
            String currency = item.optString("currency", "USD").trim().toUpperCase(Locale.US);
            String note = item.optString("note", "").trim();
            double amount = item.optDouble("amount", 0.0);
            // Never compare amounts in different currencies as if exchange rates were 1:1.
            // For non-USD donations, the maintainer must supply a verified USD equivalent.
            double amountUsd = "USD".equals(currency)
                    ? amount : item.optDouble("amount_usd", Double.NaN);
            if (name.length() == 0 || name.length() > 60 || note.length() > 120 ||
                    !currency.matches("[A-Z]{3}") || !Double.isFinite(amount) ||
                    !Double.isFinite(amountUsd) || amount <= 0 || amountUsd <= 0 ||
                    amount > 100000000 || amountUsd > 100000000) continue;
            list.add(new Supporter(name, amount, currency, note, amountUsd));
        }
        list.sort((a, b) -> {
            int cmp = Double.compare(b.amountUsd, a.amountUsd);
            return cmp != 0 ? cmp : a.name.compareToIgnoreCase(b.name);
        });
        return list;
    }

    private static byte[] readBounded(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > MAX_BYTES) throw new IllegalArgumentException("File too large");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static byte[] readCache(Context context) {
        File file = new File(context.getFilesDir(), CACHE);
        try (InputStream in = new FileInputStream(file)) {
            return readBounded(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeCache(Context context, byte[] bytes) throws Exception {
        File finalFile = new File(context.getFilesDir(), CACHE);
        File tmp = new File(context.getFilesDir(), CACHE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if (!tmp.renameTo(finalFile)) {
            // Some filesystems cannot replace an existing file by rename.
            if (!finalFile.delete() || !tmp.renameTo(finalFile)) {
                tmp.delete();
                throw new java.io.IOException("Cache rename failed");
            }
        }
    }

    private SupporterList() { }
}
