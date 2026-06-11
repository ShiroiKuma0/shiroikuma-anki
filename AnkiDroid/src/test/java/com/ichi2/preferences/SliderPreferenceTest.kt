// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.preference.PreferenceViewHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.slider.Slider
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class SliderPreferenceTest : RobolectricTest() {
    private class TestSliderPreference(
        context: Context,
        attrs: AttributeSet,
    ) : SliderPreference(context, attrs) {
        fun setInitialValueForTest(defaultValue: Any?) = onSetInitialValue(defaultValue)
    }

    @Suppress("DEPRECATION") // Robolectric.buildAttributeSet: no non-deprecated equivalent for raw attrs
    private fun newSliderPreference(
        context: Context,
        from: Int,
        to: Int,
        stepSize: Int = 1,
    ): TestSliderPreference {
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(android.R.attr.valueFrom, from.toString())
                .addAttribute(android.R.attr.valueTo, to.toString())
                .addAttribute(android.R.attr.stepSize, stepSize.toString())
                .build()
        return TestSliderPreference(context, attrs)
    }

    @Test
    fun `recycled slider row drives the preference currently bound to it`() {
        // 20260611: rows are recycled between SliderPreferences; the touch
        // listener of the first-bound preference must not survive a rebind,
        // else dragging writes the value to the wrong preference
        val context = targetContext.also { it.setTheme(R.style.Theme_Light) }
        val view = LayoutInflater.from(context).inflate(R.layout.preference_slider, null)
        val holder = PreferenceViewHolder.createInstanceForTests(view)

        val menuSize = newSliderPreference(context, 10, 30).apply { value = 14 }
        val rowPadding = newSliderPreference(context, 0, 24).apply { value = 12 }

        menuSize.onBindViewHolder(holder)
        rowPadding.onBindViewHolder(holder) // the same row, recycled

        val slider = view.findViewById<Slider>(R.id.slider)
        slider.value = 2f
        val listener = slider.getTag(R.id.tag_slider_listener_set) as Slider.OnSliderTouchListener
        listener.onStopTrackingTouch(slider)

        assertThat("the rebound preference receives the dragged value", rowPadding.value, equalTo(2))
        assertThat("the previously bound preference is untouched", menuSize.value, equalTo(14))
    }

    @Test
    fun `out-of-range persisted values are clamped instead of crashing`() {
        // aftermath of the listener bug above: another slider's value could be
        // persisted under this key; opening the screen must not crash on it
        val pref = newSliderPreference(targetContext, 10, 30)
        pref.setInitialValueForTest(2)
        assertThat(pref.value, equalTo(10))
    }

    @Test
    fun `step-misaligned persisted values are snapped instead of crashing`() {
        // 2 on a 0-900 step-100 slider is in range, but the Material Slider
        // throws on layout for values not on the step grid
        val pref = newSliderPreference(targetContext, 0, 900, stepSize = 100)
        pref.setInitialValueForTest(2)
        assertThat(pref.value, equalTo(0))

        val pref2 = newSliderPreference(targetContext, 0, 900, stepSize = 100)
        pref2.setInitialValueForTest(360)
        assertThat(pref2.value, equalTo(400))
    }
}
