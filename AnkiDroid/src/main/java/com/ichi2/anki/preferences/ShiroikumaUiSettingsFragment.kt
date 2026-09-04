// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.provider.DocumentsContract
import android.text.SpannableString
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.shiroikuma.AutomationAuth
import com.ichi2.anki.shiroikuma.SecondaryActionPreference
import com.ichi2.anki.shiroikuma.ShiroikumaExport
import com.ichi2.anki.shiroikuma.ShiroikumaUi
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.preferences.SliderPreference
import com.ichi2.utils.ContentResolverUtil
import com.ichi2.utils.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream

/**
 * Fork: the "白い熊 暗記 UI" page — colour and font management for the app UI,
 * plus the category-based Export / Import panel at the top (Kōjiki flow,
 * kxkb styling, ArcaneChat pill buttons).
 * @see ShiroikumaUi
 * @see ShiroikumaExport
 */
class ShiroikumaUiSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_shiroikuma_ui
    override val analyticsScreenNameConstant: String
        get() = "prefs.shiroikumaUi"

    private data class FontGroup(
        val role: String,
        @StringRes val fontKey: Int,
        @StringRes val resetKey: Int,
        @StringRes val sizeKey: Int,
        @StringRes val weightKey: Int,
        @StringRes val previewKey: Int,
    )

    private val fontGroups =
        listOf(
            FontGroup(
                ShiroikumaUi.ROLE_MENU,
                R.string.pref_sk_menu_font_key,
                R.string.pref_sk_menu_font_reset_key,
                R.string.pref_sk_menu_font_size_key,
                R.string.pref_sk_menu_font_weight_key,
                R.string.pref_sk_menu_font_preview_key,
            ),
            FontGroup(
                ShiroikumaUi.ROLE_DECK,
                R.string.pref_sk_deck_font_key,
                R.string.pref_sk_deck_font_reset_key,
                R.string.pref_sk_deck_font_size_key,
                R.string.pref_sk_deck_font_weight_key,
                R.string.pref_sk_deck_font_preview_key,
            ),
            FontGroup(
                ShiroikumaUi.ROLE_SETTINGS,
                R.string.pref_sk_settings_font_key,
                R.string.pref_sk_settings_font_reset_key,
                R.string.pref_sk_settings_font_size_key,
                R.string.pref_sk_settings_font_weight_key,
                R.string.pref_sk_settings_font_preview_key,
            ),
        )

    private fun group(role: String) = fontGroups.first { it.role == role }

    /** Which role the open SAF picker is choosing a font file for */
    private var pendingFontRole: String? = null

    private val fontPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val role = pendingFontRole
            pendingFontRole = null
            if (uri == null || role == null) return@registerForActivityResult
            val name =
                try {
                    ContentResolverUtil.getFileName(requireContext().contentResolver, uri)
                } catch (e: Exception) {
                    Timber.w(e, "could not obtain font file name")
                    "font"
                }
            if (ShiroikumaUi.importFont(requireContext(), uri, name, role)) {
                refreshFontSummary(role)
                refreshFontPreview(role)
            } else {
                showSnackbar(R.string.sk_font_import_failed)
            }
        }

    // Export / Import panel (Kōjiki flow: persisted export directory, category
    // checkboxes, ArcaneChat pill buttons, yellow-bordered result dialogs)

    private var eximDialog: AlertDialog? = null
    private var eximDirValue: TextView? = null
    private var eximStatus: TextView? = null
    private var eximExportButton: Button? = null
    private var eximImportButton: Button? = null
    private var eximProgressDialog: AlertDialog? = null
    private var eximProgressText: TextView? = null

    /** Set by the "Cancel export" pill; polled by the export pipeline (any thread) */
    @Volatile
    private var eximCancelRequested = false

    /** Deletes the partial export file when the export fails or is cancelled */
    private var eximPartialDeleter: (() -> Unit)? = null

    /**
     * The media count+size tally: started once when the panel first opens,
     * rendered live under the Collection line, and read again by the export
     * meter — which thus picks up the running numbers instead of recounting.
     */
    private var eximMediaTally: ShiroikumaExport.MediaTally? = null
    private var eximMediaLine: TextView? = null
    private var eximIncludeMedia: CheckBox? = null

    private fun ensureMediaTally() {
        if (eximMediaTally != null) return
        val tally = ShiroikumaExport.MediaTally()
        eximMediaTally = tally
        lifecycleScope.launch {
            runCatching { ShiroikumaExport.tallyMedia(tally) }
                .onFailure { Timber.w(it, "media tally failed") }
        }
    }

    /** Repaints the media line every 250ms until the tally finishes (or the panel closes) */
    private fun startMediaLineUpdater() {
        lifecycleScope.launch {
            while (eximMediaLine != null) {
                renderMediaLine()
                if (eximMediaTally?.done == true) break
                delay(250)
            }
        }
    }

    private fun renderMediaLine() {
        val tally = eximMediaTally ?: return
        val line = eximMediaLine ?: return
        val text =
            getString(
                R.string.sk_eim_media_line,
                tally.files.get(),
                Formatter.formatShortFileSize(line.context, tally.bytes.get()),
            )
        line.text = if (tally.done) text else "$text…"
    }

    private val eximChecks = LinkedHashMap<ShiroikumaExport.Cat, CheckBox>()

    /** Categories ticked when the save-as / import file picker was launched */
    private var pendingExportCats: Set<ShiroikumaExport.Cat>? = null
    private var pendingExportName: String? = null
    private var pendingImportCats: Set<ShiroikumaExport.Cat>? = null

    private val eximDirPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            ShiroikumaExport.setDirUri(requireContext(), uri)
            refreshEximStatus()
        }

    /** Save-as fallback when no export directory is configured yet */
    private val eximSaveAsPicker =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            val cats = pendingExportCats
            val name = pendingExportName
            pendingExportCats = null
            pendingExportName = null
            if (uri == null || cats == null || name == null) return@registerForActivityResult
            val context = requireContext()
            launchEximExport(cats, name) {
                eximPartialDeleter = {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                }
                context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("could not open the chosen file for writing")
            }
        }

    private val eximImportPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val cats = pendingImportCats
            pendingImportCats = null
            if (uri == null || cats == null) return@registerForActivityResult
            runEximImport(uri, cats)
        }

    override fun initSubscreen() {
        setupColorPreference(R.string.pref_sk_menu_background_key, ShiroikumaUi.DEFAULT_MENU_BACKGROUND)
        setupColorPreference(R.string.pref_sk_menu_text_color_key, ShiroikumaUi.DEFAULT_MENU_TEXT) {
            refreshFontPreview(ShiroikumaUi.ROLE_MENU)
        }
        setupColorPreference(R.string.pref_sk_menu_icon_color_key, ShiroikumaUi.DEFAULT_MENU_ICON)
        setupColorPreference(R.string.pref_sk_menu_selected_color_key, ShiroikumaUi.DEFAULT_MENU_SELECTED)
        setupColorPreference(R.string.pref_sk_menu_selected_background_key, ShiroikumaUi.DEFAULT_MENU_SELECTED_BACKGROUND)

        setupColorPreference(R.string.pref_sk_deck_name_color_key, ShiroikumaUi.DEFAULT_DECK_NAME) {
            refreshFontPreview(ShiroikumaUi.ROLE_DECK)
        }
        setupColorPreference(R.string.pref_sk_studied_today_color_key, ShiroikumaUi.DEFAULT_STUDIED_TODAY)
        setupColorPreference(R.string.pref_sk_deck_detail_name_color_key, ShiroikumaUi.DEFAULT_DECK_DETAIL_NAME)
        setupColorPreference(R.string.pref_sk_toolbar_title_color_key, ShiroikumaUi.DEFAULT_TOOLBAR_TITLE)
        setupColorPreference(R.string.pref_sk_toolbar_subtitle_color_key, ShiroikumaUi.DEFAULT_TOOLBAR_SUBTITLE)
        setupColorPreference(R.string.pref_sk_toolbar_icon_color_key, ShiroikumaUi.DEFAULT_TOOLBAR_ICON)
        setupColorPreference(R.string.pref_sk_study_text_color_key, ShiroikumaUi.DEFAULT_STUDY_TEXT)
        setupColorPreference(R.string.pref_sk_study_border_color_key, ShiroikumaUi.DEFAULT_STUDY_BORDER)
        setupColorPreference(R.string.pref_sk_study_background_key, ShiroikumaUi.DEFAULT_STUDY_BACKGROUND)
        setupColorPreference(R.string.pref_sk_pane_divider_color_key, ShiroikumaUi.DEFAULT_PANE_DIVIDER)

        setupColorPreference(R.string.pref_sk_settings_title_color_key, ShiroikumaUi.DEFAULT_SETTINGS_TITLE) {
            refreshFontPreview(ShiroikumaUi.ROLE_SETTINGS)
        }
        setupColorPreference(R.string.pref_sk_settings_summary_color_key, ShiroikumaUi.DEFAULT_SETTINGS_SUMMARY)
        setupColorPreference(R.string.pref_sk_settings_icon_color_key, ShiroikumaUi.DEFAULT_SETTINGS_ICON)
        setupColorPreference(R.string.pref_sk_settings_toggle_color_key, ShiroikumaUi.DEFAULT_SETTINGS_TOGGLE)
        setupColorPreference(R.string.pref_sk_settings_slider_color_key, ShiroikumaUi.DEFAULT_SETTINGS_SLIDER)
        setupColorPreference(R.string.pref_sk_settings_header_color_key, ShiroikumaUi.DEFAULT_SETTINGS_HEADER)

        for (fontGroup in fontGroups) setupFontGroup(fontGroup)

        requirePreference<Preference>(R.string.pref_sk_eximport_key).setOnPreferenceClickListener {
            showEximportDialog()
            true
        }
        // Fork spec: the export directory is queried when the page opens for
        // the latest export; the row summary carries the answer.
        refreshEximStatus()
        setupAutomationRows()

        requirePreference<Preference>(R.string.pref_sk_reset_key).setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.sk_reset_all_confirm)
                .setPositiveButton(R.string.dialog_ok) { _, _ -> resetAll() }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
            true
        }

        // SliderPreference hardcodes its layoutResource in init, so the
        // kxkb-style indented slider layout must be assigned here
        applySliderLayouts(preferenceScreen)
    }

    private fun applySliderLayouts(preferenceGroup: PreferenceGroup) {
        for (i in 0 until preferenceGroup.preferenceCount) {
            when (val preference = preferenceGroup.getPreference(i)) {
                is PreferenceGroup -> applySliderLayouts(preference)
                is SliderPreference -> preference.layoutResource = R.layout.sk_preference_slider
            }
        }
    }

    private fun resetAll() {
        ShiroikumaUi.resetAll(requireContext())
        // rebuild the screen so every swatch/summary/slider shows its default again
        preferenceScreen.removeAll()
        addPreferencesFromResource(preferenceResource)
        initSubscreen()
    }

    /** Relaunch into a fresh DeckPicker so colours, controls and theme all reload. */
    private fun restartApp() {
        val intent =
            Intent(requireContext(), DeckPicker::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        startActivity(intent)
        requireActivity().finish()
    }

    /**
     * The automation surface's three rows — the master switch, "Use
     * authorization token?", and the token itself — appended below the
     * Export / Import row because this is a backup feature and every sister app
     * puts them in the same place.
     *
     * The token row is shown **only while a token is being asked for**: a
     * 48-character secret sitting under an off switch invites 白い熊 to paste it
     * somewhere it will do nothing.
     *
     * All three values live in [AutomationAuth]'s own device-local prefs file,
     * so they are neither exported nor touched by "Reset all to defaults":
     * repainting the UI must not silently break 白い熊's 保存復元 batch.
     */
    private fun setupAutomationRows() {
        val context = requireContext()
        val switch = requirePreference<SwitchPreferenceCompat>(R.string.pref_sk_automation_enabled_key)
        switch.isChecked = AutomationAuth.enabled(context)
        switch.setOnPreferenceChangeListener { _, newValue ->
            AutomationAuth.setEnabled(context, newValue as Boolean)
            true
        }

        val requireToken = requirePreference<SwitchPreferenceCompat>(R.string.pref_sk_automation_require_token_key)
        val tokenRow = requirePreference<SecondaryActionPreference>(R.string.pref_sk_automation_token_key)
        requireToken.isChecked = AutomationAuth.requireToken(context)
        tokenRow.isVisible = requireToken.isChecked
        requireToken.setOnPreferenceChangeListener { _, newValue ->
            val on = newValue as Boolean
            AutomationAuth.setRequireToken(context, on)
            tokenRow.isVisible = on
            true
        }

        fun showToken() {
            // a colour span, because styleSettingsList repaints plain summary
            // text with the configured summary colour on every draw
            tokenRow.summary =
                SpannableString(AutomationAuth.abbreviate(AutomationAuth.token(context))).apply {
                    setSpan(ForegroundColorSpan(EXIM_YELLOW), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                }
        }
        showToken()

        tokenRow.setOnPreferenceClickListener {
            context.copyToClipboard(AutomationAuth.token(context), R.string.sk_automation_token_copied)
            true
        }
        tokenRow.onActionClick = {
            AutomationAuth.regenerateToken(context)
            showToken()
            showSnackbar(R.string.sk_automation_token_regenerated)
        }
    }

    // The Export / Import panel

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun eximText(
        text: CharSequence,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
    ): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun eximCheckbox(
        label: String,
        bold: Boolean = false,
        checked: Boolean = true,
    ): CheckBox =
        CheckBox(requireContext()).apply {
            text = label
            setTextColor(EXIM_YELLOW)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            buttonTintList = ColorStateList.valueOf(EXIM_YELLOW)
            setPadding(dp(8), dp(7), 0, dp(7))
            isChecked = checked
        }

    private fun eximDivider(topGap: Int = 0): View =
        View(requireContext()).apply {
            layoutParams =
                LinearLayout
                    .LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                    .apply { topMargin = dp(topGap) }
            setBackgroundColor(EXIM_YELLOW)
            alpha = 0.4f
        }

    private fun eximBorderBox(cornerDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(EXIM_BLACK)
            cornerRadius = dp(cornerDp).toFloat()
            setStroke((1.99f * resources.displayMetrics.density).toInt(), EXIM_YELLOW)
        }

    /** An ArcaneChat-style round pill: black fill, yellow stroke, yellow text, yellow ripple */
    private fun eximPillButton(
        label: String,
        onClick: () -> Unit,
    ): Button {
        val density = resources.displayMetrics.density
        val pill =
            GradientDrawable().apply {
                setColor(EXIM_BLACK)
                setStroke((1.5f * density).toInt(), EXIM_YELLOW)
                cornerRadius = 50 * density
            }
        return Button(requireContext()).apply {
            text = label
            isAllCaps = false
            setTextColor(EXIM_YELLOW)
            background =
                RippleDrawable(
                    ColorStateList.valueOf((EXIM_YELLOW and 0x00FFFFFF) or 0x33000000),
                    pill,
                    null,
                )
            // zeroed minimums + explicit padding so the rounded stroke is never clipped
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setOnClickListener { onClick() }
        }
    }

    @SuppressLint("CheckResult")
    private fun showEximportDialog() {
        val context = requireContext()

        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(20))
                background = eximBorderBox(16)
            }

        root.addView(
            eximText(getString(R.string.sk_eim_title), 18f, EXIM_YELLOW, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, dp(6))
            },
        )
        root.addView(
            eximText(getString(R.string.sk_eim_desc), 13f, EXIM_DIM).apply {
                setPadding(0, 0, 0, dp(10))
            },
        )

        // the persisted export directory — a bordered, clearly-tappable box
        val dirBox =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = eximBorderBox(10)
                setOnClickListener { eximDirPicker.launch(ShiroikumaExport.dirUri(context)) }
            }
        dirBox.addView(eximText(getString(R.string.sk_eim_dir), 12f, EXIM_YELLOW))
        eximDirValue = eximText("", 15f, EXIM_DIM, bold = true)
        dirBox.addView(eximDirValue)
        root.addView(
            dirBox,
            LinearLayout
                .LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply {
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                },
        )

        eximStatus = eximText("", 14f, EXIM_DIM).apply { setPadding(dp(2), 0, 0, dp(8)) }
        root.addView(eximStatus)

        root.addView(eximDivider())

        // every box starts on the answer we give automation callers in
        // LIST_CATEGORIES, so both pickers open on the same selection
        val selectAll =
            eximCheckbox(
                getString(R.string.sk_eim_select_all),
                bold = true,
                checked = ShiroikumaExport.Cat.entries.all { it.defaultOn },
            )
        root.addView(selectAll)
        eximChecks.clear()
        for (cat in ShiroikumaExport.Cat.entries) {
            val checkBox = eximCheckbox(getString(cat.labelRes), checked = cat.defaultOn)
            eximChecks[cat] = checkBox
            root.addView(checkBox)
            if (cat == ShiroikumaExport.Cat.COLLECTION) {
                // media is decided separately: the include-media toggle and the
                // live count/size tally sit indented under the Collection line
                eximIncludeMedia =
                    eximCheckbox(
                        getString(R.string.sk_eim_include_media),
                        checked = ShiroikumaExport.MEDIA_DEFAULT_ON,
                    ).apply {
                        setPadding(dp(8), dp(2), 0, dp(2))
                    }
                root.addView(
                    eximIncludeMedia,
                    LinearLayout
                        .LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { marginStart = dp(32) },
                )
                eximMediaLine = eximText("…", 13f, EXIM_DIM).apply { setPadding(dp(48), 0, 0, dp(4)) }
                root.addView(eximMediaLine)
                checkBox.setOnCheckedChangeListener { _, checked ->
                    eximIncludeMedia?.isEnabled = checked
                    eximIncludeMedia?.alpha = if (checked) 1f else 0.4f
                }
            }
        }
        selectAll.setOnCheckedChangeListener { _, isChecked ->
            eximChecks.values.forEach { it.isChecked = isChecked }
        }

        root.addView(eximDivider(topGap = 8))

        // ArcaneChat-style dialog button row: round pills, Cancel alone on the
        // left, the Import / Export actions grouped on the right
        val buttons =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, 0)
            }
        buttons.addView(eximPillButton(getString(R.string.dialog_cancel)) { eximDialog?.dismiss() })
        buttons.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        eximImportButton =
            eximPillButton(getString(R.string.sk_eim_import_label)) { onEximImportClicked() }.also {
                buttons.addView(
                    it,
                    LinearLayout
                        .LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { marginEnd = dp(8) },
                )
            }
        eximExportButton =
            eximPillButton(getString(R.string.sk_eim_export_label)) { onEximExportClicked() }.also {
                buttons.addView(it)
            }
        root.addView(buttons)

        val scroll =
            NestedScrollView(context).apply {
                addView(
                    root,
                    ViewGroup
                        .MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) },
                )
            }

        eximDialog =
            MaterialAlertDialogBuilder(context)
                .setView(scroll)
                .setOnDismissListener {
                    eximDialog = null
                    eximDirValue = null
                    eximStatus = null
                    eximExportButton = null
                    eximImportButton = null
                    eximMediaLine = null
                    eximIncludeMedia = null
                    eximChecks.clear()
                }.show()
                .apply { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }

        refreshEximStatus()
        // the media tally starts (or resumes rendering) the moment the panel
        // opens, so the count/size can inform the media decision up front
        ensureMediaTally()
        startMediaLineUpdater()
    }

    private fun selectedCats(): Set<ShiroikumaExport.Cat> = eximChecks.filterValues { it.isChecked }.keys

    /**
     * Refreshes the "latest export" answer everywhere it shows: the directory
     * box and status line of the open panel, and the summary of the
     * Export / Import row on the page itself.
     */
    private fun refreshEximStatus() {
        val context = requireContext()
        lifecycleScope.launch {
            val (dirSet, dirName, statusAndWarn) =
                withContext(Dispatchers.IO) {
                    val dir = ShiroikumaExport.exportDir(context)
                    val name = dir?.name ?: ShiroikumaExport.dirUri(context)?.lastPathSegment
                    val textAndWarn =
                        when {
                            dir == null -> context.getString(R.string.sk_eim_warn_nodir) to true
                            else -> {
                                val newest = ShiroikumaExport.latestExport(context)
                                if (newest == null) {
                                    context.getString(R.string.sk_eim_warn_none) to true
                                } else {
                                    context.getString(
                                        R.string.sk_eim_last,
                                        ShiroikumaExport.formatTimestamp(newest.lastModified()),
                                    ) to false
                                }
                            }
                        }
                    Triple(dir != null, name, textAndWarn)
                }
            val (statusText, warn) = statusAndWarn
            eximDirValue?.text = dirName ?: getString(R.string.sk_eim_dir_unset)
            eximDirValue?.setTextColor(if (dirName == null) EXIM_WARN else EXIM_DIM)
            showEximStatusText(statusText, warn)
            // The page row carries the same answer, queried on page open: red
            // while no export directory is set, yellow once there is one. A
            // colour span, because styleSettingsList repaints plain summary
            // text with the configured summary colour on every draw.
            val rowColor = if (dirSet) EXIM_YELLOW else EXIM_WARN
            findPreference<Preference>(getString(R.string.pref_sk_eximport_key))?.summary =
                SpannableString(statusText).apply {
                    setSpan(ForegroundColorSpan(rowColor), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                }
        }
    }

    private fun showEximStatusText(
        text: String,
        warn: Boolean,
    ) {
        eximStatus?.text = text
        eximStatus?.setTextColor(if (warn) EXIM_WARN else EXIM_DIM)
    }

    private fun setEximBusy(busy: Boolean) {
        for (button in listOfNotNull(eximExportButton, eximImportButton)) {
            button.isEnabled = !busy
            button.alpha = if (busy) 0.4f else 1f
        }
    }

    private fun onEximExportClicked() {
        val cats = selectedCats()
        if (cats.isEmpty()) {
            showEximStatusText(getString(R.string.sk_eim_none_selected), warn = true)
            return
        }
        val context = requireContext()
        val name = ShiroikumaExport.exportFileName()
        val dir = ShiroikumaExport.exportDir(context)
        if (dir == null) {
            // no directory configured — fall back to a save-as picker
            pendingExportCats = cats
            pendingExportName = name
            eximSaveAsPicker.launch(name)
            return
        }
        launchEximExport(cats, name) {
            val file =
                dir.createFile("application/zip", name)
                    ?: throw IOException("could not create $name in the export directory")
            eximPartialDeleter = { file.delete() }
            context.contentResolver.openOutputStream(file.uri)
                ?: throw IOException("could not open $name for writing")
        }
    }

    private fun launchEximExport(
        cats: Set<ShiroikumaExport.Cat>,
        displayName: String,
        openOutput: () -> OutputStream,
    ) {
        val context = requireContext()
        setEximBusy(true)
        eximCancelRequested = false
        eximPartialDeleter = null
        showEximStatusText(getString(R.string.sk_eim_exporting), warn = false)
        // the meter dialog opens BEFORE any work: the collection/media export
        // takes minutes, and a dead blank screen reads as a freeze
        showEximProgressDialog()
        val includeMedia = eximIncludeMedia?.isChecked ?: true
        lifecycleScope.launch {
            try {
                ShiroikumaExport.export(
                    context,
                    cats,
                    ::onEximProgress,
                    { eximCancelRequested },
                    includeMedia = includeMedia,
                    mediaTally = eximMediaTally,
                    openOutput = openOutput,
                )
                eximPartialDeleter = null
                refreshEximStatus()
                dismissEximProgress()
                showEximInfoDialog(
                    getString(R.string.sk_eim_export_done_title),
                    getString(R.string.sk_eim_export_done_body, cats.size, displayName),
                    getString(R.string.dialog_ok) to { dialog -> closeEximChain(dialog) },
                )
            } catch (e: Exception) {
                dismissEximProgress()
                deletePartialExport()
                if (eximCancelRequested ||
                    e is ShiroikumaExport.ExportCancelledException ||
                    e is BackendInterruptedException
                ) {
                    Timber.i("settings export cancelled")
                    showEximStatusText(getString(R.string.sk_eim_cancelled), warn = true)
                    refreshEximStatus()
                } else {
                    Timber.w(e, "settings export failed")
                    showEximStatusText(
                        getString(R.string.sk_eim_export_failed, e.message ?: e.javaClass.simpleName),
                        warn = true,
                    )
                }
            } finally {
                dismissEximProgress()
                setEximBusy(false)
            }
        }
    }

    /** Removes a partial export file left behind by a failed/cancelled export */
    private fun deletePartialExport() {
        val deleter = eximPartialDeleter
        eximPartialDeleter = null
        if (deleter == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { deleter() }
                .onFailure { Timber.w(it, "could not delete the partial export") }
        }
    }

    /**
     * The live export meter — a black, yellow-bordered box with a single
     * progress line fed by the backend's media counts and the zip byte meter.
     */
    private fun showEximProgressDialog() {
        val context = requireContext()
        val box =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(20), dp(22), dp(20))
                background = eximBorderBox(16)
            }
        box.addView(eximText(getString(R.string.sk_eim_exporting), 19f, EXIM_YELLOW, bold = true))
        eximProgressText =
            eximText("…", 15f, EXIM_YELLOW).apply { setPadding(0, dp(12), 0, 0) }
        box.addView(eximProgressText)
        val buttons =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(16), 0, 0)
            }
        buttons.addView(
            eximPillButton(getString(R.string.sk_eim_cancel_export)) {
                eximCancelRequested = true
                eximProgressText?.text = getString(R.string.sk_eim_cancelling)
            }.apply { setPadding(dp(18), dp(8), dp(18), dp(8)) },
        )
        box.addView(buttons)
        eximProgressDialog =
            MaterialAlertDialogBuilder(context)
                .setView(NestedScrollView(context).apply { addView(box) })
                .setCancelable(false)
                .create()
                .apply {
                    show()
                    window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                }
    }

    private fun dismissEximProgress() {
        eximProgressDialog?.dismiss()
        eximProgressDialog = null
        eximProgressText = null
    }

    /** May be called from any thread (backend poller is main, zip meter is IO) */
    private fun onEximProgress(progress: ShiroikumaExport.Progress) {
        // once cancelled, "Cancelling…" must not be overwritten by late meter lines
        if (eximCancelRequested) return
        eximProgressText?.post { eximProgressText?.text = progress.text }
    }

    private fun onEximImportClicked() {
        val cats = selectedCats()
        if (cats.isEmpty()) {
            showEximStatusText(getString(R.string.sk_eim_none_selected), warn = true)
            return
        }
        pendingImportCats = cats
        eximImportPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    private fun runEximImport(
        uri: Uri,
        cats: Set<ShiroikumaExport.Cat>,
    ) {
        val context = requireContext()
        setEximBusy(true)
        showEximStatusText(getString(R.string.sk_eim_importing), warn = false)
        lifecycleScope.launch {
            try {
                val summary =
                    ShiroikumaExport.import(context, cats) {
                        context.contentResolver.openInputStream(uri)
                            ?: throw IOException("could not open the chosen file for reading")
                    }
                showEximInfoDialog(
                    getString(R.string.sk_eim_import_done_title),
                    getString(R.string.sk_eim_import_done_body, summary),
                    getString(R.string.sk_eim_restart_later) to { dialog -> closeEximChain(dialog) },
                    getString(R.string.sk_restart_now) to { dialog ->
                        dialog.dismiss()
                        eximDialog?.dismiss()
                        restartApp()
                    },
                )
            } catch (e: Exception) {
                Timber.w(e, "settings import failed")
                showEximStatusText(
                    getString(R.string.sk_eim_import_failed, e.message ?: e.javaClass.simpleName),
                    warn = true,
                )
            } finally {
                setEximBusy(false)
            }
        }
    }

    /**
     * A black, yellow-bordered info dialog with pill action buttons — the
     * window itself is transparent so the hand-drawn bordered box is the only
     * visible surface.
     */
    private fun showEximInfoDialog(
        title: String,
        body: String,
        vararg actions: Pair<String, (AlertDialog) -> Unit>,
    ) {
        val context = requireContext()
        val box =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(20), dp(22), dp(16))
                background = eximBorderBox(16)
            }
        box.addView(eximText(title, 19f, EXIM_YELLOW, bold = true))
        box.addView(eximText(body, 14f, EXIM_YELLOW).apply { setPadding(0, dp(10), 0, 0) })

        val dialog =
            MaterialAlertDialogBuilder(context)
                .setView(NestedScrollView(context).apply { addView(box) })
                .setCancelable(false)
                .create()

        val buttons =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(16), 0, 0)
            }
        for ((index, action) in actions.withIndex()) {
            val (label, onClick) = action
            buttons.addView(
                eximPillButton(label) { onClick(dialog) }.apply {
                    setPadding(dp(18), dp(8), dp(18), dp(8))
                },
                LinearLayout
                    .LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { if (index < actions.size - 1) marginEnd = dp(10) },
            )
        }
        box.addView(buttons)

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    /**
     * Fork spec: acknowledging a successful export/import closes the whole
     * chain — the info dialog, the Export / Import panel beneath it, and the
     * UI settings page itself (back to Settings when the page was opened from
     * there, back to the deck picker when opened directly).
     */
    private fun closeEximChain(infoDialog: AlertDialog) {
        infoDialog.dismiss()
        eximDialog?.dismiss()
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    // Fonts and colours

    private fun setupFontGroup(fontGroup: FontGroup) {
        val role = fontGroup.role
        refreshFontSummary(role)
        refreshFontPreview(role)
        requirePreference<Preference>(fontGroup.fontKey).setOnPreferenceClickListener {
            pendingFontRole = role
            fontPicker.launch(arrayOf("*/*"))
            true
        }
        requirePreference<Preference>(fontGroup.resetKey).setOnPreferenceClickListener {
            ShiroikumaUi.resetFont(requireContext(), role)
            refreshFontSummary(role)
            refreshFontPreview(role)
            true
        }
        requirePreference<Preference>(fontGroup.sizeKey).setOnPreferenceChangeListener { _, newValue ->
            refreshFontPreview(role, sizeSp = newValue as Int)
            true
        }
        requirePreference<Preference>(fontGroup.weightKey).setOnPreferenceChangeListener { _, newValue ->
            refreshFontPreview(role, weight = newValue as Int)
            true
        }
    }

    private fun refreshFontSummary(role: String) {
        requirePreference<Preference>(group(role).fontKey).summary =
            ShiroikumaUi.fontName(requireContext(), role)
                ?: getString(R.string.sk_menu_font_summary_default)
    }

    private fun refreshFontPreview(
        role: String,
        sizeSp: Int = ShiroikumaUi.fontSizeSp(requireContext(), role),
        weight: Int = ShiroikumaUi.fontWeight(requireContext(), role),
    ) {
        val preview = requirePreference<Preference>(group(role).previewKey)
        // Preference.setTitle ignores a title whose characters are unchanged
        // (TextUtils.equals compares text only, not spans), so when just the
        // size/typeface/colour spans change the row would never re-render.
        // Clearing the title first forces the rebind.
        preview.title = null
        preview.title = ShiroikumaUi.buildFontPreview(requireContext(), role, sizeSp, weight)
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

    companion object {
        private const val EXIM_BLACK = 0xFF000000.toInt()
        private const val EXIM_YELLOW = 0xFFFFFF00.toInt()
        private const val EXIM_DIM = 0xFFCCCC66.toInt()
        private const val EXIM_WARN = 0xFFFF5252.toInt()
    }
}
