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

    private const val FONT_DIR = "shiroikuma_fonts"
    private const val MENU_FONT_FILE = "menu_font"

    private var cachedMenuTypeface: Typeface? = null

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

    fun menuFontSizeSp(context: Context): Int =
        context.sharedPrefs().getInt(
            context.getString(R.string.pref_sk_menu_font_size_key),
            DEFAULT_MENU_FONT_SIZE_SP,
        )

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

        val typeface = menuTypeface(context)
        val sizeSp = menuFontSizeSp(context)
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

        fun styleChild(view: View) {
            view.findViewById<TextView>(android.R.id.title)?.let {
                if (it.currentTextColor != titleColor) it.setTextColor(titleColor)
            }
            view.findViewById<TextView>(android.R.id.summary)?.let {
                if (it.currentTextColor != summaryColor) it.setTextColor(summaryColor)
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

    /** Sample line rendering the configured menu font, size and text colour */
    fun buildMenuFontPreview(
        context: Context,
        sizeSp: Int = menuFontSizeSp(context),
    ): CharSequence {
        val text = SpannableString(context.getString(R.string.sk_menu_font_preview_text))
        text.setSpan(AbsoluteSizeSpan(sizeSp, true), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        text.setSpan(
            ForegroundColorSpan(color(context, R.string.pref_sk_menu_text_color_key, DEFAULT_MENU_TEXT)),
            0,
            text.length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE,
        )
        menuTypeface(context)?.let {
            text.setSpan(TypefaceSpanCompat(it), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        return text
    }

    // Font management

    fun menuFontFile(context: Context): File = File(File(context.filesDir, FONT_DIR), MENU_FONT_FILE)

    fun menuFontName(context: Context): String? = context.sharedPrefs().getString(context.getString(R.string.pref_sk_menu_font_key), null)

    fun menuTypeface(context: Context): Typeface? {
        cachedMenuTypeface?.let { return it }
        val file = menuFontFile(context)
        if (!file.exists()) return null
        return try {
            Typeface.createFromFile(file).also { cachedMenuTypeface = it }
        } catch (e: Exception) {
            Timber.w(e, "failed to load the menu font")
            null
        }
    }

    /**
     * Copies the picked font into internal storage and validates it loads.
     * @return whether the import succeeded
     */
    fun importMenuFont(
        context: Context,
        uri: Uri,
        displayName: String,
    ): Boolean {
        val target = menuFontFile(context)
        try {
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            Typeface.createFromFile(target) // throws if the file is not a usable font
        } catch (e: Exception) {
            Timber.w(e, "failed to import font")
            target.delete()
            cachedMenuTypeface = null
            return false
        }
        cachedMenuTypeface = null
        context.sharedPrefs().edit { putString(context.getString(R.string.pref_sk_menu_font_key), displayName) }
        return true
    }

    fun resetMenuFont(context: Context) {
        menuFontFile(context).delete()
        cachedMenuTypeface = null
        context.sharedPrefs().edit { remove(context.getString(R.string.pref_sk_menu_font_key)) }
    }

    fun resetAll(context: Context) {
        resetMenuFont(context)
        context.sharedPrefs().edit {
            for (keyRes in listOf(
                R.string.pref_sk_menu_background_key,
                R.string.pref_sk_menu_text_color_key,
                R.string.pref_sk_menu_icon_color_key,
                R.string.pref_sk_menu_selected_color_key,
                R.string.pref_sk_menu_selected_background_key,
                R.string.pref_sk_menu_font_size_key,
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
            )) {
                remove(context.getString(keyRes))
            }
        }
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
