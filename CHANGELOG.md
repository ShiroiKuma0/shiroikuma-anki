# Changelog

This file carries **both histories**. The 白い熊 暗記 fork's releases come first, newest
first; anything upstream AnkiDroid ever adds to its own changelog belongs strictly *below*
this block, unedited — upstream inserts each new release directly under its own preamble, so
keeping the fork's entries above it means the two never touch and a rebase merges this file
cleanly instead of conflicting on every sync.

Each entry says which upstream base it was built on. Entries are per-release deltas — only
the first fork release below lists the whole feature set.

---

## 白い熊 暗記 2.25.0beta2+006 — 2026-10-02

Built on AnkiDroid `v2.25.0beta2` (`99efe56916`), the same base as +001 and +005. versionCode
`22500208`, installed as `322500208` for arm64-v8a.

**A 言語島 deck that keeps itself in step with 白い熊 自由作業盤.**

- **The 言語島 sync door.** 自由作業盤's 言語島 suite keeps the `Language Islands` notes here in
  step with its islands through two new methods on the existing data door. The same check
  applies: exact package, uid and pinned signing certificate. The wire is documented in
  `docs/sister-app-contract-anki-islands.md`.
  - `islands.list` hands back every `Language Islands` note: fields, tags, and each card's
    home deck.
  - `islands.sync` applies a ZIP of a manifest and audio as **one undo step**, *言語島 sync*:
    - New sentences become notes.
    - Edited ones are updated in place, so **their scheduling survives**.
    - Removed ones are deleted. Only notes the suite created or adopted are ever deleted.
    - Each island gets `言語島々::認識::<island>` for its Recognition cards and
      `言語島々::製作::<island>` for its Production cards, and both are renamed when the
      island is.
  - The note type is created if missing, exactly as the hand-made deck has it, and an existing
    one is never altered.
  - Audio is stored as `li_<sha1>.<ext>`, and `li_` files no note uses any more go to the media
    trash. Other media is never touched.
  - The reply counts what happened: `OK:<added>|<updated>|<moved>|<deleted>`. Any sentence that
    could not be applied is listed beside it, and the rest still go ahead.
- **Adoption of the hand-made deck, tested on the phone.** All 150 hand-made notes were taken
  over with their original note ids, and 7 new ones came from 仕事の話: 157 notes, 314
  cards. The 46 cards that sat in the wrong tree were moved where they belong. The 46 reviewed
  cards kept all 142 reviews, and 314 new Opus audio files replaced the HyperTTS MP3s. The old
  MP3s are now unused; Check Media will list them.
- **The public card API works under this fork's id.** The provider is declared as
  `shiroikuma.anki.flashcards`, but the API library still named upstream's
  `com.ichi2.anki.flashcards` and its permission. The provider therefore matched no request,
  and no app could reach it. The names now follow the app id.

## 白い熊 暗記 2.25.0beta2+005 — 2026-09-27

Built on AnkiDroid `v2.25.0beta2` (`99efe56916`), the same base as +001. versionCode
`22500207`, installed as `322500207` for arm64-v8a. +002 to +004 were test builds on the way
here and were not published.

**A restore onto a clean phone brings back every setting — the collection location
included — and writes nothing to shared storage.**

- **The collection location travels.** `deckPath` used to be kept out of every backup, so a
  restored install had no idea where its collection was. Startup then quietly picked the
  default `/storage/emulated/0/AnkiDroid` and ran on a new, empty collection beside the real
  one. That happened on 2026-09-08 and went unnoticed for weeks. The collection folder is a
  deliberate, portable choice under `〇/`, the same on every phone, so it now rides in the
  Anki-settings category. Sync credentials and the search history still never leave the phone.
- **The collection no longer travels through automation.** It lives on shared storage and
  survives a reinstall on its own; sending it duplicated a quarter of a gigabyte per backup.
  Importing it was also what reached for the collection directory, and a clean install has no
  all-files access yet, so a restore failed with *No write access to AnkiDroid directory*.
  Backups and restores driven by 自由作業盤 or 応用管理 now carry the settings categories only:
  UI, controls and Anki settings.
