<div align="center">

<img src="AnkiDroid/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="白い熊 暗記 icon" />

# 白い熊 暗記 (shiroikuma-anki)

**AnkiDroid in yellow on black — with working video, configurable everything, and a font engine.**

A fork of [AnkiDroid](https://github.com/ankidroid/Anki-Android) with **major additions**: a native fullscreen video player for `[sound:]` tags, a full colour-and-font management page, deep yellow-on-black theming of the whole app, role-based external fonts, deck-list density control, and a category-based Export/Import of the whole collection and every setting.

Installs **side-by-side** with official AnkiDroid (app id `shiroikuma.anki`).

**📥 Latest release: [`2.25.0alpha2+22`](https://github.com/ShiroiKuma0/shiroikuma-anki/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-anki/releases)

</div>

---

## 🎬 Video that actually plays

Upstream AnkiDroid broke video in `[sound:]` tags ([issue #20668](https://github.com/ankidroid/Anki-Android/issues/20668)): the WebView blocks `file://` sources from its `http://127.0.0.1` base URL, and short clips get misclassified as audio. This fork restores the pre-2.17 behavior with a **native fullscreen video player** — video tags are classified by file extension alone, render a play button on the card, and open in a dedicated `VideoPlayerActivity`.

## 🎨 The 白い熊 暗記 UI page

A dedicated settings page controlling the look of the whole app — reached from Settings, from a drawer entry, by long-pressing the toolbar hamburger, or by long-pressing the deck picker's top-right ⋮ button. The page itself is styled to match: bold yellow headings underlined exactly as wide as their text, hairline section separators, tight indented rows. Every colour is picked with four RGBA sliders and a live preview:

- **Navigation drawer** — background, text, icons and selected-item colours, header visibility, live font preview rows. Yellow on black by default.
- **Deck picker** — deck-name colour, toolbar icons and hamburger, the studied-today line, the right-pane deck name, the sync icon, and the pane divider (colour and width, 0 hides it).
- **Study screen** — the study button in yellow border/text on black.
- **Settings screens** — titles, secondary text, icons, toggles, sliders and screen headers, all restyled live.

## 🔤 Role-based fonts

Three independent font roles — menu, deck list, settings — each loading an **external ttf/otf** of your choosing (picked via the system file dialog), with per-role text size and weight. CJK-aware: deck rows use a tight line-height cap so ideographic font metrics don't push rows apart.

## 📏 Density down to zero

Deck-list line padding is a slider that goes all the way to 0 — every 48dp minimum in the row layout is removed so rows can genuinely touch. Fit twice the decks on one screen.

## 💾 Export / Import — the collection and every setting

The first section of the UI page opens a category-based Export/Import panel: tick what travels — the **whole collection** (a backend `.colpkg`, with an *Include media* toggle informed by a live count of your media folder's files and size), the fork's **UI colours & fonts** (including the font files), the **control bindings**, and the remaining **AnkiDroid settings** — and everything lands in one zip in your chosen export directory. The directory is remembered, queried on page open for the latest export, and a one-tap export shows a live progress meter (media counts, then a byte meter) with a working *Cancel export* button. Import applies exactly the categories you tick and offers a clean restart. Account credentials and machine-local paths are deliberately excluded.

## 🖤💛 Fork identity

Black-and-yellow traced launcher icon, black splash screen (including the Android 12+ splash API), and crash reporting to upstream **off by default**. The app id `shiroikuma.anki` keeps permissions and provider authorities separate, so it coexists with the official build on the same device.

---

## Built on AnkiDroid

A fork of [AnkiDroid](https://github.com/ankidroid/Anki-Android) (app id `shiroikuma.anki`, so it coexists with the official build), the semi-official Android port of the open-source [Anki](https://apps.ankiweb.net/) spaced-repetition flashcard system. All credit for the app itself goes to the AnkiDroid maintainers and contributors. The code remains under the [GPL-3.0 license](COPYING) (with the [AGPL-3.0](https://github.com/ankitects/anki/blob/main/LICENSE) Anki backend and the [LGPL-3.0](api/COPYING.LESSER) AnkiDroid API).

The fork lives as a clean commit stack on the `custom` branch, rebased onto upstream as it moves; `main` mirrors `upstream/main`.

## Building

```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-anki.git
cd shiroikuma-anki
git checkout custom
./gradlew :AnkiDroid:assembleFullRelease -x lintVitalFullRelease
```

The release APK is split per ABI; the arm64-v8a artifact is the one published here. Signing uses the standard AnkiDroid env-var hooks (`KEYSTOREPATH`, `KEYSTOREPWD`, `KEYALIAS`, `KEYPWD`).
