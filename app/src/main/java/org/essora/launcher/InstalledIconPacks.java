package org.essora.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Installed ADW/Nova icon pack integration. This reads an installed pack's
 * appfilter.xml and drawables; it never embeds or redistributes its images.
 * The large XML is parsed off the UI thread and only entries for apps actually
 * installed on this phone are held in memory.
 */
final class InstalledIconPacks {
    static final class Pack {
        final String pkg, label;
        Pack(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    private static final String[] ACTIONS = {
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.dlto.atom.launcher.THEME",
            "com.motorola.launcher.ACTION_ICON_PACK",
            "ch.deletescape.lawnchair.ICONPACK",
            "ginlemon.smartlauncher.THEMES",
            "com.sonymobile.home.ICON_PACK",
            "com.gau.go.launcherex.theme"
    };
    private static final String[] ARCTICONS = {
            "com.donnnno.arcticons",           // white
            "com.donnnno.arcticons.light",     // black
            "com.donnnno.arcticons.you",
            "com.donnnno.arcticons.daynight"
    };

    static List<Pack> discover(Context context) {
        PackageManager pm = context.getPackageManager();
        LinkedHashMap<String, Pack> found = new LinkedHashMap<>();
        for (String action : ACTIONS) {
            try {
                for (ResolveInfo info : pm.queryIntentActivities(new Intent(action), 0)) {
                    if (info.activityInfo == null) continue;
                    addIfIconPack(pm, found, info.activityInfo.packageName);
                }
            } catch (Exception ignored) { }
        }
        // Some Android builds filter intent queries; known Arcticons package
        // names are explicitly visible through <queries><package>.
        for (String pkg : ARCTICONS) addIfIconPack(pm, found, pkg);
        ArrayList<Pack> result = new ArrayList<>(found.values());
        Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }

    private static void addIfIconPack(PackageManager pm, Map<String, Pack> packs, String pkg) {
        if (packs.containsKey(pkg)) return;
        try {
            Resources res = pm.getResourcesForApplication(pkg);
            boolean xml = res.getIdentifier("appfilter", "xml", pkg) != 0;
            if (!xml) {
                try (InputStream input = res.getAssets().open("appfilter.xml")) { xml = input != null; }
                catch (Exception ignored) { }
            }
            if (!xml) return;
            ApplicationInfo app = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(app);
            packs.put(pkg, new Pack(pkg, label == null ? pkg : label.toString()));
        } catch (Exception ignored) { }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile State current;
    private int requestSerial = 0; // main thread only
    private String pendingKey = "";

    private static final class State {
        final String pkg;
        final Resources resources;
        final HashMap<String, String> componentIcons;
        final HashMap<String, String> packageIcons;
        final HashMap<String, Integer> drawableIds = new HashMap<>();
        final String targetKey;
        State(String p, String key, Resources r, HashMap<String,String> c, HashMap<String,String> pk) {
            pkg=p; targetKey=key; resources=r; componentIcons=c; packageIcons=pk;
        }
    }

    Drawable getDrawable(ComponentName app) {
        State state = current;
        if (state == null || app == null) return null;
        String iconName = state.componentIcons.get(app.flattenToString());
        if (iconName == null) iconName = state.packageIcons.get(app.getPackageName());
        if (iconName == null) return null;
        try {
            Integer id = state.drawableIds.get(iconName);
            if (id == null) {
                id = state.resources.getIdentifier(iconName, "drawable", state.pkg);
                state.drawableIds.put(iconName, id);
            }
            return id == 0 ? null : state.resources.getDrawable(id, null);
        } catch (Exception | OutOfMemoryError ignored) { return null; }
    }

    /** Main-thread caller. On completion, redraw the current launcher screen. */
    void loadAsync(Context context, String pkg, List<ComponentName> installed, Runnable ready) {
        String requested = pkg == null ? "" : pkg.trim();
        if (requested.isEmpty()) {
            requestSerial++;
            pendingKey = "";
            current = null;
            return;
        }
        final Context app = context.getApplicationContext();
        final HashSet<String> targetComponents = new HashSet<>();
        final HashSet<String> targetPackages = new HashSet<>();
        for (ComponentName component : installed) {
            targetComponents.add(component.flattenToString());
            targetPackages.add(component.getPackageName());
        }
        long modified;
        try { modified = app.getPackageManager().getPackageInfo(requested, 0).lastUpdateTime; }
        catch (Exception ignored) { modified = 0; }
        final long packModified = modified;
        final String targetKey = requested + ":" + packModified + ":" + sorted(targetComponents);
        // One parsing job per selected pack and set of installed applications.
        State previous = current;
        if (previous != null && targetKey.equals(previous.targetKey)) return;
        if (targetKey.equals(pendingKey)) return;
        final int serial = ++requestSerial;
        pendingKey = targetKey;
        current = null;
        new Thread(() -> {
            State loaded = null;
            try {
                PackageManager pm = app.getPackageManager();
                Resources resources = pm.getResourcesForApplication(requested);
                String cacheKey = signature(targetKey);
                File cache = new File(app.getCacheDir(), "essora_adw_" + cacheKey + ".json");
                HashMap<String,String> exact = new HashMap<>();
                HashMap<String,String> byPackage = new HashMap<>();
                if (!readCache(cache, exact, byPackage)) {
                    parseAppfilter(resources, requested, targetComponents, targetPackages, exact, byPackage);
                    writeCache(cache, exact, byPackage);
                }
                loaded = new State(requested, targetKey, resources, exact, byPackage);
            } catch (Exception | OutOfMemoryError ignored) { }
            final State result = loaded;
            mainHandler.post(() -> {
                if (serial != requestSerial) return; // ignore an obsolete selection
                pendingKey = "";
                current = result;
                if (ready != null) ready.run();
            });
        }, "Essora-IconPack-Load").start();
    }

    private static String sorted(Set<String> targets) {
        ArrayList<String> names = new ArrayList<>(targets);
        Collections.sort(names);
        return names.toString();
    }

    private static String signature(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 12; i++) result.append(String.format(Locale.ROOT, "%02x", hash[i] & 0xff));
        return result.toString();
    }

    private static boolean readCache(File file, Map<String,String> c, Map<String,String> p) {
        if (!file.isFile() || file.length() > 256 * 1024) return false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) data.append(line);
            JSONObject json = new JSONObject(data.toString());
            readMap(json.getJSONObject("components"), c);
            readMap(json.getJSONObject("packages"), p);
            return true;
        } catch (Exception ignored) { c.clear(); p.clear(); return false; }
    }

