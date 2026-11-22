// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma.chrome

import android.graphics.Color
import java.util.Locale

/**
 * Fork: the role colours of the web pages AnkiDroid takes from Anki desktop (deck options,
 * statistics, card info, import). They are styled through Anki's own CSS variables, so a page
 * is given a small style sheet that sets those variables from the roles of the "Web pages"
 * section; buttons become outlined pills. Cards are not pages: they stay as the note
 * templates draw them (白い熊, 2026-11-22).
 */
object SkPages {
    private fun css(color: Int): String =
        String.format(
            Locale.ROOT,
            "rgba(%d,%d,%d,%.3f)",
            Color.red(color),
            Color.green(color),
            Color.blue(color),
            Color.alpha(color) / 255f,
        )

    fun styleSheet(): String {
        val ground = css(SkRoles[SkRole.PAGE_GROUND])
        val text = css(SkRoles[SkRole.PAGE_TEXT])
        val accent = css(SkRoles[SkRole.PAGE_ACCENT])
        val frame = css(SkRoles[SkRole.PAGE_FRAME])
        val quiet = css(SkRoles[SkRole.NOTE])
        val disabled = css(SkRoles.disabled(SkRoles[SkRole.PAGE_TEXT]))
        val selected = css(SkRoles[SkRole.LIST_SELECTED])
        val pressed = css(SkRoles.ripple(SkRoles[SkRole.PAGE_ACCENT]))
        val vars =
            listOf(
                "canvas" to ground,
                "canvas-elevated" to ground,
                "canvas-inset" to ground,
                "canvas-overlay" to ground,
                "canvas-code" to ground,
                "canvas-glass" to ground,
                "fg" to text,
                "fg-subtle" to quiet,
                "fg-faint" to quiet,
                "fg-disabled" to disabled,
                "fg-link" to accent,
                "border" to frame,
                "border-subtle" to frame,
                "border-strong" to accent,
                "border-focus" to accent,
                "button-bg" to ground,
                "button-gradient-start" to ground,
                "button-gradient-end" to ground,
                "button-hover-border" to accent,
                "button-disabled" to ground,
                "button-primary-bg" to ground,
                "button-primary-gradient-start" to ground,
                "button-primary-gradient-end" to ground,
                "button-primary-disabled" to ground,
                "scrollbar-bg" to frame,
                "scrollbar-bg-hover" to accent,
                "scrollbar-bg-active" to accent,
                "selected-bg" to selected,
                "selected-fg" to text,
                "highlight-bg" to pressed,
                "highlight-fg" to text,
                "shadow" to "transparent",
                "shadow-inset" to "transparent",
                "shadow-subtle" to "transparent",
                "shadow-focus" to accent,
                "bs-body-bg" to ground,
                "bs-body-color" to text,
                "bs-border-color" to frame,
                "bs-link-color" to accent,
            ).joinToString("") { "--${it.first}:${it.second} !important;" }
        return ":root,:root.night-mode,body,body.night-mode{$vars color:$text;}" +
            "button,.btn,select,input[type=button]{background:transparent !important;color:$accent !important;" +
            "border:1.25px solid $accent !important;border-radius:100px !important;box-shadow:none !important;" +
            "background-image:none !important;}" +
            "button.btn-primary,.btn-primary{border-width:3px !important;font-weight:bold !important;}" +
            "button:disabled,.btn:disabled{opacity:0.38;}" +
            "input,textarea{background:transparent !important;color:$text !important;border-color:$frame !important;}" +
            "a{color:$accent !important;}"
    }

    /** JavaScript that puts [styleSheet] into the page, replacing an earlier copy */
    fun script(): String {
        val sheet = styleSheet().replace("\\", "\\\\").replace("'", "\\'")
        return "(function(){var s=document.getElementById('sk-roles');" +
            "if(!s){s=document.createElement('style');s.id='sk-roles';document.head.appendChild(s);}" +
            "s.textContent='$sheet';})();"
    }
}
