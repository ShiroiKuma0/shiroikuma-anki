---
name: upstream-new-version
description: Check ankidroid/Anki-Android upstream for a new release and bring the shiroikuma-anki fork up to it. Invoked as /upstream-new-version. Fetches upstream tags, compares the latest vX.Y.Z release tag against the custom branch base, and if newer rebases the custom patch stack onto it, reconciles the recurring conflicts, resets the fork build number, builds and verifies the APK via the build skill, and deploys to the device, stopping at the Push gate for on-device verification. Use when the user asks whether there is a new upstream AnkiDroid version, to update or rebase onto the latest upstream, or types /upstream-new-version.
---

# upstream-new-version

One-call runbook for "there is a new upstream AnkiDroid release; rebuild our
fork on it." The **build** skill is the build/deploy reference; `CLAUDE.md`
holds the fork identity table and commit-stack inventory.

## The model

The fork is a stack of feature commits on `custom`, replayed onto an upstream
**release tag** (not `upstream/main`). Bringing our changes onto a new upstream
is literally `git rebase <newtag>` on `custom`. The local `main` branch only
mirrors `upstream/main`; it carries no fork work and never blocks a build.

## Preflight

- Working tree clean: `git status --short` empty. If not, stop and ask.
- On `custom`: `git rev-parse --abbrev-ref HEAD` (else `git checkout custom`).
- Export the build environment (see the build skill — JAVA_HOME, ANDROID_HOME,
  KEYSTORE* vars).

## Step 1 — Detect a new version

```bash
git fetch upstream --tags     # if this 401s, see the note below
base=$(git tag --merged custom --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -1)
new=$(git tag -l 'v*' --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -1)
echo "base=$base  new=$new"
```

- **If the fetch asks for a GitHub username**, it is not a credential
  problem: GitHub answers the anonymous protocol-v2 `POST git-upload-pack`
  with `401` while the `GET info/refs` succeeds, so git falls through to a
  password prompt. `git -c protocol.version=0 ls-remote` reads refs, but a
  real fetch still POSTs — take the SSH route instead, which `origin` already
  has keys for (2026-09-03):

  ```bash
  git fetch --tags git@github.com:ankidroid/Anki-Android \
      '+refs/heads/*:refs/remotes/upstream/*'
  ```

- The regex keeps only bare release tags (`v2.24.0`) and skips `v2.24.0alpha12`,
  `v2.24.0beta4`, and oddities like `zeemote_support`.
- **A bare release tag is not automatically a candidate.** Upstream cuts patch
  releases from `release-X.Y` branches, so the newest bare tag can be *behind*
  our base and off `main` entirely — rebasing onto it would be a downgrade.
  Always test `git merge-base --is-ancestor "$new" upstream/main` and compare
  versions before treating a tag as the new base. (2026-09-03: `v2.24.1` was
  cut from `release-2.24` two days after we based on 2.25.0alpha3; the right
  target was `v2.25.0alpha4`, an alpha tag the regex above skips.)
- When the fork tracks `upstream/main` rather than release tags, prefer an
  alpha/beta **tag** on `main` over a bare `upstream/main` commit when one has
  just been cut — same code, but a base with a name.
- **If `new == base`**: report "already on the latest upstream (`$base`)" and
  STOP — do nothing else.
- Otherwise scope the jump so you know what to expect:

```bash
git rev-list --count "$base".."$new"                       # upstream commit count
comm -12 <(git diff --name-only "$base"..custom | sort) \
         <(git diff --name-only "$base" "$new" | sort)     # OUR files upstream also touched
```

The second command is the tell: only files in both sets can conflict.
Expected: `AnkiDroid/build.gradle` (upstream bumps versionCode/versionName every
release) and whatever sources our feature commits touch.

## Step 2 — Check whether upstream fixed our issues

Before rebasing, check upstream issue
[#20668](https://github.com/ankidroid/Anki-Android/issues/20668) (video in
`[sound:]` tags broken — `file://` blocked from the `http://127.0.0.1` base URL,
short clips misclassified as audio; reported by 白い熊). If upstream fixed it in
`$new`, drop our fix commits from the stack during the rebase (`git rebase -i`
is unavailable here — use `git rebase --onto` or resolve by taking upstream and
emptying our commit) and note it in the report.

## Step 3 — Safety branch, then rebase

```bash
git branch "custom-pre-${new}-rebase"      # backstop
git rebase "$new"
```

Resolve conflicts (Step 4), `git rebase --continue` until "Successfully
rebased". If it goes sideways: `git rebase --abort`; the safety branch is the
deeper net.

## Step 4 — Reconcile conflicts (resolve at the root, never blindly)

**`AnkiDroid/build.gradle`** — the recurring one:

- **Keep ours**: `applicationId = "shiroikuma.anki"` (+ its comment), the
  `resValue "string", "app_name", "白い熊 暗記"` line, and the fork-versioning
  comment block.
- **Take upstream**: the new `versionCode`/`versionName` values and any
  compileSdk/minSdk/targetSdk/AGP/dependency bumps — then fold the fork
  counter back in (Step 5).

