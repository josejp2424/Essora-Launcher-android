package org.essora.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.GestureDetector;
import android.Manifest;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraAccessException;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.content.ContentUris;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lightweight Essora home screen: no persistent service and no external UI dependencies. */
public final class MainActivity extends Activity {
    private static final String SUPPORT_URL = "https://paypal.me/essoralinux";
    private static final int WALLPAPER_PICKER = 45;
    private static final int HOME_ROLE_REQUEST = 29;
    private static final int TORCH_PERMISSION_REQUEST = 46;
    private static final int BLACK = 0, COLOR = 1, GREEN = 2, PINK = 3, YELLOW = 4, ESSORA_HEX = 5, ESSORA_PEAKS = 6;
    // Keep persisted IDs 0..10 unchanged so existing choices survive upgrades.
    private static final int ESSORA_SUNSET = 7, ESSORA_ZEN = 8, ESSORA_AUTUMN = 9, ESSORA_COFFEE = 10, CUSTOM_THEME = 11;
    private static final String ACTION_NONE = "none", ACTION_TORCH = "torch", ACTION_CAMERA = "camera";
    private static final String ACTION_DRAWER = "drawer", ACTION_SETTINGS = "settings", ACTION_APP = "app";
    private boolean torchEnabled = false;
    private String torchCameraId;
    private boolean torchCallbackRegistered = false;
    private final CameraManager.TorchCallback torchCallback = new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (cameraId.equals(torchCameraId)) torchEnabled = enabled;
        }
        @Override public void onTorchModeUnavailable(String cameraId) {
            if (cameraId.equals(torchCameraId)) torchEnabled = false;
        }
    };
    private static final int WHITE = Color.WHITE;
    private static final int[] PALETTE = {0xff35e02f, 0xff03aeed, 0xffff9027, 0xfffa4d5c,
            0xffad54ec, 0xffffcc21, 0xff3bd7ef};
    // Glacier-white aura, electric blue and subtle orange for the Essora Hex theme.
    private static final int[] HEX_PALETTE = {0xff8ab95a, 0xff6b8e47, 0xffa6ca74, 0xff7fa95a};
    private static final int[] PEAKS_PALETTE = {0xff86c744, 0xffd6c38a, 0xff9fd05a, 0xfff0dfa8};
    private static final int[] SUNSET_PALETTE = {0xffff9f3a, 0xffffd35f, 0xffd1e57d, 0xffffbe57};
    private static final int[] ZEN_PALETTE = {0xff789759, 0xffa4c46d, 0xff587040, 0xffb9d98a};
    private static final int[] AUTUMN_PALETTE = {0xffcc7c2f, 0xff8ba248, 0xffeb9e4a, 0xff70441f};
    private static final int[] COFFEE_PALETTE = {0xffc58b53, 0xffc58b53, 0xffc58b53, 0xffc58b53};
    private final ArrayList<AppInfo> allApps = new ArrayList<>();
    private final InstalledIconPacks installedIconPacks = new InstalledIconPacks();
    private SharedPreferences prefs;
    private ThemePack.Theme customTheme;
    private String loadedCustomId = "";
    private long customThemeStamp = 0;
    private TextView timeView, dateView;
    private BatteryIndicator batteryView;
    private int page = 0; // 0 home, 1 settings, 2 main apps, 3 dock, 4 drawer, 5 themes, 6 gestures
    private String gestureSelectedSlot = "gesture_up";
    private boolean receiverRegistered = false;
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent event) { updateClock(); }
    };

    private static final class AppInfo {
        final ComponentName component;
        final String name;
        final ResolveInfo info;
        AppInfo(ComponentName component, String name, ResolveInfo info) {
            this.component = component; this.name = name; this.info = info;
        }
        String key() { return component.flattenToString(); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("essora-launcher", MODE_PRIVATE);
        refreshCustomTheme();
        getWindow().setStatusBarColor(0xff070a09);
        getWindow().setNavigationBarColor(0xff070a09);
        refreshApps();
        loadSelectedIconPack();
        ensureInitialSelection();
        ensureMainCount();
        showHome();
        // HOME selection always uses the official Android role prompt.
        getWindow().getDecorView().post(this::showInitialSetup);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showHome();
    }

    /** Android grants HOME only through its own user-facing role/settings screen. */
    private boolean isDefaultHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = (RoleManager) getSystemService(ROLE_SERVICE);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME))
                return roles.isRoleHeld(RoleManager.ROLE_HOME);
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
                && getPackageName().equals(resolved.activityInfo.packageName);
    }

    /** Displayed once per onboarding revision (including on upgrades from 1.0.3). */
    private void showInitialSetup() {
        if (isFinishing() || prefs.getBoolean("welcome_v104_seen", false)) return;
        prefs.edit().putBoolean("welcome_v104_seen", true).apply();
        if (isDefaultHome()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.setup_title)
                .setMessage(R.string.setup_message)
                .setPositiveButton(R.string.setup_choose_home,
                        (dialog, which) -> requestHomeRole(true))
                .setNegativeButton(R.string.setup_later, null)
                .show();
    }

    @Override protected void onResume() {
        super.onResume();
        String previousCustomId = loadedCustomId;
        long oldStamp = customThemeStamp;
        refreshCustomTheme();
        if (!previousCustomId.equals(loadedCustomId) || oldStamp != customThemeStamp) showHome();
        refreshApps();
        loadSelectedIconPack();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(clockReceiver, filter);
            receiverRegistered = true;
        }
        updateClock();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (manager != null && !torchCallbackRegistered) {
                    manager.registerTorchCallback(torchCallback, null);
                    torchCallbackRegistered = true;
                }
            } catch (Exception ignored) { }
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (receiverRegistered) { unregisterReceiver(clockReceiver); receiverRegistered = false; }
        if (torchCallbackRegistered) {
            try {
                CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (manager != null) manager.unregisterTorchCallback(torchCallback);
            } catch (Exception ignored) { }
            torchCallbackRegistered = false;
        }
    }

    @Override public void onBackPressed() {
        if (page == 0) { showHome(); }
        else if (page == 2 || page == 3 || page == 5 || page == 6) { showSettings(); }
        else { showHome(); }
    }

    private int dp(float pixels) { return (int)(pixels * getResources().getDisplayMetrics().density + 0.5f); }
    // New installs start with Essora Green. Preserve stored choices during updates.
    private int theme() { return customTheme != null ? CUSTOM_THEME : prefs.getInt("theme", GREEN); }

    private void refreshCustomTheme() {
        String id = prefs.getString("custom_theme_id", "");
        long stamp = id.isEmpty() ? 0 : new File(new File(ThemePack.root(this), id), "theme.json").lastModified();
        if (!id.equals(loadedCustomId) || stamp != customThemeStamp) {
            customTheme = id.isEmpty() ? null : ThemePack.load(this, id);
            loadedCustomId = customTheme == null ? "" : id;
            customThemeStamp = customTheme == null ? 0 : stamp;
            if (!id.isEmpty() && customTheme == null) prefs.edit().remove("custom_theme_id").apply();
        }
    }

    private int border(int index) {
        switch (theme()) {
            case BLACK: return WHITE;
            case GREEN: return 0xff86c744;
            case PINK: return 0xffff67b1;
            case YELLOW: return 0xffffd441;
            case ESSORA_HEX: return HEX_PALETTE[index % HEX_PALETTE.length];
            case ESSORA_PEAKS: return PEAKS_PALETTE[index % PEAKS_PALETTE.length];
            case ESSORA_SUNSET: return SUNSET_PALETTE[index % SUNSET_PALETTE.length];
            case ESSORA_ZEN: return ZEN_PALETTE[index % ZEN_PALETTE.length];
            case ESSORA_AUTUMN: return AUTUMN_PALETTE[index % AUTUMN_PALETTE.length];
            case ESSORA_COFFEE: return COFFEE_PALETTE[index % COFFEE_PALETTE.length];
            case CUSTOM_THEME: return customTheme.border;
            default: return PALETTE[index % PALETTE.length];
        }
    }

    /** The theme controls accents and opacity, not application icon bitmaps. */
    private int accentColor() {
        switch (theme()) {
            case BLACK: return WHITE;
            case GREEN: return 0xff86c744;
            case PINK: return 0xffff67b1;
            case YELLOW: return 0xffffd441;
            case ESSORA_HEX: return 0xff8ab95a;
            case ESSORA_PEAKS: return 0xff86c744;
            case ESSORA_SUNSET: return 0xffff9f3a;
            case ESSORA_ZEN: return 0xff789759;
            case ESSORA_AUTUMN: return 0xffcc7c2f;
            case ESSORA_COFFEE: return 0xffc58b53;
            case CUSTOM_THEME: return customTheme.accent;
            default: return 0xff79d434;
        }
    }

    private int bubbleFill() {
        switch (theme()) {
            case ESSORA_HEX: return 0xc90a1210;
            case ESSORA_PEAKS: return 0xca0e1412;
            case ESSORA_SUNSET: return 0xca14100b;
            case ESSORA_ZEN: return 0xbe11150e;
            case ESSORA_AUTUMN: return 0xca140e0a;
            case ESSORA_COFFEE: return 0xcb120c08;
            case CUSTOM_THEME: return customTheme.background;
            default: return 0xa9080c0d;
        }
    }
    private int dockFill() {
        switch (theme()) {
            case ESSORA_HEX: return 0xd10b1311;
            case ESSORA_PEAKS: return 0xd10d1411;
            case ESSORA_SUNSET: return 0xd1141008;
            case ESSORA_ZEN: return 0xc812150d;
            case ESSORA_AUTUMN: return 0xd1140f09;
            case ESSORA_COFFEE: return 0xd417100a;
            case CUSTOM_THEME: return customTheme.dock;
            default: return 0xb6080c0d;
        }
    }
    private int bundledWallpaper() {
        switch (theme()) {
            case COLOR: return R.drawable.wallpaper_color;
            case BLACK: return R.drawable.wallpaper_black;
            case GREEN: return R.drawable.wallpaper_green;
            case PINK: return R.drawable.wallpaper_pink;
            case YELLOW: return R.drawable.wallpaper_yellow;
            case ESSORA_HEX: return R.drawable.wallpaper_hex;
            case ESSORA_PEAKS: return R.drawable.wallpaper_peaks;
            case ESSORA_SUNSET: return R.drawable.wallpaper_sunset;
            case ESSORA_ZEN: return R.drawable.wallpaper_zen;
            case ESSORA_AUTUMN: return R.drawable.wallpaper_autumn;
            case ESSORA_COFFEE: return R.drawable.wallpaper_coffee;
            default: return R.drawable.wallpaper_essora;
        }
    }
    private boolean usesBundledThemeWallpaper(int selectedTheme) {
        return selectedTheme == COLOR || selectedTheme == BLACK || selectedTheme == GREEN
                || selectedTheme == PINK || selectedTheme == YELLOW
                || selectedTheme == ESSORA_HEX || selectedTheme == ESSORA_PEAKS
                || selectedTheme == ESSORA_SUNSET || selectedTheme == ESSORA_ZEN
                || selectedTheme == ESSORA_AUTUMN || selectedTheme == ESSORA_COFFEE;
    }

    private GradientDrawable shape(int strokeColor, float radius, int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1.7f), strokeColor);
        return d;
    }

    private TextView text(String value, float size, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextColor(theme() == ESSORA_COFFEE ? 0xfff6e9d6 : WHITE);
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setMaxLines(2);
        return result;
    }

    private LinearLayout vertical() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        return root;
    }

    private LinearLayout horizontal() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        return root;
    }

    private void updateClock() {
        if (timeView != null) {
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            String pattern = prefs.getBoolean("clock_24h", true) ? "HH:mm" : "h:mm a";
            timeView.setText(new SimpleDateFormat(pattern, locale).format(new Date()));
        }
        if (dateView != null) {
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            String format = locale.getLanguage().equals("es") ? "EEE, d 'de' MMMM"
                    : DateFormat.getBestDateTimePattern(locale, "EEE d MMMM");
            dateView.setText(new SimpleDateFormat(format, locale).format(new Date()));
        }
        if (batteryView != null) batteryView.setLevel(batteryPercent());
    }

    private int batteryPercent() {
        Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return 0;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int max = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return level < 0 || max <= 0 ? 0 : Math.round(100f * level / max);
    }

    private void refreshApps() {
        allApps.clear();
        PackageManager pm = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(launcherIntent, 0);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : found) {
            if (info.activityInfo == null) continue;
            ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            if (component.getPackageName().equals(getPackageName()) || !seen.add(component.flattenToString())) continue;
            CharSequence label = info.loadLabel(pm);
            allApps.add(new AppInfo(component, label == null ? component.getPackageName() : label.toString(), info));
        }
        Collections.sort(allApps, Comparator.comparing(a -> a.name.toLowerCase(Locale.getDefault())));
    }

    private AppInfo match(String... terms) {
        for (String term : terms) {
            for (AppInfo a : allApps) {
                if (a.component.getPackageName().toLowerCase(Locale.ROOT).contains(term)
                        || a.name.toLowerCase(Locale.ROOT).contains(term)) return a;
            }
        }
        return null;
    }

    private void ensureInitialSelection() {
        if (prefs.getBoolean("initialized", false)) return;
        String[][] examples = {{"whatsapp"}, {"telegram"}, {"firefox", "browser"},
                {"gmail", "mail"}, {"gallery", "galería", "fotos"}, {"files", "archivos"}, {"settings", "configuración"}};
        Set<String> picked = new HashSet<>();
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < 7; i++) {
            AppInfo found = match(examples[i]);
            if (found == null || picked.contains(found.key())) {
                found = null;
                for (AppInfo possible : allApps) if (!picked.contains(possible.key())) { found = possible; break; }
            }
            if (found != null) { edit.putString("main_" + i, found.key()); picked.add(found.key()); }
        }
        String[][] dock = {{"dialer", "phone", "teléfono"}, {"message", "mensaje", "sms"},
                {"firefox", "browser", "chrome"}, {"gmail", "mail"}};
        for (int i = 0; i < 4; i++) {
            AppInfo found = match(dock[i]);
            if (found != null) edit.putString("dock_" + i, found.key());
        }
        edit.putBoolean("initialized", true).apply();
    }

    /** The number of main shortcuts is a user preference, not a fixed seven-item list.
     * Limit only to the number of installed launchable apps (or ten on small installations).
     * The home list scrolls if the chosen number exceeds the available screen height. */
    private int mainAppCount() {
        int available = Math.max(10, allApps.size());
        return Math.max(1, Math.min(prefs.getInt("main_count", 10), available));
    }

    /** Migration: upgrade existing seven-icon installations without losing their choices. */
    private void ensureMainCount() {
        if (prefs.contains("main_count")) return;
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt("main_count", 10);
        Set<String> picked = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            String chosen = prefs.getString("main_" + i, null);
            if (chosen != null) picked.add(chosen);
        }
        String[][] extras = {{"camera", "cámara"}, {"calculator", "calculadora"},
                {"clock", "reloj"}};
        for (int i = 7; i < 10; i++) {
            if (prefs.contains("main_" + i)) continue;
            AppInfo app = match(extras[i - 7]);
            if (app != null && picked.add(app.key())) edit.putString("main_" + i, app.key());
        }
        edit.apply();
    }

    private void changeMainCount(int delta) {
        int current = mainAppCount();
        int updated = Math.max(1, Math.min(current + delta, Math.max(10, allApps.size())));
        if (updated != current) {
            prefs.edit().putInt("main_count", updated).apply();
            showMainSettings();
        }
    }

    private AppInfo stored(String name) {
        String id = prefs.getString(name, null);
        if (id == null) return null;
        for (AppInfo app : allApps) if (app.key().equals(id)) return app;
        return null;
    }

    /** Global pack selection takes priority. "@theme" uses the pack referenced by a custom theme. */
    private String selectedIconPack() {
        String selected = prefs.getString("icon_pack_package", "@theme");
        if ("@theme".equals(selected))
            return customTheme == null ? "" : customTheme.iconPackPackage;
        return selected;
    }

    private void loadSelectedIconPack() {
        ArrayList<ComponentName> apps = new ArrayList<>();
        for (AppInfo info : allApps) apps.add(info.component);
        installedIconPacks.loadAsync(this, selectedIconPack(), apps, () -> {
            if (!isFinishing() && page == 0) showHome();
            // On other pages the next render uses the new mapping automatically.
        });
    }

    private String installedIconPackLabel() {
        String selected = prefs.getString("icon_pack_package", "@theme");
        if ("@theme".equals(selected)) return getString(R.string.icon_pack_from_theme);
        if (selected.isEmpty()) return getString(R.string.icon_pack_original);
        for (InstalledIconPacks.Pack pack : InstalledIconPacks.discover(this))
            if (pack.pkg.equals(selected)) return pack.label;
        return selected;
    }

    private void chooseInstalledIconPack() {
        final List<InstalledIconPacks.Pack> packs = InstalledIconPacks.discover(this);
        final String[] labels = new String[packs.size() + 2];
        final String[] packages = new String[packs.size() + 2];
        labels[0] = getString(R.string.icon_pack_from_theme);
        packages[0] = "@theme";
        labels[1] = getString(R.string.icon_pack_original);
        packages[1] = "";
        for (int i = 0; i < packs.size(); i++) {
            labels[i + 2] = packs.get(i).label;
            packages[i + 2] = packs.get(i).pkg;
        }
        String current = prefs.getString("icon_pack_package", "@theme");
        int checked = 0;
        for (int i = 0; i < packages.length; i++)
            if (packages[i].equals(current)) checked = i;
        new AlertDialog.Builder(this).setTitle(R.string.icon_pack_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    prefs.edit().putString("icon_pack_package", packages[which]).apply();
                    dialog.dismiss();
                    loadSelectedIconPack();
                    showSettings();
                })
                .setNeutralButton(R.string.icon_pack_info, (dialog, which) ->
                        new AlertDialog.Builder(this).setTitle(R.string.icon_pack_title)
                                .setMessage(R.string.icon_pack_help)
                                .setPositiveButton(android.R.string.ok, null).show())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /**
     * Do not tint the full launcher icon: adaptive icons often have opaque backgrounds,
     * and turning that background white produces an empty-looking white square.
     * Use the icon's real monochrome layer only when Android supplies one.
     * Otherwise preserve the original app icon until an optional icon pack is selected.
     */
    private ImageView icon(AppInfo app, int size, boolean monochrome) {
        ImageView image = new ImageView(this);
        if (app == null) {
            image.setImageResource(android.R.drawable.ic_menu_help);
        } else {
            try {
                File customIcon = customTheme == null ? null : ThemePack.iconFile(customTheme, app.key());
                Bitmap customBitmap = ThemePack.decode(customIcon, dp(size), dp(size));
                if (customBitmap != null) {
                    image.setImageBitmap(customBitmap);
                } else {
                    Drawable packIcon = installedIconPacks.getDrawable(app.component);
                    if (packIcon != null) {
                        image.setImageDrawable(packIcon);
                    } else {
                Drawable source = app.info.loadIcon(getPackageManager());
                if (monochrome && Build.VERSION.SDK_INT >= 33 && source instanceof AdaptiveIconDrawable) {
                    Drawable mono = ((AdaptiveIconDrawable) source).getMonochrome();
                    if (mono != null) {
                        image.setImageDrawable(mono.mutate());
                        image.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    } else {
                        image.setImageDrawable(source); // keep app color instead of blank square
                    }
                } else {
                    image.setImageDrawable(source);
                }
                    } // original icon fallback when the pack does not cover this app
                }
            } catch (Exception e) {
                image.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return image;
    }

    private ImageView missingDockIcon(int position) {
        int resource;
        switch (position) {
            case 0: resource = R.drawable.essora_dock_phone; break;
            case 1: resource = R.drawable.essora_dock_chat; break;
            case 3: resource = R.drawable.essora_dock_browser; break;
            default: resource = R.drawable.essora_dock_mail; break;
        }
        ImageView image = new ImageView(this);
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(31), dp(31)));
        return image;
    }

    private ImageView appsMenuIcon(int size) {
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.essora_dock_grid);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return image;
    }

    private void open(AppInfo app) {
        if (app == null) { showDrawer(); return; }
        try {
            // Do not leave the app drawer on Essora's activity stack.
            // Launch the selected app as a separate Android task, while Essora
            // remains on its HOME screen so Back returns to the desktop.
            if (page == 4) {
                View focus = getCurrentFocus();
                if (focus != null) {
                    InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (keyboard != null) keyboard.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                    focus.clearFocus();
                }
                showHome();
            }
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setComponent(app.component);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        } catch (Exception e) {
            Toast.makeText(this, R.string.app_missing, Toast.LENGTH_SHORT).show();
            refreshApps();
            showHome();
        }
    }

    private ImageView wallpaperView() {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        boolean preferPersonal = prefs.getBoolean("use_custom_wallpaper", prefs.contains("home_wallpaper"))
                && prefs.contains("home_wallpaper");
        if (!preferPersonal && customTheme != null && ThemePack.hasWallpaper(customTheme)) {
            Bitmap themeWallpaper = ThemePack.decode(ThemePack.wallpaper(customTheme),
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            if (themeWallpaper != null) {
                image.setImageBitmap(themeWallpaper);
                return image;
            }
        }
        Uri uri = null;
        try {
            if (preferPersonal)
                uri = Uri.parse(prefs.getString("home_wallpaper", ""));
        }
        catch (Exception ignored) { }
        if (uri != null) {
            try {
                Bitmap bmp = decodePersonalWallpaper(uri);
                if (bmp != null) {
                    image.setImageBitmap(bmp);
                    return image;
                }
            } catch (Exception ignored) { }
        }
        if (customTheme != null && ThemePack.hasWallpaper(customTheme)) {
            Bitmap themeWallpaper = ThemePack.decode(ThemePack.wallpaper(customTheme),
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            if (themeWallpaper != null) {
                image.setImageBitmap(themeWallpaper);
                return image;
            }
        }
        image.setImageResource(bundledWallpaper());
        return image;
    }

    /** Decode a user-selected image close to screen size instead of keeping a
     *  very large camera/photo bitmap in RAM. Its original file is untouched. */
    private Bitmap decodePersonalWallpaper(Uri uri) throws java.io.IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int targetWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int targetHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        int sample = 1;
        // Up to 1.5x screen dimensions preserves crop detail without large allocations.
        while ((bounds.outWidth / sample > targetWidth * 1.5f ||
                bounds.outHeight / sample > targetHeight * 1.5f) &&
                sample < 1024) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return input == null ? null : BitmapFactory.decodeStream(input, null, options);
        }
    }

    /**
     * Bubbles share the width of the longest visible application name.
     * Clamp to the actual screen width and ellipsize unusually long labels.
     * Unlike MATCH_PARENT this leaves the wallpaper and empty desktop visible.
     */
    private int mainBubbleWidth() {
        Paint measure = new Paint(Paint.ANTI_ALIAS_FLAG);
        measure.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        measure.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                18, getResources().getDisplayMetrics()));
        float widest = 0;
        for (int i = 0; i < mainAppCount(); i++) {
            AppInfo app = stored("main_" + i);
            String title = app == null ? getString(R.string.choose_app) : app.name;
            widest = Math.max(widest, measure.measureText(title));
        }
        // icon 31dp + 9/12dp padding + 14dp name margin + 12dp right padding
        int desired = Math.round(widest) + dp(83);
        int usable = getResources().getDisplayMetrics().widthPixels - dp(36);
        int limit = Math.min(usable, Math.round(usable * 0.78f));
        return Math.min(Math.max(desired, dp(186)), limit);
    }

    /** Gestures start on an EMPTY area of the home screen only. This avoids hijacking
     * app taps, bubble long presses, the dock and Android's navigation gestures.
     * An upward swipe is distance based so a normal, unhurried swipe works too. */
    private final class HomeFrame extends FrameLayout {
        private final GestureDetector gestures;
        private final ArrayList<View> touchTargets = new ArrayList<>();
        private boolean emptyGesture;
        private boolean longPressHandled;
        private float downX, downY;
        HomeFrame(Context context) {
            super(context);
            gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public void onLongPress(MotionEvent event) {
                    if (emptyGesture && page == 0) {
                        longPressHandled = true;
                        // Do not rebuild the view hierarchy while dispatching a touch.
                        getWindow().getDecorView().post(() -> {
                            if (page == 0) showSettings();
                        });
                    }
                }
                @Override public boolean onDoubleTap(MotionEvent event) {
                    if (emptyGesture && page == 0) {
                        longPressHandled = true;
                        getWindow().getDecorView().post(() -> {
                            if (page == 0) performGesture("gesture_double");
                        });
                        return true;
                    }
                    return false;
                }
                @Override public boolean onFling(MotionEvent start, MotionEvent end,
                                                  float velocityX, float velocityY) {
                    if (!emptyGesture || longPressHandled || page != 0 || start == null || end == null)
                        return false;
                    float dx = end.getX() - start.getX();
                    float dy = end.getY() - start.getY();
                    // Left/right remain independent. Up is handled on ACTION_UP below,
                    // including slow swipes that GestureDetector does not call a fling.
                    if (Math.abs(dx) < dp(85) || Math.abs(dx) < Math.abs(dy) * 1.45f
                            || Math.abs(velocityX) < dp(180)) return false;
                    final String slot = dx < 0 ? "gesture_left" : "gesture_right";
                    getWindow().getDecorView().post(() -> {
                        if (page == 0) performGesture(slot);
                    });
                    return true;
                }
                @Override public boolean onDown(MotionEvent event) { return true; }
            });
        }
        void exclude(View child) { touchTargets.add(child); }
        private boolean isTouchTarget(float x, float y) {
            int[] origin = new int[2];
            getLocationOnScreen(origin);
            int screenX = origin[0] + Math.round(x), screenY = origin[1] + Math.round(y);
            Rect rect = new Rect();
            for (View target : touchTargets) {
                if (target.getGlobalVisibleRect(rect) && rect.contains(screenX, screenY)) return true;
            }
            return false;
        }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                emptyGesture = !isTouchTarget(event.getX(), event.getY());
                longPressHandled = false;
                downX = event.getX();
                downY = event.getY();
            }
            if (emptyGesture) gestures.onTouchEvent(event);
            if (action == MotionEvent.ACTION_UP) {
                if (emptyGesture && !longPressHandled && page == 0) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (dy <= -dp(60) && -dy > Math.abs(dx) * 1.35f) {
                        getWindow().getDecorView().post(() -> {
                            if (page == 0) performGesture("gesture_up");
                        });
                    }
                }
                emptyGesture = false;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                emptyGesture = false;
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private void showHome() {
        page = 0;
        timeView = null; dateView = null; batteryView = null;
        HomeFrame frame = new HomeFrame(this);
        frame.setBackgroundColor(0xff070a09);
        frame.addView(wallpaperView(), new FrameLayout.LayoutParams(-1, -1));
        // The gold wallpaper is very bright. Dim only the on-screen rendering so
        // white clock, date and app labels stay legible; the bundled JPG is untouched.
        if (theme() == ESSORA_ZEN) {
            View contrast = new View(this);
            contrast.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xb5000000, 0x89000000, 0x74000000}));
            frame.addView(contrast, new FrameLayout.LayoutParams(-1, -1));
        }
        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(13), dp(16), dp(12));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = horizontal();
        header.setMinimumHeight(dp(76));
        header.setClipChildren(false);
        header.setClipToPadding(false);
        timeView = text("", 47, false);
        timeView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        timeView.setSingleLine(true);
        timeView.setIncludeFontPadding(true); // top/bottom glyph ascenders must not be clipped
        timeView.setAutoSizeTextTypeUniformWithConfiguration(30, 47, 1,
                TypedValue.COMPLEX_UNIT_SP);
        timeView.setMinHeight(dp(72));
        header.addView(timeView, new LinearLayout.LayoutParams(0, -2, 1));
        frame.exclude(timeView);
        timeView.setOnClickListener(v -> launchClockApp());
        batteryView = new BatteryIndicator(this);
        batteryView.setFillColor(customTheme != null ? customTheme.battery : accentColor());
        if (customTheme != null) {
            batteryView.setOutlineColor(customTheme.batteryOutline);
            batteryView.setLabelColor(customTheme.text);
        } else if (theme() == ESSORA_COFFEE) {
            batteryView.setOutlineColor(0xfff3ddbe);
            batteryView.setLabelColor(0xfff9ebd7);
        }
        LinearLayout.LayoutParams batteryParams = new LinearLayout.LayoutParams(dp(132), dp(51));
        batteryParams.leftMargin = dp(8);
        header.addView(batteryView, batteryParams);
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));
        if (customTheme != null) timeView.setTextColor(customTheme.text);
        dateView = text("", 18, true);
        if (customTheme != null) dateView.setTextColor(customTheme.text);
        dateView.setIncludeFontPadding(true);
        dateView.setMaxLines(2);
        dateView.setPadding(0, dp(1), 0, dp(4));
        dateView.setMinHeight(dp(37));
        content.addView(dateView, new LinearLayout.LayoutParams(-1, -2));
        frame.exclude(dateView);
        dateView.setOnClickListener(v -> launchCalendarApp());
        updateClock();

        // Visible separation from the clock, like the reference design.
        View separation = new View(this);
        content.addView(separation, new LinearLayout.LayoutParams(1, dp(41)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false); // Never stretch the bubbles vertically.
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout rows = vertical();
        rows.setGravity(Gravity.LEFT | Gravity.TOP);
        rows.setPadding(dp(2), dp(1), dp(2), dp(6));
        int bubbleWidth = mainBubbleWidth();
        for (int i = 0; i < mainAppCount(); i++) {
            final int index = i;
            AppInfo app = stored("main_" + i);
            LinearLayout item = horizontal();
            item.setPadding(dp(9), 0, dp(12), 0);
            item.setBackground(shape(border(i), 19, bubbleFill()));
            item.addView(icon(app, mainAppCount() >= 10 ? 27 : 31, theme() == BLACK));
            TextView name = text(app == null ? getString(R.string.choose_app) : app.name,
                    mainAppCount() >= 10 ? 15.5f : 18, true);
            if (customTheme != null) name.setTextColor(customTheme.text);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(dp(13), 0, 0, 0);
            item.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(bubbleWidth,
                    dp(mainAppCount() >= 10 ? 39 : 46));
            rowParams.setMargins(dp(2), dp(2), 0, dp(mainAppCount() >= 10 ? 5 : 7));
            rows.addView(item, rowParams);
            frame.exclude(item);
            item.setOnClickListener(v -> open(app));
            item.setOnLongClickListener(v -> {
                chooseApp("main_" + index, MainActivity.this::showHome);
                return true;
            });
        }
        scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = horizontal();
        dock.setGravity(Gravity.CENTER);
        for (int i = 0; i < 5; i++) {
            final int index = i;
            AppInfo app = i == 2 ? null : stored("dock_" + (i > 2 ? i - 1 : i));
            LinearLayout cell = horizontal();
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(shape(border(i), 15, dockFill()));
            if (i == 2) cell.addView(appsMenuIcon(31));
            else cell.addView(app == null ? missingDockIcon(i) : icon(app, 31, theme() == BLACK));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            dock.addView(cell, params);
            frame.exclude(cell);
            cell.setOnClickListener(v -> { if (index == 2) showDrawer(); else open(app); });
            cell.setOnLongClickListener(v -> {
                if (index == 2) showSettings();
                else chooseApp("dock_" + (index > 2 ? index - 1 : index), MainActivity.this::showHome);
                return true;
            });
        }
        content.addView(dock);
        setContentView(frame);
    }

    /** Executes only actions the user explicitly configured. No simulated lock screen,
     * device administrator, accessibility service, or persistent background service. */
    private String defaultGestureAction(String slot) {
        if ("gesture_up".equals(slot)) return ACTION_DRAWER;
        if ("gesture_double".equals(slot)) return ACTION_TORCH;
        return ACTION_NONE;
    }

    private boolean performGesture(String slot) {
        String action = prefs.getString(slot, defaultGestureAction(slot));
        switch (action) {
            case ACTION_TORCH: toggleTorch(); return true;
            case ACTION_CAMERA: launchCamera(); return true;
            case ACTION_DRAWER: showDrawer(); return true;
            case ACTION_SETTINGS: showSettings(); return true;
            case ACTION_APP:
                AppInfo application = stored(slot + "_app");
                if (application != null) { open(application); return true; }
                Toast.makeText(this, R.string.gesture_app_missing, Toast.LENGTH_SHORT).show();
                return false;
            default: return false;
        }
    }

    /** Uses Android's native camera intent, not direct camera access. */
    private void launchCamera() {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        try {
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
            // OEM fallback if no standard camera activity was registered.
            Intent fallback = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (fallback.resolveActivity(getPackageManager()) != null) {
                startActivity(fallback);
                return;
            }
        } catch (Exception ignored) { }
        Toast.makeText(this, R.string.gesture_camera_unavailable, Toast.LENGTH_SHORT).show();
    }

    /** Opens the alarm/clock application when tapping on the time. */
    private void launchClockApp() {
        try {
            Intent showAlarms = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
            showAlarms.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (showAlarms.resolveActivity(getPackageManager()) != null) {
                startActivity(showAlarms);
                return;
            }
        } catch (Exception ignored) { }
        AppInfo clockApp = match("deskclock", "alarm", "alarma", "clock", "reloj");
        if (clockApp != null) {
            open(clockApp);
            return;
        }
        Toast.makeText(this, R.string.app_missing, Toast.LENGTH_SHORT).show();
    }

    /** Opens the calendar application at the current date when tapping on the date. */
    private void launchCalendarApp() {
        try {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            ContentUris.appendId(builder, System.currentTimeMillis());
            Intent calIntent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
            calIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (calIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(calIntent);
                return;
            }
        } catch (Exception ignored) { }
        try {
            Intent catIntent = Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR);
            catIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (catIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(catIntent);
                return;
            }
        } catch (Exception ignored) { }
        AppInfo calApp = match("calendar", "calendario", "agenda", "etar");
        if (calApp != null) {
            open(calApp);
            return;
        }
        Toast.makeText(this, R.string.app_missing, Toast.LENGTH_SHORT).show();
    }

    /** Direct flashlight toggle: camera permission is requested only on use. */
    private void toggleTorch() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.gesture_torch)
                    .setMessage(R.string.gesture_torch_permission)
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            requestPermissions(new String[]{Manifest.permission.CAMERA}, TORCH_PERMISSION_REQUEST))
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) throw new IllegalStateException("Camera manager unavailable");
            if (torchCameraId == null) {
                for (String id : manager.getCameraIdList()) {
                    Boolean flash = manager.getCameraCharacteristics(id)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (Boolean.TRUE.equals(flash)) { torchCameraId = id; break; }
                }
            }
            if (torchCameraId == null) throw new IllegalStateException("No torch camera");
            boolean newState = !torchEnabled;
            manager.setTorchMode(torchCameraId, newState);
            torchEnabled = newState;
        } catch (CameraAccessException | SecurityException | IllegalArgumentException | IllegalStateException ex) {
            Toast.makeText(this, R.string.gesture_torch_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == TORCH_PERMISSION_REQUEST && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleTorch();
            } else {
                Toast.makeText(this, R.string.gesture_torch_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Each screen has a compact header. A wrap-content layout prevents clipping
     * when the Android font scale or the translated heading grows. */
    private LinearLayout darkPage(String heading, Runnable back) {
        timeView = null; dateView = null; batteryView = null;
        LinearLayout pageLayout = vertical();
        pageLayout.setPadding(dp(16), dp(8), dp(16), dp(14));
        switch (theme()) {
            case COLOR: pageLayout.setBackgroundResource(R.drawable.essora_settings_bg_color); break;
            case BLACK: pageLayout.setBackgroundResource(R.drawable.essora_settings_bg_black); break;
            case GREEN: pageLayout.setBackgroundResource(R.drawable.essora_settings_bg_green); break;
            case PINK: pageLayout.setBackgroundResource(R.drawable.essora_settings_bg_pink); break;
            case YELLOW: pageLayout.setBackgroundResource(R.drawable.essora_settings_bg_yellow); break;
            case ESSORA_HEX: pageLayout.setBackgroundResource(R.drawable.essora_settings_bg_hex); break;
            case ESSORA_PEAKS: pageLayout.setBackgroundResource(R.drawable.essora_settings_bg_peaks); break;
            case ESSORA_AUTUMN: pageLayout.setBackgroundResource(R.drawable.essora_settings_bg_autumn); break;
            case ESSORA_COFFEE: pageLayout.setBackgroundResource(R.drawable.essora_settings_bg_coffee); break;
            case CUSTOM_THEME: pageLayout.setBackgroundColor(0xff101512); break;
            default: pageLayout.setBackgroundColor(0xff090c0c); break;
        }

        LinearLayout toolbar = horizontal();
        toolbar.setMinimumHeight(dp(59));
        TextView backButton = text("‹", 34, false);
        backButton.setGravity(Gravity.CENTER);
        toolbar.addView(backButton, new LinearLayout.LayoutParams(dp(40), dp(54)));
        backButton.setOnClickListener(v -> back.run());

        TextView header = text(heading.equals(getString(R.string.app_name)) ? "" : heading, 22, true);
        header.setMaxLines(2);
        header.setPadding(dp(5), 0, dp(4), 0);
        toolbar.addView(header, new LinearLayout.LayoutParams(0, -2, 1));
        header.setOnClickListener(v -> back.run());
        pageLayout.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(pageLayout);
        return pageLayout;
    }

    private ScrollView scrollInside(LinearLayout parent) {
        ScrollView view = new ScrollView(this);
        view.setFillViewport(false);
        view.setClipToPadding(false);
        view.setVerticalScrollBarEnabled(false);
        parent.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
        return view;
    }

    /** No fixed row height: long labels and accessibility font sizes can expand. */
    private void settingsRow(LinearLayout parent, int iconId,
                             String heading, String description, Runnable action) {
        LinearLayout item = horizontal();
        item.setMinimumHeight(dp(79));
        item.setPadding(dp(12), dp(12), dp(11), dp(12));
        boolean animeTheme = usesBundledThemeWallpaper(theme());
        item.setBackground(shape(animeTheme ? accentColor() : 0xff626868, 13,
                animeTheme ? dockFill() : 0xff1b1e1e));

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(iconId);
        iconView.setColorFilter(accentColor(), PorterDuff.Mode.SRC_IN);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(27), dp(27));
        iconParams.setMargins(0, 0, dp(13), 0);
        item.addView(iconView, iconParams);

        LinearLayout labels = vertical();
        TextView title = text(heading, 16, true);
        title.setMaxLines(3);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text(description, 12.5f, false);
        subtitle.setTextColor(0xffd7d7d7);
        subtitle.setMaxLines(Integer.MAX_VALUE);
        subtitle.setPadding(0, dp(3), 0, 0);
        labels.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView chevron = text("›", 26, false);
        chevron.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chevParams = new LinearLayout.LayoutParams(dp(27), dp(44));
        chevParams.setMargins(dp(8), 0, 0, 0);
        item.addView(chevron, chevParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(9);
        parent.addView(item, params);
        item.setContentDescription(heading + ". " + description);
        item.setOnClickListener(v -> action.run());
    }

    private void showSettings() {
        page = 1;
        LinearLayout root = darkPage(getString(R.string.app_name), this::showHome);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_foreground);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(53), dp(53));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        logoParams.setMargins(0, 0, 0, dp(3));
        root.addView(logo, logoParams);
        TextView launcherTitle = text(getString(R.string.app_name), 22, true);
        launcherTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams launcherTitleParams = new LinearLayout.LayoutParams(-1, dp(39));
        launcherTitleParams.bottomMargin = dp(9);
        root.addView(launcherTitle, launcherTitleParams);

        ScrollView scroll = scrollInside(root);
        LinearLayout list = vertical();
        list.setPadding(dp(1), dp(2), dp(1), dp(10));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        settingsRow(list, android.R.drawable.ic_menu_view, getString(R.string.main_apps),
                getString(R.string.main_apps_desc), this::showMainSettings);
        settingsRow(list, android.R.drawable.ic_menu_sort_by_size, getString(R.string.dock),
                getString(R.string.dock_desc), this::showDockSettings);
        settingsRow(list, android.R.drawable.ic_menu_manage, getString(R.string.theme),
                getString(R.string.theme_desc) + " · " + themeName(), this::showThemePicker);
        settingsRow(list, android.R.drawable.ic_menu_gallery, getString(R.string.icon_pack_title),
                installedIconPackLabel(), this::chooseInstalledIconPack);
        settingsRow(list, android.R.drawable.ic_menu_edit, getString(R.string.creator_title),
                getString(R.string.creator_intro), this::openThemeCreator);
        settingsRow(list, android.R.drawable.ic_menu_recent_history, getString(R.string.clock_battery),
                getString(R.string.clock_battery_desc), this::chooseClock);
        settingsRow(list, android.R.drawable.ic_menu_compass, getString(R.string.gestures),
                getString(R.string.gestures_desc), this::showGesturesPage);
        settingsRow(list, android.R.drawable.ic_menu_gallery, getString(R.string.wallpaper),
                getString(R.string.wallpaper_desc), this::chooseWallpaper);
        settingsRow(list, android.R.drawable.ic_menu_myplaces, getString(R.string.home_default),
                getString(R.string.home_default_desc), this::requestHomeRole);
        // Optional contribution: opens PayPal.Me only when the user taps this row.
        settingsRow(list, R.drawable.essora_action_support, getString(R.string.support_essora),
                getString(R.string.support_essora_desc), this::openSupportLink);
        settingsRow(list, android.R.drawable.ic_menu_info_details, getString(R.string.about),
                getString(R.string.app_name) + " " + installedVersion(), () ->
                new AlertDialog.Builder(this).setTitle(R.string.app_name)
                        .setMessage("Version " + installedVersion() + "\n" + getPackageName()
                                + "\n" + getString(R.string.about_developer) + ": Essora Project"
                                + "\nFreedom to Create. Freedom to Explore.")
                        .setPositiveButton(android.R.string.ok, null).show());
    }

    /** No payment SDK, browser tracking, or background process: the user decides in PayPal. */
    private void openSupportLink() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, R.string.support_essora_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    /** Always read the installed package version; never hard-code About text. */
    private String installedVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    private void chooseClock() {
        boolean use24 = prefs.getBoolean("clock_24h", true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.clock_battery)
                .setSingleChoiceItems(new String[]{"24 h", "12 h"}, use24 ? 0 : 1,
                        (dialog, which) -> {
                            prefs.edit().putBoolean("clock_24h", which == 0).apply();
                            dialog.dismiss();
                            showSettings();
                        })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private String themeName() {
        switch (theme()) {
            case BLACK: return "Essora Black";
            case GREEN: return "Essora Green";
            case PINK: return "Essora Pink";
            case YELLOW: return "Essora Yellow";
            case ESSORA_HEX: return "Essora Hex";
            case ESSORA_PEAKS: return "Essora Peaks";
            case ESSORA_SUNSET: return "Essora Sunset";
            case ESSORA_ZEN: return "Essora Zen";
            case ESSORA_AUTUMN: return "Essora Autumn";
            case ESSORA_COFFEE: return "Essora Coffee";
            case CUSTOM_THEME: return customTheme == null ? "Essora" : customTheme.name;
            default: return "Essora Color";
        }
    }

    /** Theme chooser is a real screen with bitmap previews, not a system AlertDialog. */
    private void showThemePicker() {
        page = 5;
        LinearLayout root = darkPage(getString(R.string.theme), this::showSettings);
        TextView description = text(getString(R.string.theme_choose_desc), 13.5f, false);
        description.setPadding(dp(7), dp(1), dp(5), dp(15));
        root.addView(description);
        ScrollView scroll = scrollInside(root);
        LinearLayout list = vertical();
        list.setPadding(dp(2), 0, dp(2), dp(20));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        if (customTheme != null) {
            LinearLayout personalCard = horizontal();
            personalCard.setPadding(dp(9), dp(9), dp(11), dp(9));
            personalCard.setBackground(shape(customTheme.accent, 14, 0xd0141f18));
            ImageView thumbnail = new ImageView(this);
            Bitmap personalPreview = ThemePack.decode(ThemePack.preview(customTheme), dp(96), dp(55));
            if (personalPreview == null)
                personalPreview = ThemePack.decode(ThemePack.wallpaper(customTheme), dp(96), dp(55));
            if (personalPreview != null) thumbnail.setImageBitmap(personalPreview);
            else thumbnail.setImageResource(R.drawable.wallpaper_essora);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(92), dp(52));
            thumbParams.rightMargin = dp(12);
            personalCard.addView(thumbnail, thumbParams);
            LinearLayout customLabels = vertical();
            customLabels.addView(text(customTheme.name, 15.5f, true));
            customLabels.addView(text(getString(R.string.creator_author) + ": " + customTheme.author, 12, false));
            personalCard.addView(customLabels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView selectedMark = text("◉", 25, true);
            selectedMark.setTextColor(customTheme.accent);
            personalCard.addView(selectedMark);
            LinearLayout.LayoutParams personalParams = new LinearLayout.LayoutParams(-1, -2);
            personalParams.bottomMargin = dp(9);
            list.addView(personalCard, personalParams);
            personalCard.setOnClickListener(v -> openThemeCreator());
        }
        settingsRow(list, android.R.drawable.ic_menu_edit, getString(R.string.creator_title),
                getString(R.string.creator_new_desc), this::openThemeCreator);
        settingsRow(list, android.R.drawable.ic_menu_upload,
                getString(R.string.creator_install_local), getString(R.string.creator_install_local_desc),
                this::openLocalThemePicker);
        final int[] themes = {COLOR, BLACK, GREEN, PINK, YELLOW, ESSORA_HEX, ESSORA_PEAKS,
                ESSORA_SUNSET, ESSORA_ZEN, ESSORA_AUTUMN, ESSORA_COFFEE};
        final int[] previews = {R.drawable.theme_preview_color, R.drawable.theme_preview_black,
                R.drawable.theme_preview_green, R.drawable.theme_preview_pink,
                R.drawable.theme_preview_yellow, R.drawable.theme_preview_hex,
                R.drawable.theme_preview_peaks, R.drawable.theme_preview_sunset,
                R.drawable.theme_preview_zen, R.drawable.theme_preview_autumn,
                R.drawable.theme_preview_coffee};
        final int[] descriptions = {R.string.theme_color_desc, R.string.theme_black_desc,
                R.string.theme_green_desc, R.string.theme_pink_desc,
                R.string.theme_yellow_desc, R.string.theme_dragon_desc,
                R.string.theme_dragon_green_desc, R.string.theme_dragon_blue_desc,
                R.string.theme_dragon_gold_desc, R.string.theme_dragon_ultra_desc,
                R.string.theme_coffee_desc};
        final String[] names = {"Essora Color", "Essora Black", "Essora Green", "Essora Pink",
                "Essora Yellow", "Essora Hex", "Essora Peaks", "Essora Sunset",
                "Essora Zen", "Essora Autumn", "Essora Coffee"};
        for (int i = 0; i < themes.length; i++) {
            final int selectedTheme = themes[i];
            boolean active = theme() == selectedTheme;
            LinearLayout item = horizontal();
            item.setMinimumHeight(dp(66));
            item.setPadding(dp(9), dp(7), dp(10), dp(7));
            int selectedFill = selectedTheme == ESSORA_HEX ? 0xe6142a17
                    : selectedTheme == ESSORA_PEAKS ? 0xe6152818
                    : selectedTheme == ESSORA_SUNSET ? 0xe92a190a
                    : selectedTheme == ESSORA_ZEN ? 0xe9172110
                    : selectedTheme == ESSORA_AUTUMN ? 0xe925150d
                    : selectedTheme == ESSORA_COFFEE ? 0xe922160d : 0xe6143221;
            item.setBackground(shape(active ? accentColor() : 0xff53635f, 14,
                    active ? selectedFill : 0xd8101c1d));
            ImageView preview = new ImageView(this);
            preview.setImageResource(previews[i]);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackground(shape(0xff708a83, 9, 0xff101918));
            preview.setClipToOutline(true);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(92), dp(52));
            imageParams.rightMargin = dp(12);
            item.addView(preview, imageParams);
            LinearLayout labels = vertical();
            TextView name = text(names[i], 15, true);
            name.setMaxLines(2);
            labels.addView(name);
            TextView detail = text(getString(descriptions[i]), 11.5f, false);
            detail.setTextColor(0xffc7d7d1);
            detail.setPadding(0, dp(2), 0, 0);
            detail.setMaxLines(2);
            labels.addView(detail);
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView radio = text(active ? "◉" : "○", 25, false);
            radio.setTextColor(active ? accentColor() : WHITE);
            radio.setGravity(Gravity.CENTER);
            item.addView(radio, new LinearLayout.LayoutParams(dp(32), dp(42)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(8);
            list.addView(item, rowParams);
            item.setOnClickListener(v -> {
                SharedPreferences.Editor editor = prefs.edit().putInt("theme", selectedTheme)
                        .remove("custom_theme_id");
                customTheme = null;
                loadedCustomId = "";
                customThemeStamp = 0;
                if (usesBundledThemeWallpaper(selectedTheme))
                    editor.putBoolean("use_custom_wallpaper", false);
                editor.apply();
                showThemePicker();
            });
            if (active) {
                final View activeItem = item;
                scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, activeItem.getTop() - dp(160))));
            }
        }
    }

    private void openThemeCreator() {
        startActivity(new Intent(this, ThemeCreatorActivity.class));
    }

    /** Open the Android documents picker directly from the main Themes screen. */
    private void openLocalThemePicker() {
        Intent intent = new Intent(this, ThemeCreatorActivity.class);
        intent.putExtra("open_local_theme_picker", true);
        startActivity(intent);
    }

    private String gestureActionLabel(String key) {
        String action = prefs.getString(key, defaultGestureAction(key));
        switch (action) {
            case ACTION_TORCH: return getString(R.string.gesture_torch);
            case ACTION_CAMERA: return getString(R.string.gesture_camera);
            case ACTION_DRAWER: return getString(R.string.gesture_all_apps);
            case ACTION_SETTINGS: return getString(R.string.gesture_launcher_settings);
            case ACTION_APP:
                AppInfo app = stored(key + "_app");
                return app == null ? getString(R.string.gesture_select_app) : app.name;
            default: return getString(R.string.gesture_none);
        }
    }

    /** Four configurable gestures; tapping one selects which action is being edited. */
    private void showGesturesPage() {
        page = 6;
        LinearLayout root = darkPage(getString(R.string.gestures), this::showSettings);
        ScrollView scroll = scrollInside(root);
        LinearLayout body = vertical();
        body.setPadding(dp(2), dp(2), dp(2), dp(18));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        TextView description = text(getString(R.string.gestures_choose_desc), 13.5f, false);
        description.setPadding(dp(5), 0, dp(5), dp(15));
        body.addView(description);
        final String[] keys = {"gesture_up", "gesture_left", "gesture_right", "gesture_double"};
        final int[] labels = {R.string.gesture_up, R.string.gesture_left,
                R.string.gesture_right, R.string.gesture_double};
        final String[] symbols = {"↑", "←", "→", "◎"};
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            boolean selected = gestureSelectedSlot.equals(key);
            LinearLayout item = horizontal();
            item.setMinimumHeight(dp(68));
            item.setPadding(dp(12), dp(9), dp(12), dp(9));
            item.setBackground(shape(selected ? accentColor() : 0xff546b65,
                    13, selected ? 0xe419401f : 0xe60e211d));
            TextView glyph = text(symbols[i], 29, true);
            glyph.setTextColor(accentColor());
            glyph.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(dp(35), dp(42));
            glyphParams.rightMargin = dp(10);
            item.addView(glyph, glyphParams);
            LinearLayout caption = vertical();
            TextView title = text(getString(labels[i]), 15, true);
            caption.addView(title);
            TextView assigned = text(gestureActionLabel(key), 12.5f, false);
            assigned.setTextColor(selected ? 0xffe7ffe3 : 0xffc1d6cb);
            assigned.setPadding(0, dp(2), 0, 0);
            caption.addView(assigned);
            item.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
            TextView arrow = text("›", 26, false);
            arrow.setGravity(Gravity.CENTER);
            item.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(44)));
            LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
            row.bottomMargin = dp(8);
            body.addView(item, row);
            item.setOnClickListener(v -> {
                gestureSelectedSlot = key;
                showGesturesPage();
            });
        }
        TextView choose = text(getString(R.string.gesture_choose_action), 16, true);
        choose.setPadding(dp(7), dp(15), 0, dp(12));
        body.addView(choose);
        final String[] actions = {ACTION_NONE, ACTION_TORCH, ACTION_CAMERA,
                ACTION_SETTINGS, ACTION_DRAWER, ACTION_APP};
        final String[] symbols2 = {"⊘", "♜", "▣", "⚙", "▦", "▷"};
        final int[] labels2 = {R.string.gesture_tile_none, R.string.gesture_tile_torch,
                R.string.gesture_tile_camera, R.string.gesture_tile_settings,
                R.string.gesture_tile_drawer, R.string.gesture_tile_app};
        for (int r = 0; r < 2; r++) {
            LinearLayout line = horizontal();
            for (int c = 0; c < 3; c++) {
                int idx = r * 3 + c;
                String action = actions[idx];
                boolean active = action.equals(prefs.getString(gestureSelectedSlot,
                        defaultGestureAction(gestureSelectedSlot)));
                LinearLayout cell = vertical();
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(3), dp(7), dp(3), dp(7));
                cell.setBackground(shape(active ? accentColor() : 0xff60736d,
                        13, active ? 0xe41c481e : 0xe40d211e));
                if (ACTION_TORCH.equals(action) || ACTION_CAMERA.equals(action)) {
                    ImageView icon = new ImageView(this);
                    icon.setImageResource(ACTION_TORCH.equals(action) ? R.drawable.essora_action_flashlight
                            : android.R.drawable.ic_menu_camera);
                    icon.setColorFilter(active ? accentColor() : WHITE, PorterDuff.Mode.SRC_IN);
                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
                    iconParams.gravity = Gravity.CENTER_HORIZONTAL;
                    iconParams.bottomMargin = dp(8);
                    cell.addView(icon, iconParams);
                } else {
                    TextView icon = text(symbols2[idx], 31, false);
                    icon.setGravity(Gravity.CENTER);
                    icon.setTextColor(active ? accentColor() : WHITE);
                    cell.addView(icon, new LinearLayout.LayoutParams(-1, dp(36)));
                }
                TextView label = text(getString(labels2[idx]), 11.2f, false);
                label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                label.setMaxLines(3);
                cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(107), 1);
                lp.setMargins(dp(4), 0, dp(4), dp(8));
                line.addView(cell, lp);
                cell.setOnClickListener(v -> {
                    if (ACTION_APP.equals(action)) chooseGestureApp(gestureSelectedSlot);
                    else {
                        prefs.edit().putString(gestureSelectedSlot, action).apply();
                        showGesturesPage();
                    }
                });
            }
            body.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void chooseGestureApp(String slot) {
        refreshApps();
        String[] names = new String[allApps.size()];
        for (int i = 0; i < names.length; i++) names[i] = allApps.get(i).name;
        new AlertDialog.Builder(this).setTitle(R.string.gesture_select_app)
                .setItems(names, (dialog, which) -> {
                    prefs.edit().putString(slot + "_app", allApps.get(which).key())
                            .putString(slot, ACTION_APP).apply();
                    showGesturesPage();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showMainSettings() {
        page = 2;
        LinearLayout root = darkPage(getString(R.string.main_apps), this::showSettings);
        root.addView(text(getString(R.string.main_apps_desc), 15, false));

        // Explicit +/- count instead of the previous seven-item hardcoded limit.
        LinearLayout countControls = horizontal();
        countControls.setPadding(dp(10), dp(5), dp(10), dp(5));
        countControls.setBackground(shape(0xff6c8d6c, 12, 0xff1a231b));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.setMargins(0, dp(12), 0, dp(5));
        root.addView(countControls, countParams);
        TextView countLabel = text(getString(R.string.main_count_label) + ": " + mainAppCount(), 16, true);
        countControls.addView(countLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView fewer = text("−", 27, true);
        fewer.setGravity(Gravity.CENTER);
        fewer.setContentDescription(getString(R.string.fewer_apps));
        countControls.addView(fewer, new LinearLayout.LayoutParams(dp(48), dp(48)));
        fewer.setOnClickListener(v -> changeMainCount(-1));
        TextView more = text("+", 27, true);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription(getString(R.string.more_apps));
        countControls.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        more.setOnClickListener(v -> changeMainCount(+1));
        ScrollView scroll = scrollInside(root);
        LinearLayout rows = vertical();
        rows.setPadding(0, dp(13), 0, dp(12));
        scroll.addView(rows);
        for (int i = 0; i < mainAppCount(); i++) {
            final int index = i;
            String key = "main_" + i;
            AppInfo app = stored(key);
            LinearLayout line = horizontal();
            line.setMinimumHeight(dp(59));
            line.setPadding(dp(10), dp(9), dp(7), dp(9));
            line.setBackground(shape(0xff676d6d, 12, 0xff1d2020));
            line.addView(icon(app, 35, theme() == BLACK));
            TextView name = text(app == null ? getString(R.string.choose_app) : app.name, 17, true);
            name.setPadding(dp(11), 0, 0, 0);
            name.setMaxLines(3);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView moveUp = text("↑", 24, true); moveUp.setGravity(Gravity.CENTER);
            TextView moveDown = text("↓", 24, true); moveDown.setGravity(Gravity.CENTER);
            line.addView(moveUp, new LinearLayout.LayoutParams(dp(35), dp(44)));
            line.addView(moveDown, new LinearLayout.LayoutParams(dp(35), dp(44)));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.bottomMargin = dp(7);
            rows.addView(line, p);
            name.setOnClickListener(v -> chooseApp(key, this::showMainSettings));
            moveUp.setOnClickListener(v -> { if (index > 0) { swap("main_" + index, "main_" + (index - 1)); showMainSettings(); } });
            moveDown.setOnClickListener(v -> { if (index < mainAppCount() - 1) { swap("main_" + index, "main_" + (index + 1)); showMainSettings(); } });
            line.setOnClickListener(v -> chooseApp(key, this::showMainSettings));
        }
    }

    private void showDockSettings() {
        page = 3;
        LinearLayout root = darkPage(getString(R.string.dock), this::showSettings);
        root.addView(text(getString(R.string.dock_desc), 15, false));
        ScrollView scroll = scrollInside(root);
        LinearLayout list = vertical();
        list.setPadding(0, dp(15), 0, 0);
        scroll.addView(list);
        String[] headings = {getString(R.string.phone), getString(R.string.messages),
                getString(R.string.app_menu), getString(R.string.browser), getString(R.string.mail)};
        for (int i = 0; i < 5; i++) {
            final int index = i;
            AppInfo app = i == 2 ? null : stored("dock_" + (i > 2 ? i - 1 : i));
            LinearLayout row = horizontal();
            row.setMinimumHeight(dp(63));
            row.setPadding(dp(11), dp(9), dp(12), dp(9));
            row.setBackground(shape(0xff697373, 12, 0xff1d2020));
            if (i == 2) row.addView(appsMenuIcon(35));
            else row.addView(app == null ? missingDockIcon(i) : icon(app, 35, theme() == BLACK));
            TextView label = text(headings[i] + (i == 2 ? "" : app == null ? " · " + getString(R.string.choose_app) : " · " + app.name), 17, true);
            label.setPadding(dp(10), 0, 0, 0);
            label.setMaxLines(3);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.bottomMargin = dp(9);
            list.addView(row, p);
            row.setOnClickListener(v -> {
                if (index == 2) Toast.makeText(this, R.string.app_menu_fixed, Toast.LENGTH_SHORT).show();
                else chooseApp("dock_" + (index > 2 ? index - 1 : index), this::showDockSettings);
            });
        }
    }

    private void swap(String a, String b) {
        String one = prefs.getString(a, null), two = prefs.getString(b, null);
        SharedPreferences.Editor editor = prefs.edit();
        if (two == null) editor.remove(a); else editor.putString(a, two);
        if (one == null) editor.remove(b); else editor.putString(b, one);
        editor.apply();
    }

    private void chooseApp(String setting, Runnable after) {
        refreshApps();
        String[] names = new String[allApps.size()];
        for (int i = 0; i < names.length; i++) names[i] = allApps.get(i).name;
        new AlertDialog.Builder(this).setTitle(R.string.choose_app)
                .setItems(names, (dialog, which) -> {
                    prefs.edit().putString(setting, allApps.get(which).key()).apply(); after.run();
                }).setNeutralButton(R.string.clear, (dialog, which) -> {
                    prefs.edit().remove(setting).apply(); after.run();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showDrawer() {
        page = 4;
        refreshApps();
        LinearLayout root = darkPage(getString(R.string.app_menu), this::showHome);
        EditText search = new EditText(this);
        search.setSingleLine(true); search.setTextColor(WHITE);
        search.setHintTextColor(0xffaaaaaa); search.setHint(R.string.search);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(55)));
        TextView launcherSettings = text("⚙  " + getString(R.string.launcher_settings), 16, true);
        launcherSettings.setPadding(dp(12), 0, dp(12), 0);
        launcherSettings.setBackground(shape(0xff6e8c69, 12, 0xff17221a));
        LinearLayout.LayoutParams launcherSettingsParams = new LinearLayout.LayoutParams(-1, dp(46));
        launcherSettingsParams.setMargins(0, dp(5), 0, dp(9));
        root.addView(launcherSettings, launcherSettingsParams);
        launcherSettings.setOnClickListener(v -> showSettings());
        ScrollView scroll = scrollInside(root);
        LinearLayout list = vertical();
        scroll.addView(list);
        Runnable reload = () -> {
            list.removeAllViews();
            String query = search.getText().toString().toLowerCase(Locale.getDefault());
            for (AppInfo app : allApps) {
                if (!app.name.toLowerCase(Locale.getDefault()).contains(query)) continue;
                LinearLayout line = horizontal();
                line.setPadding(dp(12), dp(8), dp(7), dp(8));
                line.addView(icon(app, 36, theme() == BLACK));
                TextView name = text(app.name, 17, false);
                name.setPadding(dp(12), 0, 0, 0);
                line.addView(name);
                list.addView(line, new LinearLayout.LayoutParams(-1, dp(57)));
                line.setOnClickListener(v -> open(app));
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { reload.run(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        reload.run();
    }

    private void requestHomeRole() { requestHomeRole(false); }

    /** Request from Android, not an app-designed imitation of the default launcher dialog. */
    private void requestHomeRole(boolean fromInitialSetup) {
        if (isDefaultHome()) {
            Toast.makeText(this, R.string.home_already_default, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = (RoleManager) getSystemService(ROLE_SERVICE);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME),
                            HOME_ROLE_REQUEST);
                    return;
                } catch (Exception ignored) { /* OEM fallback to Android HOME settings. */ }
            }
        }
        try {
            startActivityForResult(new Intent(Settings.ACTION_HOME_SETTINGS), HOME_ROLE_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, R.string.home_default_desc, Toast.LENGTH_LONG).show();
        }
    }

    private void chooseWallpaper() {
        final boolean hasPersonal = prefs.contains("home_wallpaper");
        final String[] options = hasPersonal
                ? new String[]{getString(R.string.wallpaper_choose_image),
                    getString(R.string.wallpaper_theme_restore),
                    getString(R.string.wallpaper_personal_restore),
                    getString(R.string.wallpaper_theme_system)}
                : new String[]{getString(R.string.wallpaper_choose_image),
                    getString(R.string.wallpaper_theme_restore),
                    getString(R.string.wallpaper_theme_system)};
        new AlertDialog.Builder(this).setTitle(R.string.wallpaper)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: pickPersonalWallpaper(); break;
                        case 1:
                            prefs.edit().putBoolean("use_custom_wallpaper", false).apply();
                            showHome();
                            break;
                        case 2:
                            if (hasPersonal) {
                                prefs.edit().putBoolean("use_custom_wallpaper", true).apply();
                                showHome();
                            } else applyBundledWallpaperToAndroid();
                            break;
                        default: applyBundledWallpaperToAndroid();
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Optional: copy the bundled theme image to Android HOME/LOCK on user request. */
    private void applyBundledWallpaperToAndroid() {
        final int resource = bundledWallpaper();
        final File customWall = (customTheme != null && ThemePack.hasWallpaper(customTheme))
                ? ThemePack.wallpaper(customTheme) : null;
        String[] targets = {getString(R.string.wallpaper_home),
                getString(R.string.wallpaper_lock), getString(R.string.wallpaper_both)};
        new AlertDialog.Builder(this).setTitle(R.string.wallpaper)
                .setItems(targets, (dialog, which) -> {
                    int flags = which == 0 ? WallpaperManager.FLAG_SYSTEM
                            : which == 1 ? WallpaperManager.FLAG_LOCK
                            : WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
                    new Thread(() -> {
                        boolean success = false;
                        try (InputStream stream = customWall != null
                                ? new java.io.FileInputStream(customWall)
                                : getResources().openRawResource(resource)) {
                            WallpaperManager.getInstance(this).setStream(stream, null, false, flags);
                            if ((flags & WallpaperManager.FLAG_SYSTEM) != 0)
                                prefs.edit().putBoolean("use_custom_wallpaper", false).apply();
                            success = true;
                        } catch (Exception ignored) { }
                        final boolean done = success;
                        runOnUiThread(() -> {
                            Toast.makeText(this, done ? R.string.wallpaper_applied
                                    : R.string.wallpaper_failed, Toast.LENGTH_LONG).show();
                            if (done && (flags & WallpaperManager.FLAG_SYSTEM) != 0) showHome();
                        });
                    }, "essora-theme-wallpaper").start();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void pickPersonalWallpaper() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(picker, WALLPAPER_PICKER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == HOME_ROLE_REQUEST) {
            // HOME selection is handled by Android; no screen-lock permissions exist.
            return;
        }

        if (requestCode != WALLPAPER_PICKER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri selected = data.getData();
        try { getContentResolver().takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { }
        String[] options = {getString(R.string.wallpaper_home), getString(R.string.wallpaper_lock), getString(R.string.wallpaper_both)};
        new AlertDialog.Builder(this).setTitle(R.string.wallpaper)
                .setItems(options, (dialog, which) -> {
                    int flags = which == 0 ? WallpaperManager.FLAG_SYSTEM
                            : which == 1 ? WallpaperManager.FLAG_LOCK : WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
                    new Thread(() -> {
                        boolean success = false;
                        try (InputStream in = getContentResolver().openInputStream(selected)) {
                            if (in != null) {
                                WallpaperManager.getInstance(this).setStream(in, null, false, flags);
                                if ((flags & WallpaperManager.FLAG_SYSTEM) != 0)
                                    prefs.edit().putString("home_wallpaper", selected.toString())
                                            .putBoolean("use_custom_wallpaper", true).apply();
                                success = true;
                            }
                        } catch (Exception ignored) { }
                        boolean done = success;
                        runOnUiThread(() -> {
                            Toast.makeText(this, done ? R.string.wallpaper_applied : R.string.wallpaper_failed, Toast.LENGTH_LONG).show();
                            if (done) showHome();
                        });
                    }).start();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
