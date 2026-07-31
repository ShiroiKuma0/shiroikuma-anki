---
name: build
description: Build the signed shiroikuma-anki fork APK (full flavor, arm64-v8a), verify it, stage it in ~/tmp and auto-deliver it via the global /after-build skill (adb-push if a phone is connected, else scp to skhw — no prompt). Invoked as /build. Covers the fork version bump (versionName <upstream>+<N>, versionCode DD digits), signing via the anki-custom keystore, APK naming, and post-build integrity checks. Use when the user asks to build the app, build the APK, rebuild, or deploy a new build to the device.
---

# build

Build pipeline for the **shiroikuma-anki** fork. Produces a signed `full`-flavor
release APK for arm64-v8a, names it by fork convention, stages it in `~/tmp`,
and auto-delivers it via the global `/after-build` skill. See `CLAUDE.md` for the fork identity table.

## Identity (must hold in every build)

- applicationId `shiroikuma.anki` — namespace/code packages stay `com.ichi2.anki`.
- App label `白い熊 暗記` — `resValue "string", "app_name"` in `AnkiDroid/build.gradle`.
- The manifest derives permissions/provider authorities from `${applicationId}`
  (`shiroikuma.anki.permission.READ_WRITE_DATABASE`, `shiroikuma.anki.flashcards`,
  `shiroikuma.anki.apkgfileprovider`), so the fork installs alongside official AnkiDroid.

## Versioning — bump before any build that will be deployed

Upstream scheme is `AbbCCtDD` (`A`=major, `bb`=minor, `CC`=maintenance, `t`=type,
`DD`=build). Upstream public releases always end `300`; the fork claims the `DD`
digits as its build counter:

- versionName: `<upstream>+<N>` — e.g. `2.25.0alpha1+1`, `2.25.0alpha1+2`, …
- versionCode: upstream code `+N` — e.g. `22500102`, `22500103`, …
  (on a public-release base, which ends `…300`, that is `AbbCC3NN`)
- Both live in `defaultConfig` in `AnkiDroid/build.gradle`. Bump `N` by one for
  each new build deployed to the device; reset to `+1` after a rebase onto a
  new upstream base (the `upstream-new-version` skill does that).
- `N` has a budget of 99 per upstream release. Release ABI splits prefix a 9th
  digit (arm64-v8a = 3), so the installed versionCode reads e.g. `322500102`.

Current base: **upstream/main at v2.25.0alpha2-21** (last rebase 2026-07-16). Deployed: `2.25.0alpha2+24`.

## Environment (export every run; not set in non-interactive shells)

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # repo requires Java 21-25
export ANDROID_HOME=$HOME/android-sdk ANDROID_SDK_ROOT=$HOME/android-sdk
```

## Signing

Per-fork keystore, read by the existing `signingConfigs.release` env-var hooks in
`AnkiDroid/build.gradle` — no gradle edits needed (and none should be made; the
env route survives rebases):

```bash
export KEYSTOREPATH=$HOME/.android-keystores/anki-custom.jks
export KEYSTOREPWD=anki123 KEYALIAS=anki KEYPWD=anki123
```

Without these the build silently falls back to upstream's public test keystore
(`tools/fallback-release-keystore.jks`) — an APK signed with that will not
upgrade an install signed with ours. Always export them.

## Build

```bash
cd "/datadisk/〇牛羚羊/[710] git/anki-android"
version=$(grep -oP 'versionName="\K[^"]+' AnkiDroid/build.gradle)
./gradlew :AnkiDroid:assembleFullRelease -x lintVitalFullRelease \
    2>&1 | tee /tmp/claude/anki-build-${version}.log
build_apk=$(ls -t AnkiDroid/build/outputs/apk/full/release/*arm64-v8a*.apk | head -1)
```

- `-x lintVitalFullRelease`: upstream's own CI skips lintVital on assemble
  (`codeql.yml` does the same); all warnings are errors in this repo and lint on
  a release build is slow and can block on issues unrelated to fork work.
- Release builds are minified (R8) and split per ABI; we ship the arm64-v8a APK.
- `./gradlew clean` first whenever resources/strings changed or after a rebase —
  incremental Gradle has shipped stale APKs in sister forks.
- A debug build (`:AnkiDroid:assembleFullDebug`) gets `applicationIdSuffix
  ".debug"` and suffix `-debug` — fine for quick tests, not for deployment.

## Verify (MANDATORY before claiming success)

```bash
grep -cE 'error:|エラー:|FAILED' /tmp/claude/anki-build-${version}.log   # must be 0
ls -lh "$build_apk"; date                       # mtime must be current, not a prior build
aapt2=$(ls "$HOME"/android-sdk/build-tools/*/aapt2 | sort -V | tail -1)
"$aapt2" dump badging "$build_apk" | grep -E "package: name|versionName|application-label"
# expect: name='shiroikuma.anki'  versionName='<upstream>+<N>'  application-label:'白い熊 暗記'
"$aapt2" dump badging "$build_apk" | grep -o "versionCode='[0-9]*'"   # expect 3AbbCC3NN (arm64 prefix 3)
```

Note: grepping the label `白い熊` out of `resources.arsc` with `strings` false-
negatives (UTF-16); the `aapt2` application-label line is authoritative.

## Stage + deploy (never auto-install)

```bash
apk_name="shiroikuma-anki_${version}_arm64-v8a.apk"
mkdir -p ~/tmp
cp "$build_apk" ~/tmp/"$apk_name"
```

Then auto-deliver via the global **/after-build** skill — no asking, no "is the phone
connected?" prompt. It runs `/adb-check` (UNSANDBOXED — a sandboxed check falsely reports
no device), then `/adb-push` to `/sdcard/tmp/` if a phone is connected (`adb shell mkdir -p
/sdcard/tmp` then `adb push ~/tmp/"$apk_name" /sdcard/tmp/"$apk_name"`), otherwise `/scp` of
the newest `~/tmp/*.apk` to host `skhw`, and announces the filename that landed.

- **Never delete old APKs — on either side** (hard rule, per 白い熊). Leave prior
  `/sdcard/tmp/shiroikuma-anki_*.apk` on the device **and** prior
  `~/tmp/shiroikuma-anki_*.apk` on this machine in place, however many pile up;
  白い熊 clears them themselves. Each build lands under its own `+N` filename, so
  nothing is ever clobbered — that is the whole point of bumping the counter.
  (This skill used to `rm -f ~/tmp/shiroikuma-anki_*.apk` before staging, which
  destroyed the previous build every time; removed 2026-07-31.)
- Never `adb install`; 白い熊 installs by hand and verifies.
- The `~/tmp` copy is always kept; `/after-build` delivers from it (device or skhw).

## Report, then the Push gate

Report versionName/versionCode, APK path and size, and what changed since the
last build. **Do NOT `git push`. Wait for 白い熊 to type "Push."** Then
`git push --force-with-lease origin custom` and verify it landed:
`git fetch origin && [ "$(git rev-parse custom)" = "$(git rev-parse origin/custom)" ]`.