**Feature commits** (e.g. the video-player fix): re-anchor our hooks to wherever
upstream moved the neighbouring code. The commit messages in the stack say what
each change attaches to. Resolve by understanding, not by picking a side.

Invariants that MUST survive any reconciliation:

- `applicationId = "shiroikuma.anki"`; `namespace` stays `com.ichi2.anki`.
- `app_name` resValue = `白い熊 暗記`.
- Fork versioning scheme (`+N` versionName, `DD` digits as fork build).
- Never edit non-English string resources — they are Crowdin-generated; in
  conflicts under `res/values-<lang>/`, take upstream wholesale.

After resolving, confirm no markers remain:

```bash
git grep -nE '^(<<<<<<<|=======|>>>>>>>)' -- '*.gradle' '*.kt' '*.xml' || echo "clean"
```

## Step 5 — Reset (or continue) the fork build number

First build on a **new upstream version** is `+1`. In `AnkiDroid/build.gradle`
`defaultConfig`: versionName `<new without v>+1`, versionCode = upstream's new
code `+1` (upstream releases end `…300`, so ours ends `…301`).

```bash
grep -nE 'versionCode=|versionName=' AnkiDroid/build.gradle
# expect: versionCode=AbbCC301   versionName="X.Y.Z+1"
```

**The reset is conditional: versionCode must never go backwards.** Compute the
`+1` candidate, then compare it with the versionCode already deployed. If the
candidate is not strictly greater, do **NOT** reset — **continue** the counter
instead, so the new build outranks the installed one. Android refuses to
install a lower versionCode over a higher one, so a well-meaning reset simply
produces an APK 白い熊 cannot install.

Whether upstream's version *string* moved is **not** the test — only the number
is. Two ways the reset goes backwards:

- Upstream did not bump at all (still mid-development on the same alpha).
  (2026-06-29: rebased onto `v2.25.0alpha1-114`; upstream still `2.25.0alpha1`,
  so continued to `+17`, `…117` → `…118`.)
- Upstream bumped, but by less than our counter has climbed. An alpha-to-alpha
  bump moves upstream's code by **one** while our counter may be dozens ahead.
  (2026-08-29: rebased onto `v2.25.0alpha3-20`; upstream `22500102` →
  `22500103`, but we had shipped `+025`/`22500127`, so `+1` would have meant
  `22500104` — a downgrade. Continued to `2.25.0alpha3+026`/`22500129`.)

Reset to `+1` only on a base whose upstream code clears the deployed one — in
practice a new public release, which ends `…300`.

## Step 6 — Build + verify + deploy

Follow the **build** skill exactly (clean build mandatory after a rebase):
build `:AnkiDroid:assembleFullRelease`, run every verification step (badging
must show `shiroikuma.anki` / `白い熊 暗記` / the new `+1` version), stage in
`~/tmp`, then auto-deliver via the global **/after-build** skill (adb-push to
`/sdcard/tmp/` if a phone is connected, else scp to skhw — no prompt) — never
delete old device APKs, never `adb install`.

## Step 7 — Device verification, then the Push gate

Report: `$base` → `$new`, versionName/versionCode, what upstream changed
(highlights from `git log $base..$new --oneline`), whether issue #20668 is
fixed upstream, and any non-trivial reconciliation. Ask 白い熊 to install and
verify on the device — especially video playback on `[sound:]` cards.
**Do NOT push. Wait for "Push."**

## Step 8 — After "Push."

1. Refresh the docs to the new tag and fold them into the fork docs commit
   (amend keeps the stack a constant size across rebases):
   - `CLAUDE.md`: version examples (`X.Y.Z+1`, `AbbCC301`), the fork-changes
     inventory if commits were added/dropped.
   - `.claude/skills/build/SKILL.md`: the `Current base tag` line.
   - Stage ONLY the doc files by path, then `git commit --amend` into the docs
     commit if it is HEAD, else a separate `docs:` commit. Never `git add -A`.
2. Push and verify it landed (a silently failed push has caused real damage in
   sister forks):

   ```bash
   git push --force-with-lease origin custom
   git fetch origin
   [ "$(git rev-parse custom)" = "$(git rev-parse origin/custom)" ] && echo "landed"
   git merge-base --is-ancestor "$new" origin/custom && echo "based on $new"
   ```

3. Delete the safety branch: `git branch -D "custom-pre-${new}-rebase"`.
4. Optionally fast-forward the mirror: `git fetch upstream &&
   git branch -f main upstream/main && git push origin main` — cosmetic only;
   the fork builds from `custom`.

## Lessons baked in (from the sister forks)

- Verify the push landed before treating the rebase as done.
- A build can silently ship the previous APK — always check mtime + badging.
- `clean` after res/strings/SDK changes; restart the daemon (`./gradlew --stop`)
  if `gradle.properties` changed in the rebase.
- One feature = one commit; prose messages, ~72 col; "Push." gates push;
  no Claude attribution trailers.
