// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.annotation.SuppressLint
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File

/**
 * When the all-files gate stands in front of the deck list: a collection on
 * shared storage, a platform that has all-files access, and no grant yet —
 * the state a restored install opens in.
 */
@SuppressLint("SdCardPath") // literal device paths are the point of these cases
class ShiroikumaAllFilesGateTest {
    private val appRoots =
        listOf(
            File("/data/user/0/shiroikuma.anki"),
            File("/storage/emulated/0/Android/data/shiroikuma.anki"),
            File("/storage/emulated/0/Android/media/shiroikuma.anki"),
        )

    private fun needs(
        path: String?,
        sdk: Int = 33,
        canRequest: Boolean = true,
        granted: Boolean = false,
        skipped: Boolean = false,
    ) = ShiroikumaAllFilesGate.needsGate(path, appRoots, sdk, canRequest, granted, skipped)

    @Test
    fun `a restored install with its collection under 〇 is gated`() {
        assertThat(needs("/storage/emulated/0/〇/[271] 暗記ドロイド"), equalTo(true))
    }

    @Test
    fun `the upstream default on shared storage is gated too`() {
        assertThat(needs("/storage/emulated/0/AnkiDroid"), equalTo(true))
    }

    @Test
    fun `granted access passes`() {
        assertThat(needs("/storage/emulated/0/〇/[271] 暗記ドロイド", granted = true), equalTo(false))
    }

    @Test
    fun `a skip lets this launch through`() {
        assertThat(needs("/storage/emulated/0/〇/[271] 暗記ドロイド", skipped = true), equalTo(false))
    }

    @Test
    fun `an app-private collection needs no permission`() {
        assertThat(needs("/data/user/0/shiroikuma.anki/files/AnkiDroid"), equalTo(false))
        assertThat(needs("/storage/emulated/0/Android/data/shiroikuma.anki/files/AnkiDroid"), equalTo(false))
        assertThat(needs("/storage/emulated/0/Android/media/shiroikuma.anki/AnkiDroid"), equalTo(false))
    }

    @Test
    fun `a sibling whose name only starts like an app root is still shared storage`() {
        assertThat(needs("/storage/emulated/0/Android/data/shiroikuma.anki.other/AnkiDroid"), equalTo(true))
    }

    @Test
    fun `no collection path yet is upstream's first run, not ours`() {
        assertThat(needs(null), equalTo(false))
        assertThat(needs(""), equalTo(false))
    }

    @Test
    fun `nothing to ask for before Android 11 or without the manifest permission`() {
        assertThat(needs("/storage/emulated/0/〇/[271] 暗記ドロイド", sdk = 29), equalTo(false))
        assertThat(needs("/storage/emulated/0/〇/[271] 暗記ドロイド", canRequest = false), equalTo(false))
    }
}
