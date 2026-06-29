// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
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
        val name = ShiroikumaUi.exportFileName()
        val pattern = Regex("""shiroikuma-anki_settings\.\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}\.json""")
        assertThat("'$name' matches the datetime-stamped pattern", name.matches(pattern), equalTo(true))
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
