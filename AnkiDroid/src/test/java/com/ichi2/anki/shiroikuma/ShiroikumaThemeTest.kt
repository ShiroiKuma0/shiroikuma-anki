// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.shiroikuma.chrome.MenuPills
import com.ichi2.anki.shiroikuma.chrome.Pills
import com.ichi2.anki.shiroikuma.chrome.SkIcons
import com.ichi2.anki.shiroikuma.chrome.SkPageState
import com.ichi2.anki.shiroikuma.chrome.SkPages
import com.ichi2.anki.shiroikuma.chrome.SkRole
import com.ichi2.anki.shiroikuma.chrome.SkRoles
import com.ichi2.anki.shiroikuma.chrome.SkSection
import com.ichi2.anki.shiroikuma.chrome.SkWidgets
import com.ichi2.themes.Themes
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** The black-yellow theme: the role store, the theme overlay and the widgets drawn from roles */
@RunWith(AndroidJUnit4::class)
class ShiroikumaThemeTest : RobolectricTest() {
    @Before
    fun attach() = SkRoles.attach(targetContext)

    private fun themedActivity(): AppCompatActivity {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        saveControllerForCleanup(controller)
        Themes.setTheme(controller.get())
        return controller.setup().get()
    }

    @Test
    fun `the defaults are the two primaries`() {
        assertThat(SkRoles[SkRole.BACKGROUND], equalTo(0xFF000000.toInt()))
        assertThat(SkRoles[SkRole.ACCENT], equalTo(0xFFFFFF00.toInt()))
        assertThat(SkRoles[SkRole.TEXT], equalTo(0xFFFFFF00.toInt()))
        assertThat("the dim gold of explanations", SkRoles[SkRole.NOTE], equalTo(0xFFC8C800.toInt()))
        assertThat("a switch that is off", SkRoles[SkRole.SWITCH_OFF], equalTo(0xFF8C8C00.toInt()))
    }

    @Test
    fun `a role that was never set follows the foundation, a set one keeps its own colour`() {
        SkRoles.set(SkRole.DIALOG_TITLE, Color.CYAN)
        SkRoles.set(SkRole.ACCENT, Color.GREEN)
        assertThat("derived through TEXT from ACCENT", SkRoles[SkRole.DECK_NAME], equalTo(Color.GREEN))
        assertThat(SkRoles[SkRole.DIALOG_TITLE], equalTo(Color.CYAN))
        assertThat(SkRoles.isSet(SkRole.DIALOG_TITLE), equalTo(true))

        SkRoles.set(SkRole.DIALOG_TITLE, null)
        assertThat("reset per role", SkRoles[SkRole.DIALOG_TITLE], equalTo(Color.GREEN))
        SkRoles.resetAll()
        assertThat(SkRoles[SkRole.DECK_NAME], equalTo(0xFFFFFF00.toInt()))
    }

    @Test
    fun `the roles the older pages stored keep their keys`() {
        assertThat(SkRole.DRAWER_GROUND.key, equalTo(targetContext.getString(R.string.pref_sk_menu_background_key)))
        assertThat(SkRole.DECK_NAME.key, equalTo(targetContext.getString(R.string.pref_sk_deck_name_color_key)))
        assertThat(SkRole.DIALOG_FRAME.key, equalTo(targetContext.getString(R.string.pref_sk_dialog_frame_key)))
        assertThat(
            "no two roles share a key",
            SkRole.entries
                .map { it.key }
                .toSet()
                .size,
            equalTo(SkRole.entries.size),
        )
    }

    @Test
    fun `every role belongs to a section of the page and has a label`() {
        for (role in SkRole.entries) {
            assertThat(role.name, targetContext.getString(role.labelRes).isNotBlank(), equalTo(true))
            assertThat(role.name, role.section in SkSection.entries, equalTo(true))
        }
    }

    @Test
    fun `the overlay points the theme's colours at roles`() {
        val activity = themedActivity()
        val tv = TypedValue()
        for ((attr, role) in listOf(
            androidx.appcompat.R.attr.colorPrimary to SkRole.ACCENT,
            android.R.attr.colorBackground to SkRole.BACKGROUND,
            com.google.android.material.R.attr.colorOnSurface to SkRole.TEXT,
            com.google.android.material.R.attr.colorSurfaceContainerHigh to SkRole.BACKGROUND,
        )) {
            assertThat(activity.theme.resolveAttribute(attr, tv, true), equalTo(true))
            assertThat(role.name, tv.data, equalTo(SkRoles[role]))
        }
    }

