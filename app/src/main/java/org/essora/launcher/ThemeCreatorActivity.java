package org.essora.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Create, edit, import and export lightweight GPLv3 Essora theme archives. */
public final class ThemeCreatorActivity extends Activity {
    private static final int PICK_THEME = 301;
    private static final int SAVE_THEME = 302;
    private static final int PICK_WALLPAPER = 303;
    private static final int PICK_ICON = 304;
    private static final int PICK_ICON_PACK = 305;
    private static final int SURFACE = 0xff101512;
    private static final int PANEL = 0xff1b2420;
    private static final int WHITE = 0xfff2f5ee;
    private static final int GREEN = 0xff8ed163;
    private ThemePack.Theme draft;
    private ThemePack.Theme exportPending;
    private String iconPending;
    private int screen = 0; // 0 list, 1 editor, 2 icon selection

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String tr(int id) { return getString(id); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff0b100d);
        getWindow().setNavigationBarColor(0xff0b100d);
        showManager();
        if (getIntent().getBooleanExtra("open_local_theme_picker", false)) {
            getIntent().removeExtra("open_local_theme_picker");
            getWindow().getDecorView().post(() -> pickFile(PICK_THEME, "*/*"));
        }
    }

    @Override public void onBackPressed() {
        if (screen == 2) showEditor();
        else if (screen == 1) confirmLeave();
        else finish();
    }

    private GradientDrawable rounded(int fill, int outline) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(14));
        shape.setStroke(dp(1), outline);
        return shape;
    }

    private TextView text(String text, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(WHITE);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMaxLines(4);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }
    private LinearLayout horizontal() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.HORIZONTAL);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    private LinearLayout page(String title, Runnable back) {
        LinearLayout root = vertical();
        root.setBackgroundColor(SURFACE);
        root.setPadding(dp(16), dp(12), dp(16), dp(14));
        LinearLayout bar = horizontal();
        TextView arrow = text("‹", 33, false);
        arrow.setGravity(Gravity.CENTER);
        bar.addView(arrow, new LinearLayout.LayoutParams(dp(42), dp(49)));
        arrow.setOnClickListener(v -> back.run());
        TextView heading = text(title, 22, true);
        heading.setPadding(dp(5), 0, 0, 0);
        bar.addView(heading, new LinearLayout.LayoutParams(0, dp(51), 1));
        root.addView(bar);
        setContentView(root);
        return root;
    }

    private LinearLayout scroller(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = vertical();
        content.setPadding(dp(1), dp(8), dp(1), dp(26));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return content;
    }

    private void section(LinearLayout parent, String label) {
        TextView title = text(label, 16, true);
        title.setPadding(dp(4), dp(17), 0, dp(9));
        parent.addView(title);
    }

    private void info(LinearLayout parent, String value) {
        TextView note = text(value, 12.5f, false);
        note.setTextColor(0xffc5d3c7);
        note.setPadding(dp(5), dp(6), dp(6), dp(12));
        parent.addView(note);
    }

    private void action(LinearLayout parent, String title, String subtitle, Runnable listener) {
        LinearLayout outer = vertical();
        outer.setBackground(rounded(PANEL, 0xff526856));
        outer.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView label = text(title, 16, true);
        outer.addView(label);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = text(subtitle, 12.5f, false);
            detail.setTextColor(0xffc9d6cb);
            detail.setPadding(0, dp(3), 0, 0);
            outer.addView(detail);
        }
        LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(-1, -2);
        param.bottomMargin = dp(9);
        parent.addView(outer, param);
        outer.setOnClickListener(v -> listener.run());
    }

    private void showManager() {
        screen = 0;
        draft = null;
        LinearLayout root = page(tr(R.string.creator_title), this::finish);
        LinearLayout list = scroller(root);
        info(list, tr(R.string.creator_intro));
        action(list, tr(R.string.creator_new), tr(R.string.creator_new_desc), () -> {
            try { draft = ThemePack.startNew(this); showEditor(); }
            catch (Exception e) { error(e); }
        });
        action(list, tr(R.string.creator_install_local), tr(R.string.creator_install_local_desc),
                () -> pickFile(PICK_THEME, "*/*"));
        section(list, tr(R.string.creator_my_themes));
        List<ThemePack.Theme> installed = ThemePack.list(this);
        if (installed.isEmpty()) info(list, tr(R.string.creator_empty));
        for (ThemePack.Theme t : installed) showThemeCard(list, t);
        section(list, tr(R.string.creator_license_title));
        info(list, tr(R.string.creator_license_note));
    }

    private void showThemeCard(LinearLayout parent, ThemePack.Theme theme) {
        LinearLayout item = horizontal();
        item.setBackground(rounded(PANEL, 0xff566e5a));
        item.setPadding(dp(10), dp(9), dp(11), dp(9));
        ImageView image = new ImageView(this);
        Bitmap bmp = ThemePack.decode(ThemePack.preview(theme), 118, 65);
        if (bmp == null) bmp = ThemePack.decode(ThemePack.wallpaper(theme), 118, 65);
        if (bmp != null) image.setImageBitmap(bmp);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(rounded(bmp == null ? theme.border : 0xff171b17, 0xff8c9a84));
        image.setClipToOutline(true);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(dp(105), dp(66));
        imgParams.rightMargin = dp(12);
        item.addView(image, imgParams);
        LinearLayout labels = vertical();
        labels.addView(text(theme.name, 16, true));
        TextView author = text(tr(R.string.creator_author) + ": " + theme.author, 12, false);
        author.setTextColor(0xffc9d6cb);
        labels.addView(author);
        TextView license = text("GNU GPL-3.0", 11, false);
        license.setTextColor(GREEN);
        labels.addView(license);
        item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(9);
        parent.addView(item, params);
        item.setOnClickListener(v -> themeOptions(theme));
    }

    private void themeOptions(ThemePack.Theme theme) {
        String[] labels = {tr(R.string.creator_apply), tr(R.string.creator_edit),
                tr(R.string.creator_duplicate), tr(R.string.creator_export),
                tr(R.string.creator_share), tr(R.string.creator_credits), tr(R.string.creator_delete)};
        new AlertDialog.Builder(this).setTitle(theme.name).setItems(labels, (dialog, which) -> {
            switch (which) {
                case 0:
                    SharedPreferences.Editor applyEdit = getSharedPreferences("essora-launcher", MODE_PRIVATE).edit()
                            .putString("custom_theme_id", theme.id);
                    if (ThemePack.hasWallpaper(theme)) {
                        applyEdit.putBoolean("use_custom_wallpaper", false);
                    }
                    applyEdit.apply();
                    Toast.makeText(this, R.string.creator_applied, Toast.LENGTH_SHORT).show();
                    finish();
                    break;
                case 1:
                    try { draft = ThemePack.edit(this, theme); showEditor(); }
                    catch (Exception e) { error(e); }
                    break;
                case 2:
                    try { draft = ThemePack.duplicate(this, theme); showEditor(); }
                    catch (Exception e) { error(e); }
                    break;
                case 3: askExport(theme); break;
                case 4: askShare(theme); break;
                case 5: new AlertDialog.Builder(this).setTitle(tr(R.string.creator_credits))
                        .setMessage(ThemePack.credits(theme))
                        .setPositiveButton(android.R.string.ok, null).show(); break;
                case 6: new AlertDialog.Builder(this).setTitle(tr(R.string.creator_delete))
                        .setMessage(theme.name)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.creator_delete, (dlg, button) -> {
                            if (theme.id.equals(getSharedPreferences("essora-launcher", MODE_PRIVATE)
                                    .getString("custom_theme_id", ""))) {
                                getSharedPreferences("essora-launcher", MODE_PRIVATE).edit()
                                        .remove("custom_theme_id").apply();
                            }
                            ThemePack.delete(this, theme);
                            showManager();
                        }).show(); break;
            }
        }).show();
    }

    private void showEditor() {
        if (draft == null) { showManager(); return; }
        screen = 1;
        LinearLayout root = page(tr(R.string.creator_editor), this::confirmLeave);
        LinearLayout list = scroller(root);
        info(list, tr(R.string.creator_license_note));
        section(list, tr(R.string.creator_preview));
        preview(list);
        section(list, tr(R.string.creator_details));
        field(list, tr(R.string.creator_name), draft.name, 75, v -> draft.name = v, true);
        boolean isOriginal = draft.originalAuthor.isEmpty();
        field(list, tr(R.string.creator_author), draft.author, 75, v -> draft.author = v, isOriginal);
        if (!draft.originalAuthor.isEmpty())
            info(list, tr(R.string.creator_original_author) + ": " + draft.originalAuthor);
        field(list, tr(R.string.creator_modified_by), draft.modifiedBy, 75, v -> draft.modifiedBy = v, true);
        field(list, tr(R.string.creator_version), draft.version, 30, v -> draft.version = v, true);
        field(list, tr(R.string.creator_description), draft.description, 400, v -> draft.description = v, true);
        field(list, tr(R.string.creator_website), draft.website, 200, v -> draft.website = v, true);
        field(list, tr(R.string.creator_wallpaper_credit), draft.wallpaperCredit, 160, v -> draft.wallpaperCredit = v, true);
        field(list, tr(R.string.creator_icon_credit), draft.iconsCredit, 160, v -> draft.iconsCredit = v, true);
        section(list, tr(R.string.creator_visual));
        boolean hasOwnWall = ThemePack.hasWallpaper(draft);
        String wallSubtitle = tr(R.string.creator_wallpaper_desc) + " · "
                + (hasOwnWall ? "Fondo propio cargado en este tema" : "Sin fondo propio (opcional)");
        action(list, tr(R.string.creator_wallpaper), wallSubtitle, this::chooseThemeWallpaperSource);
        colorRow(list, R.string.creator_border, draft.border, color -> draft.border = color);
        colorRow(list, R.string.creator_bubble, draft.background, color -> draft.background = color);
        colorRow(list, R.string.creator_dock, draft.dock, color -> draft.dock = color);
        colorRow(list, R.string.creator_text, draft.text, color -> draft.text = color);
        colorRow(list, R.string.creator_battery, draft.battery, color -> draft.battery = color);
        colorRow(list, R.string.creator_battery_outline, draft.batteryOutline, color -> draft.batteryOutline = color);
        colorRow(list, R.string.creator_accent, draft.accent, color -> draft.accent = color);
        section(list, tr(R.string.creator_icons));
        action(list, tr(R.string.icon_pack_title), selectedDraftIconPackLabel(), this::chooseInstalledIconPack);
        action(list, tr(R.string.creator_choose_icons), tr(R.string.creator_choose_icons_desc), this::showIconPicker);
        action(list, tr(R.string.creator_import_icons), tr(R.string.creator_import_icons_desc),
                () -> pickFile(PICK_ICON_PACK, "*/*"));
        section(list, tr(R.string.creator_save));
        action(list, tr(R.string.creator_save), tr(R.string.creator_save_desc), this::saveDraft);
    }

    private void preview(LinearLayout parent) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(rounded(0xff241c16, 0xff839379));
        ImageView image = new ImageView(this);
        Bitmap wallpaper = ThemePack.decode(ThemePack.wallpaper(draft), 600, 500);
        if (wallpaper != null) {
            image.setImageBitmap(wallpaper);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        }
        LinearLayout overlay = vertical();
        overlay.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView clock = text("10:15", 26, true);
        clock.setTextColor(draft.text);
        overlay.addView(clock);
        TextView date = text("14 · 11 · 2026", 12, false);
        date.setTextColor(draft.text);
        overlay.addView(date);
        TextView bubble = text("  ◉  " + tr(R.string.creator_sample_app), 16, true);
        bubble.setTextColor(draft.text);
        bubble.setBackground(rounded(draft.background, draft.border));
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(dp(200), dp(43));
        bubbleParams.topMargin = dp(35);
        overlay.addView(bubble, bubbleParams);
        LinearLayout battery = horizontal();
        TextView fill = text("   75%   ", 15, true);
        fill.setTextColor(draft.text);
        fill.setBackground(rounded(draft.battery, draft.batteryOutline));
        battery.addView(fill);
        LinearLayout.LayoutParams batteryParams = new LinearLayout.LayoutParams(-2, dp(38));
        batteryParams.topMargin = dp(13);
        overlay.addView(battery, batteryParams);
        frame.addView(overlay);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(240));
        params.bottomMargin = dp(6);
        parent.addView(frame, params);
    }

    private void field(LinearLayout parent, String name, String value, int max, Consumer<String> change, boolean enabled) {
        TextView label = text(name, 13, true);
        label.setPadding(dp(4), dp(7), 0, dp(3));
        parent.addView(label);
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextColor(WHITE);
        input.setHintTextColor(0xffabb9ae);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(5), dp(12), dp(5));
        input.setBackground(rounded(0xff1a2820, 0xff5b7660));
        input.setEnabled(enabled);
        if (!enabled) input.setAlpha(0.6f);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(45));
        params.bottomMargin = dp(4);
        parent.addView(input, params);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { change.accept(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void colorRow(LinearLayout parent, int label, int color, Consumer<Integer> changed) {
        LinearLayout row = horizontal();
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(rounded(PANEL, 0xff5c7561));
        TextView title = text(tr(label), 15, true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        View sample = new View(this);
        sample.setBackground(rounded(color, 0xffd4e5d7));
        row.addView(sample, new LinearLayout.LayoutParams(dp(48), dp(29)));
        TextView value = text(String.format(Locale.ROOT, " #%08X", color), 11, false);
        row.addView(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(7);
        parent.addView(row, params);
        row.setOnClickListener(v -> pickColor(tr(label), color, newColor -> {
            changed.accept(newColor);
            showEditor();
        }));
    }

    private void pickColor(String label, int existing, Consumer<Integer> done) {
        final int[] palette = {0xfff8f7f1, 0xff111111, 0xff86c744, 0xffff67b1, 0xffffd441,
                0xff4ab7e9, 0xffba80ff, 0xffd49a5a, 0xff75452f, 0xffc7d4d0,
                0xcc15100c, 0x99151515};
        LinearLayout content = vertical();
        content.setPadding(dp(12), dp(12), dp(12), 0);
        final AlertDialog[] pickerDialog = new AlertDialog[1];
        LinearLayout paletteRows = vertical();
        for (int start = 0; start < palette.length; start += 4) {
            LinearLayout row = horizontal();
            for (int i = start; i < start + 4; i++) {
                final int c = palette[i];
                TextView swatch = text("●", 30, false);
                swatch.setTextColor(c);
                swatch.setGravity(Gravity.CENTER);
                row.addView(swatch, new LinearLayout.LayoutParams(0, dp(49), 1));
                swatch.setOnClickListener(v -> {
                    if (pickerDialog[0] != null) pickerDialog[0].dismiss();
                    done.accept(c);
                });
            }
            paletteRows.addView(row);
        }
        content.addView(paletteRows);
        EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setText(String.format(Locale.ROOT, "#%08X", existing));
        hex.setTextColor(WHITE);
        hex.setHint("#AARRGGBB");
        content.addView(hex);
        pickerDialog[0] = new AlertDialog.Builder(this).setTitle(label).setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try { done.accept(Color.parseColor(hex.getText().toString().trim())); }
                    catch (Exception error) { Toast.makeText(this, R.string.creator_invalid_color, Toast.LENGTH_SHORT).show(); }
                }).show();
    }

    private void saveDraft() {
        try {
            if (draft == null) return;
            if (draft.name.trim().isEmpty() || draft.author.trim().isEmpty()) {
                Toast.makeText(this, R.string.creator_required, Toast.LENGTH_LONG).show();
                return;
            }
            if (!draft.originalAuthor.isEmpty() && draft.modifiedBy.trim().isEmpty()) {
                Toast.makeText(this, R.string.creator_modifier_required, Toast.LENGTH_LONG).show();
                return;
            }
            ThemePack.save(this, draft);
            Toast.makeText(this, R.string.creator_saved, Toast.LENGTH_SHORT).show();
            showManager();
        } catch (Exception error) { error(error); }
    }

    private void confirmLeave() {
        new AlertDialog.Builder(this).setMessage(R.string.creator_unsaved)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.creator_discard, (dialog, which) -> {
                    if (draft != null && draft.directory.getParentFile().equals(getCacheDir()))
                        ThemePack.deleteTree(draft.directory);
                    draft = null;
                    showManager();
                }).show();
    }

    private void showIconPicker() {
        if (draft == null) return;
        screen = 2;
        LinearLayout root = page(tr(R.string.creator_choose_icons), this::showEditor);
        LinearLayout list = scroller(root);
        info(list, tr(R.string.creator_icon_help));
        // Installed APK icon packs are separate from launchable applications.
        action(list, tr(R.string.icon_pack_title), selectedDraftIconPackLabel(), this::chooseInstalledIconPack);
        action(list, tr(R.string.creator_import_icons), tr(R.string.creator_import_icons_desc),
                () -> pickFile(PICK_ICON_PACK, "*/*"));
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(query, 0);
        java.util.Collections.sort(apps, (a,b) -> String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        for (ResolveInfo app : apps) {
            if (app.activityInfo == null || getPackageName().equals(app.activityInfo.packageName)) continue;
            String key = new ComponentName(app.activityInfo.packageName, app.activityInfo.name).flattenToString();
            String label = String.valueOf(app.loadLabel(pm));
            action(list, label, draft.icons.containsKey(key) || draft.icons.containsKey(app.activityInfo.packageName) ?
                    tr(R.string.creator_icon_custom) : (draft.iconPackPackage.isEmpty() ?
                    tr(R.string.creator_icon_original) : tr(R.string.icon_pack_assigned)),
                    () -> iconOptions(key, label));
        }
    }

    private String selectedDraftIconPackLabel() {
        if (draft == null || draft.iconPackPackage.isEmpty()) return tr(R.string.icon_pack_from_global);
        for (InstalledIconPacks.Pack pack : InstalledIconPacks.discover(this))
            if (pack.pkg.equals(draft.iconPackPackage)) return pack.label;
        return draft.iconPackPackage;
    }

    private void chooseInstalledIconPack() {
        if (draft == null) return;
        List<InstalledIconPacks.Pack> found = InstalledIconPacks.discover(this);
        if (found.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(R.string.icon_pack_title)
                    .setMessage(R.string.icon_pack_none_found)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        String[] choices = new String[found.size() + 1];
        choices[0] = tr(R.string.icon_pack_from_global);
        int selected = 0;
        for (int i = 0; i < found.size(); i++) {
            choices[i + 1] = found.get(i).label;
            if (found.get(i).pkg.equals(draft.iconPackPackage)) selected = i + 1;
        }
        new AlertDialog.Builder(this).setTitle(R.string.icon_pack_title)
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    draft.iconPackPackage = which == 0 ? "" : found.get(which - 1).pkg;
                    if (which > 0 && draft.iconsCredit.trim().isEmpty()) {
                        InstalledIconPacks.Pack pack = found.get(which - 1);
                        draft.iconsCredit = pack.pkg.startsWith("com.donnnno.arcticons")
                                ? "Arcticons — CC BY-SA 4.0" : pack.label;
                    }
                    dialog.dismiss();
                    if (screen == 2) showIconPicker(); else showEditor();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void iconOptions(String component, String label) {
        new AlertDialog.Builder(this).setTitle(label)
                .setItems(new String[]{tr(R.string.creator_icon_image), tr(R.string.creator_icon_restore)}, (dlg, choice) -> {
                    if (choice == 0) { iconPending = component; pickFile(PICK_ICON, "image/*"); }
                    else { ThemePack.removeIcon(draft, component); showIconPicker(); }
                }).show();
    }

    private void askExport(ThemePack.Theme theme) {
        new AlertDialog.Builder(this).setTitle(R.string.creator_license_title)
                .setMessage(R.string.creator_export_rights)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.creator_export, (dialog, which) -> {
                    exportPending = theme;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_TITLE, safeFilename(theme.name) + ".essoratheme");
                    startActivityForResult(intent, SAVE_THEME);
                }).show();
    }

    /** Show the native Android share sheet so installed messaging apps can receive a ZIP document. */
    private void askShare(ThemePack.Theme theme) {
        new AlertDialog.Builder(this).setTitle(R.string.creator_license_title)
                .setMessage(R.string.creator_export_rights)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.creator_share, (dialog, which) -> shareTheme(theme)).show();
    }

    private void shareTheme(ThemePack.Theme theme) {
        Toast.makeText(this, R.string.creator_share_preparing, Toast.LENGTH_SHORT).show();
        // ZIP creation happens off the main thread to avoid freezing the launcher on large themes.
        new Thread(() -> {
            try {
                File archive = ThemePack.exportForSharing(getApplicationContext(), theme);
                Uri uri = ThemeShareProvider.uriForFile(getApplicationContext(), archive);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(ThemeShareProvider.MIME);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.putExtra(Intent.EXTRA_SUBJECT, theme.name);
                    send.setClipData(ClipData.newRawUri(theme.name, uri));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(send, getString(R.string.creator_share_chooser));
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(chooser); }
                    catch (Exception problem) { error(problem); }
                });
            } catch (Exception problem) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) error(problem); });
            }
        }, "essora-share-theme").start();
    }

    private String safeFilename(String source) {
        String cleaned = source.replaceAll("[^a-zA-Z0-9_-]+", "-");
        return cleaned.isEmpty() ? "Essora-Theme" : cleaned;
    }

    private void chooseThemeWallpaperSource() {
        if (draft == null) return;
        boolean hasOwn = ThemePack.hasWallpaper(draft);
        String[] options = hasOwn
                ? new String[]{
                    tr(R.string.wallpaper_choose_image) + " (Galería / Archivos)",
                    "Elegir de la galería integrada de Essora",
                    "Quitar fondo propio de este tema"
                }
                : new String[]{
                    tr(R.string.wallpaper_choose_image) + " (Galería / Archivos)",
                    "Elegir de la galería integrada de Essora"
                };
        new AlertDialog.Builder(this)
                .setTitle(R.string.creator_wallpaper)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickImageFile(PICK_WALLPAPER);
                    } else if (which == 1) {
                        chooseBundledWallpaperForTheme();
                    } else if (which == 2) {
                        ThemePack.clearWallpaper(draft);
                        showEditor();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void chooseBundledWallpaperForTheme() {
        if (draft == null) return;
        final String[] names = {
            "Essora Original",
            "Essora Color",
            "Essora Black",
            "Essora Green",
            "Essora Pink",
            "Essora Yellow",
            "Essora Hex",
            "Essora Peaks",
            "Essora Sunset",
            "Essora Zen",
            "Essora Autumn",
            "Essora Coffee"
        };
        final int[] resources = {
            R.drawable.wallpaper_essora,
            R.drawable.wallpaper_color,
            R.drawable.wallpaper_black,
            R.drawable.wallpaper_green,
            R.drawable.wallpaper_pink,
            R.drawable.wallpaper_yellow,
            R.drawable.wallpaper_hex,
            R.drawable.wallpaper_peaks,
            R.drawable.wallpaper_sunset,
            R.drawable.wallpaper_zen,
            R.drawable.wallpaper_autumn,
            R.drawable.wallpaper_coffee
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.creator_wallpaper)
                .setItems(names, (dialog, which) -> {
                    try {
                        ThemePack.setWallpaperFromResource(this, draft, resources[which]);
                        if (draft.wallpaperCredit.trim().isEmpty()) {
                            draft.wallpaperCredit = "Essora (" + names[which] + ")";
                        }
                        showEditor();
                    } catch (Exception e) {
                        error(e);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickImageFile(int code) {
        Intent getContent = new Intent(Intent.ACTION_GET_CONTENT);
        getContent.addCategory(Intent.CATEGORY_OPENABLE);
        getContent.setType("image/*");
        getContent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(getContent, tr(R.string.creator_wallpaper)), code);
        } catch (Exception fallback) {
            pickFile(code, "image/*");
        }
    }

    private void pickFile(int code, String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, code);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == PICK_THEME) {
                ThemePack.Theme imported = ThemePack.importTheme(this, uri);
                new AlertDialog.Builder(this).setTitle(tr(R.string.creator_install))
                        .setMessage(imported.name + "\n" + tr(R.string.creator_author) + ": " + imported.author +
                                "\n" + "GNU GPL-3.0" + "\n" + imported.description +
                                "\n" + tr(R.string.creator_original_author) + ": " +
                                (imported.originalAuthor.isEmpty() ? imported.author : imported.originalAuthor))
                        .setNegativeButton(android.R.string.cancel, (dlg, which) -> ThemePack.deleteTree(imported.directory))
                        .setPositiveButton(R.string.creator_install, (dlg, which) -> {
                            try {
                                ThemePack.save(this, imported);
                                Toast.makeText(this, R.string.creator_installed, Toast.LENGTH_SHORT).show();
                                showManager();
                            } catch (Exception error) { error(error); }
                        })
                        .setOnCancelListener(dlg -> ThemePack.deleteTree(imported.directory)).show();
            } else if (requestCode == SAVE_THEME && exportPending != null) {
                ThemePack.exportTheme(this, exportPending, uri);
                Toast.makeText(this, R.string.creator_exported, Toast.LENGTH_LONG).show();
                exportPending = null;
            } else if (requestCode == PICK_WALLPAPER && draft != null) {
                ThemePack.setWallpaper(this, draft, uri);
                showEditor();
            } else if (requestCode == PICK_ICON && draft != null && iconPending != null) {
                ThemePack.setIcon(this, draft, iconPending, uri);
                showIconPicker();
            } else if (requestCode == PICK_ICON_PACK && draft != null) {
                String result = ThemePack.importIconPack(this, draft, uri);
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                showIconPicker();
            }
        } catch (Exception error) { error(error); }
    }

    private void error(Exception exception) {
        new AlertDialog.Builder(this).setTitle(R.string.creator_error)
                .setMessage(exception.getMessage() == null ? exception.toString() : exception.getMessage())
                .setPositiveButton(android.R.string.ok, null).show();
    }
}
