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
import android.text.style.MetricAffectingSpan
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.google.android.material.navigation.NavigationView
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

    /** @return the parsed ARGB colour, or `null` if [text] is not a valid RRGGBB/AARRGGBB value */
    fun parseColor(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (!hex.matches(Regex("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) return null
        return Color.parseColor("#${if (hex.length == 6) "FF$hex" else hex}")
    }

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
