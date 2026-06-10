// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.annotation.SuppressLint
import android.util.TypedValue
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import com.ichi2.anki.shiroikuma.ShiroikumaUi
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.ContentResolverUtil
import timber.log.Timber

/**
 * Fork: the "白い熊 暗記 UI" page — colour and font management for the app UI.
 * @see ShiroikumaUi
 */
class ShiroikumaUiSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_shiroikuma_ui
    override val analyticsScreenNameConstant: String
        get() = "prefs.shiroikumaUi"

    private val fontPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val name =
                try {
                    ContentResolverUtil.getFileName(requireContext().contentResolver, uri)
                } catch (e: Exception) {
                    Timber.w(e, "could not obtain font file name")
                    "font"
                }
            if (ShiroikumaUi.importMenuFont(requireContext(), uri, name)) {
                refreshFontSummary()
            } else {
                showSnackbar(R.string.sk_font_import_failed)
            }
        }

    override fun initSubscreen() {
        setupColorPreference(R.string.pref_sk_menu_background_key, ShiroikumaUi.DEFAULT_MENU_BACKGROUND)
        setupColorPreference(R.string.pref_sk_menu_text_color_key, ShiroikumaUi.DEFAULT_MENU_TEXT)
        setupColorPreference(R.string.pref_sk_menu_icon_color_key, ShiroikumaUi.DEFAULT_MENU_ICON)
        setupColorPreference(R.string.pref_sk_menu_selected_color_key, ShiroikumaUi.DEFAULT_MENU_SELECTED)
        setupColorPreference(R.string.pref_sk_menu_selected_background_key, ShiroikumaUi.DEFAULT_MENU_SELECTED_BACKGROUND)

        refreshFontSummary()
        requirePreference<Preference>(R.string.pref_sk_menu_font_key).setOnPreferenceClickListener {
            fontPicker.launch(arrayOf("*/*"))
            true
        }
        requirePreference<Preference>(R.string.pref_sk_menu_font_reset_key).setOnPreferenceClickListener {
            ShiroikumaUi.resetMenuFont(requireContext())
            refreshFontSummary()
            true
        }

        requirePreference<Preference>(R.string.pref_sk_reset_key).setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.sk_reset_all_confirm)
                .setPositiveButton(R.string.dialog_ok) { _, _ -> resetAll() }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
            true
        }
    }

    private fun resetAll() {
        ShiroikumaUi.resetAll(requireContext())
        // rebuild the screen so every swatch/summary/slider shows its default again
        preferenceScreen.removeAll()
        addPreferencesFromResource(preferenceResource)
        initSubscreen()
    }

    private fun refreshFontSummary() {
        requirePreference<Preference>(R.string.pref_sk_menu_font_key).summary =
            ShiroikumaUi.menuFontName(requireContext())
                ?: getString(R.string.sk_menu_font_summary_default)
    }

    private fun setupColorPreference(
        @StringRes keyRes: Int,
        default: Int,
    ) {
        val preference = requirePreference<Preference>(keyRes)

        fun refresh() {
            val color = ShiroikumaUi.color(requireContext(), keyRes, default)
            preference.icon = ShiroikumaUi.swatch(color)
            preference.summary = ShiroikumaUi.toHex(color)
        }
        refresh()

        preference.setOnPreferenceClickListener {
            showColorDialog(keyRes, default, ::refresh)
            true
        }
    }

    @SuppressLint("CheckResult")
    private fun showColorDialog(
        @StringRes keyRes: Int,
        default: Int,
        onChanged: () -> Unit,
    ) {
        val context = requireContext()
        val input =
            EditText(context).apply {
                setText(ShiroikumaUi.toHex(ShiroikumaUi.color(context, keyRes, default)).removePrefix("#"))
                hint = getString(R.string.sk_color_dialog_hint)
            }
        // give the EditText the dialog's standard horizontal padding
        val container =
            FrameLayout(context).apply {
                val padding =
                    TypedValue
                        .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)
                        .toInt()
                setPadding(padding, 0, padding, 0)
                addView(input)
            }
        MaterialAlertDialogBuilder(context)
            .setTitle(requirePreference<Preference>(keyRes).title)
            .setView(container)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val color = ShiroikumaUi.parseColor(input.text.toString())
                if (color == null) {
                    showSnackbar(R.string.sk_color_invalid)
                } else {
                    ShiroikumaUi.setColor(context, keyRes, color)
                    onChanged()
                }
            }.setNeutralButton(R.string.sk_default) { _, _ ->
                ShiroikumaUi.setColor(context, keyRes, default)
                onChanged()
            }.setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
