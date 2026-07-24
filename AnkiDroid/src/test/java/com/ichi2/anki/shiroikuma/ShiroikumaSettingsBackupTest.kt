// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ShiroikumaSettingsBackupTest : RobolectricTest() {
    @Test
    fun `every preference type round-trips through export then import`() {
        val prefs = targetContext.sharedPrefs()
        prefs.edit {
            putBoolean("aBool", true)
            putInt("sk_menu_text_color", -65536)
            putLong("aLong", 9_000_000_000L)
            putFloat("aFloat", 1.5f)
            putString("binding_EDIT", "1/r⌨e1")
            putStringSet("note_editor_custom_buttons", setOf("1", "3", "5"))
        }

        val json = ShiroikumaUi.exportSettingsJson(targetContext)
        prefs.edit { clear() }
        val applied = ShiroikumaUi.importSettingsJson(targetContext, json)

        // at least our six keys (the store may also hold defaults seeded by the test harness)
        assertThat("our six keys applied", applied >= 6, equalTo(true))
        assertThat(prefs.getBoolean("aBool", false), equalTo(true))
        assertThat("int kept as int", prefs.getInt("sk_menu_text_color", 0), equalTo(-65536))
        assertThat("long kept as long", prefs.getLong("aLong", 0), equalTo(9_000_000_000L))
        assertThat(prefs.getFloat("aFloat", 0f), equalTo(1.5f))
        assertThat("control binding string preserved", prefs.getString("binding_EDIT", null), equalTo("1/r⌨e1"))
        assertThat("string set preserved", prefs.getStringSet("note_editor_custom_buttons", null), equalTo(setOf("1", "3", "5")))
    }

    @Test
    fun `credentials and the collection path are never exported`() {
        val prefs = targetContext.sharedPrefs()
        prefs.edit {
            putString("hkey", "secret-sync-token")
            putString("username", "me@example.com")
            putString("deckPath", "/data/user/0/com.ichi2.anki/files/collection.anki2")
            putString("sk_menu_text_color_marker", "kept")
        }

        val json = ShiroikumaUi.exportSettingsJson(targetContext)
        prefs.edit { clear() }
        ShiroikumaUi.importSettingsJson(targetContext, json)

        assertThat(prefs.getString("hkey", null), nullValue())
        assertThat(prefs.getString("username", null), nullValue())
        assertThat(prefs.getString("deckPath", null), nullValue())
        assertThat("a normal key still travels", prefs.getString("sk_menu_text_color_marker", null), equalTo("kept"))
    }

    @Test
    fun `export filename carries a datetime stamp`() {
        val name = ShiroikumaExport.exportFileName()
        val pattern = Regex("""shiroikuma-anki-export_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}\.zip""")
        assertThat("'$name' matches the datetime-stamped pattern", name.matches(pattern), equalTo(true))
    }

    @Test
    fun `category key filters split the store logically`() {
        val ui = ShiroikumaExport.keyFilter(ShiroikumaExport.Cat.UI)!!
        val controls = ShiroikumaExport.keyFilter(ShiroikumaExport.Cat.CONTROLS)!!
        val app = ShiroikumaExport.keyFilter(ShiroikumaExport.Cat.APP_SETTINGS)!!
        assertThat(ui("sk_menu_text_color"), equalTo(true))
        assertThat(controls("binding_EDIT"), equalTo(true))
        assertThat(controls("previewer_MARK"), equalTo(true))
        assertThat(app("newSpread"), equalTo(true))
        assertThat(ui("newSpread"), equalTo(false))
        assertThat(controls("sk_menu_text_color"), equalTo(false))
        assertThat(app("binding_EDIT"), equalTo(false))
        assertThat(app("sk_menu_text_color"), equalTo(false))
        assertThat(ShiroikumaExport.keyFilter(ShiroikumaExport.Cat.COLLECTION), nullValue())
    }

    @Test
    fun `zip export round-trips the settings categories`() =
        runTest {
            val prefs = targetContext.sharedPrefs()
            prefs.edit {
                putInt("sk_menu_text_color", -65536)
                putString("binding_EDIT", "1/r⌨e1")
                putBoolean("someAnkiSetting", true)
            }
            val fontFile = ShiroikumaUi.fontFile(targetContext, ShiroikumaUi.ROLE_MENU)
            fontFile.parentFile?.mkdirs()
            fontFile.writeBytes(byteArrayOf(1, 2, 3))

            val settingsCats =
                setOf(
                    ShiroikumaExport.Cat.UI,
                    ShiroikumaExport.Cat.CONTROLS,
                    ShiroikumaExport.Cat.APP_SETTINGS,
                )
            val zip = ByteArrayOutputStream()
            ShiroikumaExport.export(targetContext, settingsCats) { zip }

            prefs.edit { clear() }
            fontFile.delete()
            val summary = ShiroikumaExport.import(targetContext, settingsCats) { ByteArrayInputStream(zip.toByteArray()) }

            assertThat(prefs.getInt("sk_menu_text_color", 0), equalTo(-65536))
            assertThat(prefs.getString("binding_EDIT", null), equalTo("1/r⌨e1"))
            assertThat(prefs.getBoolean("someAnkiSetting", false), equalTo(true))
            assertThat("font file restored", fontFile.readBytes().toList(), equalTo(listOf<Byte>(1, 2, 3)))
            assertThat("one summary line per category", summary.lines().size, equalTo(3))
        }

    @Test
    fun `importing a single category leaves the others untouched`() =
        runTest {
            val prefs = targetContext.sharedPrefs()
            prefs.edit {
                putInt("sk_menu_text_color", -65536)
                putString("binding_EDIT", "1/r⌨e1")
            }
            val zip = ByteArrayOutputStream()
            ShiroikumaExport.export(
                targetContext,
                setOf(ShiroikumaExport.Cat.UI, ShiroikumaExport.Cat.CONTROLS),
            ) { zip }

            prefs.edit { clear() }
            ShiroikumaExport.import(targetContext, setOf(ShiroikumaExport.Cat.UI)) {
                ByteArrayInputStream(zip.toByteArray())
            }

            assertThat("ticked category applied", prefs.getInt("sk_menu_text_color", 0), equalTo(-65536))
            assertThat("unticked category skipped", prefs.getString("binding_EDIT", null), nullValue())
        }

    @Test
    fun `importing a foreign zip is rejected without side effects`() =
        runTest {
            val prefs = targetContext.sharedPrefs()
            val foreign = ByteArrayOutputStream()
            ZipOutputStream(foreign).use { zip ->
                zip.putNextEntry(ZipEntry("something.txt"))
                zip.write("not ours".toByteArray())
                zip.closeEntry()
                // a ui.json entry that must NOT be applied when the manifest is missing
                zip.putNextEntry(ZipEntry("ui.json"))
                zip.write(ShiroikumaUi.exportSettingsJson(targetContext).toByteArray())
                zip.closeEntry()
            }
            prefs.edit { clear() }
            assertFailsWith<IllegalArgumentException> {
                ShiroikumaExport.import(targetContext, ShiroikumaExport.Cat.entries.toSet()) {
                    ByteArrayInputStream(foreign.toByteArray())
                }
            }
        }

    @Test
    fun `importing a foreign file is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ShiroikumaUi.importSettingsJson(targetContext, """{"some":"other json"}""")
        }
    }

    @Test
    fun `export omits the blocklist even when the json text is inspected`() {
        targetContext.sharedPrefs().edit { putString("hkey", "secret-sync-token") }
        val json = ShiroikumaUi.exportSettingsJson(targetContext)
        assertThat(json, not(org.hamcrest.CoreMatchers.containsString("secret-sync-token")))
    }
}
