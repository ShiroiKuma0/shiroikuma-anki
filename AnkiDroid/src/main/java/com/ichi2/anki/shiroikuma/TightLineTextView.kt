// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import android.util.AttributeSet
import com.ichi2.ui.FixedTextView

/**
 * Fork: a [FixedTextView] whose measured height is capped near the glyph box.
 *
 * CJK fonts carry large ascent/descent metrics (~1.4em for Noto CJK), so a
 * plain TextView is much taller than its visible glyphs and deck rows cannot
 * touch even with every padding and minHeight removed. This caps the height
 * at [LINE_FACTOR] em per line (plus padding), so the 白い熊 暗記 UI line
 * padding slider reaches "lines basically touch" at 0.
 */
class TightLineTextView : FixedTextView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val lineCount = layout?.lineCount ?: 1
        val cap = (lineCount * textSize * LINE_FACTOR).toInt() + paddingTop + paddingBottom
        if (measuredHeight > cap) {
            setMeasuredDimension(measuredWidth, cap)
        }
    }

    companion object {
        private const val LINE_FACTOR = 1.15f
    }
}
