// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Fork: the gate in front of the external-automation surface — the
 * [StateExportReceiver] broadcasts and the [AutomationProvider] data door.
 * The same model the sister apps use (renrakusaki's `Config`, 自由作業盤's
 * `AutomationAuth`).
 *
 * ## What contract v2 changed, and why (白い熊, 2026-09-04)
 *
 * v1 shipped every app closed: the switch defaulted to false and a caller
 * also had to present a 48-character secret pasted from this page into the
 * caller's settings. That is the wrong shape for where this is going — **a
 * pasted secret cannot survive a wipe**, and the case the family now exists
 * to serve is 応用管理 restoring apps *and their data* onto a clean phone,
 * where nothing has been configured and nobody has pasted anything.
 *
 * So [enabled] now defaults to **true** and [requireToken] is a new, separate
 * switch defaulting to **false**. The token is unchanged — still 24
 * `SecureRandom` bytes, still generated lazily, still never exported — it is
 * simply opt-in.
 *
 * ## Idempotent about the token
 *
 * **A token handed to an app that does not require one is IGNORED, never an
 * error.** Tokens live in task arguments and workspace variables that outlive
 * the setting they were pasted for; refusing them would turn "白い熊 turned a
 * switch off" into "half the batch mysteriously fails".
 *
 * ## Every write is a `commit()`, never an `apply()`
 *
 * v2 flipped [enabled]'s default to **true**, which changes what a lost write
 * means: an `apply()` that never reaches disk before the process dies now falls
 * back to **ON**, silently re-opening a surface 白い熊 had closed. A gate that
 * fails open is not a gate, so all three flags are written synchronously.
 *
 * ## Device-local by design
 *
 * The prefs file is its own, *not* the default store the export walks, so
 * neither switch nor token ever travels in an export zip — a restored backup
 * can never silently open this surface on another device.
 */
object AutomationAuth {
    private const val PREFS_FILE = "sk_automation" // device-local; never exported
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /**
     * Whether this app answers automation at all. **Default true** since
     * contract v2: the app is on the 保存復元 batch out of the box.
     *
     * Kept as a switch rather than removed because it is the only way to close
     * this one app off, and a feature that can be turned on but never off is
     * one 白い熊 cannot retreat from.
     */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(
        context: Context,
        value: Boolean,
    ) = prefs(context).edit(commit = true) { putBoolean(KEY_ENABLED, value) }

    /** Whether a caller must also present [token]. **Default false** — the token is opt-in now. */
    fun requireToken(context: Context): Boolean = prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    fun setRequireToken(
        context: Context,
        value: Boolean,
    ) = prefs(context).edit(commit = true) { putBoolean(KEY_REQUIRE_TOKEN, value) }

    /** The shared secret; generated on first read so the settings row always shows a value. */
    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
            ?: regenerateToken(context)

    fun regenerateToken(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit(commit = true) { putString(KEY_TOKEN, token) }
        return token
    }

    /**
     * True when [candidate] matches the stored secret, compared constant-time.
     * Kept separate from [enabled] so callers can report "disabled" and "bad
     * token" as distinct failures — they debug differently.
     */
    fun isTokenValid(
        context: Context,
        candidate: String?,
    ): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /**
     * The whole gate, in the one place every entry point asks.
     *
     * @return null to proceed, or the exact `ERROR:` string to answer with.
     *
     * Written as one function so no receiver, provider or service can order
     * the two checks differently — which is how "disabled" and "bad token"
     * would drift apart across forty-two apps. A token supplied to an app that
     * does not require one is ignored here, not refused.
     */
    fun refuse(
        context: Context,
        candidate: String?,
    ): String? =
        when {
            !enabled(context) -> "ERROR:automation disabled"
            requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
            else -> null
        }

    /** `80922d8c…4c49a87c` — the settings row never shows the whole secret. */
    fun abbreviate(token: String): String = if (token.length <= 20) token else "${token.take(8)}…${token.takeLast(8)}"
}
