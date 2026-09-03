# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

This is **shiroikuma-anki** — 白い熊's personal fork of AnkiDroid, the semi-official Android port of the Anki spaced-repetition flashcard system. Kotlin app on top of the upstream Anki Rust backend (`net.ankiweb.rsdroid`, dependency `anki-android-backend`). Also checked out at `~/git/shiroikuma-anki` (symlink to this directory).

## Fork identity and workflow

| | |
|---|---|
| applicationId | `shiroikuma.anki` (namespace and code packages stay `com.ichi2.anki`) |
| App label | `白い熊 暗記` (resValue `app_name` in `AnkiDroid/build.gradle`) |
| versionName | `<upstream>+<fork build>` with the counter zero-padded to three digits, e.g. `2.25.0alpha4+027` |
| versionCode | upstream scheme `AbbCCtDD` with the `DD` digits as the fork build counter, e.g. `22500131` |
| Build flavor | `full` release, arm64-v8a APK, signed with `~/.android-keystores/anki-custom.jks` (alias `anki`) |
| Remotes | `origin` = `git@github.com:ShiroiKuma0/shiroikuma-anki.git`; `upstream` = `https://github.com/ankidroid/Anki-Android` (push disabled) |
| Branches | `main` mirrors `upstream/main` (no fork work); `custom` = fork commit stack, rebased onto each upstream release tag (`vX.Y.Z`), or onto `upstream/main` when 白い熊 asks (current base: the `v2.25.0alpha4` tag, rebased 2026-09-03) |

Workflow: develop fork additions as a clean commit stack on `custom`; when upstream publishes a new release tag, rebase `custom` onto it (see the `upstream-new-version` skill). Build and deploy via the `build` skill. **Never push without 白い熊 typing "Push."** — and never push to `upstream`.

### Fork changes (commit stack on `custom`)

