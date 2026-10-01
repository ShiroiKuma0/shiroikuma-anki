# Sister-app contract: 言語島 ↔ 白い熊 暗記 (islands sync)

How 白い熊 自由作業盤's 言語島 suite (`shiroikuma.jiyusagyoban`) keeps the `Language Islands`
notes in 白い熊 暗記 (`shiroikuma.anki`) in step with its islands. Version 1, 2026-10-01.

The door is the existing 保存復元 v2 **data door**, so everything that contract says about
callers, the gate and replies holds here unchanged. This document only adds two methods.

## 1. Where

| | |
| --- | --- |
| Provider authority | `shiroikuma.anki.automation` |
| Access | `ContentResolver.call(Uri.parse("content://shiroikuma.anki.automation"), method, null, extras)` |
| Methods | `islands.list`, `islands.sync` (and the existing `cancel`, by `job_id`) |
| Who may call | Exact package + uid + pinned signing certificate (`AutomationCallers`); `shiroikuma.jiyusagyoban` is pinned |
| Gate | `automation_enabled` (default on); `token` only when 暗記 requires one (default not), and ignored otherwise |

## 2. The call

Extras (all `String` except `fd`):

| Key | | |
| --- | --- | --- |
| `fd` | required | A `ParcelFileDescriptor`. **`islands.list`: 暗記 writes** UTF-8 JSON into it (open it for writing). **`islands.sync`: 暗記 reads** the ZIP from it (open it for reading). 暗記 duplicates it before `call()` returns and closes its copy when done. |
| `reply_action` | required for an answer | Action of the terminal broadcast. |
| `reply_package` | required for an answer | Package the terminal broadcast is addressed to. |
| `progress_action` | optional | Progress broadcasts, the v2 `AutomationProgress` shape: real counts, unit `文` (sentences), 25 s heartbeat. |
| `token` | optional | Only if 暗記 is set to require one. |

`call()` returns at once with a `Bundle` whose `result` is `OK:<job_id>`, or `ERROR:<reason>`
when the call was refused (`caller`, gate, no descriptor, unknown method). A refusal gets no
broadcast.

## 3. The answer

Exactly one terminal broadcast per accepted call: action `reply_action`, package
`reply_package`, `FLAG_INCLUDE_STOPPED_PACKAGES`, string extras only.

| Extra | |
| --- | --- |
| `job_id` | The id `call()` returned. |
| `result` | See the grammar below. |
| `location` | Optional, opaque: where the job wrote (sync) or read (list) — the collection directory. Present on failures too. |
| `errors` | `islands.sync` only, optional: one line per sentence that could not be applied, `<uuid><TAB><reason>`. Absent when there were none. |

Result grammar — this is all of it:

| Method | Success | Failure |
| --- | --- | --- |
| `islands.list` | `OK:<notes written>` | `ERROR:<reason>` |
| `islands.sync` | `OK:<added>\|<updated>\|<moved>\|<deleted>` | `ERROR:<reason>` |
| either, after `cancel` | | `ERROR:cancelled` |

- `added`: notes created. `updated`: existing notes whose fields or tags changed. `moved`: cards
  put into a different deck. `deleted`: notes removed.
- Per-sentence problems do **not** turn the result into an error: the rest is applied, the
  counts say what happened, and `errors` lists the misses.
- `ERROR:` means nothing was changed: an unreadable ZIP or manifest, an incompatible note type,
  or a collection that cannot be opened (for example, all-files access not granted).

## 4. `islands.list` — the JSON

```json
{
  "format": "shiroikuma-anki-islands",
  "version": 1,
  "notetype": "Language Islands",
  "notes": [
    {
      "nid": 1759674101234,
      "fields": {
        "english": "I worked seven years in Russia starting in 2009.",
        "japanese": "私は2009年から7年間ロシアで働きました。",
        "japanese-audio": "[sound:hypertts-dfe1….mp3]",
        "english-audio": "[sound:hypertts-4b8d….mp3]"
      },
      "tags": [],
      "cards": [
        { "ord": 0, "deck": "言語島々::認識::ロシア" },
        { "ord": 1, "deck": "言語島々::製作::ロシア" }
      ]
    }
  ]
}
```

- Every note of the `Language Islands` note type, in note-id (creation) order. An empty
  `notes` array when the note type does not exist.
- `fields` by name, values **verbatim as stored** (Anki field HTML, so `&amp;`, `<br>`, … can
  occur).