- **A restore writes only app-private data.** It goes through a settings-only import that
  refuses the collection outright and writes nothing but the preferences and the font files.
  It never touches shared storage and never needs all-files access. An older archive's
  collection is skipped unread.
- **Old selections keep working.** The retired item ids `collection` and `collection.media` are
  accepted and ignored, so a caller with a saved selection does not fail its whole backup.
- **The in-app Export / Import panel is unchanged.** It still offers the collection, with or
  without media.
- **An all-files access gate before the deck list.** A restored install points at the right
  collection and has no permission to read it: it looks restored and is not. When the
  collection is on shared storage and the grant is missing, the app now opens on a black and
  yellow screen first. It says why, shows the collection's path, and offers *Grant access*
  (this app's page in Settings, or the global list if EMUI refuses it) and *Not now*. It closes
  itself as soon as you come back with access granted. *Not now* (or back) lasts for this
  launch only. It also gets you past upstream's permission screen, which has no skip, so a
  phone that will not grant access can still open the app. It lands on upstream's "collection
  folder inaccessible" dialog, without a collection being opened or created.
- **Replies say where the work happened.** A reply to 応用管理 now carries an optional
  `location` beside the result, never inside it, on failures too: the app's private data
  directory, where an import writes and an export reads. 応用管理 shows it in its restore log.

## 白い熊 暗記 2.25.0beta2+001 — 2026-09-26

Built on AnkiDroid `v2.25.0beta2` (`99efe56916`), 297 upstream commits on from +030's
`v2.25.0alpha4` base — the first fork release on a 2.25 **beta**. versionCode `22500203`,
installed as `322500203` for arm64-v8a. The counter restarts at `+001` because upstream's
beta bump carried its own code past every fork build so far; it still installs over +030.

**Every fork feature carried over unchanged onto upstream's first 2.25 betas.**

- **The video fix stays.** Upstream issue #20668 is still open, and the competing upstream
  PR #20732 was closed without being merged, so `[sound:]` videos still play only in the
  fork's native fullscreen player.
- **Still no tracker.** Nothing from ACRA came back with the rebase — upstream's new
  references to it are comments only — and the no-trackers test passes.
- **Slider fix, now alongside upstream's own test.** Upstream added a test that re-binding a
  settings slider must not stack touch listeners. The fork's deeper fix (a recycled slider
  row drives the preference it is *now* bound to, and out-of-range stored values are clamped
  instead of crashing the screen) passes it, and all three tests now live together.
- **Copy-token row adapted** to upstream's new clipboard API, which caps copied text at a
  safe length.

**What upstream brings in this base** (highlights):

- **Studying:** a `nosuggest` field filter and type-answer modifier that turns off keyboard
  suggestions while typing an answer; chained `[[type:…]]` modifiers parse; answering works
  after the app was killed in the background; the first answer waits for a custom scheduler;
  full-screen taps line up with card content; a crashed card WebView recovers instead of
  leaving a blank screen.
- **Custom study:** “review ahead” validates its input, shows a day/days suffix, warns on
  0 days and disables *Create* when nothing can be reviewed ahead.
- **Review reminders:** notification permission requested before Android 13 too, a
  battery-optimisation exemption dialog, and copyable debug info.
- **Keyboard and layout:** the deck picker, card browser, note-type and card-template screens
  keep their content above the keyboard; edge-to-edge fixes in study options and settings.
- **Stability:** a corrupted-database dialog for backend corruption, no
  TransactionTooLargeException on note types with many fields, safer shared-deck downloads,
  whiteboard and shake-gesture lifecycle fixes, and lower memory use reading note types.
- Plus sentence-case wording passes, Crowdin translation updates, and licence headers
  converted to SPDX form.

## 白い熊 暗記 2.25.0alpha4+030 — 2026-09-05

Built on AnkiDroid `v2.25.0alpha4` (`e2ad79e351`), the same base as +027 and +029.
versionCode `22500134`, installed as `322500134` for arm64-v8a.

**The one tracker is gone. Nothing in the app reports a crash to anybody.**

