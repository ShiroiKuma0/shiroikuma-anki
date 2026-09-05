// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.app.Activity
import android.content.Context
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.exception.ManuallyReportedException
import com.ichi2.anki.servicelayer.ThrowableFilterService
import timber.log.Timber

/**
 * Fork: the crash reporter which reports nothing off the device.
 *
 * Upstream implements [CrashReporter] with ACRA, which uploads a crash — stack
 * trace, 500 lines of logcat, the shared preferences, the device details and an
 * install UUID — to `ankidroid.org/acra/report`. Tracker scanners list ACRA as a
 * tracker for exactly that reason, and a fork's crashes are not upstream's to
 * triage in any case, so the library is gone from the build and this takes its
 * place.
 *
 * Every method of upstream's interface stays, because the app calls them from
 * everywhere; each one is inert. An exception handed to us is logged and, if the
 * user opted in to analytics, counted there — nothing else leaves the phone.
 */
private object ShiroikumaCrashReporter : CrashReporter {
    /** Used when we don't have an exception, but know something is wrong. */
    override fun sendExceptionReport(
        message: String?,
        origin: String?,
    ) = sendExceptionReport(ManuallyReportedException(message), origin)

    override fun sendExceptionReport(
        e: Throwable,
        origin: String?,
        additionalInfo: String?,
        onlyIfSilent: Boolean,
    ) = report(e, origin, additionalInfo)

    override fun sendExceptionReport(
        e: Throwable,
        origin: String?,
        additionalInfo: String?,
        onlyIfSilent: Boolean,
        context: Context,
    ) = report(e, origin, additionalInfo)

    private fun report(
        e: Throwable,
        origin: String?,
        additionalInfo: String?,
    ) {
        // the filter keeps sync server messages and other PII out of reporting,
        // and analytics is reporting too
        if (!ThrowableFilterService.shouldDiscardThrowable(e)) {
            Analytics.sendAnalyticsException(e, false)
        }
        AnkiDroidApp.sentExceptionReportHack = true
        Timber.w(e, "exception report from '%s'%s", origin ?: "", additionalInfo?.let { ": $it" } ?: "")
    }

    /** No report is ever queued, so there is nothing for the caller to announce. */
    override fun sendReport(activity: Activity): Boolean = false

    /** Reporting is off and cannot be switched on: there is no reporter to configure. */
    override fun setReportingMode(value: String) = Unit

    override fun onPreferenceChanged(
        ctx: Context,
        newValue: String,
    ) = Unit

    /** ACRA's rate limiter kept its state on disk; nothing writes that file now. */
    override fun deleteLimiterData(context: Context) = Unit

    override fun isEnabled(
        context: Context,
        defaultValue: Boolean,
    ): Boolean = false
}

/**
 * Registers the fork's do-nothing reporter as the global [CrashReportService]
 * implementation. Called from `AnkiDroidApp.onCreate` where upstream initializes ACRA.
 */
fun initializeCrashReporter() {
    CrashReportService.setReporter(ShiroikumaCrashReporter)
}
