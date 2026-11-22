// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma.chrome

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.shiroikuma.chrome.SkIcons.Does
import com.ichi2.anki.shiroikuma.chrome.SkIcons.Icon
import com.ichi2.preferences.SliderPreference

/**
 * Fork: the rows of every settings screen — upstream's and the 白い熊 暗記 UI page alike.
 *
 *  - [decorate] gives every row an icon coloured by what the row does, told from its key and,
 *    failing that, from the kind of row it is. A row that brings an icon keeps it.
 *  - [styleRow] makes a row that opens or runs something an outlined pill hugging its label
 *    (with a › when it opens something), leaves a control row plain, sizes the icon by the
 *    label, strips the 48 dp minimums, and gives an explained row its dim-gold left rule.
 */
object SkPreferences {
    private val byKeyword: List<Pair<Regex, Pair<Icon, Does>>> =
        listOf(
            Regex("delete|clear|reset|erase|forget|remove|purge") to (Icon.DELETE to Does.DELETE),
            Regex("export|import|backup|restore|share|eximport") to (Icon.EXPORT to Does.SHARE),
            Regex("sync|server|network|metered|automation|api|thirdparty|plugin") to (Icon.GLOBE to Does.CONNECT),
            Regex("deck|card|note|browser|study|review|answer|learn|new_|collection|directory") to (Icon.LAYERS to Does.CONTACT),
            Regex("clipboard|copy|paste") to (Icon.COPY to Does.SHARE),
            Regex("privacy|block|password|secure|lock|dangerous|permission|token") to (Icon.PRIVACY to Does.BLOCK),
            Regex("voice|tts|audio|sound|mic|record") to (Icon.MIC to Does.MESSAGE),
            Regex("language|locale") to (Icon.GLOBE to Does.CONTACT),
            Regex("theme|colou?r|appearance|wallpaper|background|frame|ground|_ui|icon") to (Icon.PALETTE to Does.VIEW),
            Regex("gesture|swipe|tap|touch|control|shortcut|keyboard|binding|button") to (Icon.POINTER to Does.VIEW),
            Regex("font|text|title|label|heading|typing|newline|field|edit") to (Icon.TEXT to Does.MESSAGE),
            Regex("size|zoom|scale|height|width|padding|weight|gap") to (Icon.SIZE to Does.VIEW),
            Regex("notif|remind|alarm|time|minute|timeout|delay|speed|sensitiv") to (Icon.GAUGE to Does.VIEW),
            Regex("about|licen|version|help|debug|developer|changelog|website|warn") to (Icon.INFO to Does.VIEW),
            Regex("show|display|hide|preview|fullscreen|safe|header|visible") to (Icon.EYE to Does.VIEW),
        )

