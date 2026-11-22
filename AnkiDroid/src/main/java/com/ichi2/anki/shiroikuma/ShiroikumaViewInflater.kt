// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.theme.MaterialComponentsViewInflater

/**
 * Fork: the theme's `viewInflaterClass` (set by `ThemeOverlay.Shiroikuma`, applied in
 * [ShiroikumaDialogs.install]). It creates exactly the views Material would and hands
 * each to [ShiroikumaDialogs.onViewCreated], which acts only inside a dialog.
 *
 * AppCompat instantiates this class by name, so it must survive R8 under this name.
 */
@Keep
class ShiroikumaViewInflater : MaterialComponentsViewInflater() {
    override fun createButton(
        context: Context,
        attrs: AttributeSet,
    ): AppCompatButton = super.createButton(context, attrs).also(ShiroikumaDialogs::onViewCreated)

    override fun createTextView(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatTextView = super.createTextView(context, attrs).also(ShiroikumaDialogs::onViewCreated)

    override fun createImageView(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatImageView = super.createImageView(context, attrs).also(ShiroikumaDialogs::onViewCreated)

    override fun createEditText(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatEditText = super.createEditText(context, attrs).also(ShiroikumaDialogs::onViewCreated)

    override fun createCheckBox(
        context: Context,
        attrs: AttributeSet,
    ): AppCompatCheckBox = super.createCheckBox(context, attrs).also(ShiroikumaDialogs::onViewCreated)

    override fun createRadioButton(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatRadioButton = super.createRadioButton(context, attrs).also(ShiroikumaDialogs::onViewCreated)

    override fun createCheckedTextView(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatCheckedTextView = super.createCheckedTextView(context, attrs).also(ShiroikumaDialogs::onViewCreated)
}
