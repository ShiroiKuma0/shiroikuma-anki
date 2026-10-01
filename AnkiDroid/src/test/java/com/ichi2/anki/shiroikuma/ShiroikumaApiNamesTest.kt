// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.RobolectricTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The public card API must name this app, not upstream's: the manifest
 * declares the provider and its permission from `${applicationId}`, and an
 * `:api` constant still reading `com.ichi2.anki…` leaves every URI unmatched
 * and every caller asking for a permission nobody defines.
 */
@RunWith(AndroidJUnit4::class)
class ShiroikumaApiNamesTest : RobolectricTest() {
    @Test
    fun `the provider authority is this app's`() {
        assertThat(FlashCardsContract.AUTHORITY, equalTo("${targetContext.packageName}.flashcards"))
    }

    @Test
    fun `the read-write permission is this app's`() {
        assertThat(
            FlashCardsContract.READ_WRITE_PERMISSION,
            equalTo("${targetContext.packageName}.permission.READ_WRITE_DATABASE"),
        )
    }
}
