// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
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
        setupColorPreference(R.string.pref_sk_menu_text_color_key, ShiroikumaUi.DEFAULT_MENU_TEXT, ::refreshFontPreview)
        setupColorPreference(R.string.pref_sk_menu_icon_color_key, ShiroikumaUi.DEFAULT_MENU_ICON)
        setupColorPreference(R.string.pref_sk_menu_selected_color_key, ShiroikumaUi.DEFAULT_MENU_SELECTED)
        setupColorPreference(R.string.pref_sk_menu_selected_background_key, ShiroikumaUi.DEFAULT_MENU_SELECTED_BACKGROUND)

        setupColorPreference(R.string.pref_sk_deck_name_color_key, ShiroikumaUi.DEFAULT_DECK_NAME)
        setupColorPreference(R.string.pref_sk_studied_today_color_key, ShiroikumaUi.DEFAULT_STUDIED_TODAY)
        setupColorPreference(R.string.pref_sk_deck_detail_name_color_key, ShiroikumaUi.DEFAULT_DECK_DETAIL_NAME)
        setupColorPreference(R.string.pref_sk_toolbar_title_color_key, ShiroikumaUi.DEFAULT_TOOLBAR_TITLE)
        setupColorPreference(R.string.pref_sk_toolbar_subtitle_color_key, ShiroikumaUi.DEFAULT_TOOLBAR_SUBTITLE)
        setupColorPreference(R.string.pref_sk_toolbar_icon_color_key, ShiroikumaUi.DEFAULT_TOOLBAR_ICON)
        setupColorPreference(R.string.pref_sk_study_text_color_key, ShiroikumaUi.DEFAULT_STUDY_TEXT)
        setupColorPreference(R.string.pref_sk_study_border_color_key, ShiroikumaUi.DEFAULT_STUDY_BORDER)
        setupColorPreference(R.string.pref_sk_study_background_key, ShiroikumaUi.DEFAULT_STUDY_BACKGROUND)
        setupColorPreference(R.string.pref_sk_pane_divider_color_key, ShiroikumaUi.DEFAULT_PANE_DIVIDER)

        setupColorPreference(R.string.pref_sk_settings_title_color_key, ShiroikumaUi.DEFAULT_SETTINGS_TITLE)
        setupColorPreference(R.string.pref_sk_settings_summary_color_key, ShiroikumaUi.DEFAULT_SETTINGS_SUMMARY)
        setupColorPreference(R.string.pref_sk_settings_icon_color_key, ShiroikumaUi.DEFAULT_SETTINGS_ICON)
        setupColorPreference(R.string.pref_sk_settings_toggle_color_key, ShiroikumaUi.DEFAULT_SETTINGS_TOGGLE)
        setupColorPreference(R.string.pref_sk_settings_slider_color_key, ShiroikumaUi.DEFAULT_SETTINGS_SLIDER)
        setupColorPreference(R.string.pref_sk_settings_header_color_key, ShiroikumaUi.DEFAULT_SETTINGS_HEADER)

        refreshFontSummary()
        refreshFontPreview()
        requirePreference<Preference>(R.string.pref_sk_menu_font_key).setOnPreferenceClickListener {
            fontPicker.launch(arrayOf("*/*"))
            true
        }
        requirePreference<Preference>(R.string.pref_sk_menu_font_reset_key).setOnPreferenceClickListener {
            ShiroikumaUi.resetMenuFont(requireContext())
            refreshFontSummary()
            refreshFontPreview()
            true
        }
        requirePreference<Preference>(R.string.pref_sk_menu_font_size_key).setOnPreferenceChangeListener { _, newValue ->
            refreshFontPreview(newValue as Int)
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

    private fun refreshFontPreview() = refreshFontPreview(ShiroikumaUi.menuFontSizeSp(requireContext()))

    private fun refreshFontPreview(sizeSp: Int) {
        requirePreference<Preference>(R.string.pref_sk_menu_font_preview_key).title =
            ShiroikumaUi.buildMenuFontPreview(requireContext(), sizeSp)
    }

    private fun setupColorPreference(
        @StringRes keyRes: Int,
        default: Int,
        extraOnChanged: (() -> Unit)? = null,
    ) {
        val preference = requirePreference<Preference>(keyRes)

        fun refresh() {
            val color = ShiroikumaUi.color(requireContext(), keyRes, default)
            preference.icon = ShiroikumaUi.swatch(color)
            preference.summary = ShiroikumaUi.toHex(color)
            extraOnChanged?.invoke()
        }
        refresh()

        preference.setOnPreferenceClickListener {
            showColorDialog(keyRes, default, ::refresh)
            true
        }
    }

    /** Colour picker: four RGBA sliders with a live preview swatch */
    @SuppressLint("CheckResult", "SetTextI18n")
    private fun showColorDialog(
        @StringRes keyRes: Int,
        default: Int,
        onChanged: () -> Unit,
    ) {
        val context = requireContext()
        val initial = ShiroikumaUi.color(context, keyRes, default)
        val channels = intArrayOf(Color.red(initial), Color.green(initial), Color.blue(initial), Color.alpha(initial))

        fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

        fun current() = Color.argb(channels[3], channels[0], channels[1], channels[2])

        val preview =
            TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(12) }
            }

        fun refreshPreview() {
            val color = current()
            preview.background =
                GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(color)
                    setStroke(2, 0xFF888888.toInt())
                }
            preview.text = ShiroikumaUi.toHex(color)
            // readable hex on any colour: black text on light, white on dark
            val luminance = Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114
            preview.setTextColor(if (luminance > 128) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
        refreshPreview()

        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), 0)
                addView(preview)
                for ((index, label) in listOf("R", "G", "B", "A").withIndex()) {
                    val valueText =
                        TextView(context).apply {
                            text = channels[index].toString()
                            minWidth = dp(36)
                            gravity = Gravity.END
                        }
                    val seekBar =
                        SeekBar(context).apply {
                            max = 255
                            progress = channels[index]
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            setOnSeekBarChangeListener(
                                object : SeekBar.OnSeekBarChangeListener {
                                    override fun onProgressChanged(
                                        seekBar: SeekBar?,
                                        progress: Int,
                                        fromUser: Boolean,
                                    ) {
                                        channels[index] = progress
                                        valueText.text = progress.toString()
                                        refreshPreview()
                                    }

                                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                                },
                            )
                        }
                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            addView(
                                TextView(context).apply {
                                    text = label
                                    minWidth = dp(24)
                                },
                            )
                            addView(seekBar)
                            addView(valueText)
                        },
                    )
                }
            }

        MaterialAlertDialogBuilder(context)
            .setTitle(requirePreference<Preference>(keyRes).title)
            .setView(container)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                ShiroikumaUi.setColor(context, keyRes, current())
                onChanged()
            }.setNeutralButton(R.string.sk_default) { _, _ ->
                ShiroikumaUi.setColor(context, keyRes, default)
                onChanged()
            }.setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