    @Test
    fun `a button is an outlined pill, one upstream drew filled is the heavy one`() {
        val activity = themedActivity()
        val main = MaterialButton(activity).apply { text = "Save" }
        val other = MaterialButton(activity).apply { text = "Cancel" }
        SkWidgets.button(main, wasFilled = true)
        SkWidgets.button(other, wasFilled = false)
        for (button in listOf(main, other)) {
            assertThat("never filled", button.backgroundTintList!!.defaultColor, equalTo(Color.TRANSPARENT))
            assertThat(button.currentTextColor, equalTo(SkRoles[SkRole.PILL_TEXT]))
        }
        assertThat(main.strokeWidth, equalTo(Pills.dp(activity, Pills.MAIN_DP)))
        assertThat(other.strokeWidth, equalTo(Pills.dp(activity, Pills.REGULAR_DP)))
        assertThat(main.typeface, equalTo(Typeface.DEFAULT_BOLD))
    }

    @Test
    fun `a button with a colour of its own lends it to its frame`() {
        val activity = themedActivity()
        val again = MaterialButton(activity).apply { text = "Again" }
        again.setTextColor(SkRoles[SkRole.EASE_AGAIN])
        SkWidgets.button(again, wasFilled = false)
        assertThat(again.currentTextColor, equalTo(SkRoles[SkRole.EASE_AGAIN]))
        assertThat(again.strokeColor!!.defaultColor, equalTo(SkRoles[SkRole.EASE_AGAIN]))
    }

    @Test
    fun `a switch keeps its class and is an outline with a dot`() {
        val switch = MaterialSwitch(themedActivity())
        SkWidgets.switch(switch)
        assertThat(switch.trackDrawable is GradientDrawable, equalTo(true))
        assertThat(switch.thumbDrawable is LayerDrawable, equalTo(true))
        val states = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
        assertThat(switch.trackTintList!!.getColorForState(states, 0), equalTo(SkRoles[SkRole.SWITCH_ON]))
        assertThat(
            switch.trackTintList!!.getColorForState(intArrayOf(android.R.attr.state_enabled), 0),
            equalTo(SkRoles[SkRole.SWITCH_OFF]),
        )
    }

    @Test
    fun `a menu row's icon and colour are told from the item's name`() {
        val delete = MenuPills.of(targetContext, R.id.action_delete)
        assertThat(delete.destructive, equalTo(true))
        assertThat(delete.does, equalTo(SkIcons.Does.DELETE))
        val rename = MenuPills.of(targetContext, R.id.action_rename)
        assertThat(rename.destructive, equalTo(false))
        assertThat(rename.does, equalTo(SkIcons.Does.MESSAGE))
        assertThat("an item without an id is a plain row", MenuPills.of(targetContext, 0).does, equalTo(SkIcons.Does.VIEW))
    }

    @Test
    fun `sections are folded by default and a saved order survives a new section`() {
        assertThat(SkPageState.isOpen(targetContext, "dialogs"), equalTo(false))
        SkPageState.setOpen(targetContext, "dialogs", true)
        assertThat(SkPageState.isOpen(targetContext, "dialogs"), equalTo(true))

        val built = listOf("a", "b", "c", "d")
        assertThat(SkPageState.merge(emptyList(), built), equalTo(built))
        assertThat(
            "c is new: after its built predecessor b",
            SkPageState.merge(listOf("d", "b", "a"), built),
            equalTo(listOf("d", "b", "c", "a")),
        )
        assertThat(
            "a removed section is dropped",
            SkPageState.merge(listOf("x", "b", "a", "c", "d"), built),
            equalTo(listOf("b", "a", "c", "d")),
        )
    }

    @Test
    fun `web pages get their roles as Anki's CSS variables`() {
        SkRoles.set(SkRole.PAGE_GROUND, 0xFF102030.toInt())
        val sheet = SkPages.styleSheet()
        assertThat(sheet, containsString("--canvas:rgba(16,32,48,1.000)"))
        assertThat(sheet, containsString("--fg:rgba(255,255,0,1.000)"))
        assertThat("no quote may break out of the script string", SkPages.script().count { it == '\'' } % 2, equalTo(0))
    }
}
