// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import kotlin.test.assertFailsWith

/**
 * The sister-app automation contract (v2): the gate — an open switch and an
 * opt-in token — the category enumeration, the `items` selection, and the data
 * door's caller check. The export itself needs a live broadcast (`goAsync`) and
 * a real directory, so it is verified on the device against the acceptance
 * checklist.
 */
@RunWith(AndroidJUnit4::class)
class ShiroikumaAutomationTest : RobolectricTest() {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private val replyAction = "shiroikuma.jiyusagyoban.action.INTENT_REPLY"

    @Before
    fun clearAutomationState() {
        // cleared rather than closed: v2's defaults are what a fresh install
        // answers with, and those are what the batch depends on
        targetContext
            .getSharedPreferences("sk_automation", 0)
            .edit()
            .clear()
            .commit()
        shadowOf(app).clearBroadcastIntents()
    }

    private fun send(
        action: String,
        token: String,
        vararg extras: Pair<String, String>,
    ) {
        val intent =
            Intent(action).apply {
                putExtra(StateExportReceiver.EXTRA_TOKEN, token)
                putExtra(StateExportReceiver.EXTRA_REPLY_ACTION, replyAction)
                putExtra(StateExportReceiver.EXTRA_REPLY_PACKAGE, "shiroikuma.jiyusagyoban")
                putExtra(StateExportReceiver.EXTRA_REPLY_ID, "test-1")
                for ((key, value) in extras) putExtra(key, value)
            }
        StateExportReceiver().onReceive(targetContext, intent)
    }

    /** The `result` of the single reply broadcast, or null when none was sent */
    private fun reply(): String? =
        shadowOf(app)
            .broadcastIntents
            .filter { it.action == replyAction }
            .also { assertThat("exactly one terminal reply", it.size <= 1, equalTo(true)) }
            .lastOrNull()
            ?.getStringExtra(StateExportReceiver.EXTRA_RESULT)

    @Test
    fun `the surface answers out of the box`() {
        // v2: nothing to turn on and nothing to paste — a phone that has just
        // been wiped is exactly where this has to work
        send(StateExportReceiver.listCategoriesAction(targetContext), "")
        assertThat(reply()!!.startsWith("OK:"), equalTo(true))
    }

    @Test
    fun `the master switch still closes the app off`() {
        AutomationAuth.setEnabled(targetContext, false)
        send(StateExportReceiver.listCategoriesAction(targetContext), AutomationAuth.token(targetContext))
        assertThat(reply(), equalTo("ERROR:automation disabled"))
    }

    @Test
    fun `a token sent to an app that does not require one is ignored, never refused`() {
        // tokens outlive the settings they were pasted for; refusing one would
        // turn "白い熊 turned a switch off" into "half the batch fails"
        send(StateExportReceiver.listCategoriesAction(targetContext), "a stale token from last year")
        assertThat(reply()!!.startsWith("OK:"), equalTo(true))
    }

    @Test
    fun `a wrong token is refused once the token is required, and distinctly from a closed switch`() {
        AutomationAuth.setRequireToken(targetContext, true)
        send(StateExportReceiver.listCategoriesAction(targetContext), "wrong")
        assertThat(reply(), equalTo("ERROR:bad token"))
    }

    @Test
    fun `an empty token is refused once the token is required`() {
        AutomationAuth.setRequireToken(targetContext, true)
        send(StateExportReceiver.listCategoriesAction(targetContext), "")
        assertThat(reply(), equalTo("ERROR:bad token"))
    }

    @Test
    fun `the right token passes once the token is required`() {
        AutomationAuth.setRequireToken(targetContext, true)
        send(StateExportReceiver.listCategoriesAction(targetContext), AutomationAuth.token(targetContext))
        assertThat(reply()!!.startsWith("OK:"), equalTo(true))
    }

    @Test
    fun `the gate is one function, and reports its two failures distinctly`() {
        assertThat(AutomationAuth.refuse(targetContext, null), nullValue())
        AutomationAuth.setRequireToken(targetContext, true)
        assertThat(AutomationAuth.refuse(targetContext, null), equalTo("ERROR:bad token"))
        assertThat(AutomationAuth.refuse(targetContext, AutomationAuth.token(targetContext)), nullValue())
        AutomationAuth.setEnabled(targetContext, false)
        assertThat(
            "a closed switch outranks a good token",
            AutomationAuth.refuse(targetContext, AutomationAuth.token(targetContext)),
            equalTo("ERROR:automation disabled"),
        )
    }

    @Test
    fun `a cancel is answered with nothing at all, refused or not`() {
        // fire-and-forget: the one terminal reply belongs to the export it
        // stops, and 自由作業盤 fires it without knowing how far we got
        send(StateExportReceiver.cancelExportAction(targetContext), "")
        assertThat(reply(), nullValue())

        AutomationAuth.setEnabled(targetContext, false)
        send(StateExportReceiver.cancelExportAction(targetContext), "")
        assertThat(reply(), nullValue())
    }