- **ACRA removed from the build entirely.** A tracker scan of +029 reported exactly one
  tracker, and it was upstream's crash reporter. On a crash ACRA uploaded the stack trace,
  500 lines of logcat, the whole shared-preferences file, the device details and a persistent
  install UUID to `ankidroid.org/acra/report`. The fork had already defaulted the reporting
  mode to *never* — but a default is a setting, and the library was still linked in, still ran
  its own `:acra` process, and still put a "send this" dialog in front of you. Defaulting a
  thing off is not the same as not shipping it. The four `ch.acra:*` dependencies, the ACRA
  reporter, its analytics hook, its report dialog and layout, the manifest entry running that
  dialog in a separate process, the report URL and the ACRA test suite are all deleted. The
  built APK contains **zero occurrences of the string `acra`** in its bytecode.
- **Crash reporting still exists as a seam — it simply goes nowhere.** Upstream keeps the
  `CrashReporter` interface in a shared module with ACRA as one implementation, so the fork
  supplies its own: an exception is written to logcat, counted in usage analytics *only* if
  you opted in to those, and that is the end of it. Every one of the app's crash-report call
  sites is untouched, which is what keeps this a small change rather than a hundred-file one.
- **Two pieces of UI existed only to feed it, and would now be lying.** *Error reporting mode*
  in General settings offered a choice between always, never and ask — of nothing. *Send
  exception report* under Help → Get help offered to send a report that would never be sent.
  Both are gone.
- **A test guards the result.** A rebase is how ACRA comes back: upstream's file returns with
  a conflict resolved the lazy way, and the tracker is quietly shipping again. The fork's new
  no-trackers test fails loudly the moment `org.acra` is on the classpath.
- Two incidental uses of ACRA utility classes had to be replaced along the way — the deep copy
  in the multimedia note editor now serializes through `java.io` directly, and the debug-info
  screen drops its ACRA UUID line.

## 白い熊 暗記 2.25.0alpha4+029 — 2026-09-04

Built on AnkiDroid `v2.25.0alpha4` (`e2ad79e351`), the same base as +027. versionCode
`22500133`, installed as `322500133` for arm64-v8a. (`+028` was built and delivered but
never released — its manifest was cut minutes before the `<queries>` fix below.)

**Sister-app automation contract v2 — the app can now be backed up *with its data*, and restored onto a wiped phone.**

- **The gate opens by default.** The automation switch now ships **on**, and the
  authorization token became a separate, **opt-in** switch that ships **off**. A pasted
  48-character secret cannot survive a factory reset, which is precisely the situation the
  feature now exists for: restoring this app and its collection onto a clean phone where
  nothing has been configured. The token still exists, still regenerates, still never leaves
  the device — it is simply no longer the gate. A token sent to the app while the token
  switch is off is **ignored rather than refused**, so a caller configured last year keeps
  working. Both switches and the secret live in a preferences file the export deliberately
  never walks, and all three are written synchronously — with the switch now defaulting to
  *on*, a lost write would have silently re-opened a surface you had closed.
- **A data door that knows who is knocking.** A new content provider at
  `shiroikuma.anki.automation` answers `describe`, `export`, `import` and `cancel`. A
  broadcast cannot tell you who sent it, which is what the shared secret used to paper over;
  a provider learns the caller's identity from the framework. Callers are checked three ways —
  an **exact** package name (never a prefix: a package name is not a namespace anyone owns,
  so any sideloaded app may call itself `shiroikuma.evil`), the uid the kernel reports, and a
  **pinned signing certificate**. The last one matters most on a clean phone, where whichever
  caller is not yet installed is a name anyone could take.
- **The backup travels through a file descriptor the caller opens**, never a path and never a
  URI. The backup app writes into a temporary file and renames it on commit, and it encrypts
  and checksums per file it knows about — a file this app dropped in itself would be renamed
  out from under it and would sit in plaintext inside an otherwise encrypted archive. A
  descriptor is also a capability that expires when it is closed.
- **`import` exists only on the provider**, never as a broadcast. An import replaces the
  collection, and the broadcast receiver is exported without a permission — an import there
  would let any app on the phone wipe your cards.
- **The work runs in a foreground service** with a partial wakelock, because a collection with
  media is minutes of writing and a backgrounded app is frozen mid-stream on EMUI — which
  yields a truncated archive underneath a success reply, the one failure indistinguishable
  from a good backup until the day you need it.

