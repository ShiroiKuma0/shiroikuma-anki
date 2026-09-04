// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.slider.Slider
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Fork: colour and font management for the app UI — the "白い熊 暗記 UI" page.
 *
 * The navigation drawer menu defaults to yellow on black; every colour, the
 * menu font (an external ttf/otf file), the text size and the header image are
 * configurable in
 * [com.ichi2.anki.preferences.ShiroikumaUiSettingsFragment].
 */
object ShiroikumaUi {
    const val DEFAULT_MENU_BACKGROUND = 0xFF000000.toInt()
    const val DEFAULT_MENU_TEXT = 0xFFFFFF00.toInt()
    const val DEFAULT_MENU_ICON = 0xFFFFFF00.toInt()
    const val DEFAULT_MENU_SELECTED = 0xFFFFC107.toInt()
    const val DEFAULT_MENU_SELECTED_BACKGROUND = 0xFF332E00.toInt()
    const val DEFAULT_MENU_FONT_SIZE_SP = 14

    const val DEFAULT_DECK_NAME = 0xFFFFFF00.toInt()
    const val DEFAULT_TOOLBAR_ICON = 0xFFFFFF00.toInt()
    const val DEFAULT_STUDIED_TODAY = 0xFFFFFF00.toInt()
    const val DEFAULT_STUDY_TEXT = 0xFFFFFF00.toInt()
    const val DEFAULT_STUDY_BORDER = 0xFFFFFF00.toInt()
    const val DEFAULT_STUDY_BACKGROUND = 0xFF000000.toInt()
    const val DEFAULT_SETTINGS_TITLE = 0xFFFFFF00.toInt()
    const val DEFAULT_SETTINGS_SUMMARY = 0xFF9E9E9E.toInt()
    const val DEFAULT_SETTINGS_ICON = 0xFFFFFF00.toInt()
    const val DEFAULT_SETTINGS_TOGGLE = 0xFFFFFF00.toInt()
    const val DEFAULT_SETTINGS_SLIDER = 0xFFFFFF00.toInt()
    const val DEFAULT_SETTINGS_HEADER = 0xFFFFFF00.toInt()

    const val DEFAULT_TOOLBAR_TITLE = 0xFFFFFF00.toInt()
    const val DEFAULT_TOOLBAR_SUBTITLE = 0xFF81D4FA.toInt() // light blue
    const val DEFAULT_DECK_DETAIL_NAME = 0xFF000000.toInt()
    const val DEFAULT_PANE_DIVIDER = 0xFFFFFF00.toInt()
    const val DEFAULT_PANE_DIVIDER_WIDTH_DP = 1

    /** 12dp reproduces the upstream 48dp row height; 0 makes the rows touch */
    const val DEFAULT_DECK_ROW_PADDING_DP = 12

    private const val FONT_DIR = "shiroikuma_fonts"

    /** Keyed by role (base family) and by "role:weight" (weight-styled) */
    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun color(
        context: Context,
        @StringRes keyRes: Int,
        default: Int,
    ): Int = context.sharedPrefs().getInt(context.getString(keyRes), default)

    fun setColor(
        context: Context,
        @StringRes keyRes: Int,
        color: Int,
    ) = context.sharedPrefs().edit { putInt(context.getString(keyRes), color) }

    private fun showHeaderImage(context: Context): Boolean =
        context.sharedPrefs().getBoolean(context.getString(R.string.pref_sk_menu_show_header_key), true)