    @Test
    fun `the category list is one id-tab-label line per category, media under the collection`() {
        send(StateExportReceiver.listCategoriesAction(targetContext), AutomationAuth.token(targetContext))

        val result = reply()!!
        assertThat(result.startsWith("OK:"), equalTo(true))
        val lines = result.removePrefix("OK:").lines()
        assertThat("every category plus the media sub-option", lines.size, equalTo(ShiroikumaExport.Cat.entries.size + 1))

        val ids = lines.map { it.split("\t")[0] }
        assertThat(ids.take(2), equalTo(listOf("collection", ShiroikumaExport.MEDIA_ITEM_ID)))
        assertThat("ids are the zip entry names", ids.filter { '.' !in it }, equalTo(ShiroikumaExport.Cat.entries.map { it.id }))
        for (line in lines) {
            val fields = line.split("\t")
            assertThat("'$line' carries a label", fields[1].isNotEmpty(), equalTo(true))
        }
        // the sub-option names its parent in a third field; parents leave it
        // empty, and the fourth field says whether the item starts ticked
        assertThat(lines[1].split("\t")[2], equalTo("collection"))
        assertThat(lines[0].split("\t")[2], equalTo(""))
        val flags = lines.map { it.split("\t")[3] }
        assertThat("the media folder starts unticked", flags[1], equalTo("off"))
        assertThat(
            "everything else starts ticked",
            flags.filterIndexed { i, _ -> i != 1 }.distinct(),
            equalTo(listOf("on")),
        )
    }

    @Test
    fun `an unknown items id is refused before anything is written`() {
        send(
            StateExportReceiver.exportStateAction(targetContext),
            AutomationAuth.token(targetContext),
            StateExportReceiver.EXTRA_ITEMS to "bogus",
        )
        assertThat(reply(), equalTo("ERROR:unknown category in items: bogus"))
    }

    @Test
    fun `an unknown action is refused`() {
        send("${targetContext.packageName}.action.SOMETHING_ELSE", AutomationAuth.token(targetContext))
        assertThat(reply()!!.startsWith("ERROR:unknown action:"), equalTo(true))
    }

    @Test
    fun `an absent items list selects our default set, not everything`() {
        // the contract: absent items means the items we report as `on`, which
        // is every category but NOT the media folder — by far the largest part
        // of the export, and re-obtainable by syncing
        val all = ShiroikumaExport.parseItems("")
        assertThat(
            all.cats,
            equalTo(
                ShiroikumaExport.Cat.entries
                    .filter { it.defaultOn }
                    .toSet(),
            ),
        )
        assertThat(all.includeMedia, equalTo(ShiroikumaExport.MEDIA_DEFAULT_ON))
        assertThat("no category is opt-out today", all.cats, equalTo(ShiroikumaExport.Cat.entries.toSet()))
    }

    @Test
    fun `a parent id alone means that category's own data only`() {
        val collectionOnly = ShiroikumaExport.parseItems("collection")
        assertThat(collectionOnly.cats, equalTo(setOf(ShiroikumaExport.Cat.COLLECTION)))
        assertThat("media is a separate, unticked sub-option", collectionOnly.includeMedia, equalTo(false))

        val withMedia = ShiroikumaExport.parseItems("collection,collection.media")
        assertThat(withMedia.cats, equalTo(setOf(ShiroikumaExport.Cat.COLLECTION)))
        assertThat(withMedia.includeMedia, equalTo(true))

        // a child on its own implies its parent
        val mediaOnly = ShiroikumaExport.parseItems(ShiroikumaExport.MEDIA_ITEM_ID)
        assertThat(mediaOnly.cats, equalTo(setOf(ShiroikumaExport.Cat.COLLECTION)))
        assertThat(mediaOnly.includeMedia, equalTo(true))
    }

    @Test
    fun `a subset of ids selects exactly those categories`() {
        val selection = ShiroikumaExport.parseItems(" ui , controls ")
        assertThat(selection.cats, equalTo(setOf(ShiroikumaExport.Cat.UI, ShiroikumaExport.Cat.CONTROLS)))
    }

    @Test
    fun `parsing refuses an unknown id`() {
        val e = assertFailsWith<IllegalArgumentException> { ShiroikumaExport.parseItems("settings,bogus") }
        assertThat(e.message, equalTo("unknown category in items: settings,bogus"))
    }