**Fixes**

- **A headless backup no longer sweeps the entire media folder.** An automation request that
  named no categories was exporting *everything*, media included, when the contract says an
  absent list means the app's **default set** — and this app has always reported the media
  folder as starting unticked, being the bulk of the archive and re-obtainable by syncing.
  A batch backup of every app was therefore carrying the whole collection's media every single
  time.
- **Replies to the automation caller were being discarded.** Android 11+ requires a package to
  be named under `<queries>` before an app may address it, and the fork named only one of the
  two callers. The export ran, wrote correctly, and was never heard of. Both are now declared —
  which is needed twice over, since reading a caller's signing certificate needs the same
  visibility.
- **A retried backup request could crash the app it was backing up.** The service read its
  arguments before going to the foreground, but the platform requires a foreground promise to
  be kept once made — so a caller retrying with a stale job id killed the process. It now goes
  foreground as its first statement and stops silently on a stale id.
- **A restored settings file could be silently undone.** The backup app force-stops this one
  the instant a restore reports success — a `SIGKILL`, which an asynchronous preferences write
  does not survive. The restore path now commits synchronously.
- Upstream's `ExternalEntryPointsTest` pins the set of externally-reachable components, and the
  fork's automation receiver had never been added to it — the suite was failing before this
  release touched it. Both it and the storage-undecided suite now know the fork's entry points.

**Internals**

- One progress sender is now shared by both doors, so the broadcast export and the provider
  export report identically: real counts never a percentage, throttled to one every 500 ms,
  with a 25-second heartbeat so a long single step is not mistaken for a dead process.
- The foreground service is declared `dataSync` rather than `specialUse`: that is what writing
  a backup into a descriptor actually is, the permission was already held, and `specialUse` is
  an API 34 constant this phone's platform would reject.

## 白い熊 暗記 2.25.0alpha4+027 — 2026-09-03

Built on AnkiDroid `v2.25.0alpha4` (`e2ad79e351`) — a tagged base again, rather than the bare
`upstream/main` commit the previous two releases sat on. versionCode `22500131`, installed as
`322500131` for arm64-v8a.

- **Rebased onto upstream 2.25.0alpha4** — 101 commits, `v2.25.0alpha3-20` → `v2.25.0alpha4`.
  The whole fork stack replayed; nothing dropped. Upstream's churn was again dominated by an
  edge-to-edge sweep, this time reaching the reviewer (answer buttons, app-bar tint, immersive
  review, display cutouts and rounded corners, hidden 3-button navigation, "show answer"
  extended under the insets), the note editor, the preferences screens, the navigation drawer,
  the card-template editor, review reminders and every fragment hosted by
  `SingleFragmentActivity`. Alongside it: **targetSdk moved to 36**, path-traversal checks were
  centralized and import filenames sanitized, a screenshot-test suite arrived for a dozen
  screens, and `observability`, `SelectableDeck` and the widget classes moved out into
  `:anki-common` and `:widgets`.
- **Three collisions, all resolved by keeping both sides.** Upstream added a
  `SHOW_DONATE_LINKS` build flag immediately beside the `app_name` resValue this fork
  overrides, so `build.gradle` took upstream's line and kept the fork's identity block. The
  drawer's edge-to-edge work added `ViewCompat`/`WindowInsetsCompat` imports where the fork
  adds `children`. And `SettingsFragment.onViewCreated` gained a window-insets listener at
  exactly the point the fork calls `ShiroikumaUi.styleSettingsList` — both now run, the fork's
  restyle last, so it still wins on colour.
- **The build counter continues rather than resetting, for the second rebase running.** Upstream's
  alpha bump moved its own versionCode by one, `22500103` → `22500104`, while this fork's counter
  stood at `+026`/`22500129`. A reset to `+1` would have produced `22500105` — *lower* than the
  build already on the device, which Android refuses to install over. The counter carries on to
  `+027`/`22500131`.
