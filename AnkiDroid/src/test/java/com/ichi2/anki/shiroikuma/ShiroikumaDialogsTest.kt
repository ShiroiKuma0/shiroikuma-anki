// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.themes.Themes
import com.ichi2.utils.isWebViewWarningEnabled
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import com.ichi2.anki.common.android.R as CommonR

/**
 * The black-yellow dialog look is applied in one place for every dialog upstream builds,
 * so these cases use a plain stock dialog: if it comes out right, so do the other 130.
 */
@RunWith(AndroidJUnit4::class)
class ShiroikumaDialogsTest : RobolectricTest() {
    private fun themedActivity(): AppCompatActivity {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        saveControllerForCleanup(controller)
        Themes.setTheme(controller.get())
        return controller.setup().get()
    }

    private fun show(build: AlertDialog.Builder.() -> Unit): AlertDialog {
        val dialog = AlertDialog.Builder(themedActivity()).apply(build).show()
        shadowOf(Looper.getMainLooper()).idle()
        return dialog
    }

    @Test
    fun `the buttons of a stock dialog are outlined pills, the positive one heavier`() {
        val dialog =
            show {
                setMessage("Message")
                setPositiveButton("OK", null)
                setNegativeButton("Cancel", null)
            }
        val ok = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as MaterialButton
        val cancel = dialog.getButton(DialogInterface.BUTTON_NEGATIVE) as MaterialButton

        for (button in listOf(ok, cancel)) {
            assertThat("never filled", button.backgroundTintList!!.defaultColor, equalTo(Color.TRANSPARENT))
            assertThat(button.currentTextColor, equalTo(ShiroikumaDialogs.DEFAULT_ACCENT))
            assertThat(button.strokeColor!!.defaultColor, equalTo(ShiroikumaDialogs.DEFAULT_ACCENT))
        }
        assertThat("the main action has the thick frame", ok.strokeWidth, greaterThan(cancel.strokeWidth))
        assertThat("the main action is bold", ok.typeface, equalTo(Typeface.DEFAULT_BOLD))
        assertThat(cancel.typeface, equalTo(Typeface.DEFAULT))
    }

    @Test
    fun `title and message take their colour roles`() {
        targetContext.sharedPrefs().edit {
            putInt(targetContext.getString(R.string.pref_sk_dialog_title_key), Color.GREEN)
            putInt(targetContext.getString(R.string.pref_sk_dialog_text_key), Color.CYAN)
        }
        val dialog =
            show {
                setTitle("Title")
                setMessage("Message")
                setPositiveButton("OK", null)
            }
        assertThat(dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.currentTextColor, equalTo(Color.GREEN))
        assertThat(dialog.findViewById<TextView>(android.R.id.message)!!.currentTextColor, equalTo(Color.CYAN))
    }

    @Test
    fun `a button that deletes is red`() {
        val dialog =
            show {
                setMessage("Message")
                setPositiveButton(CommonR.string.dialog_positive_delete, null)
                setNegativeButton("Cancel", null)
            }
        val delete = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as MaterialButton
        assertThat(delete.currentTextColor, equalTo(ShiroikumaDialogs.DEFAULT_DELETE))
        assertThat(delete.strokeColor!!.defaultColor, equalTo(ShiroikumaDialogs.DEFAULT_DELETE))
        assertThat("still not filled", delete.backgroundTintList!!.defaultColor, equalTo(Color.TRANSPARENT))
    }

    @Test
    fun `the panel is replaced once per dialog window`() {
        val dialog = show { setMessage("Message") }
        val decor = dialog.window!!.decorView
        assertThat(decor.getTag(R.id.tag_sk_dialog_styled) != null, equalTo(true))
    }

    @Test
    fun `the outdated WebView warning stays off until reset`() {
        assertThat("on by default", isWebViewWarningEnabled(targetContext), equalTo(true))
        targetContext.sharedPrefs().edit { putBoolean(targetContext.getString(R.string.pref_sk_webview_warning_key), false) }
        assertThat(isWebViewWarningEnabled(targetContext), equalTo(false))
        ShiroikumaUi.resetAll(targetContext)
        assertThat(isWebViewWarningEnabled(targetContext), equalTo(true))
    }
}
