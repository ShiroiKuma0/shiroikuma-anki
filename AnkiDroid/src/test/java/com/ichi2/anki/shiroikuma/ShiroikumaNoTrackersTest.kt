// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/**
 * Fork: nothing in the app may report to a server behind the user's back.
 *
 * Upstream's crash reporter is ACRA, which tracker scanners list as a tracker
 * because it uploads crashes — logcat, shared preferences, device details, an
 * install UUID — to `ankidroid.org/acra/report`. The fork removed the library
 * and replaced the reporter with [initializeCrashReporter]'s inert one.
 *
 * A rebase is what would bring ACRA back: upstream's `AcraCrashReporter.kt` and
 * its four `ch.acra:*` dependencies return with any conflict resolved the lazy
 * way. This test fails loudly if that happens.
 */
class ShiroikumaNoTrackersTest {
    @Test
    fun `ACRA is not on the classpath`() {
        assertThat(
            "ch.acra:* must stay out of the build: it uploads crash reports to upstream's server. " +
                "If a rebase brought it back, drop the dependencies again and re-check " +
                "AnkiDroidApp, the manifest and the general settings screen",
            classIsPresent("org.acra.ACRA"),
            equalTo(false),
        )
    }

    @Test
    fun `the crash report dialog is gone with it`() {
        assertThat(
            "the ACRA report dialog was deleted along with the library",
            classIsPresent("com.ichi2.anki.analytics.AnkiDroidCrashReportDialog"),
            equalTo(false),
        )
    }

    private fun classIsPresent(name: String): Boolean =
        try {
            Class.forName(name, false, javaClass.classLoader)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
}
