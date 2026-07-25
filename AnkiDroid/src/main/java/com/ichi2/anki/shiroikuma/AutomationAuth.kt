// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Fork: the gate in front of the external-automation intent surface
 * ([StateExportReceiver]) — a master switch plus a shared secret that every
 * automation broadcast must carry. The same model the sister apps use
 * (renrakusaki's `Config`, 自由作業盤's `AutomationAuth`).
 *
 * Device-local by design: the prefs file is its own, *not* the default store
 * the export walks, so neither the switch nor the token ever travels in an
 * export zip — a restored backup can never silently open this surface on
 * another device.
 */
object AutomationAuth {
    private const val PREFS_FILE = "sk_automation" // device-local; never exported
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Default false: nothing is reachable until 白い熊 turns the switch on. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(
        context: Context,
        value: Boolean,
    ) = prefs(context).edit { putBoolean(KEY_ENABLED, value) }

    /** The shared secret; generated on first read so the settings row always shows a value. */
    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
            ?: regenerateToken(context)

    fun regenerateToken(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit { putString(KEY_TOKEN, token) }
        return token
    }

    /**
     * True when [candidate] matches the stored secret, compared constant-time.
     * The switch is checked separately so callers can report "disabled" and
     * "bad token" as distinct failures — they debug differently.
     */
    fun isTokenValid(
        context: Context,
        candidate: String?,
    ): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — the settings row never shows the whole secret. */
    fun abbreviate(token: String): String = if (token.length <= 20) token else "${token.take(8)}…${token.takeLast(8)}"
}
