// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import com.google.protobuf.ByteString
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NotetypeJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Fork: the 言語島 sync — what keeps the `Language Islands` notes in step with
 * 白い熊 自由作業盤's islands. The wire is in
 * `docs/sister-app-contract-anki-islands.md`; [AutomationDataService] runs it
 * behind the data door.
 *
 * Everything here works on an open [Collection] and touches nothing else: the
 * caller decides how the collection is reached and how the answer leaves.
 */
object ShiroikumaIslands {
    const val NOTETYPE = "Language Islands"
    const val F_EN = "english"
    const val F_JA = "japanese"
    const val F_JA_AUDIO = "japanese-audio"
    const val F_EN_AUDIO = "english-audio"
    private val FIELDS = listOf(F_EN, F_JA, F_JA_AUDIO, F_EN_AUDIO)

    /** A note belongs to sentence `<uuid>` when it carries `li::uuid::<uuid>`. */
    const val TAG_PREFIX = "li::uuid::"

    /** Island uuid → the name its two decks were last given (collection config, so it syncs). */
    const val DECKS_CONFIG_KEY = "shiroikuma_islands_decks"

    /** Audio this door owns: `li_<sha1>.<ext>`. Nothing else in the media folder is ever touched. */
    const val MEDIA_PREFIX = "li_"

    const val LIST_FORMAT = "shiroikuma-anki-islands"
    const val SYNC_FORMAT = "shiroikuma-anki-islands-sync"
    const val VERSION = 1

    const val UNDO_NAME = "言語島 sync"

    /** The progress unit: sentences */
    const val UNIT = "文"

    data class Island(
        val uuid: String,
        val name: String,
    )

    data class Sentence(
        val uuid: String,
        val island: String,
        val position: Int,
        val english: String,
        val japanese: String,
        /** Path inside the ZIP; null = leave the field alone, "" = clear it */
        val jaAudio: String?,
        val enAudio: String?,
    )

    data class Manifest(
        val full: Boolean,
        val root: String,
        val recognition: String,
        val production: String,
        val islands: List<Island>,
        val sentences: List<Sentence>,
        val deleted: List<String>,
        val deletedIslands: List<String>,
        val adopt: Map<String, Long>,
    ) {
        fun recognitionDeck(island: String) = "$root::$recognition::$island"

        fun productionDeck(island: String) = "$root::$production::$island"
    }

    data class SyncResult(
        val added: Int,
        val updated: Int,
        val moved: Int,
        val deleted: Int,
        /** uuid → reason, for the sentences (or islands) that could not be applied */
        val errors: List<Pair<String, String>>,
    ) {
        val result get() = "OK:$added|$updated|$moved|$deleted"

        /** The `errors` extra: `<uuid><TAB><reason>` per line, or null when there were none */
        val errorLines get() = errors.takeIf { it.isNotEmpty() }?.joinToString("\n") { (uuid, why) -> "$uuid\t$why" }
    }

    // ---------------------------------------------------------------- list

    /**
     * Every `Language Islands` note as the contract's JSON, in note-id order.
     * @return the JSON and the number of notes in it
     */
    fun listJson(col: Collection): Pair<String, Int> {
        val notes = JSONArray()
        val notetype = col.notetypes.byName(NOTETYPE)
        if (notetype != null) {
            for (nid in notesOf(col).sorted()) {
                val note = col.getNote(nid)
                val fields = JSONObject()
                for ((i, name) in notetype.fieldsNames.withIndex()) fields.put(name, note.fields.getOrElse(i) { "" })
                val cards = JSONArray()
                for (card in note.cards(col).sortedBy { it.ord }) {
                    val home = if (card.oDid != 0L) card.oDid else card.did
                    cards.put(JSONObject().put("ord", card.ord).put("deck", col.decks.name(home)))
                }
                notes.put(
                    JSONObject()
                        .put("nid", nid)
                        .put("fields", fields)
                        .put("tags", JSONArray(note.tags))
                        .put("cards", cards),
                )
            }
        }
        val json =
            JSONObject()
                .put("format", LIST_FORMAT)
                .put("version", VERSION)
                .put("notetype", NOTETYPE)
                .put("notes", notes)
        return json.toString() to notes.length()
    }

