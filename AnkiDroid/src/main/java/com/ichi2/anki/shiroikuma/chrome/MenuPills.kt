// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma.chrome

import android.content.Context
import android.view.MenuItem
import com.ichi2.anki.shiroikuma.chrome.SkIcons.Does
import com.ichi2.anki.shiroikuma.chrome.SkIcons.Icon

/**
 * Fork: what a menu item does. A menu item carries no "opens something" or "deletes" flag, and
 * upstream has some thirty menu files, so this is told from the item's resource name rather
 * than from a table of ids: the icon a row shows when it brings none, the colour that icon
 * takes, and whether the row is red.
 */
object MenuPills {
    data class What(
        val icon: Icon,
        val does: Does,
        val opens: Boolean = false,
        val destructive: Boolean = false,
    )

    private val byKeyword: List<Pair<Regex, What>> =
        listOf(
            Regex("delete|remove|empty|discard|clear|forget") to What(Icon.DELETE, Does.DELETE, destructive = true),
            Regex("suspend|bury|unbury|block|lock|disable|mute") to What(Icon.BLOCK, Does.BLOCK),
            Regex("export|import|share|copy|backup|restore|save|download|upload") to What(Icon.EXPORT, Does.SHARE),
            Regex("sync|study|review|play|start|resume|replay|record|check") to What(Icon.PLAY, Does.CONNECT),
            Regex("add|new|create|insert") to What(Icon.ADD, Does.MESSAGE),
            Regex("edit|rename|note|type|write|whiteboard|draw|pen|field|template|reposition|reschedule|reset|change") to
                What(Icon.EDIT, Does.MESSAGE),
            Regex("deck|card|browse|tag|flag|mark|select|filter|column") to What(Icon.LAYERS, Does.CONTACT),
            Regex("undo|redo|history") to What(Icon.HISTORY, Does.VIEW),
            Regex("search|find|preview|show|view|info|statistic|stats") to What(Icon.EYE, Does.VIEW),
            Regex("setting|option|preference|config|custom") to What(Icon.SLIDERS, Does.VIEW),
            Regex("help|about|support|feedback") to What(Icon.INFO, Does.VIEW),
        )

    private val plain = What(Icon.RIGHT, Does.VIEW)

    fun of(
        context: Context,
        item: MenuItem,
    ): What = of(context, item.itemId)

    fun of(
        context: Context,
        id: Int,
    ): What {
        if (id == 0 || id == -1) return plain
        val name = runCatching { context.resources.getResourceEntryName(id) }.getOrNull()?.lowercase() ?: return plain
        return byKeyword.firstOrNull { it.first.containsMatchIn(name) }?.second ?: plain
    }
}