    private static void readMap(JSONObject json, Map<String,String> target) {
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = json.optString(key);
            if (validResource(value)) target.put(key, value);
        }
    }

    private static void writeCache(File cache, Map<String,String> c, Map<String,String> p) {
        try {
            JSONObject json = new JSONObject();
            json.put("components", new JSONObject(c));
            json.put("packages", new JSONObject(p));
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(cache), StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (Exception ignored) { }
    }

    private static boolean validResource(String name) {
        return name != null && name.length() > 0 && name.length() < 140 && name.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    private static void parseAppfilter(Resources resources, String pkg, Set<String> components,
            Set<String> packages, Map<String,String> exact, Map<String,String> fallback) throws Exception {
        int xmlId = resources.getIdentifier("appfilter", "xml", pkg);
        if (xmlId != 0) {
            try (android.content.res.XmlResourceParser xml = resources.getXml(xmlId)) {
                parse(xml, components, packages, exact, fallback);
            }
        } else {
            try (InputStream input = resources.getAssets().open("appfilter.xml")) {
                XmlPullParser xml = Xml.newPullParser();
                xml.setInput(input, "UTF-8");
                parse(xml, components, packages, exact, fallback);
            }
        }
    }

    private static void parse(XmlPullParser xml, Set<String> components, Set<String> packages,
            Map<String,String> exact, Map<String,String> fallback) throws Exception {
        for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT; event = xml.next()) {
            if (event != XmlPullParser.START_TAG || !"item".equals(xml.getName())) continue;
            String raw = xml.getAttributeValue(null, "component");
            String drawable = xml.getAttributeValue(null, "drawable");
            if (!validResource(drawable) || raw == null) continue;
            if (raw.startsWith("ComponentInfo{") && raw.endsWith("}"))
                raw = raw.substring("ComponentInfo{".length(), raw.length() - 1);
            ComponentName component = ComponentName.unflattenFromString(raw);
            if (component == null) continue;
            String name = component.flattenToString();
            if (components.contains(name)) exact.put(name, drawable);
            if (packages.contains(component.getPackageName()) && !fallback.containsKey(component.getPackageName()))
                fallback.put(component.getPackageName(), drawable);
        }
    }
}