- Fork identity: applicationId, app label, fork versioning (`AnkiDroid/build.gradle`).
- Fork docs and skills (`CLAUDE.md`, `.agents/skills/build`, `.agents/skills/upstream-new-version` — reached through upstream's `.claude/skills` symlink).
- Video playback fix for `[sound:]` tags — upstream issue [#20668](https://github.com/ankidroid/Anki-Android/issues/20668), reported by 白い熊, unfixed upstream. Native fullscreen `VideoPlayerActivity` (restores pre-2.17 behavior), extension-only tag classification, play-button rendering for video. Check on every rebase whether upstream has fixed it; drop the commit if so.
- 白い熊 暗記 UI page — colour/font management (`com.ichi2.anki.shiroikuma.ShiroikumaUi`, `ShiroikumaUiSettingsFragment`, `res/xml/preferences_shiroikuma_ui.xml`, strings in `res/values/100-shiroikuma.xml`). The navigation drawer is yellow on black by default and fully configurable: colours (background/text/icons/selected), header visibility, live font preview rows. Fonts are role-based (`ROLE_MENU`/`ROLE_DECK`/`ROLE_SETTINGS`): each role has an external font file (ttf/otf via SAF → `filesDir/shiroikuma_fonts/<role>_font`), text size and weight (0 = natural; `Typeface.create(base, weight)` on API 28+). Reached from Settings, a drawer entry above Settings, long-pressing the toolbar hamburger, and long-pressing the DeckPicker's top-right overflow (⋮) button (`ShiroikumaUi.attachOverflowLongPress`).
- Yellow-on-black app styling (same page, all configurable): deck names (`DeckAdapter`), DeckPicker toolbar icons + hamburger, studied-today line, study button (yellow border/text on black, `StudyOptionsFragment`), settings titles yellow / secondary text grey (`SettingsFragment.styleSettingsList` hook).
- Black-yellow traced launcher icon: original layout (three-line list top-left, Anki star bottom-right) traced in `#FFFF00` on black at 75% of the visible icon. Adaptive vectors (`ic_launcher_foreground/background/monochrome.xml`) + regenerated legacy PNG mipmaps; sources for regeneration in `~/tmp/sk-icon/` (square2.svg/round2.svg via rsvg-convert).
- Black splash: `drawable/launch_screen.xml` (black + traced icon, was grey + old logo) and v31 `windowSplashScreenBackground/AnimatedIcon` in `Theme_Dark.Launcher` and the new `Theme_IntentHandler` (manifest LAUNCHER theme).
- More configurable slots (same page): toolbar title/cards-due subtitle, right-pane deck name, pane divider colour + width (0 = hidden, `ResizingDivider`), settings icons/toggles/sliders/screen headers, deck-list line padding (0 = rows touch: every 48dp minimum in `item_deck.xml` is zeroed at bind and the deck name is a `TightLineTextView` capped at 1.15em/line, since CJK font metrics otherwise keep rows apart); sync icon tinted via its `SyncActionProvider`. `SliderPreference` carries two fork fixes: recycled rows re-bind their touch listener, and persisted values are clamped + snapped to the step grid. The UI page is organized into sections with yellow divider lines (`sk_preference_category` layout) and double-indented items under subheadings (`sk_preference_subheader`/`sk_preference_indented`). Colours are picked with four RGBA sliders + live preview. Settings rows are re-styled on every draw (change-guarded) because preference rebinds (header highlight) restore theme colours.
- Export / Import (first section of the UI page, Kōjiki-flow panel): a category checklist — Collection (backend colpkg incl. media), UI (`sk_*` prefs + font files), Controls (`binding_*`/`previewer_*`), Anki settings (the rest) — written as one zip (`manifest.json` + per-category type-tagged JSON + `fonts/` + `collection.colpkg`) by `com.ichi2.anki.shiroikuma.ShiroikumaExport`. A persisted SAF export directory (own prefs file `sk_eximport`, never exported) enables one-tap export and is queried on page open for the latest `shiroikuma-anki_*.zip` (status shown as the row summary and in the panel); without it a save-as picker opens. Prefs round-trip via `ShiroikumaUi.exportSettingsJson(context, keyFilter)`/`importSettingsJson` (type tags preserve Int vs Long, Set vs List; blocklist excludes `deckPath`/`hkey`/`username`/`currentSyncUri`/`browser_search_history`; import merges). Panel + result dialogs are hand-built black boxes with yellow borders and ArcaneChat-style pill buttons (Cancel left, Import/Export right); on success, acknowledging the info dialog closes the whole chain (dialog → panel → UI page), import also offering "Restart now"; failures stay in the panel's status line. Covered by `ShiroikumaSettingsBackupTest`. Backups are named by the sister-app family convention — `shiroikuma-anki_<yyyy-MM-dd_HH-mm-ss>.zip`, no version/infix/suffix, since 白い熊 keeps every app's backups in one directory; the old `shiroikuma-anki-export_*` name stays recognised.
- 保存復元 automation contract (`StateExportReceiver`, `AutomationAuth`): the exported, token-gated intent surface every sister app implements so 自由作業盤's 保存復元 project backs them all up headlessly. `shiroikuma.anki.action.EXPORT_STATE` runs the panel's own export with no UI (extras `token`/`path`/`items`/`progress_action` + the reply trio `reply_action`/`reply_package`/`reply_id`); `…action.LIST_CATEGORIES` enumerates `id<TAB>label<TAB>parent<TAB>on|off` lines, `collection.media` carrying `collection` as its parent and the only item that starts `off` (largest part of the export, re-obtainable by syncing); `…action.CANCEL_EXPORT` (extras `token` + optional `reply_id`) raises the flag the export polls, deletes the partial file and answers the *export's* request with `ERROR:cancelled` — the cancel itself gets no reply, and is a silent no-op when nothing is running. The in-app picker seeds its boxes from the same `Cat.defaultOn`/`MEDIA_DEFAULT_ON`. The reply is a fresh broadcast (never a binder, never the ordered result — EMUI severs both), exactly one per request. Progress broadcasts carry real counts, never a percentage, throttled to one per 500 ms. The switch (default OFF) and the 24-byte token live in their own `sk_automation` prefs file, so neither is exported; their two rows sit inside the Export / Import section. Covered by `ShiroikumaAutomationTest`.
- The UI page is kxkb-styled: section headings 20sp bold with text-wide 2.5dp underlines and full-width 1px hairlines between sections (`sk_preference_category[_first]`), sub-headings 17sp with 1.5dp underlines (`sk_preference_subheader`), tight indented rows with the colour swatch on the right (`sk_preference_indented`), and indented slider rows (`sk_preference_slider`, assigned in code since `SliderPreference` hardcodes its layout).
- Two fork entries in upstream's `ManifestThemeTest` allowlist (`IntentHandler` → `Theme_IntentHandler` for the black launch splash, and `VideoPlayerActivity`'s fullscreen theme). Upstream added the test on 2026-08-16 to forbid `android:theme` in the manifest; re-check on each rebase that the allowlist still has both, since a moved or renamed entry fails the test rather than the build.

## Commands

```bash
# Build a debug APK (flavors: play / amazon / full; CI default is play)
./gradlew :AnkiDroid:assemblePlayDebug

# Run all unit tests (Robolectric, JUnit 5 platform)
./gradlew :AnkiDroid:testPlayDebugUnitTest

# Run a single test class / method
./gradlew :AnkiDroid:testPlayDebugUnitTest --tests "com.ichi2.anki.UndoTest"
./gradlew :AnkiDroid:testPlayDebugUnitTest --tests "com.ichi2.anki.UndoTest.testUndoReview"

# Kotlin formatting (ktlint; pre-commit hook runs this automatically)
./gradlew ktlintCheck
./gradlew ktlintFormat

# Lint (what CI runs)
./gradlew lintPlayDebug :api:lintDebug :libanki:lintDebug ktlintCheck lintVitalFullRelease lint-rules:test

# Instrumented tests (device/emulator required)
./gradlew :AnkiDroid:connectedPlayDebugAndroidTest
```

Notes:

- Robolectric SDK jars are fetched by the `robolectricSdkDownload` task (applied from `AnkiDroid/robolectricDownloader.gradle`); CI runs it before testing.
- Unit tests use the JUnit 5 platform with tag filters: screenshot tests (`@ScreenshotTestCategory`) are excluded unless `-Pscreenshot` is passed; `-PemptyApplication` runs only `@EmptyApplicationCategory` tests.
- All compiler warnings are errors (`fatal_warnings`); a local Rust backend build can be used via `local_backend=true` in `local.properties`.
- The `installGitHook` Gradle task (runs on `preBuild`) installs the repo's `pre-commit` hook, which runs ktlint on staged Kotlin files.

## Module structure

Dependency direction is roughly `:AnkiDroid` → everything else; lower modules must not depend on the app.

- `:AnkiDroid` — the application: all UI (activities/fragments), sync, services, widgets. Packages under `com.ichi2.anki` (e.g. `deckpicker`, `reviewer`, `noteeditor`, `browser`, `cardviewer`, `preferences`, `pages`, `dialogs`).
- `:libanki` — business-logic wrapper around the Rust backend (`Collection`, `Card`, `Note`, `Notetypes`…), no app dependencies. Test fixtures in `libanki/src/testFixtures` (e.g. `InMemoryCollectionManager`).
- `:common` — pure-JVM shared utilities; `:common:android` — the Android-dependent part split out of it.
- `:compat` — Android API-level compatibility layer (`CompatV28`, `CompatV31`, …).
- `:api` — published public API for third-party apps (strict explicit-API mode; keep its surface stable).
- `:lint-rules` — custom lint checks (see below); `:vbpd` — vendored ViewBinding property delegate.

## Architecture essentials

- **Collection access is serialized.** `CollectionManager` (in `:AnkiDroid`) owns the collection behind a single-threaded dispatcher (`Dispatchers.IO.limitedParallelism(1)`). Access it only via `withCol { … }` (opens lazily) or `withOpenColOrNull { … }`. Never touch the collection from arbitrary threads.
- **Mutations go through `undoableOp`** (`com.ichi2.anki.observability`): it wraps a backend op, records undo state, and broadcasts `OpChanges` via `ChangeManager`, which UI components subscribe to for refresh.
- **Time:** never call `Calendar.getInstance()`, `new Date()`, or `System.currentTimeMillis()` directly — use `TimeManager`. Custom lint rules enforce this, along with no `printStackTrace()`, Snackbar over Toast, copyright headers in new files, and layout-file naming prefixes.
- **Tests:** unit tests extend `RobolectricTest` (`AnkiDroid/src/test/java/com/ichi2/anki/RobolectricTest.kt`), which provides an in-memory collection, mocked time, and helpers. Instrumented tests live in `AnkiDroid/src/androidTest`.

## Refactoring Scope
- Constrain scope tightly: do not modify unrelated files, themes, or settings 'while you're in there'.

## Verification
- For bug fixes, write the failing regression test FIRST and confirm it fails before applying the fix.

## Hard constraints

- **Never edit non-English string resources.** Only `res/values/strings.xml` (and other English source files) are edited in-repo; all `res/values-<lang>/` translations come from Crowdin via `tools/localization` and will be overwritten.
- Build variants: flavors `play`, `amazon`, `full` (dimension `appStore`) × `debug`/`release`. Release builds produce per-ABI APKs with version codes prefixed by an ABI digit.
- Upstream contribution rules: commits should each compile and have a single purpose; rebase instead of merge commits. The project has a strict AI-use policy (`AI_POLICY.md`) requiring disclosure of AI-assisted contributions — relevant when preparing PRs for the upstream repository.

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer — nor a "🤖 Generated with Claude Code" / Anthropic-attribution line — to commit messages or PR bodies in this repo. End commit messages at the last line of the body. This **overrides** the harness default. One feature = one commit; prose messages, ~72 columns.
