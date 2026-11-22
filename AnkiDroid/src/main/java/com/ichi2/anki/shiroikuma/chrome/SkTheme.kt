// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma.chrome

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.ichi2.anki.R
import com.ichi2.anki.shiroikuma.ShiroikumaDialogs
import com.ichi2.compose.ui.dialogs.ForkDialogChrome
import com.ichi2.compose.ui.dialogs.ForkDialogPalette
import java.util.WeakHashMap

/**
 * Fork: puts the black-yellow theme on a context. Called for every themed context from
 * `Themes.setTheme`, whatever theme the picker names ("always", 白い熊, 2026-11-22):
 *  - the stored role colours are laid over the role colour resources ([SkPalette]);
 *  - `ThemeOverlay.Shiroikuma` points every colour attribute at a role and names the fork's
 *    view inflater, which draws buttons, switches, marks and dialogs;
 *  - the Compose dialogs get their colours.
 *
 * An activity built from older colours recreates itself when it is next resumed.
 */
object SkTheme {
    private val builtWith = WeakHashMap<Activity, Int>()

    @Volatile
    private var watching = false

    fun install(context: Context) {
        SkRoles.attach(context)
        SkPalette.apply(context)
        context.theme.applyStyle(R.style.ThemeOverlay_Shiroikuma, true)
        if (ForkDialogChrome.palette == null) {
            ForkDialogChrome.palette = { c ->
                val r = ShiroikumaDialogs.roles(c)
                ForkDialogPalette(r.ground, r.frame, r.title, r.text, r.buttonText, r.buttonFrame, r.delete)
            }
        }
        if (context is Activity) {
            synchronized(builtWith) { builtWith[context] = SkRoles.generation }
            watch(context.application)
        }
    }

    private fun watch(application: Application) {
        if (watching) return
        watching = true
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    val built = synchronized(builtWith) { builtWith[activity] } ?: return
                    if (built != SkRoles.generation) activity.recreate()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    /** The activity showing a colour change itself: it already redrew, so it is up to date */
    fun markCurrent(activity: Activity) {
        synchronized(builtWith) { builtWith[activity] = SkRoles.generation }
    }
}