- `deck` is the card's **home** deck, full name with `::`; a card sitting in a filtered deck
  reports the deck it returns to.

## 5. `islands.sync` — the ZIP

`manifest.json` at the root of the ZIP; audio files anywhere else in it, named by the manifest.

```json
{
  "format": "shiroikuma-anki-islands-sync",
  "version": 1,
  "mode": "delta",
  "root": "言語島々",
  "recognition": "認識",
  "production": "製作",
  "islands": [ { "uuid": "…", "name": "ロシア" } ],
  "sentences": [
    {
      "uuid": "…",
      "island": "<island uuid>",
      "position": 1,
      "english": "I worked seven years in Russia starting in 2009.",
      "japanese": "私は2009年から7年間ロシアで働きました。",
      "ja_audio": "audio/<uuid>-ja.ogg",
      "en_audio": "audio/<uuid>-en.ogg"
    }
  ],
  "deleted": [ "<sentence uuid>" ],
  "deleted_islands": [ "<island uuid>" ],
  "adopt": { "<sentence uuid>": 1759674101234 }
}
```

| Field | |
| --- | --- |
| `format`, `version` | Optional; a `version` above 1 is refused. |
| `mode` | `delta` (default) or `full`. |
| `root`, `recognition`, `production` | Deck name parts; defaults `言語島々`, `認識`, `製作`. |
| `islands` | Every island any sentence in this manifest refers to, plus any being renamed. |
| `sentences` | The sentences to create or update. |
| `english`, `japanese` | **Plain text**; 暗記 escapes `&`, `<`, `>` when it writes the field. |
| `ja_audio`, `en_audio` | Paths **inside the ZIP**. Absent or `null`: leave the field as it is (a new note gets it empty). `""`: clear the field. |
| `position` | Order of the sentence within its island; new notes are added in island order, then position, so their new cards come up in that order. Existing notes are not repositioned. |
| `deleted` | Sentence uuids whose notes to delete. |
| `deleted_islands` | Island uuids whose two decks to remove — **only when they hold no cards** after this sync; otherwise reported in `errors`. |
| `adopt` | Sentence uuid → note id of an existing untagged note to take over (one-time adoption). The note keeps its id, cards and review history. |

## 6. What 暗記 does with it

All in one operation, recorded as a single undo step `言語島 sync`:

1. **Note type.** `Language Islands` is created if missing — fields `english`, `japanese`,
   `japanese-audio`, `english-audio`; templates `Recognition` (front `{{japanese}}` +
   `{{japanese-audio}}`) and `Production` (front `{{english}}` + `{{english-audio}}`), both backs
   showing both texts and both audios; the hand-made deck's CSS. An existing one is **never
   changed**; if it lacks one of the four fields or has fewer than two templates, the sync stops
   with `ERROR:note type …`.
2. **Island decks.** For each island, `<root>::<recognition>::<name>` and
   `<root>::<production>::<name>`. 暗記 remembers each island uuid's last name (collection config
   `shiroikuma_islands_decks`); a changed name renames both decks, keeping their cards.
3. **Notes**, keyed by the tag `li::uuid::<sentence uuid>`:
   - `adopt` first: the named note is tagged (a note id that does not exist, or is not a
     `Language Islands` note, is an `errors` line).
   - A tagged note is updated in place — **scheduling is kept**; `updated` counts it only if a
     field changed.
   - No tagged note: a new one is created (`added`).
4. **Card placement.** Card ord 0 goes to `<root>::<recognition>::<island>`, ord 1 to
   `<root>::<production>::<island>` (`moved` counts the cards that changed deck; a card in a
   filtered deck has its home deck changed instead).
5. **Deletion.** `delta`: the notes of `deleted`. `full`: additionally every `li::uuid::` note
   whose uuid is not in `sentences`. Untagged notes are never deleted.
6. **Media.** Each audio file is stored as `li_<sha1 of its bytes>.<ext>` and written as
   `[sound:li_….ogg]`. After the sync, any `li_*` file in the media folder that no
   `Language Islands` note references any more is moved to Anki's media trash. Other media (the
   hand-made deck's `hypertts-*.mp3`, for instance) is never touched; Check Media handles it.

## 7. Not changed by this door

The note type once it exists, deck options, the scheduling of existing cards, untagged notes,
and any media not named `li_*`.