    /**
     * Styles the navigation drawer from the preferences: background, item
     * text/icon colours (with a distinct selected state), item font and text
     * size, and header visibility. Called whenever the drawer is initialized;
     * the defaults apply when nothing was configured yet.
     */
    fun applyToNavigationDrawer(navigationView: NavigationView) {
        val context = navigationView.context

        navigationView.setBackgroundColor(color(context, R.string.pref_sk_menu_background_key, DEFAULT_MENU_BACKGROUND))

        val selected = color(context, R.string.pref_sk_menu_selected_color_key, DEFAULT_MENU_SELECTED)
        val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        navigationView.itemTextColor =
            ColorStateList(checkedStates, intArrayOf(selected, color(context, R.string.pref_sk_menu_text_color_key, DEFAULT_MENU_TEXT)))
        navigationView.itemIconTintList =
            ColorStateList(checkedStates, intArrayOf(selected, color(context, R.string.pref_sk_menu_icon_color_key, DEFAULT_MENU_ICON)))
        navigationView.itemBackground =
            StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_checked),
                    ColorDrawable(color(context, R.string.pref_sk_menu_selected_background_key, DEFAULT_MENU_SELECTED_BACKGROUND)),
                )
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }

        if (navigationView.headerCount > 0) {
            navigationView.getHeaderView(0).isVisible = showHeaderImage(context)
        }

        val typeface = styledTypeface(context, ROLE_MENU)
        val sizeSp = fontSizeSp(context, ROLE_MENU)
        val menu = navigationView.menu
        for (i in 0 until menu.size()) {
            applyItemStyle(menu.getItem(i), typeface, sizeSp)
        }
    }

    private fun applyItemStyle(
        item: MenuItem,
        typeface: Typeface?,
        sizeSp: Int,
    ) {
        // toString() strips the spans of a previous application
        val title = SpannableString(item.title?.toString() ?: return)
        title.setSpan(AbsoluteSizeSpan(sizeSp, true), 0, title.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        if (typeface != null) {
            title.setSpan(TypefaceSpanCompat(typeface), 0, title.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        item.title = title
    }

    /** Tints a toolbar's action icons, overflow and navigation icon (deck picker top bar) */
    fun tintToolbarIcons(
        context: Context,
        menu: Menu,
        toolbar: androidx.appcompat.widget.Toolbar?,
    ) {
        val color = color(context, R.string.pref_sk_toolbar_icon_color_key, DEFAULT_TOOLBAR_ICON)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.setTint(color)
        }
        toolbar?.overflowIcon?.setTint(color)
        toolbar?.navigationIcon?.setTint(color)
    }

    fun toolbarIconColor(context: Context): Int = color(context, R.string.pref_sk_toolbar_icon_color_key, DEFAULT_TOOLBAR_ICON)

    /**
     * Attaches a long-press action to the toolbar's overflow button (the ⋮ at
     * the top right) — the deck picker uses it to open the 白い熊 暗記 UI page.
     * The overflow button is the ImageView child of the toolbar's
     * ActionMenuView; it only exists after the menu is laid out, hence the
     * post. Safe to call repeatedly (menu rebuilds recreate the button).
     */
    fun attachOverflowLongPress(
        toolbar: androidx.appcompat.widget.Toolbar?,
        onLongPress: () -> Unit,
    ) {
        if (toolbar == null) return
        toolbar.post {
            val overflowButton =
                toolbar.children
                    .filterIsInstance<androidx.appcompat.widget.ActionMenuView>()
                    .firstOrNull()
                    ?.children
                    ?.filterIsInstance<ImageView>()
                    ?.lastOrNull() ?: return@post
            overflowButton.setOnLongClickListener {
                onLongPress()
                true
            }
        }
    }

    /** Yellow border and text on black, configurable — the deck study button */
    fun applyStudyButton(button: MaterialButton?) {
        if (button == null) return
        val context = button.context
        button.setTextColor(color(context, R.string.pref_sk_study_text_color_key, DEFAULT_STUDY_TEXT))
        button.backgroundTintList =
            ColorStateList.valueOf(color(context, R.string.pref_sk_study_background_key, DEFAULT_STUDY_BACKGROUND))
        button.strokeColor =
            ColorStateList.valueOf(color(context, R.string.pref_sk_study_border_color_key, DEFAULT_STUDY_BORDER))
        button.strokeWidth =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.resources.displayMetrics).toInt()
    }

    fun deckNameColor(context: Context): Int = color(context, R.string.pref_sk_deck_name_color_key, DEFAULT_DECK_NAME)

    fun deckRowPaddingDp(context: Context): Int =
        context.sharedPrefs().getInt(context.getString(R.string.pref_sk_deck_row_padding_key), DEFAULT_DECK_ROW_PADDING_DP)

    fun studiedTodayColor(context: Context): Int = color(context, R.string.pref_sk_studied_today_color_key, DEFAULT_STUDIED_TODAY)

    fun deckDetailNameColor(context: Context): Int = color(context, R.string.pref_sk_deck_detail_name_color_key, DEFAULT_DECK_DETAIL_NAME)

    /** Toolbar title (app name) and subtitle (cards due) colours */
    fun applyToolbarTitleColors(toolbar: androidx.appcompat.widget.Toolbar?) {
        if (toolbar == null) return
        val context = toolbar.context
        toolbar.setTitleTextColor(color(context, R.string.pref_sk_toolbar_title_color_key, DEFAULT_TOOLBAR_TITLE))
        toolbar.setSubtitleTextColor(color(context, R.string.pref_sk_toolbar_subtitle_color_key, DEFAULT_TOOLBAR_SUBTITLE))
    }

    fun paneDividerWidthDp(context: Context): Int =
        context.sharedPrefs().getInt(context.getString(R.string.pref_sk_pane_divider_width_key), DEFAULT_PANE_DIVIDER_WIDTH_DP)

    /**
     * Styles the deck picker pane divider: configurable colour and width in
     * dp, 0 hiding it entirely (it then no longer works as a drag handle).
     */
    fun applyPaneDivider(divider: View?) {
        if (divider == null) return
        val context = divider.context
        val widthDp = paneDividerWidthDp(context)
        if (widthDp == 0) {
            divider.isVisible = false
            return
        }
        divider.layoutParams =
            divider.layoutParams.apply {
                width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp.toFloat(), context.resources.displayMetrics).toInt()
            }
        val color = color(context, R.string.pref_sk_pane_divider_color_key, DEFAULT_PANE_DIVIDER)
        divider.setBackgroundColor(color)
        divider.findViewById<View>(R.id.divider_handle)?.backgroundTintList = ColorStateList.valueOf(color)
    }

    /**
     * Colours every preference row of a settings list: titles (including
     * category headers), summaries, icons, toggles and sliders, each
     * configurable. Re-applied (change-guarded) on every draw, because
     * preference rebinds — e.g. the header highlight in the split settings
     * view — restore the theme colours after the row was already styled.
     */
    fun styleSettingsList(
        list: RecyclerView,
        context: Context,
    ) {
        val titleColor = color(context, R.string.pref_sk_settings_title_color_key, DEFAULT_SETTINGS_TITLE)
        val summaryColor = color(context, R.string.pref_sk_settings_summary_color_key, DEFAULT_SETTINGS_SUMMARY)
        val iconColor = color(context, R.string.pref_sk_settings_icon_color_key, DEFAULT_SETTINGS_ICON)
        val toggleColor = color(context, R.string.pref_sk_settings_toggle_color_key, DEFAULT_SETTINGS_TOGGLE)
        val sliderColor = color(context, R.string.pref_sk_settings_slider_color_key, DEFAULT_SETTINGS_SLIDER)
        val typeface = styledTypeface(context, ROLE_SETTINGS)
        val titleSizeSp = fontSizeSp(context, ROLE_SETTINGS)
        val titleSizePx =
            if (titleSizeSp > 0) {
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, titleSizeSp.toFloat(), context.resources.displayMetrics)
            } else {
                0f
            }

        fun styleChild(view: View) {
            view.findViewById<TextView>(android.R.id.title)?.let {
                if (it.currentTextColor != titleColor) it.setTextColor(titleColor)
                if (typeface != null && it.typeface !== typeface) it.typeface = typeface
                if (titleSizePx > 0f && kotlin.math.abs(it.textSize - titleSizePx) > 0.5f) {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSizePx)
                }
            }
            view.findViewById<TextView>(android.R.id.summary)?.let {
                if (it.currentTextColor != summaryColor) it.setTextColor(summaryColor)
                if (typeface != null && it.typeface !== typeface) it.typeface = typeface
            }
            // colour preference icons, except our own colour swatches
            view.findViewById<ImageView>(android.R.id.icon)?.let { icon ->
                if (icon.drawable !is GradientDrawable && icon.colorFilter == null) icon.setColorFilter(iconColor)
            }
            (view as? ViewGroup)?.let { tintWidgets(it, toggleColor, sliderColor) }
        }

        list.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) = styleChild(view)

                override fun onChildViewDetachedFromWindow(view: View) {}
            },
        )
        list.viewTreeObserver.addOnPreDrawListener {
            for (child in list.children) styleChild(child)
            true
        }
    }

    private fun tintWidgets(
        root: ViewGroup,
        toggleColor: Int,
        sliderColor: Int,
    ) {
        for (child in root.children) {
            when (child) {
                is SwitchCompat -> {
                    if (child.tag == toggleColor) continue
                    child.tag = toggleColor
                    val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    child.thumbTintList = ColorStateList(checkedStates, intArrayOf(toggleColor, 0xFFBDBDBD.toInt()))
                    child.trackTintList =
                        ColorStateList(
                            checkedStates,
                            intArrayOf((toggleColor and 0x00FFFFFF) or 0x66000000, 0xFF616161.toInt()),
                        )
                }
                is Slider -> {
                    if (child.tag == sliderColor) continue
                    child.tag = sliderColor
                    val csl = ColorStateList.valueOf(sliderColor)
                    child.thumbTintList = csl
                    child.trackActiveTintList = csl
                    child.trackInactiveTintList = ColorStateList.valueOf((sliderColor and 0x00FFFFFF) or 0x4D000000)
                    child.haloTintList = ColorStateList.valueOf((sliderColor and 0x00FFFFFF) or 0x33000000)
                }
                is ViewGroup -> tintWidgets(child, toggleColor, sliderColor)
            }
        }
    }

    fun settingsHeaderColor(context: Context): Int = color(context, R.string.pref_sk_settings_header_color_key, DEFAULT_SETTINGS_HEADER)

    // Font management — three roles, each with an external font file (SAF
    // import), a text size and a weight: the drawer menu, the deck picker
    // list, and the settings screens.

    const val ROLE_MENU = "menu"
    const val ROLE_DECK = "deck"
    const val ROLE_SETTINGS = "settings"

    val FONT_ROLES = listOf(ROLE_MENU, ROLE_DECK, ROLE_SETTINGS)

    @StringRes
    private fun fontNameKey(role: String): Int =
        when (role) {
            ROLE_DECK -> R.string.pref_sk_deck_font_key
            ROLE_SETTINGS -> R.string.pref_sk_settings_font_key
            else -> R.string.pref_sk_menu_font_key
        }

    @StringRes
    private fun fontSizeKey(role: String): Int =
        when (role) {
            ROLE_DECK -> R.string.pref_sk_deck_font_size_key
            ROLE_SETTINGS -> R.string.pref_sk_settings_font_size_key
            else -> R.string.pref_sk_menu_font_size_key
        }

    @StringRes
    private fun fontWeightKey(role: String): Int =
        when (role) {
            ROLE_DECK -> R.string.pref_sk_deck_font_weight_key
            ROLE_SETTINGS -> R.string.pref_sk_settings_font_weight_key
            else -> R.string.pref_sk_menu_font_weight_key
        }

    /** 0 means "keep the default size", except for the menu, whose spans always need a concrete size */
    fun fontSizeSp(
        context: Context,
        role: String,
    ): Int =
        context.sharedPrefs().getInt(
            context.getString(fontSizeKey(role)),
            if (role == ROLE_MENU) DEFAULT_MENU_FONT_SIZE_SP else 0,
        )

    /** 0 means "natural weight" */
    fun fontWeight(
        context: Context,
        role: String,
    ): Int = context.sharedPrefs().getInt(context.getString(fontWeightKey(role)), 0)

    fun fontName(
        context: Context,
        role: String,
    ): String? = context.sharedPrefs().getString(context.getString(fontNameKey(role)), null)

    fun fontFile(
        context: Context,
        role: String,
    ): File = File(fontsDir(context), "${role}_font")

    fun fontsDir(context: Context): File = File(context.filesDir, FONT_DIR)

    /** Drops every cached typeface, e.g. after font files were replaced by a settings import. */
    fun invalidateAllFontCaches() = typefaceCache.clear()

    private fun baseTypeface(
        context: Context,
        role: String,
    ): Typeface? {
        typefaceCache[role]?.let { return it }
        val file = fontFile(context, role)
        if (!file.exists()) return null
        return try {
            Typeface.createFromFile(file).also { typefaceCache[role] = it }
        } catch (e: Exception) {
            Timber.w(e, "failed to load the %s font", role)
            null
        }
    }

    /**
     * The role's font family combined with its weight; `null` means "leave
     * the system default". Cached so callers can identity-compare.
     */
    fun styledTypeface(
        context: Context,
        role: String,
        weight: Int = fontWeight(context, role),
    ): Typeface? {
        val base = baseTypeface(context, role)
        if (weight == 0) return base
        val cacheKey = "$role:$weight"
        typefaceCache[cacheKey]?.let { return it }
        val baseOrDefault = base ?: Typeface.DEFAULT
        val styled =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(baseOrDefault, weight, false)
            } else if (weight >= 600) {
                Typeface.create(baseOrDefault, Typeface.BOLD)
            } else {
                baseOrDefault
            }
        typefaceCache[cacheKey] = styled
        return styled
    }

    private fun invalidateFontCache(role: String) {
        typefaceCache.keys.removeAll { it == role || it.startsWith("$role:") }
    }

    /**
     * Copies the picked font into internal storage and validates it loads.
     * @return whether the import succeeded
     */
    fun importFont(
        context: Context,
        uri: Uri,
        displayName: String,
        role: String,
    ): Boolean {
        val target = fontFile(context, role)
        try {
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            Typeface.createFromFile(target) // throws if the file is not a usable font
        } catch (e: Exception) {
            Timber.w(e, "failed to import font")
            target.delete()
            invalidateFontCache(role)
            return false
        }
        invalidateFontCache(role)
        context.sharedPrefs().edit { putString(context.getString(fontNameKey(role)), displayName) }
        return true
    }

    fun resetFont(
        context: Context,
        role: String,
    ) {
        fontFile(context, role).delete()
        invalidateFontCache(role)
        context.sharedPrefs().edit { remove(context.getString(fontNameKey(role))) }
    }

    /** Sample line rendering a role's font, size, weight and text colour */
    fun buildFontPreview(
        context: Context,
        role: String,
        sizeSp: Int = fontSizeSp(context, role),
        weight: Int = fontWeight(context, role),
    ): CharSequence {
        val text = SpannableString(context.getString(R.string.sk_menu_font_preview_text))
        text.setSpan(AbsoluteSizeSpan(if (sizeSp > 0) sizeSp else 16, true), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        val previewColor =
            when (role) {
                ROLE_DECK -> deckNameColor(context)
                ROLE_SETTINGS -> color(context, R.string.pref_sk_settings_title_color_key, DEFAULT_SETTINGS_TITLE)
                else -> color(context, R.string.pref_sk_menu_text_color_key, DEFAULT_MENU_TEXT)
            }
        text.setSpan(ForegroundColorSpan(previewColor), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        styledTypeface(context, role, weight)?.let {
            text.setSpan(TypefaceSpanCompat(it), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        return text
    }

    fun resetAll(context: Context) {
        for (role in FONT_ROLES) resetFont(context, role)
        context.sharedPrefs().edit {
            for (keyRes in listOf(
                R.string.pref_sk_menu_background_key,
                R.string.pref_sk_menu_text_color_key,
                R.string.pref_sk_menu_icon_color_key,
                R.string.pref_sk_menu_selected_color_key,
                R.string.pref_sk_menu_selected_background_key,
                R.string.pref_sk_menu_font_size_key,
                R.string.pref_sk_menu_font_weight_key,
                R.string.pref_sk_deck_font_size_key,
                R.string.pref_sk_deck_font_weight_key,
                R.string.pref_sk_settings_font_size_key,
                R.string.pref_sk_settings_font_weight_key,
                R.string.pref_sk_menu_show_header_key,
                R.string.pref_sk_deck_name_color_key,
                R.string.pref_sk_toolbar_icon_color_key,
                R.string.pref_sk_studied_today_color_key,
                R.string.pref_sk_study_text_color_key,
                R.string.pref_sk_study_border_color_key,
                R.string.pref_sk_study_background_key,
                R.string.pref_sk_settings_title_color_key,
                R.string.pref_sk_settings_summary_color_key,
                R.string.pref_sk_settings_icon_color_key,
                R.string.pref_sk_settings_toggle_color_key,
                R.string.pref_sk_settings_slider_color_key,
                R.string.pref_sk_settings_header_color_key,
                R.string.pref_sk_toolbar_title_color_key,
                R.string.pref_sk_toolbar_subtitle_color_key,
                R.string.pref_sk_deck_detail_name_color_key,
                R.string.pref_sk_pane_divider_color_key,
                R.string.pref_sk_pane_divider_width_key,
                R.string.pref_sk_deck_row_padding_key,
            )) {
                remove(context.getString(keyRes))
            }
        }
    }

    // Settings backup (export / import)
    //
    // Round-trips the whole default SharedPreferences — our sk_* colours/fonts
    // AND all Anki settings, including the controls (each binding is a plain
    // String pref keyed binding_<COMMAND> / previewer_<ACTION>). Values are
    // type-tagged because JSON cannot tell Int from Long, nor a Set from a
    // List, and the app does store Set<String> prefs (e.g. note_editor_custom_buttons).

    private const val BACKUP_FORMAT = "shiroikuma-anki-settings"
    private const val BACKUP_VERSION = 1

    /**
     * Keys never exported/imported: the device-specific collection path and
     * sync credentials (mirrors AcraCrashReporter's never-share list). Importing
     * a foreign deckPath could point the app at a missing collection.
     */
    private val BACKUP_BLOCKLIST = setOf("deckPath", "hkey", "username", "currentSyncUri", "browser_search_history")

    /**
     * Serializes the default SharedPreferences (minus credentials) to a type-tagged JSON string.
     * [keyFilter] restricts the output to a subset of keys (a category of the zip export).
     */
    fun exportSettingsJson(
        context: Context,
        keyFilter: (String) -> Boolean = { true },
    ): String {
        val entries = JSONObject()
        for ((key, value) in context.sharedPrefs().all) {
            if (key in BACKUP_BLOCKLIST || value == null || !keyFilter(key)) continue
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "boolean").put("v", value)
                is Int -> entry.put("t", "int").put("v", value)
                is Long -> entry.put("t", "long").put("v", value)
                is Float -> entry.put("t", "float").put("v", value.toDouble())
                is String -> entry.put("t", "string").put("v", value)
                is Set<*> -> entry.put("t", "stringSet").put("v", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            entries.put(key, entry)
        }
        return JSONObject()
            .put("_format", BACKUP_FORMAT)
            .put("_version", BACKUP_VERSION)
            .put("entries", entries)
            .toString(2)
    }

    /**
     * Applies a previously exported settings JSON onto the default
     * SharedPreferences (merge: only the keys present in the file are written).
     *
     * @return the number of keys applied
     * @throws org.json.JSONException malformed JSON
     * @throws IllegalArgumentException the file is not a 白い熊 暗記 settings export
     */
    fun importSettingsJson(
        context: Context,
        json: String,
    ): Int {
        val root = JSONObject(json)
        require(root.optString("_format") == BACKUP_FORMAT) { "not a 白い熊 暗記 settings file" }
        val entries = root.getJSONObject("entries")
        var applied = 0
        // commit, not apply: 応用管理 force-stops this app the instant the data
        // door replies OK to an import — a SIGKILL, which an apply() in flight
        // does not survive. A restore that reports success and restored nothing
        // is the one failure worse than a restore that fails.
        context.sharedPrefs().edit(commit = true) {
            for (key in entries.keys()) {
                if (key in BACKUP_BLOCKLIST) continue
                val entry = entries.getJSONObject(key)
                val matched =
                    when (entry.getString("t")) {
                        "boolean" -> {
                            putBoolean(key, entry.getBoolean("v"))
                            true
                        }
                        "int" -> {
                            putInt(key, entry.getInt("v"))
                            true
                        }
                        "long" -> {
                            putLong(key, entry.getLong("v"))
                            true
                        }
                        "float" -> {
                            putFloat(key, entry.getDouble("v").toFloat())
                            true
                        }
                        "string" -> {
                            putString(key, entry.getString("v"))
                            true
                        }
                        "stringSet" -> {
                            val arr = entry.getJSONArray("v")
                            putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                            true
                        }
                        else -> false
                    }
                if (matched) applied++
            }
        }
        return applied
    }

    // Helpers for the settings page

    /** A circular colour swatch with a grey outline, used as a preference icon */
    fun swatch(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, 0xFF888888.toInt())
            setSize(56, 56)
        }

    fun toHex(color: Int): String = String.format("#%08X", color)

    /** [android.text.style.TypefaceSpan] with a custom [Typeface] needs API 28; this works on every API */
    private class TypefaceSpanCompat(
        private val typeface: Typeface,
    ) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) = apply(tp)

        override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)

        private fun apply(paint: Paint) {
            paint.typeface = typeface
        }
    }
}
