#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fork: generates the colour ROLE table of 白い熊 暗記 from the one table below.

Run from the repository root:  python3 tools/shiroikuma/gen_roles.py

Writes
  AnkiDroid/src/main/java/com/ichi2/anki/shiroikuma/chrome/SkRoleTable.kt   the enum and its defaults
  AnkiDroid/src/main/res/values/sk_roles.xml      one colour resource and one label per role
  AnkiDroid/src/main/res/values/sk_theme.xml      the overlay pointing theme attributes at roles

A role's default is one of
  "#AARRGGBB"            a literal (only the two primaries and the colours that carry meaning)
  ("=", ROLE)            another role
  ("=", ROLE, alpha)     another role at that alpha
  ("mix", fraction)      that far from BACKGROUND to ACCENT, opaque
"""
import os

SECTIONS = [  # key, label  (the order the UI page is built in; "eximport" and "maintenance" hold no role)
    ("eximport", "Export / Import"),
    ("foundation", "Foundation"),
    ("text", "Text and icons"),
    ("buttons", "Buttons"),
    ("dialogs", "Dialogs"),
    ("switches", "Switches"),
    ("marks", "Check boxes and radio buttons"),
    ("menus", "Menus"),
    ("lists", "Lists"),
    ("fields", "Text fields"),
    ("bars", "Bars"),
    ("drawer", "Navigation drawer"),
    ("deck", "Deck list"),
    ("study", "Study screen"),
    ("browser", "Card browser"),
    ("settings", "Settings screens"),
    ("sheets", "Sheets and messages"),
    ("pages", "Web pages"),
    ("maintenance", "Maintenance"),
]

Y, K = "#FFFFFF00", "#FF000000"
R = lambda role, a=None: ("=", role) if a is None else ("=", role, a)
ROLES = [  # NAME, preference key, section, label, default
    ("BACKGROUND", "sk_background", "foundation", "Background", K),
    ("ACCENT", "sk_accent", "foundation", "Accent", Y),
    ("TEXT", "sk_text", "foundation", "Text", R("ACCENT")),
    ("NOTE", "sk_note", "foundation", "Explanations (dim gold)", ("mix", 0.785)),

    ("TEXT_SECONDARY", "sk_text_secondary", "text", "Secondary text", R("NOTE")),
    ("TEXT_HINT", "sk_text_hint", "text", "Hints", ("mix", 0.55)),
    ("TEXT_DISABLED", "sk_text_disabled", "text", "Disabled text", R("TEXT", 0.38)),
    ("HEADING", "sk_heading", "text", "Headings", R("ACCENT")),
    ("LINK", "sk_link", "text", "Links", R("ACCENT")),
    ("TEXT_SELECTION", "sk_text_selection", "text", "Text selection", R("ACCENT", 0.35)),
    ("ICON", "sk_icon", "text", "Icons", R("ACCENT")),
    ("ICON_ACTIVE", "sk_icon_active", "text", "Icons, active", R("ACCENT")),
    ("ERROR", "sk_error", "text", "Errors", "#FFFF0000"),
    ("ON_ACCENT", "sk_on_accent", "text", "Text on an accent ground", R("BACKGROUND")),

    ("PILL_FRAME", "sk_pill_frame", "buttons", "Frame", R("ACCENT")),
    ("PILL_TEXT", "sk_pill_text", "buttons", "Label", R("ACCENT")),
    ("PILL_GROUND", "sk_pill_ground", "buttons", "Ground", "#00000000"),
    ("DELETE", "sk_delete", "buttons", "Buttons that delete", "#FFFF0000"),
    ("FAB_FRAME", "sk_fab_frame", "buttons", "Floating button, ring", R("ACCENT")),
    ("FAB_ICON", "sk_fab_icon", "buttons", "Floating button, icon", R("ACCENT")),
    ("FAB_GROUND", "sk_fab_ground", "buttons", "Floating button, ground", R("BACKGROUND")),

    ("DIALOG_GROUND", "sk_dialog_ground", "dialogs", "Background", R("BACKGROUND")),
    ("DIALOG_FRAME", "sk_dialog_frame", "dialogs", "Frame", R("ACCENT")),
    ("DIALOG_TITLE", "sk_dialog_title", "dialogs", "Title", R("TEXT")),
    ("DIALOG_TEXT", "sk_dialog_text", "dialogs", "Text", R("TEXT")),
    ("DIALOG_BUTTON_TEXT", "sk_dialog_button_text", "dialogs", "Button label", R("PILL_TEXT")),
    ("DIALOG_BUTTON_FRAME", "sk_dialog_button_frame", "dialogs", "Button frame", R("PILL_FRAME")),
    ("DIALOG_MARK", "sk_dialog_mark", "dialogs", "Check boxes and radio buttons", R("MARK_ON")),
    ("DIALOG_DELETE", "sk_dialog_delete", "dialogs", "Buttons that delete", R("DELETE")),

    ("SWITCH_ON", "sk_settings_toggle_color", "switches", "On", R("ACCENT")),
    ("SWITCH_OFF", "sk_switch_off", "switches", "Off", ("mix", 0.55)),

    ("MARK_ON", "sk_mark_on", "marks", "Chosen", R("ACCENT")),
    ("MARK_OFF", "sk_mark_off", "marks", "Not chosen", ("mix", 0.55)),

    ("MENU_GROUND", "sk_popup_ground", "menus", "Background", R("BACKGROUND")),
    ("MENU_FRAME", "sk_popup_frame", "menus", "Frame", R("ACCENT")),
    ("MENU_LABEL", "sk_popup_label", "menus", "Label", R("TEXT")),
    ("MENU_ROW_FRAME", "sk_popup_row_frame", "menus", "Row frame", R("ACCENT")),
    ("MENU_DIVIDER", "sk_popup_divider", "menus", "Divider", ("mix", 0.7)),
    ("ICON_CONNECT", "sk_icon_connect", "menus", "Icon: study, sync, connect", "#FF00FF00"),
    ("ICON_MESSAGE", "sk_icon_message", "menus", "Icon: write, add, edit", "#FF4D94FF"),
    ("ICON_CONTACT", "sk_icon_contact", "menus", "Icon: decks, notes, people", "#FF00FFFF"),
    ("ICON_SHARE", "sk_icon_share", "menus", "Icon: copy, share, export", "#FFFF8000"),
    ("ICON_VIEW", "sk_icon_view", "menus", "Icon: view, open, settings", R("ACCENT")),
    ("ICON_BLOCK", "sk_icon_block", "menus", "Icon: suspend, bury, block", "#FFFF00FF"),

    ("LIST_DIVIDER", "sk_list_divider", "lists", "Divider", R("ACCENT", 0.2)),
    ("LIST_PRESSED", "sk_list_pressed", "lists", "Pressed row", R("ACCENT", 0.3)),
    ("LIST_SELECTED", "sk_list_selected", "lists", "Selected row", ("mix", 0.2)),
    ("LIST_SELECTED_TEXT", "sk_list_selected_text", "lists", "Selected row, text", R("TEXT")),
    ("CARD_GROUND", "sk_card_ground", "lists", "Card ground", R("BACKGROUND")),
    ("OUTLINE", "sk_outline", "lists", "Outlines", R("ACCENT")),
    ("OUTLINE_VARIANT", "sk_outline_variant", "lists", "Outlines, quiet", ("mix", 0.55)),

    ("FIELD_TEXT", "sk_field_text", "fields", "Text", R("TEXT")),
    ("FIELD_FRAME", "sk_field_frame", "fields", "Frame and underline", R("ACCENT")),

    ("TOOLBAR_GROUND", "sk_toolbar_ground", "bars", "Top bar, ground", R("BACKGROUND")),
    ("TOOLBAR_TITLE", "sk_toolbar_title_color", "bars", "Top bar, title", R("TEXT")),
    ("TOOLBAR_SUBTITLE", "sk_toolbar_subtitle_color", "bars", "Top bar, cards due", "#FF81D4FA"),
    ("TOOLBAR_ICON", "sk_toolbar_icon_color", "bars", "Top bar, icons", R("ACCENT")),
    ("STATUS_BAR", "sk_status_bar", "bars", "Status bar", R("TOOLBAR_GROUND")),
    ("NAVIGATION_BAR", "sk_navigation_bar", "bars", "Navigation bar", R("BACKGROUND")),
    ("BOTTOM_NAV_ACTIVE", "sk_bottom_nav_active", "bars", "Bottom bar, active item", R("ACCENT")),

    ("DRAWER_GROUND", "sk_menu_background", "drawer", "Background", R("BACKGROUND")),
    ("DRAWER_TEXT", "sk_menu_text_color", "drawer", "Text", R("TEXT")),
    ("DRAWER_ICON", "sk_menu_icon_color", "drawer", "Icons without a role", R("ACCENT")),
    ("DRAWER_SELECTED", "sk_menu_selected_color", "drawer", "Selected item", "#FFFFC107"),
    ("DRAWER_SELECTED_GROUND", "sk_menu_selected_background", "drawer", "Selected item, ground", "#FF332E00"),

    ("DECK_NAME", "sk_deck_name_color", "deck", "Deck names", R("TEXT")),
    ("DECK_DYNAMIC", "sk_deck_dynamic", "deck", "Filtered decks", "#FF03A9F4"),
    ("STUDIED_TODAY", "sk_studied_today_color", "deck", "Studied-today line", R("TEXT")),
    ("DECK_DETAIL_NAME", "sk_deck_detail_name_color", "deck", "Right pane, deck name", R("TEXT")),
    ("STUDY_BUTTON_TEXT", "sk_study_text_color", "deck", "Study button, label", R("PILL_TEXT")),
    ("STUDY_BUTTON_FRAME", "sk_study_border_color", "deck", "Study button, frame", R("PILL_FRAME")),
    ("STUDY_BUTTON_GROUND", "sk_study_background", "deck", "Study button, ground", R("BACKGROUND")),
    ("PANE_DIVIDER", "sk_pane_divider_color", "deck", "Pane divider", R("ACCENT")),
    ("COUNT_NEW", "sk_count_new", "deck", "New count", "#FF1E88E5"),
    ("COUNT_LEARN", "sk_count_learn", "deck", "Learning count", "#FFEF5350"),
    ("COUNT_REVIEW", "sk_count_review", "deck", "Review count", "#FF66BB6A"),
    ("COUNT_ZERO", "sk_count_zero", "deck", "Zero count", "#FF202020"),
    ("COUNT_BURIED", "sk_count_buried", "deck", "Buried count", "#FF616161"),

    ("ANSWER_GROUND", "sk_answer_ground", "study", "Answer area, ground", R("BACKGROUND")),
    ("SHOW_ANSWER_TEXT", "sk_show_answer_text", "study", "Show answer, label", R("PILL_TEXT")),
    ("EASE_AGAIN", "sk_ease_again", "study", "Again", "#FFEF9A9A"),
    ("EASE_HARD", "sk_ease_hard", "study", "Hard", "#FFFFCC80"),
    ("EASE_GOOD", "sk_ease_good", "study", "Good", "#FFC5E1A5"),
    ("EASE_EASY", "sk_ease_easy", "study", "Easy", "#FF80D8FF"),
    ("EASE_GROUND", "sk_ease_ground", "study", "Answer buttons, ground", R("BACKGROUND")),
    ("TIMER_MAX", "sk_timer_max", "study", "Timer at its limit", "#FFE57373"),

    ("ROW_SUSPENDED", "sk_row_suspended", "browser", "Suspended card", "#FFA8A634"),
    ("ROW_MARKED", "sk_row_marked", "browser", "Marked note", "#FF391080"),
    ("ROW_BURIED", "sk_row_buried", "browser", "Buried card", "#FF424242"),
    ("ROW_DUPLICATE", "sk_row_duplicate", "browser", "Duplicate", "#FF885555"),
    ("BROWSER_DIVIDER", "sk_browser_divider", "browser", "Divider", R("LIST_DIVIDER")),

    ("SETTINGS_TITLE", "sk_settings_title_color", "settings", "Titles", R("TEXT")),
    ("SETTINGS_SUMMARY", "sk_settings_summary_color", "settings", "Explanations", R("NOTE")),
    ("SETTINGS_ICON", "sk_settings_icon_color", "settings", "Icons without a role", R("ACCENT")),
    ("SETTINGS_SLIDER", "sk_settings_slider_color", "settings", "Sliders", R("ACCENT")),
    ("SETTINGS_HEADER", "sk_settings_header_color", "settings", "Screen headers", R("HEADING")),

    ("SHEET_GROUND", "sk_sheet_ground", "sheets", "Sheet, ground", R("BACKGROUND")),
    ("SHEET_FRAME", "sk_sheet_frame", "sheets", "Sheet, frame", R("ACCENT")),
    ("SNACK_GROUND", "sk_snack_ground", "sheets", "Message bar, ground", R("BACKGROUND")),
    ("SNACK_TEXT", "sk_snack_text", "sheets", "Message bar, text", R("TEXT")),
    ("SNACK_FRAME", "sk_snack_frame", "sheets", "Message bar, frame", R("ACCENT")),
    ("BADGE_WARNING", "sk_badge_warning", "sheets", "Badge, warning", "#FFFF8000"),
    ("BADGE_ERROR", "sk_badge_error", "sheets", "Badge, error", "#FFFF0000"),
    ("BADGE_TEXT", "sk_badge_text", "sheets", "Badge, text", R("BACKGROUND")),

    ("PAGE_GROUND", "sk_page_ground", "pages", "Background", R("BACKGROUND")),
    ("PAGE_TEXT", "sk_page_text", "pages", "Text", R("TEXT")),
    ("PAGE_ACCENT", "sk_page_accent", "pages", "Buttons and links", R("ACCENT")),
    ("PAGE_FRAME", "sk_page_frame", "pages", "Frames and dividers", ("mix", 0.55)),
]

# Theme attribute -> role. Upstream's own attributes and Material's; the overlay holds no value.
ATTRS = [
    ("android:windowBackground", "BACKGROUND"), ("android:colorBackground", "BACKGROUND"),
    ("colorSurface", "BACKGROUND"), ("colorSurfaceVariant", "BACKGROUND"), ("colorSurfaceContainer", "BACKGROUND"),
    ("colorSurfaceContainerHigh", "BACKGROUND"), ("colorSurfaceContainerHighest", "BACKGROUND"),
    ("colorSurfaceContainerLow", "BACKGROUND"), ("colorSurfaceContainerLowest", "BACKGROUND"),
    ("colorSurfaceDim", "BACKGROUND"), ("colorSurfaceBright", "BACKGROUND"),
    ("colorOnSurface", "TEXT"), ("colorOnBackground", "TEXT"), ("colorOnSurfaceVariant", "TEXT_SECONDARY"),
    ("colorPrimary", "ACCENT"), ("colorSecondary", "ACCENT"), ("colorTertiary", "ACCENT"), ("colorAccent", "ACCENT"),
    ("colorPrimaryInverse", "ACCENT"),
    ("colorOnPrimary", "ON_ACCENT"), ("colorOnSecondary", "ON_ACCENT"), ("colorOnTertiary", "ON_ACCENT"),
    ("colorOnError", "ON_ACCENT"),
    ("colorPrimaryContainer", "LIST_SELECTED"), ("colorSecondaryContainer", "LIST_SELECTED"),
    ("colorTertiaryContainer", "LIST_SELECTED"), ("colorErrorContainer", "LIST_SELECTED"),
    ("colorOnPrimaryContainer", "LIST_SELECTED_TEXT"), ("colorOnSecondaryContainer", "LIST_SELECTED_TEXT"),
    ("colorOnTertiaryContainer", "LIST_SELECTED_TEXT"),
    ("colorError", "ERROR"), ("colorOnErrorContainer", "ERROR"),
    ("colorOutline", "OUTLINE"), ("colorOutlineVariant", "OUTLINE_VARIANT"),
    ("colorSurfaceInverse", "SNACK_GROUND"), ("colorOnSurfaceInverse", "SNACK_TEXT"),
    ("colorControlNormal", "ICON"), ("colorControlActivated", "ICON_ACTIVE"), ("colorControlHighlight", "LIST_PRESSED"),
    ("android:textColor", "TEXT"), ("android:textColorTertiary", "TEXT_HINT"), ("android:textColorHint", "TEXT_HINT"),
    ("android:textColorHighlight", "TEXT_SELECTION"), ("android:textColorLink", "LINK"),
    ("android:editTextColor", "FIELD_TEXT"), ("editTextColor", "FIELD_TEXT"),
    ("android:statusBarColor", "STATUS_BAR"), ("android:navigationBarColor", "NAVIGATION_BAR"),
    ("preferenceCategoryTitleTextColor", "HEADING"),
    ("appBarColor", "TOOLBAR_GROUND"), ("toolbarBackgroundColor", "TOOLBAR_GROUND"), ("topBarColor", "TOOLBAR_GROUND"),
    ("actionBarTextColor", "TOOLBAR_TITLE"), ("overflowAndPopupMenuIconColor", "TOOLBAR_ICON"),
    ("popupBackgroundColor", "MENU_GROUND"), ("dialogBackground", "DIALOG_GROUND"),
    ("cardBackgroundColor", "CARD_GROUND"), ("selectedRowBackgroundColor", "LIST_SELECTED"),
    ("navDrawerItemColor", "DRAWER_TEXT"),
    ("bottomNavActiveIconColor", "BOTTOM_NAV_ACTIVE"), ("bottomNavIndicatorColor", "LIST_SELECTED"),
    ("dynDeckColor", "DECK_DYNAMIC"), ("newCountColor", "COUNT_NEW"), ("learnCountColor", "COUNT_LEARN"),
    ("reviewCountColor", "COUNT_REVIEW"), ("zeroCountColor", "COUNT_ZERO"), ("buryCountColor", "COUNT_BURIED"),
    ("showAnswerButtonBackground", "ANSWER_GROUND"), ("showAnswerColor", "ANSWER_GROUND"),
    ("answerButtonBackground", "ANSWER_GROUND"), ("showAnswerTextColor", "SHOW_ANSWER_TEXT"),
    ("againButtonTextColor", "EASE_AGAIN"), ("hardButtonTextColor", "EASE_HARD"),
    ("goodButtonTextColor", "EASE_GOOD"), ("easyButtonTextColor", "EASE_EASY"),
    ("againTextColor", "EASE_AGAIN"), ("hardTextColor", "EASE_HARD"), ("goodTextColor", "EASE_GOOD"),
    ("easyTextColor", "EASE_EASY"),
    ("againIconColor", "EASE_AGAIN"), ("hardIconColor", "EASE_HARD"), ("goodIconColor", "EASE_GOOD"),
    ("easyIconColor", "EASE_EASY"),
    ("againBackgroundColor", "EASE_GROUND"), ("hardBackgroundColor", "EASE_GROUND"),
    ("goodBackgroundColor", "EASE_GROUND"), ("easyBackgroundColor", "EASE_GROUND"),
    ("againButtonBackground", "EASE_GROUND"), ("hardButtonBackground", "EASE_GROUND"),
    ("goodButtonBackground", "EASE_GROUND"), ("easyButtonBackground", "EASE_GROUND"),
    ("maxTimerColor", "TIMER_MAX"), ("cardBrowserDivider", "BROWSER_DIVIDER"),
    ("suspendedColor", "ROW_SUSPENDED"), ("buriedColor", "ROW_BURIED"), ("markedColor", "ROW_MARKED"),
    ("duplicateColor", "ROW_DUPLICATE"),
    ("fabNormalColor", "FAB_GROUND"), ("fabPressedColor", "FAB_GROUND"),
    ("incrementerButtonBackground", "BACKGROUND"), ("incrementerButtonIconColor", "ICON"),
    ("buttonForegroundColor", "PILL_TEXT"), ("buttonRippleColor", "LIST_PRESSED"), ("buttonShadowColor", "BACKGROUND"),
    ("clozeColor", "LINK"), ("widgetStrokeColor", "OUTLINE"),
    ("badgeWarningColor", "BADGE_WARNING"), ("badgeErrorColor", "BADGE_ERROR"), ("badgeTextColor", "BADGE_TEXT"),
    ("reminderTroubleshootingOk", "ICON_CONNECT"), ("reminderTroubleshootingWarning", "BADGE_WARNING"),
    ("introductionLogoTransparent", "BACKGROUND"),
]

by_name = {r[0]: r for r in ROLES}


def rgba(h):
    v = int(h[1:], 16)
    return [(v >> 24) & 255, (v >> 16) & 255, (v >> 8) & 255, v & 255]


def value(name):
    d = by_name[name][4]
    if isinstance(d, str):
        return rgba(d)
    if d[0] == "=":
        c = value(d[1])
        return [int(c[0] * d[2]), c[1], c[2], c[3]] if len(d) == 3 else c
    a, b = value("BACKGROUND"), value("ACCENT")
    return [255] + [round(a[i] + (b[i] - a[i]) * d[1]) for i in (1, 2, 3)]


def hexs(c):
    return "#%02X%02X%02X%02X" % tuple(c)


def kdefault(d):
    if isinstance(d, str):
        return "0x%s.toInt()" % d[1:]
    if d[0] == "=":
        return "of(SkRole.%s)" % d[1] if len(d) == 2 else "alpha(of(SkRole.%s), %sf)" % (d[1], d[2])
    return "mix(%sf)" % d[1]


J = "AnkiDroid/src/main/java/com/ichi2/anki/shiroikuma/chrome/SkRoleTable.kt"
V = "AnkiDroid/src/main/res/values/"
HEAD = "GENERATED by tools/shiroikuma/gen_roles.py — edit the table there, never this file."

kt = ["// SPDX-License-Identifier: GPL-3.0-or-later", "", "// " + HEAD, "", "package com.ichi2.anki.shiroikuma.chrome", "",
      "import androidx.annotation.ColorRes", "import androidx.annotation.StringRes", "import com.ichi2.anki.R", "",
      "/** A top-level section of the 白い熊 暗記 UI page, in built order */",
      "enum class SkSection(", "    val key: String,", "    @StringRes val labelRes: Int,", ") {"]
kt += ["    %s(\"%s\", R.string.sk_section_%s)," % (k.upper(), k, k) for k, _ in SECTIONS]
kt += ["}", "", "/**", " * One settable colour. [key] is its preference key (absent = the default); [colorRes] is the",
       " * colour resource the theme overlay names for it, fed at run time by SkPalette.", " */",
       "enum class SkRole(", "    val key: String,", "    val section: SkSection,", "    @StringRes val labelRes: Int,",
       "    @ColorRes val colorRes: Int,", ") {"]
for n, key, sec, _, _ in ROLES:
    low = n.lower()
    kt.append("    %s(\"%s\", SkSection.%s, R.string.sk_role_label_%s, R.color.sk_role_%s)," % (n, key, sec.upper(), low, low))
kt += ["}", "", "/** The default of [role]: the two primaries are written here once, everything else derives */",
       "internal fun skRoleDefault(", "    role: SkRole,", "    of: (SkRole) -> Int,", "    alpha: (Int, Float) -> Int,",
       "    mix: (Float) -> Int,", "): Int =", "    when (role) {"]
kt += ["        SkRole.%s -> %s" % (n, kdefault(d)) for n, _, _, _, d in ROLES]
kt += ["    }", ""]
open(J, "w", encoding="utf-8").write("\n".join(kt))

x = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Fork: the colour ROLES as resources. " + HEAD,
     "     Each name is fed at run time by SkPalette with the colour its role has (Android 11+).",
     "     The values here are each role's computed default, which an older system shows.",
     "     Default values/ only: a qualified copy would beat the run-time value. -->",
     '<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedResources,MissingTranslation">']
x += ['    <color name="sk_role_%s">%s</color>' % (n.lower(), hexs(value(n))) for n, *_ in ROLES]
# section labels are preference titles: upstream's PreferenceTitleLengthTest wants the limit named
x += ['    <string name="sk_section_%s" maxLength="41">%s</string>' % (k, l) for k, l in SECTIONS]
x += ['    <string name="sk_role_label_%s">%s</string>' % (n.lower(), l.replace("'", "\\'")) for n, _, _, l, _ in ROLES]
x += ["</resources>", ""]
open(V + "sk_roles.xml", "w", encoding="utf-8").write("\n".join(x))

t = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Fork: the 白い熊 theme overlay, applied to every theme in code (SkTheme.install).",
     "     " + HEAD, "     It holds no colour value: every colour attribute upstream's layouts and Material's widgets",
     "     read is pointed at a role colour resource (sk_roles.xml). -->",
     '<resources xmlns:tools="http://schemas.android.com/tools">',
     '    <style name="ThemeOverlay.Shiroikuma" parent="">']
t += ['        <item name="%s"%s>@color/sk_role_%s</item>' % (a, ' tools:ignore="NewApi"' if a == "android:navigationBarColor" else "", r.lower()) for a, r in ATTRS]
t += ['        <item name="android:textColorPrimary">@color/sk_text_primary</item>',
      '        <item name="android:textColorSecondary">@color/sk_text_secondary</item>',
      '        <item name="elevationOverlayEnabled">false</item>',
      "        <!-- upstream's own outline for widgets (made for its E-Ink theme): a regular pill's frame -->",
      '        <item name="widgetStrokeWidth">1.25dp</item>',
      "        <!-- the older study screen's answer buttons: an outlined pill -->",
      '        <item name="answerButtonRef">@drawable/sk_answer_button</item>',
      '        <item name="answerButtonRippleRef">@drawable/sk_answer_button</item>',
      '        <item name="viewInflaterClass">com.ichi2.anki.shiroikuma.ShiroikumaViewInflater</item>',
      "    </style>", "</resources>", ""]
open(V + "sk_theme.xml", "w", encoding="utf-8").write("\n".join(t))
for a, r in ATTRS:
    assert r in by_name, r
print(len(ROLES), "roles,", len(ATTRS), "attributes")
