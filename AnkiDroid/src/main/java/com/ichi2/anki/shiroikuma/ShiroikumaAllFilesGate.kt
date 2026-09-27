// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.utils.Permissions
import timber.log.Timber
import java.io.File

/**
 * Fork: all-files access, asked for **before the deck list opens** — the
 * sister-app gate (reference: 書籍閲覧's `WhiteBearAllFilesGate`).
 *
 * ## Why this exists (白い熊, 2026-09-27)
 *
 * A restored install is the case this is for. 応用管理 reinstalls the APK and
 * the automation import hands back every setting, the collection location
 * (`deckPath`, under `〇/`) included, and the collection itself never left
 * shared storage. What does **not** come back is permission: to the framework
 * the reinstall is a new app, so all-files access is off — and a 応用管理
 * restore may even revoke a grant it finds. Everything therefore points at the
 * right collection and nothing can read it, which is the most misleading state
 * this app can be in: it looks restored and is not.
 *
 * Upstream does ask too (`PermissionsActivity`), but only when its own storage
 * policy concludes that it should, and on a screen with no way past. This gate
 * asks on one plain condition — the collection is on shared storage and the
 * grant is missing — and says why in terms of *this* situation.
 *
 * ## Why it can still be skipped
 *
 * A permission screen with no way past can make the app unopenable on a ROM
 * that will not grant it. So "Not now" is there, deliberately quiet, and lasts
 * for this process only: it is not remembered, and the next start asks again.
 * A skip also stands in for upstream's own unskippable screen, so the app
 * opens on its "collection folder inaccessible" dialog, from which the path
 * can be changed. Nothing is opened or created: startup stops at the failed
 * directory check, before any collection is touched.
 */
object ShiroikumaAllFilesGate {
    /** "Not now" for the lifetime of this process; never persisted. */
    @Volatile
    var skippedThisLaunch = false
        private set

    /** Whether to show the gate now, on this device. */
    fun needsGate(context: Context): Boolean {
        // the cheap answers first: the platform is only asked once there is
        // something to ask it about (and Robolectric cannot answer it at all)
        if (skippedThisLaunch ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            !Permissions.canManageExternalStorage(context)
        ) {
            return false
        }
        return needsGate(
            collectionPath = context.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null),
            appPrivateRoots = appPrivateRoots(context),
            sdk = Build.VERSION.SDK_INT,
            canRequest = true,
            granted = Environment.isExternalStorageManager(),
            skipped = false,
        )
    }

    /**
     * The decision, free of the platform. No path at all is upstream's first
     * run (its own setup flow picks one); an app-private path needs no
     * permission; and there is nothing to ask for before Android 11 or in a
     * flavor that does not declare `MANAGE_EXTERNAL_STORAGE`.
     */
    @VisibleForTesting
    fun needsGate(
        collectionPath: String?,
        appPrivateRoots: List<File>,
        sdk: Int,
        canRequest: Boolean,
        granted: Boolean,
        skipped: Boolean,
    ): Boolean {
        if (skipped || granted || !canRequest || sdk < Build.VERSION_CODES.R) return false
        if (collectionPath.isNullOrEmpty()) return false
        val path = File(collectionPath).absoluteFile.normalize()
        return appPrivateRoots.none { path.startsWith(it.absoluteFile.normalize()) }
    }

    /**
     * Where this app may write without any permission: its data directory,
     * and its `Android/data` and `Android/media` directories on every volume.
     */
    private fun appPrivateRoots(context: Context): List<File> =
        buildList {
            add(context.dataDir)
            // …/Android/data/<pkg>/files → …/Android/data/<pkg>
            context.getExternalFilesDirs(null).filterNotNull().mapNotNullTo(this) { it.parentFile }
            @Suppress("DEPRECATION") // still the only way to name the Android/media dirs
            addAll(context.externalMediaDirs.filterNotNull())
        }

    fun skip() {
        Timber.i("all-files gate: skipped for this launch")
        skippedThisLaunch = true
    }

    /**
     * The per-app page first, the global list as a fallback: EMUI has been
     * known to refuse the targeted intent, and a button that does nothing is
     * worse than one extra tap.
     */
    fun openAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val direct =
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:${context.packageName}".toUri(),
            )
        runCatching { context.startActivity(direct) }.onFailure {
            Timber.w(it, "all-files gate: per-app page refused; opening the list")
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                .onFailure { e -> Timber.w(e, "all-files gate: no settings page at all") }
        }
    }
}

/**
 * The gate's screen: black, yellow text, ArcaneChat pills — "Not now" left,
 * "Grant access" right. It closes itself on the resume that finds the grant
 * given, since coming back from Settings is the only sign of it.
 */
class ShiroikumaAllFilesGateActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        // back is a skip: a gate the system back gesture cannot leave is the
        // unopenable app the skip exists to prevent
        onBackPressedDispatcher.addCallback(this) { skipAndClose() }
    }

    override fun onResume() {
        super.onResume()
        if (!ShiroikumaAllFilesGate.needsGate(this)) {
            Timber.i("all-files gate: access granted")
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun skipAndClose() {
        ShiroikumaAllFilesGate.skip()
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildContent(): ScrollView {
        val path = sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null).orEmpty()
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(28), dp(48), dp(28), dp(32))
            }
        column.addView(
            text(getString(R.string.sk_all_files_gate_title), 22f, bold = true),
        )
        column.addView(space(16))
        column.addView(text(getString(R.string.sk_all_files_gate_body), 16f))
        column.addView(space(12))
        column.addView(text(path, 14f).apply { alpha = 0.75f })
        column.addView(space(28))
        column.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(pill(getString(R.string.sk_all_files_gate_skip)) { skipAndClose() })
                addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
                addView(
                    pill(getString(R.string.sk_all_files_gate_grant)) {
                        ShiroikumaAllFilesGate.openAllFilesAccessSettings(this@ShiroikumaAllFilesGateActivity)
                    },
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return ScrollView(this).apply {
            setBackgroundColor(BLACK)
            isFillViewport = true
            fitsSystemWindows = true
            addView(column)
        }
    }

    private fun text(
        value: String,
        sizeSp: Float,
        bold: Boolean = false,
    ) = TextView(this).apply {
        text = value
        setTextColor(YELLOW)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun space(heightDp: Int) =
        Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp))
        }

    /** An ArcaneChat-style round pill: black fill, yellow stroke, yellow text, yellow ripple */
    private fun pill(
        label: String,
        onClick: () -> Unit,
    ): Button {
        val shape =
            GradientDrawable().apply {
                setColor(BLACK)
                setStroke((1.5f * resources.displayMetrics.density).toInt(), YELLOW)
                cornerRadius = dp(50).toFloat()
            }
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(YELLOW)
            background = RippleDrawable(ColorStateList.valueOf((YELLOW and 0x00FFFFFF) or 0x33000000), shape, null)
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setOnClickListener { onClick() }
        }
    }

    companion object {
        private const val BLACK = 0xFF000000.toInt()
        private const val YELLOW = 0xFFFFFF00.toInt()

        fun getIntent(context: Context) = Intent(context, ShiroikumaAllFilesGateActivity::class.java)
    }
}
