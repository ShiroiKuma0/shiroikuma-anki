# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

This is **shiroikuma-anki** — 白い熊's personal fork of AnkiDroid, the semi-official Android port of the Anki spaced-repetition flashcard system. Kotlin app on top of the upstream Anki Rust backend (`net.ankiweb.rsdroid`, dependency `anki-android-backend`). Also checked out at `~/git/shiroikuma-anki` (symlink to this directory).

## Fork identity and workflow

| | |
|---|---|
| applicationId | `shiroikuma.anki` (namespace and code packages stay `com.ichi2.anki`) |
| App label | `白い熊 暗記` (resValue `app_name` in `AnkiDroid/build.gradle`) |
| versionName | `<upstream>+<fork build>`, e.g. `2.25.0alpha1+1` |
| versionCode | upstream scheme `AbbCCtDD` with the `DD` digits as the fork build counter, e.g. `22500102` |
| Build flavor | `full` release, arm64-v8a APK, signed with `~/.android-keystores/anki-custom.jks` (alias `anki`) |
| Remotes | `origin` = `git@github.com:ShiroiKuma0/shiroikuma-anki.git`; `upstream` = `https://github.com/ankidroid/Anki-Android` (push disabled) |
| Branches | `main` mirrors `upstream/main` (no fork work); `custom` = fork commit stack, rebased onto each upstream release tag (`vX.Y.Z`), or onto `upstream/main` when 白い熊 asks (current base: `upstream/main` at `v2.25.0alpha1`) |

Workflow: develop fork additions as a clean commit stack on `custom`; when upstream publishes a new release tag, rebase `custom` onto it (see the `upstream-new-version` skill). Build and deploy via the `build` skill. **Never push without 白い熊 typing "Push."** — and never push to `upstream`.

### Fork changes (commit stack on `custom`)

- Fork identity: applicationId, app label, fork versioning (`AnkiDroid/build.gradle`).
- Fork docs and skills (`CLAUDE.md`, `.agents/skills/build`, `.agents/skills/upstream-new-version` — reached through upstream's `.claude/skills` symlink).
- Video playback fix for `[sound:]` tags — upstream issue [#20668](https://github.com/ankidroid/Anki-Android/issues/20668), reported by 白い熊, unfixed upstream. Native fullscreen `VideoPlayerActivity` (restores pre-2.17 behavior), extension-only tag classification, play-button rendering for video. Check on every rebase whether upstream has fixed it; drop the commit if so.

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