- **The newest upstream release tag was a trap this time.** Upstream cut `v2.24.1` from its
  `release-2.24` branch two days before this rebase, which makes it the newest bare `vX.Y.Z` tag
  and therefore exactly what the rebase runbook's tag detection selects — but it is not on `main`
  and is a whole minor version behind this fork's base, so rebasing onto it would have been a
  downgrade. The right target was `v2.25.0alpha4`, an alpha tag that same detection skips. The
  runbook now checks a candidate tag against `upstream/main` before treating it as a base.
- **Fetching from upstream now needs SSH.** An anonymous `git fetch` from github.com answers the
  protocol-v2 `POST git-upload-pack` with `401` while the ref-listing `GET` succeeds, so git falls
  through to a username prompt instead of reporting an error — it reads as a credential problem
  and is not one. The runbook records the SSH URL as the way through.
- **Video in `[sound:]` tags still needs this fork.** Upstream issue
  [#20668](https://github.com/ankidroid/Anki-Android/issues/20668) is open — reopened, in fact —
  and the community fix (PR #20732) is still unmerged. Upstream touched nothing in
  `com.ichi2.anki.cardviewer` across all 101 commits, so the native fullscreen player replayed
  untouched.
- **The `ManifestThemeTest` allowlist needed no change.** Both fork exceptions still match the
  manifest exactly — `IntentHandler` → `Theme_IntentHandler` for the black launch splash, and
  `VideoPlayerActivity`'s fullscreen theme — and upstream removed no manifest theme this round,
  which is the other way that test breaks. The fork's own suites pass on the new base:
  `ManifestThemeTest`, `ShiroikumaSettingsBackupTest` (10 tests) and `ShiroikumaAutomationTest`
  (16 tests), 27 in all, green.

## 白い熊 暗記 2.25.0alpha3+026 — 2026-08-29

Built on AnkiDroid `upstream/main` at `v2.25.0alpha3-20` (`103a807d88`). versionCode
`22500129`, installed as `322500129` for arm64-v8a.

- **Rebased onto upstream 2.25.0alpha3** — 59 commits, `v2.25.0alpha2-167` →
  `v2.25.0alpha3-20`. The whole fork stack replayed; nothing dropped. Upstream's churn was
  dominated by an edge-to-edge sweep (introduction, account, multimedia, permissions,
  manage-space, study-options, filtered-deck options and every fragment hosted by
  `SingleFragmentActivity`), the navigation `Destination` migration reaching the card
  browser, searches and Info, usage analytics moving from raw strings to typed events, and
  the backend adopting Anki 26.05.
- **The build counter continues instead of resetting, so the APK can still be installed.**
  The rebase runbook resets the fork counter to `+1` on a new upstream base, but upstream's
  alpha bump moved its own versionCode by just one (`22500102` → `22500103`) while this
  fork's counter had climbed to `+025`/`22500127`. A reset would have produced `22500104` —
  *lower* than the build already on the device, which Android refuses to install over. The
  counter carries on to `+026`/`22500129`, and both fork skills now state the rule as
  "versionCode must never go backwards" rather than "reset when the upstream version string
  moves", which is what made the trap look safe.
- **Fork hooks re-anchored where upstream moved the ground under them.** `currentCardId`
  migrated from `NavigationDrawerActivity` to `AbstractFlashcardViewer`, so the drawer keeps
  only the fork's own `openShiroikumaUiSettings`. The deck picker's studied-today line kept
  its fork colour and `ROLE_DECK` typeface while the `doOnLayout` block that used to raise
  the FAB was dropped, upstream having replaced it with `raiseFabAboveSummary` — keeping the
  fork's copy would have left two owners fighting over the same margin.
- **`ManifestThemeTest` allowlist trimmed to what the manifest still sets.** Upstream's
  edge-to-edge work removed `IntroductionActivity`'s manifest theme, and that test fails on a
  *stale* allowlist entry exactly as it does on a missing one, so the entry went with it. The
  fork's two genuine exceptions stay allowlisted with their reasons: `IntentHandler` →
  `Theme_IntentHandler` for the black launch splash, and `VideoPlayerActivity`'s fullscreen
  theme.
- **Video in `[sound:]` tags still needs this fork.** Upstream issue
  [#20668](https://github.com/ankidroid/Anki-Android/issues/20668) remains open and the
  community fix (PR #20732) is still unmerged, so the native fullscreen player stays. The
  rebase confirmed the fix intact: upstream's `CardMediaPlayer` threads a `javascriptEvaluator`
  into its WebView-based `VideoPlayer`, which this fork does not construct at all.

## 白い熊 暗記 2.25.0alpha2+025 — 2026-08-17

Built on AnkiDroid `upstream/main` at `v2.25.0alpha2-167` (`12114e7f72`). versionCode
`22500127`, installed as `322500127` for arm64-v8a.

- **Rebased onto a month of upstream work** — 146 commits, `v2.25.0alpha2-21` →
  `v2.25.0alpha2-167`. The whole fork stack replayed; nothing dropped. Upstream's churn
  included an edge-to-edge sweep across many activities, a new E-ink theme, a full rewrite of
  usage analytics onto GA4, `StudyOptions` migrated to the destination pattern, and
  `CollectionManager`/`DeckUtils` moved into `:anki-common`.
- **The fork's two manifest themes are now declared to upstream's new `ManifestThemeTest`.**
  Upstream added a test forbidding `android:theme` on any activity, because
  `Themes.setTheme` applies the user's theme in `onCreate` and a differing manifest theme
  both flashes the wrong colours in the starting window and freezes `windowBackground`. Ours
  are genuine exceptions and are allowlisted with their reasons: `IntentHandler` points at
  `Theme_IntentHandler` so the launch splash is black rather than the platform default, and
  `VideoPlayerActivity` draws no themed chrome and never calls `Themes.setTheme` at all.
- **The build counter is zero-padded to three digits inside the APK**, not just in its
  filename — `2.25.0alpha2+025`, so the version the app reports matches the artefact name
  and the release tag. Release tags are padded from this release onward; the four earlier
  tags keep the unpadded names they were published under and are not retagged.
- **Video in `[sound:]` tags still needs this fork.** Upstream issue
  [#20668](https://github.com/ankidroid/Anki-Android/issues/20668) remains open and the
  community fix (PR #20732) is unmerged, so the native fullscreen player stays.
- Verified against upstream's new theme guards: the black splash still qualifies for the
  `launch_screen` exemption in `Themes`, and the new GA4 analytics default to opt-out with no
  API key in this build, so the fork sends upstream neither crash reports nor usage data.

## 白い熊 暗記 2.25.0alpha2+24 — 2026-07-31

Built on AnkiDroid `upstream/main` at `v2.25.0alpha2-21`. versionCode `322500126`.

- **The category list says which items start ticked.** `LIST_CATEGORIES` lines gained the
  contract's optional fourth field — `id<TAB>label<TAB>parent<TAB>on|off` — so a backup app
  whose picker redraws from this reply no longer has to guess a default. The field is
  positional and optional, so a caller that ignores it is unaffected.
- **Only `collection.media` starts off** — by far the largest part of the export, and it
  comes back on the next AnkiWeb sync. Every other category starts on, and an absent `items`
  extra still means exactly that default set.
- **The in-app Export/Import panel is seeded from the same constants** sent on the wire, so
  the app's own sheet and an automation caller's picker open on the same selection.
- **A running export can be cancelled from outside** —
  `shiroikuma.anki.action.CANCEL_EXPORT`, a third action on the same exported receiver.
  Extras `token` and an optional `reply_id`; absent means whatever is running, unambiguous
  because two concurrent exports are forbidden.
- The cancel raises a flag the export **already polls** — between zip entries, in the byte
  meter, and in the backend poll. Nothing is interrupted mid-write and no process is killed;
  the run unwinds at the next boundary.
- **A cancelled export leaves the backup directory exactly as it found it.** One guard now
  covers everything after the file is created, the media tally included, so a cancel during
  the tally can no longer leave an empty file behind.
- The stopped export answers **its own** request with `ERROR:cancelled` under the existing
  single-reply guard, so a cancel racing a finished export can never turn an `OK:` into an
  error. The cancel action itself replies nothing, and is a silent no-op when nothing runs.

## 白い熊 暗記 2.25.0alpha2+23 — 2026-07-25

Built on AnkiDroid `upstream/main` at `v2.25.0alpha2-21`. versionCode `322500125`.

- **保存復元 automation contract — a headless, token-gated state export.** A new exported
  receiver answers `shiroikuma.anki.action.EXPORT_STATE` and
  `shiroikuma.anki.action.LIST_CATEGORIES`, so a sister automation app can back this one up
  unattended as part of a batch. It runs the Export/Import panel's own export — one zip, same
  format, still importable from the panel — with no UI at all.
- `LIST_CATEGORIES` enumerates categories as `id<TAB>label` lines, with `collection.media` a
  sub-option of `collection` so media can be included or left out independently.
- `EXPORT_STATE` extras: `token`, optional `path` (an absolute directory overriding the
  configured one, created if missing), optional `items`, optional `progress_action`, and the
  reply trio `reply_action`/`reply_package`/`reply_id`. Directory precedence is `path` → the
  configured directory → `ERROR:no-directory`.
- The reply is a **fresh broadcast** carrying `OK:<path>|<bytes>|<human size>|<n> categories`,
  the category lines, or `ERROR:<reason>` — never a binder and never the ordered-broadcast
  result, both of which EMUI severs between third-party apps. Exactly one terminal reply per
  request, guarded so an async success and a synchronous error cannot both fire;
  `automation disabled` and `bad token` stay distinct errors.
- **Progress in real numbers, never a percentage** — structured as `text`/`current`/`total`/
  `unit`: a category counter, the backend's media count against the true total, then a byte
  meter while the zip is written. Automation broadcasts are throttled to one per 500 ms with
  a forced final line.
- **Automation switch and token live in their own preferences file the export never walks**,
  so the secret can neither travel in a backup nor arrive from someone else's. 24
  `SecureRandom` bytes, hex, generated on first read, compared constant-time. Both rows sit
  in the Export/Import section: master switch (default **off**), then the token — tap to
  copy, `Regenerate` on the right. Untouched by "Reset all to defaults".
- **Backups renamed to the sister-app family convention** —
  `shiroikuma-anki_<yyyy-MM-dd_HH-mm-ss>.zip`, no version, infix or suffix, from both the
  automation path and the page, because every app's backups share one directory. The old
  `shiroikuma-anki-export_*` name is still recognised as a latest export.
- Writing to an arbitrary absolute path uses All-Files-Access; without it the export falls
  back to the configured SAF directory rather than failing.

## 白い熊 暗記 2.25.0alpha2+22 — 2026-07-25

Built on AnkiDroid `upstream/main` at `v2.25.0alpha2-21`.

- **Category-based Export / Import of the collection and every setting**, as the first
  section of the 白い熊 暗記 UI page: a select-all checklist over **Collection (decks · cards ·
  media)** as a backend `.colpkg` with an indented *Include media* toggle, **UI (colours ·
  fonts)** including the font files themselves, **Controls (bindings)**, and **Anki
  settings** for the whole remaining preference store.
- One zip per export: `manifest.json` + one type-tagged JSON per settings category +
  `fonts/` + `collection.colpkg`. Categories are independent entries, so exports stay
  tolerant across versions; import applies exactly the ticked ones and skips absent ones.
- A **persisted export directory** (SAF tree, stored device-locally and never itself
  exported) enables one-tap export and is queried on page open for the latest export; the
  answer shows as the row summary — red while unset, yellow once set — and in the panel's
  status line. Without one, a save-as picker takes over.
- A **live media tally** starts as the panel opens ("Media: N files · X GB", repainted every
  250 ms) so the size can inform the include-media decision up front.
- **A progress meter from the first moment**, no dead blank screen: media counts with an
  out-of-how-many denominator that reuses the panel's running tally, then a "Writing zip:
  x MB / y MB" byte meter. A **Cancel export** pill aborts the backend op within 100 ms,
  stops the zip write per 512 K chunk, cleans up the partial file, and reports "Export
  cancelled." rather than a failure.
- Result dialogs are black with a yellow border and pill buttons. Acknowledging a successful
  export or import closes the whole chain — dialog → panel → UI page — and import also offers
  "Restart now". Failures land red in the panel's status line and leave it open.
- Type-tagged JSON preserves Int vs Long and Set vs List; `deckPath`, `hkey`, `username`,
  `currentSyncUri` and `browser_search_history` are never exported. Import merges rather than
  wiping. Round-trip, category-split, single-category and foreign-file rejection are unit
  tested.

## 白い熊 暗記 2.25.0alpha2+17 — 2026-07-16

The first published fork release. Built on AnkiDroid `upstream/main` at `v2.25.0alpha2-21`.
versionCode `22500119` (installed as `322500119` for arm64-v8a).

### Major features

- **Native fullscreen video player for `[sound:]` video tags** — upstream issue
  [#20668](https://github.com/ankidroid/Anki-Android/issues/20668), reported by 白い熊 and
  unfixed upstream. Restores pre-2.17 behaviour: tags are classified as video by file
  extension alone (no more short clips misfiled as audio), video cards render a play button,
  and playback opens in a dedicated `VideoPlayerActivity` instead of failing in the WebView,
  which blocks `file://` sources from its `http://127.0.0.1` base URL.
- **The 白い熊 暗記 UI page** — a colour-and-font management page for the whole app, reached
  from Settings, from a drawer entry above Settings, and by long-pressing the toolbar
  hamburger (later also the deck picker's ⋮ overflow). Sectioned with yellow divider lines
  and indented sub-items; every colour is chosen with four RGBA sliders and a live preview.
- **Role-based font engine** — three independent roles (menu / deck list / settings), each
  with an external ttf/otf loaded via the system file picker, per-role text size, and
  per-role weight (0 = natural; variable-font weights on API 28+), with live preview rows.
- **Settings export/import** — round-trips the entire default `SharedPreferences` as
  type-tagged JSON via the system file dialog, preserving Int vs Long and Set vs List.
  Credentials, sync URLs, collection path and browser search history are excluded; import
  merges and offers a clean restart. Covered by a round-trip unit test.

### UI & theming

- **Yellow-on-black navigation drawer** by default, fully configurable: background, item
  text, icon and selected-item colours, plus header visibility.
- **Deck picker styling** — deck-name colour, toolbar icons and hamburger tint, the
  studied-today line, the right-pane deck name, and the sync icon.
- **Configurable pane divider** between deck list and study pane: colour and width, where 0
  hides it entirely.
- **Study button** in yellow border and text on black.
- **Settings screens restyled** — yellow titles, grey secondary text, tinted icons, toggles,
  sliders and screen headers, re-applied on every draw (change-guarded) so preference-list
  rebinds cannot wash it out.
- **Toolbar title and cards-due subtitle** colours configurable.
- **Deck-list line padding down to 0** so rows genuinely touch: every 48dp minimum in the row
  layout, the counts column included, is zeroed at bind, and deck names use a tight
  line-height cap (1.15em) so CJK font metrics don't force rows apart.
- **Black-yellow traced launcher icon** — AnkiDroid's original layout (three-line list, Anki
  star) traced in yellow on black; adaptive vectors plus regenerated legacy PNG mipmaps.
- **Black splash screen** replacing the grey flash, including the Android 12+ SplashScreen
  background and icon in both the launcher theme and the intent-handler theme.

### Fixes & behaviour

- **Crash reports are never sent to upstream by default** — a fork's stacktraces are not
  upstream's to triage.
- **`SliderPreference` recycled-row fix** — recycled preference rows re-bind their touch
  listener, so a slider can no longer write another slider's value under the wrong key.
- **`SliderPreference` value hygiene** — persisted values are clamped to the range *and*
  snapped to the step grid; an off-grid value crashed the Material Slider on layout.
- **Font preview refresh fix** — the menu font preview updates immediately on size changes.
- **The bug-report form names the fork** and points at this repository, so reports aren't
  misattributed to upstream.

### Packaging

- App id `shiroikuma.anki` (code packages stay `com.ichi2.anki`); manifest permissions and
  provider authorities derive from the app id, so the fork installs alongside official
  AnkiDroid.
- App label **白い熊 暗記**.
- Fork versioning: versionName `<upstream>+<N>`, versionCode = upstream base code + N.
- Signed `full`-flavour release, arm64-v8a, with the fork's own keystore.