    @Test
    fun `the token is 24 random bytes, hex, and stable once generated`() {
        val first = AutomationAuth.token(targetContext)
        assertThat(first.length, equalTo(48))
        assertThat(first.matches(Regex("[0-9a-f]{48}")), equalTo(true))
        assertThat("re-reading returns the same secret", AutomationAuth.token(targetContext), equalTo(first))

        val second = AutomationAuth.regenerateToken(targetContext)
        assertThat(second, not(equalTo(first)))
        assertThat("the old copy stops working", AutomationAuth.isTokenValid(targetContext, first), equalTo(false))
        assertThat(AutomationAuth.isTokenValid(targetContext, second), equalTo(true))
        assertThat(AutomationAuth.isTokenValid(targetContext, null), equalTo(false))
        assertThat(AutomationAuth.isTokenValid(targetContext, ""), equalTo(false))
    }

    @Test
    fun `the switch defaults to on and the token to not-required`() {
        // v2: a pasted secret cannot survive a wipe, and a clean phone is
        // exactly where 応用管理 has to be able to restore this app
        assertThat(AutomationAuth.enabled(targetContext), equalTo(true))
        assertThat(AutomationAuth.requireToken(targetContext), equalTo(false))
    }

    @Test
    fun `the data door refuses a caller it cannot identify`() {
        // no calling package at all, which is what a direct call() looks like
        val provider = Robolectric.buildContentProvider(AutomationProvider::class.java).create().get()
        val result = provider.call(AutomationProvider.METHOD_DESCRIBE, null, null)
        assertThat(result.getString(AutomationProvider.KEY_RESULT), equalTo("ERROR:caller unknown"))
    }

    @Test
    fun `the data door refuses a caller that is not on the list`() {
        assertThat(
            AutomationCallers.verify(targetContext, "shiroikuma.evil"),
            equalTo(AutomationCallers.Verdict.Refused("ERROR:caller not permitted: shiroikuma.evil") as AutomationCallers.Verdict),
        )
        assertThat(
            "a prefix is not an identity",
            AutomationCallers.verify(targetContext, "shiroikuma.oyokanri.evil"),
            equalTo(
                AutomationCallers.Verdict.Refused("ERROR:caller not permitted: shiroikuma.oyokanri.evil")
                    as AutomationCallers.Verdict,
            ),
        )
        assertThat(
            AutomationCallers.verify(targetContext, null),
            equalTo(AutomationCallers.Verdict.Refused("ERROR:caller unknown") as AutomationCallers.Verdict),
        )
    }

    @Test
    fun `a permitted caller name still has to survive the uid check`() {
        // the name is on the list, but this process is not that package: the
        // kernel's answer is what decides, and it cannot be borrowed
        val verdict = AutomationCallers.verify(targetContext, "shiroikuma.oyokanri")
        assertThat(
            verdict,
            equalTo(AutomationCallers.Verdict.Refused("ERROR:caller uid mismatch: shiroikuma.oyokanri") as AutomationCallers.Verdict),
        )
    }

    @Test
    fun `the token never travels in an export`() {
        val token = AutomationAuth.token(targetContext)
        val json = ShiroikumaUi.exportSettingsJson(targetContext)
        assertThat(json, not(org.hamcrest.CoreMatchers.containsString(token)))
        assertThat(json, not(org.hamcrest.CoreMatchers.containsString("automation_token")))
    }

    @Test
    fun `a zip export carries neither the token nor the switch`() =
        kotlinx.coroutines.test.runTest {
            val token = AutomationAuth.token(targetContext)
            val zip = ByteArrayOutputStream()
            ShiroikumaExport.export(
                targetContext,
                setOf(ShiroikumaExport.Cat.UI, ShiroikumaExport.Cat.CONTROLS, ShiroikumaExport.Cat.APP_SETTINGS),
            ) { zip }

            val bytes = zip.toByteArray().decodeToString()
            assertThat(bytes, not(org.hamcrest.CoreMatchers.containsString(token)))
            assertThat(bytes, not(org.hamcrest.CoreMatchers.containsString("automation_")))
        }

    @Test
    fun `the reply is silent when the caller gave no reply target`() {
        StateExportReceiver().onReceive(
            targetContext,
            Intent(StateExportReceiver.listCategoriesAction(targetContext)).apply {
                putExtra(StateExportReceiver.EXTRA_TOKEN, AutomationAuth.token(targetContext))
            },
        )
        assertThat(reply(), nullValue())
    }

    @Test
    fun `human sizes read the same in every locale`() {
        assertThat(StateExportReceiver.humanSize(0), equalTo("0 B"))
        assertThat(StateExportReceiver.humanSize(512), equalTo("512 B"))
        assertThat(StateExportReceiver.humanSize(2048), equalTo("2.0 KB"))
        assertThat(StateExportReceiver.humanSize(4_823_711), equalTo("4.6 MB"))
        assertThat(StateExportReceiver.humanSize(1_288_490_189), equalTo("1.20 GB"))
    }
}
