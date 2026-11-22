// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma.chrome

import android.content.Context
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.material.color.SkColorLoaderBridge
import com.ichi2.anki.common.utils.android.isRobolectric
import timber.log.Timber
import java.util.WeakHashMap
import com.ichi2.anki.common.android.R as CommonR

/**
 * Fork: how a colour role reaches the XML this fork does not draw itself (the "mixed way",
 * decided by 白い熊 on 2026-11-22).
 *
 * The fork is rebased onto upstream, so upstream's layouts and themes are not edited for colour.
 * `ThemeOverlay.Shiroikuma` (sk_theme.xml) points every colour attribute they read at a role
 * colour resource, `@color/sk_role_…`, and this object lays the stored colours over those
 * resources with a ResourcesLoader (Android 11 and later), together with the few colour names
 * upstream's layouts use directly. Below Android 11 the values in sk_roles.xml show: each role's
 * default.
 *
 * [apply] must run on a context before anything is inflated from it; SkTheme.install does that.
 */
object SkPalette {
    private class Applied(
        val loader: ResourcesLoader,
        val generation: Int,
    )

    private val applied = WeakHashMap<Resources, Applied>()

    @Synchronized
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        // Robolectric's asset manager cannot apply a style once a loader is attached; the
        // role resources then show their defaults, as they do below Android 11
        if (isRobolectric) return
        applyTo(context)
        val application = context.applicationContext
        if (application != null && application !== context) applyTo(application)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyTo(context: Context) {
        val resources = context.resources
        val generation = SkRoles.generation
        val current = applied[resources]
        if (current != null && current.generation == generation) return
        try {
            val loader = SkColorLoaderBridge.create(context, resourceColors()) ?: return
            if (current != null) resources.removeLoaders(current.loader)
            resources.addLoaders(loader)
            applied[resources] = Applied(loader, generation)
        } catch (e: Throwable) {
            Timber.w(e, "Could not lay the colour roles over the resources")
        }
    }

    private fun resourceColors(): Map<Int, Int> {
        val colors = HashMap<Int, Int>()
        for (role in SkRole.entries) colors[role.colorRes] = SkRoles[role]
        // Colour names upstream's layouts and shapes use directly, with no night-qualified copy
        for ((res, role) in UPSTREAM_NAMES) colors[res] = SkRoles[role]
        return colors
    }

    private val UPSTREAM_NAMES =
        listOf(
            CommonR.color.material_blue_500 to SkRole.ACCENT,
            CommonR.color.material_light_blue_500 to SkRole.ACCENT,
            CommonR.color.material_blue_400 to SkRole.ACCENT,
            CommonR.color.white to SkRole.TEXT,
        )
}
