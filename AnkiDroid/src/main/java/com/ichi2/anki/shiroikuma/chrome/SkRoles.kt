// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma.chrome

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import com.ichi2.anki.common.preferences.sharedPrefs

/**
 * Fork: the store of the colour roles ([SkRole]).
 *
 * A role is an int in the default preferences under its key; a role that is not stored shows
 * its default, which derives from the foundation roles (so changing the accent moves everything
 * that was never set by itself). Living in the default preferences, the roles travel in the UI
 * export with the fonts.
 */
object SkRoles {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cache: IntArray? = null

    /** Bumped whenever a stored role changed; screens built from older colours rebuild */
    @Volatile
    var generation = 1
        private set

    // held strongly: SharedPreferences keeps its listeners weakly
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // the page's own fold state and section order are not colours
            if (key == null || (key.startsWith("sk_") && !key.startsWith("sk_ui_"))) invalidate()
        }

    fun attach(context: Context) {
        val app = context.applicationContext ?: context
        if (appContext === app) return
        appContext = app
        runCatching { app.sharedPrefs().registerOnSharedPreferenceChangeListener(listener) }
        // anything read before the store was reachable was a default
        invalidate()
    }

    private fun invalidate() {
        cache = null
        generation++
    }

    private fun load(): IntArray {
        cache?.let { return it }
        val prefs = runCatching { appContext?.sharedPrefs() }.getOrNull()
        val values = arrayOfNulls<Int>(SkRole.entries.size)

        fun of(role: SkRole): Int {
            values[role.ordinal]?.let { return it }
            val stored =
                runCatching { if (prefs != null && prefs.contains(role.key)) prefs.getInt(role.key, 0) else null }.getOrNull()
            val value = stored ?: skRoleDefault(role, ::of, ::alpha) { mix(of(SkRole.BACKGROUND), of(SkRole.ACCENT), it) }
            values[role.ordinal] = value
            return value
        }
        val loaded = IntArray(SkRole.entries.size) { of(SkRole.entries[it]) }
        cache = loaded
        return loaded
    }

    /** The colour [role] has now: what was set for it, else its default */
    operator fun get(role: SkRole): Int = load()[role.ordinal]

    fun isSet(role: SkRole): Boolean = runCatching { appContext?.sharedPrefs()?.contains(role.key) == true }.getOrDefault(false)

    /** Stores [color] for [role]; null puts its default back */
    fun set(
        role: SkRole,
        color: Int?,
    ) {
        appContext?.sharedPrefs()?.edit(commit = true) { if (color == null) remove(role.key) else putInt(role.key, color) }
        invalidate()
    }

    fun resetAll() {
        appContext?.sharedPrefs()?.edit(commit = true) { for (role in SkRole.entries) remove(role.key) }
        invalidate()
    }

    fun byKey(key: String): SkRole? = SkRole.entries.firstOrNull { it.key == key }

    /** [color] as a disabled control shows it (38 %) */
    fun disabled(color: Int): Int = alpha(color, 0.38f)

    fun ripple(color: Int): Int = ColorUtils.setAlphaComponent(color, 0x4D)

    fun alpha(
        color: Int,
        fraction: Float,
    ): Int = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * fraction).toInt())

    /** [fraction] of the way from [from] to [to], opaque */
    fun mix(
        from: Int,
        to: Int,
        fraction: Float,
    ): Int = ColorUtils.blendARGB(from, to, fraction) or 0xFF000000.toInt()
}
