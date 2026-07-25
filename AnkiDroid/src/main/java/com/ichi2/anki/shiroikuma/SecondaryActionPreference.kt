// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.R

/**
 * Fork: a preference row carrying a second, independent action on its right
 * (the automation token row: tap the row to copy, tap "Regenerate" to replace
 * the secret). The label lives in the row's widget frame, so the row's own
 * click listener is untouched.
 */
class SecondaryActionPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs) {
        var onActionClick: (() -> Unit)? = null

        init {
            widgetLayoutResource = R.layout.sk_preference_action
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            // rows are recycled: rebind the listener every time
            holder.findViewById(R.id.sk_preference_action)?.setOnClickListener { onActionClick?.invoke() }
        }
    }