    // ---------------------------------------------------------------- the ZIP

    /**
     * Reads the sync ZIP: `manifest.json` plus every other entry as bytes,
     * keyed by its path. Audio is small (seconds of 48 kbps Opus), so the
     * whole archive is held in memory rather than staged on disk.
     *
     * @throws IllegalArgumentException no manifest, or a manifest we cannot read
     */
    fun readZip(input: InputStream): Pair<Manifest, Map<String, ByteArray>> {
        var manifest: String? = null
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val bytes = zip.readBytes()
                    if (entry.name == "manifest.json") manifest = bytes.decodeToString() else files[entry.name] = bytes
                }
                entry = zip.nextEntry
            }
        }
        return parseManifest(requireNotNull(manifest) { "no manifest.json in the archive" }) to files
    }

    /** @throws IllegalArgumentException the manifest is not one we can read */
    fun parseManifest(text: String): Manifest {
        val json = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("manifest.json is not JSON") }
        val version = json.optInt("version", VERSION)
        require(version <= VERSION) { "manifest version $version is newer than this build reads ($VERSION)" }
        val mode = json.optString("mode", "delta")
        require(mode == "delta" || mode == "full") { "unknown mode: $mode" }

        fun strings(key: String) = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

        /** null for absent or JSON null, the string otherwise ("" included) */
        fun JSONObject.audio(key: String): String? = if (isNull(key)) null else getString(key)

        val islands =
            json
                .optJSONArray("islands")
                ?.let { a ->
                    (0 until a.length()).map { i ->
                        a.getJSONObject(i).let { Island(it.getString("uuid"), it.getString("name")) }
                    }
                }.orEmpty()
        val sentences =
            json
                .optJSONArray("sentences")
                ?.let { a ->
                    (0 until a.length()).map { i ->
                        a.getJSONObject(i).let {
                            Sentence(
                                uuid = it.getString("uuid"),
                                island = it.getString("island"),
                                position = it.optInt("position", 0),
                                english = it.optString("english"),
                                japanese = it.optString("japanese"),
                                jaAudio = it.audio("ja_audio"),
                                enAudio = it.audio("en_audio"),
                            )
                        }
                    }
                }.orEmpty()
        val adopt =
            json.optJSONObject("adopt")?.let { o -> o.keys().asSequence().associateWith { o.getLong(it) } }.orEmpty()
        return Manifest(
            full = mode == "full",
            root = json.optString("root").ifEmpty { "言語島々" },
            recognition = json.optString("recognition").ifEmpty { "認識" },
            production = json.optString("production").ifEmpty { "製作" },
            islands = islands,
            sentences = sentences,
            deleted = strings("deleted"),
            deletedIslands = strings("deleted_islands"),
            adopt = adopt,
        )
    }

    // ---------------------------------------------------------------- sync

    /**
     * Applies [manifest] to the collection as one operation. Fatal problems —
     * an incompatible note type — throw before anything is changed; a
     * sentence that cannot be applied is reported in [SyncResult.errors] and
     * the rest goes ahead.
     *
     * The caller wraps this in a custom undo entry ([UNDO_NAME]).
     *
     * @param audio the ZIP's other entries, by path
     * @param onProgress sentences done, out of how many
     * @throws IllegalStateException the note type exists but is not one we can fill
     */
    fun sync(
        col: Collection,
        manifest: Manifest,
        audio: Map<String, ByteArray>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): SyncResult {
        val notetype = ensureNotetype(col)
        val errors = mutableListOf<Pair<String, String>>()
        val islandsByUuid = manifest.islands.associateBy { it.uuid }

        // 1. the island decks, renamed by uuid
        val deckNames = col.config.getObject(DECKS_CONFIG_KEY, JSONObject())
        for (island in manifest.islands) {
            val old = deckNames.optString(island.uuid).takeIf { it.isNotEmpty() && it != island.name }
            if (old != null) {
                for ((from, to) in listOf(
                    manifest.recognitionDeck(old) to manifest.recognitionDeck(island.name),
                    manifest.productionDeck(old) to manifest.productionDeck(island.name),
                )) {
                    val fromId = col.decks.idForName(from) ?: continue
                    if (col.decks.idForName(to) != null) {
                        errors += island.uuid to "cannot rename $from: $to already exists"
                        continue
                    }
                    col.decks.rename(fromId, to)
                }
            }
            col.decks.id(manifest.recognitionDeck(island.name))
            col.decks.id(manifest.productionDeck(island.name))
            deckNames.put(island.uuid, island.name)
        }

        // 2. the notes this door already owns, by sentence uuid
        val owned = HashMap<String, NoteId>()
        for (nid in col.findNotes("tag:$TAG_PREFIX*")) {
            for (tag in col.getNote(nid).tags) {
                if (tag.startsWith(TAG_PREFIX, ignoreCase = true)) owned[tag.substring(TAG_PREFIX.length)] = nid
            }
        }
        val updatedNids = HashSet<NoteId>()

        // 3. adoption: an existing note takes on a sentence's identity, history and all
        for ((uuid, nid) in manifest.adopt) {
            val note = runCatching { col.getNote(nid) }.getOrNull()
            when {
                note == null -> errors += uuid to "adopt: note $nid does not exist"
                note.noteTypeId != notetype.id -> errors += uuid to "adopt: note $nid is not a $NOTETYPE note"
                owned[uuid] == nid -> Unit
                owned.containsKey(uuid) -> errors += uuid to "adopt: the sentence already has note ${owned[uuid]}"
                else -> {
                    note.addTag(TAG_PREFIX + uuid)
                    col.updateNote(note).let { } // the OpChanges reach the deck list through the merged undo step
                    owned[uuid] = nid
                    updatedNids += nid
                }
            }
        }

        // 4. upsert, in island order then position, so new cards come up in that order
        val islandOrder = manifest.islands.withIndex().associate { (i, island) -> island.uuid to i }
        val ordered =
            manifest.sentences.sortedWith(
                compareBy<Sentence>({ islandOrder[it.island] ?: Int.MAX_VALUE }, { it.position }),
            )
        var added = 0
        val moves = HashMap<DeckId, MutableList<Long>>()
        // a new note's Production card is placed, not moved: only cards that
        // already existed count towards `moved`
        var moved = 0
        for ((done, sentence) in ordered.withIndex()) {
            onProgress(done, ordered.size)
            val island = islandsByUuid[sentence.island]
            if (island == null) {
                errors += sentence.uuid to "unknown island ${sentence.island}"
                continue
            }
            try {
                val jaAudio = soundField(col, sentence.jaAudio, audio)
                val enAudio = soundField(col, sentence.enAudio, audio)
                val recognitionDid = col.decks.id(manifest.recognitionDeck(island.name))
                val productionDid = col.decks.id(manifest.productionDeck(island.name))
                val existing = owned[sentence.uuid]?.let { runCatching { col.getNote(it) }.getOrNull() }
                val note: Note
                if (existing != null) {
                    note = existing
                    val before = note.fields.toList()
                    note.setItem(F_EN, escape(sentence.english))
                    note.setItem(F_JA, escape(sentence.japanese))
                    jaAudio?.let { note.setItem(F_JA_AUDIO, it) }
                    enAudio?.let { note.setItem(F_EN_AUDIO, it) }
                    if (note.fields != before) {
                        col.updateNote(note).let { } // the OpChanges reach the deck list through the merged undo step
                        updatedNids += note.id
                    }
                } else {
                    note = col.newNote(notetype)
                    note.setItem(F_EN, escape(sentence.english))
                    note.setItem(F_JA, escape(sentence.japanese))
                    note.setItem(F_JA_AUDIO, jaAudio.orEmpty())
                    note.setItem(F_EN_AUDIO, enAudio.orEmpty())
                    note.addTag(TAG_PREFIX + sentence.uuid)
                    col.addNote(note, recognitionDid)
                    owned[sentence.uuid] = note.id
                    added++
                }
                // 5. placement: Recognition under 認識, Production under 製作
                for (card in note.cards(col)) {
                    val target =
                        when (card.ord) {
                            0 -> recognitionDid
                            1 -> productionDid
                            else -> continue
                        }
                    val home = if (card.oDid != 0L) card.oDid else card.did
                    if (home != target) {
                        moves.getOrPut(target) { mutableListOf() } += card.id
                        if (existing != null) moved++
                    }
                }
            } catch (e: Exception) {
                errors += sentence.uuid to (e.message ?: e.javaClass.simpleName)
            }
        }
        onProgress(ordered.size, ordered.size)
        for ((did, cids) in moves) col.setDeck(cids, did)

        // 6. deletion: what the caller named, and in full mode every owned note it no longer lists
        val keep = manifest.sentences.map { it.uuid }.toSet()
        val doomed =
            buildSet {
                manifest.deleted.mapNotNullTo(this) { owned[it] }
                if (manifest.full) owned.filterKeys { it !in keep }.values.toCollection(this)
            }
        if (doomed.isNotEmpty()) col.removeNotes(noteIds = doomed)

        // 7. deleted islands: their decks go only when nothing is left in them
        for (uuid in manifest.deletedIslands) {
            val name = deckNames.optString(uuid).ifEmpty { islandsByUuid[uuid]?.name }
            if (name == null) {
                errors += uuid to "unknown island"
                continue
            }
            for (deckName in listOf(manifest.recognitionDeck(name), manifest.productionDeck(name))) {
                val did = col.decks.idForName(deckName) ?: continue
                if (holdsCards(col, did, deckName)) {
                    errors += uuid to "$deckName still holds cards; not removed"
                } else {
                    col.decks.remove(listOf(did))
                }
            }
            deckNames.remove(uuid)
        }
        col.config.set(DECKS_CONFIG_KEY, deckNames)

        // 8. media: our audio that nothing references any more goes to the trash
        trashUnreferencedAudio(col)

        return SyncResult(
            added = added,
            updated = (updatedNids - doomed).size,
            moved = moved,
            deleted = doomed.size,
            errors = errors,
        )
    }

    /** All notes of the [NOTETYPE] note type (empty when it does not exist). */
    private fun notesOf(col: Collection): List<NoteId> =
        if (col.notetypes.byName(NOTETYPE) == null) emptyList() else col.findNotes("\"note:$NOTETYPE\"")

    /** A deck holds cards when any card lives there, or calls it home from a filtered deck, or it has children. */
    private fun holdsCards(
        col: Collection,
        did: DeckId,
        name: String,
    ): Boolean =
        col.db.queryScalar("SELECT count() FROM cards WHERE did = ? OR odid = ?", did, did) > 0 ||
            col.decks.allNamesAndIds().any { it.name.startsWith("$name::") }

    /**
     * The field value for one audio path: null leaves the field as it is, ""
     * clears it, anything else is stored under its content hash.
     */
    private fun soundField(
        col: Collection,
        path: String?,
        audio: Map<String, ByteArray>,
    ): String? {
        if (path == null) return null
        if (path.isEmpty()) return ""
        val bytes = audio[path] ?: throw IllegalArgumentException("audio $path is not in the archive")
        require(bytes.isNotEmpty()) { "audio $path is empty" }
        val ext = path.substringAfterLast('.', "").lowercase().ifEmpty { "ogg" }
        val wanted = "$MEDIA_PREFIX${sha1(bytes)}.$ext"
        val name = if (col.media.have(wanted)) wanted else col.media.writeData(wanted, ByteString.copyFrom(bytes))
        return "[sound:$name]"
    }

    private fun trashUnreferencedAudio(col: Collection) {
        val soundRef = Regex("""\[sound:($MEDIA_PREFIX[^\]]+)]""")
        val referenced = HashSet<String>()
        for (nid in notesOf(col)) {
            for (field in col.getNote(nid).fields) soundRef.findAll(field).mapTo(referenced) { it.groupValues[1] }
        }
        val orphans =
            col.media.dir
                .listFiles()
                ?.map { it.name }
                ?.filter { it.startsWith(MEDIA_PREFIX) && it !in referenced }
                .orEmpty()
        if (orphans.isNotEmpty()) col.media.trashFiles(orphans)
    }

    /**
     * The note type, created on first use exactly as 白い熊's hand-made deck
     * has it. An existing one is never altered; one we cannot fill stops the
     * sync before it changes anything.
     */
    private fun ensureNotetype(col: Collection): NotetypeJson {
        col.notetypes.byName(NOTETYPE)?.let { existing ->
            requireCompatibleNotetype(col)
            return existing
        }
        val previousCurrent = col.config.get<Long>("curModel")
        val notetype = col.notetypes.new(NOTETYPE)
        for (name in FIELDS) col.notetypes.addField(notetype, col.notetypes.newField(name))
        col.notetypes.add_template(
            notetype,
            col.notetypes.newTemplate("Recognition").apply {
                qfmt = RECOGNITION_FRONT
                afmt = BACK
            },
        )
        col.notetypes.add_template(
            notetype,
            col.notetypes.newTemplate("Production").apply {
                qfmt = PRODUCTION_FRONT
                afmt = BACK
            },
        )
        notetype.css = CSS
        col.notetypes.add(notetype)
        // creating it made it the "current" note type; that is 白い熊's choice, not ours
        previousCurrent?.let { col.config.set("curModel", it) }
        return col.notetypes.byName(NOTETYPE)!!
    }

    /**
     * Refuses an existing `Language Islands` note type we cannot fill — before
     * a sync opens its undo step, so a refusal leaves not even an empty one.
     * No note type at all is fine: the sync creates it.
     *
     * @throws IllegalStateException a field or a template is missing
     */
    fun requireCompatibleNotetype(col: Collection) {
        val existing = col.notetypes.byName(NOTETYPE) ?: return
        val missing = FIELDS - existing.fieldsNames.toSet()
        check(missing.isEmpty()) { "note type $NOTETYPE lacks the fields $missing" }
        check(existing.templates.length() >= 2) { "note type $NOTETYPE needs the Recognition and Production templates" }
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    // The hand-made deck's templates and styling, verbatim (2026-10-01 export).

    private const val RECOGNITION_FRONT = "<span class=\"mid2\">{{japanese}}</span><br/>\n{{japanese-audio}}\n"

    private const val PRODUCTION_FRONT = "<span class=\"mid2\">{{english}}</span><br/>\n{{english-audio}}\n"

    private const val BACK =
        "<span class=\"mid2\">{{japanese}}</span><br/>\n" +
            "<span class=\"meaning\">{{english}}</span>\n" +
            "<hr/>\n" +
            "{{japanese-audio}}\n" +
            "{{english-audio}}\n"

    private val CSS =
        """
        .card
        {
          font-family: arial;
          text-align: center;
          color: blue;
        }

        .expression
        {
          font-family: 'MS PMincho';
          font-size: 32px;
          color: blue;
        }

        .media
        {
          font-family: Arial;
          font-size: 8px;
          color: yellow;
        }

        .meaning
        {
          font-family: Arial;
          font-size: 25px;
          font-weight: bold;
          color: blue;
        }

        .mid2 {
         font-family: gennominchou;
         font-size: 40px;
         text-align: center;
         color: yellow;
        }

        .reading
        {
          font-family: 'MS PGothic';
          font-size: 20px;
          color: yellow;
        }

        .time
        {
          font-family: Arial;
          font-size: 14px;
          color: blue;
        }

        @font-face { font-family: gennominchou; src: url('_gennominchou.ttf'); }
        """.trimIndent()
}