    fun decorate(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceGroup) {
                decorate(pref)
                if (pref is PreferenceCategory) continue
            }
            if (pref.icon != null || (!pref.isSelectable && pref.key == null)) continue
            val (icon, does) = iconOf(pref)
            pref.icon = SkIcons.drawable(pref.context, icon, does, 22f)
        }
    }

    private fun iconOf(pref: Preference): Pair<Icon, Does> {
        val key = pref.key.orEmpty().lowercase()
        byKeyword.firstOrNull { it.first.containsMatchIn(key) }?.let { return it.second }
        return when (pref) {
            is TwoStatePreference -> Icon.TOGGLE to Does.VIEW
            is SliderPreference -> Icon.SLIDERS to Does.VIEW
            is DialogPreference -> Icon.LIST to Does.VIEW
            else -> Icon.RIGHT to Does.VIEW
        }
    }

    /** Whether a tap on [pref] opens or runs something, as opposed to working a control */
    private fun isAction(pref: Preference): Boolean {
        if (pref is PreferenceGroup || pref is TwoStatePreference || pref is DialogPreference || pref is SliderPreference) return false
        if (!pref.isSelectable) return false
        // a swatch or value row is a control: its widget on the right is the affordance
        if (pref.extras.getBoolean(EXTRA_CONTROL, false)) return false
        return true
    }

    private fun opens(pref: Preference): Boolean =
        pref.fragment != null || pref.intent != null || pref.extras.getBoolean(EXTRA_OPENS, false)

    /** Marks a plain row as a control (a colour swatch, a font value): never a pill */
    const val EXTRA_CONTROL = "sk_control"

    /** Marks an action row as opening something (a panel, a picker): it gets a › */
    const val EXTRA_OPENS = "sk_opens"

    /** Marks the main action of its section: 3 dp and bold */
    const val EXTRA_MAIN = "sk_main"

    @SuppressLint("RestrictedApi")
    fun styleRow(
        list: RecyclerView,
        row: View,
    ) {
        val position = list.getChildAdapterPosition(row)
        val pref = if (position >= 0) (list.adapter as? PreferenceGroupAdapter)?.getItem(position) else null
        val title = row.findViewById<TextView>(android.R.id.title)
        val context = row.context

        // only as tall as its content
        if (row.minimumHeight != 0) row.minimumHeight = 0
        row.findViewById<View>(androidx.preference.R.id.icon_frame)?.let { frame ->
            val pad = Pills.dp(context, 2f)
            if (frame.minimumHeight != 0) frame.minimumHeight = 0
            if (frame.paddingTop != pad) frame.setPadding(frame.paddingLeft, pad, frame.paddingRight, pad)
            (title?.parent as? View)?.let { box ->
                if (box.paddingTop != pad) box.setPadding(box.paddingLeft, pad, box.paddingRight, pad)
            }
        }

        // a sub-heading inside a section: its rule takes the heading role
        row.findViewById<View>(R.id.sk_sub_underline)?.let {
            it.setBackgroundColor(SkRoles[SkRole.HEADING])
            title?.setTextColor(SkRoles[SkRole.HEADING])
            return
        }
        // the secondary action at a row's right is a small pill; one that regenerates or deletes is red
        (row.findViewById<View>(R.id.sk_preference_action) as? TextView)?.let { action ->
            if (action.getTag(R.id.tag_sk_dialog_styled) == null) {
                action.setTag(R.id.tag_sk_dialog_styled, true)
                Pills.style(action, main = false, color = SkRoles[SkRole.DELETE], small = true)
            }
        }
        if (title == null || pref == null || pref is PreferenceCategory) return

        // the icon is as tall as the label's line, never under 18 dp
        (row.findViewById<View>(android.R.id.icon) as? ImageView)?.let { icon ->
            if (SkIcons.isOurs(icon.drawable)) {
                val size = maxOf(Pills.dp(context, 18f), (title.textSize * 1.25f).toInt())
                val lp = icon.layoutParams
                if (lp != null && (lp.width != size || lp.height != size)) {
                    lp.width = size
                    lp.height = size
                    icon.layoutParams = lp
                }
                if (icon.colorFilter != null) icon.clearColorFilter()
            } else if (icon.drawable != null && icon.drawable !is android.graphics.drawable.GradientDrawable) {
                // an icon upstream brought: it takes the colour of what its row does
                val does = iconOf(pref).second
                if (does != Does.VIEW) icon.setColorFilter(SkIcons.color(does))
            }
        }

        val action = isAction(pref)
        val wanted = if (action) (if (pref.extras.getBoolean(EXTRA_MAIN, false)) 2 else 1) else 0
        if (title.getTag(R.id.tag_sk_dialog_styled) != wanted) {
            title.setTag(R.id.tag_sk_dialog_styled, wanted)
            if (action) {
                val main = wanted == 2
                val ink = if (iconOf(pref).second == Does.DELETE) SkRoles[SkRole.DELETE] else SkRoles[SkRole.PILL_TEXT]
                val frame = if (ink == SkRoles[SkRole.DELETE]) ink else SkRoles[SkRole.PILL_FRAME]
                title.background = Pills.background(context, frame, main)
                title.setPadding(Pills.dp(context, 14f), Pills.dp(context, 7f), Pills.dp(context, 14f), Pills.dp(context, 7f))
                title.setTypeface(Typeface.create(title.typeface, Typeface.NORMAL), if (main) Typeface.BOLD else Typeface.NORMAL)
                title.setTextColor(ink)
                (title.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    it.topMargin = Pills.dp(context, 3f)
                    it.bottomMargin = Pills.dp(context, 3f)
                    title.layoutParams = it
                }
                val arrow = if (opens(pref)) SkIcons.drawable(context, Icon.CHEVRON, ink, 14f) else null
                title.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrow, null)
                title.compoundDrawablePadding = Pills.dp(context, 8f)
                title.isSingleLine = false
            } else {
                title.background = null
                title.setPadding(0, 0, 0, 0)
                title.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                (title.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    if (it.topMargin != 0 || it.bottomMargin != 0) {
                        it.topMargin = 0
                        it.bottomMargin = 0
                        title.layoutParams = it
                    }
                }
            }
        }

        // an explained row: bold label, dim-gold description, a 2 dp dim-gold rule down its left
        val summary = row.findViewById<TextView>(android.R.id.summary)
        val explained = summary != null && summary.visibility == View.VISIBLE && !summary.text.isNullOrEmpty()
        (title.parent as? View)?.let { box ->
            val has = box.background is LeftRule
            if (explained && !has) {
                box.background = LeftRule(SkRoles[SkRole.SETTINGS_SUMMARY], Pills.dp(context, 2f))
                box.setPaddingRelative(Pills.dp(context, 10f), box.paddingTop, box.paddingEnd, box.paddingBottom)
                if (!action) title.setTypeface(Typeface.create(title.typeface, Typeface.NORMAL), Typeface.BOLD)
                summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            } else if (!explained && has) {
                box.background = null
                box.setPaddingRelative(0, box.paddingTop, box.paddingEnd, box.paddingBottom)
                if (!action) title.setTypeface(Typeface.create(title.typeface, Typeface.NORMAL), Typeface.NORMAL)
            }
        }
    }

    /** A thin bar down the left edge of an explained row */
    class LeftRule(
        color: Int,
        private val width: Int,
    ) : Drawable() {
        private val paint = Paint().apply { this.color = color }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val left = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) b.right - width else b.left
            canvas.drawRect(left.toFloat(), b.top.toFloat(), (left + width).toFloat(), b.bottom.toFloat(), paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
