// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.shiroikuma.ShiroikumaIslands.Island
import com.ichi2.anki.shiroikuma.ShiroikumaIslands.Manifest
import com.ichi2.anki.shiroikuma.ShiroikumaIslands.Sentence
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

/** The 言語島 sync engine against a real (in-memory) collection. */
@RunWith(AndroidJUnit4::class)
class ShiroikumaIslandsTest : RobolectricTest() {
    // the sync writes and trashes audio through the backend, which needs a real media folder and database
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    private val russia = Island("i-ru", "ロシア")
    private val sumo = Island("i-sumo", "相撲")

    private fun sentence(
        uuid: String,
        island: Island = russia,
        position: Int = 1,
        english: String = "I worked in Russia.",
        japanese: String = "ロシアで働きました。",
        jaAudio: String? = "audio/$uuid-ja.ogg",
        enAudio: String? = "audio/$uuid-en.ogg",
    ) = Sentence(uuid, island.uuid, position, english, japanese, jaAudio, enAudio)

    private fun manifest(
        vararg sentences: Sentence,
        islands: List<Island> = listOf(russia, sumo),
        full: Boolean = false,
        deleted: List<String> = emptyList(),
        deletedIslands: List<String> = emptyList(),
        adopt: Map<String, Long> = emptyMap(),
    ) = Manifest(full, "言語島々", "認識", "製作", islands, sentences.toList(), deleted, deletedIslands, adopt)

    private fun audioFor(vararg sentences: Sentence): Map<String, ByteArray> =
        sentences
            .flatMap { s -> listOfNotNull(s.jaAudio, s.enAudio).filter { it.isNotEmpty() } }
            .associateWith { "opus bytes of $it".toByteArray() }

    private fun sync(
        manifest: Manifest,
        audio: Map<String, ByteArray> = audioFor(*manifest.sentences.toTypedArray()),
    ) = ShiroikumaIslands.sync(col, manifest, audio)

    private fun noteOf(uuid: String): NoteId = col.findNotes("tag:li::uuid::$uuid").single()

    private fun deckOf(
        nid: NoteId,
        ord: Int,
    ): String =
        col
            .getNote(nid)
            .cards(col)
            .single { it.ord == ord }
            .let { col.decks.name(it.did) }

    @Test
    fun `a new sentence becomes one note with its two cards in the right trees`() {
        val result = sync(manifest(sentence("s1")))

        assertThat(result.errorLines, nullValue())
        assertThat(result.result, equalTo("OK:1|0|0|0"))
        val nid = noteOf("s1")
        val note = col.getNote(nid)
        assertThat(note.getItem("english"), equalTo("I worked in Russia."))
        assertThat(note.getItem("japanese-audio").startsWith("[sound:li_"), equalTo(true))
        assertThat(deckOf(nid, 0), equalTo("言語島々::認識::ロシア"))
        assertThat(deckOf(nid, 1), equalTo("言語島々::製作::ロシア"))
        val media = note.getItem("japanese-audio").removePrefix("[sound:").removeSuffix("]")
        assertThat("the audio is in the media folder", File(col.media.dir, media).exists(), equalTo(true))
    }

    @Test
    fun `the note type matches the hand-made deck and is created once`() {
        sync(manifest(sentence("s1")))
        val notetype = col.notetypes.byName("Language Islands")!!
        assertThat(notetype.fieldsNames, equalTo(listOf("english", "japanese", "japanese-audio", "english-audio")))
        assertThat(notetype.templates.length(), equalTo(2))
        assertThat(notetype.templates[0].name, equalTo("Recognition"))
        assertThat(notetype.templates[1].qfmt.contains("{{english-audio}}"), equalTo(true))

        sync(manifest(sentence("s2")))
        assertThat(col.notetypes.all().count { it.name == "Language Islands" }, equalTo(1))
    }

    @Test
    fun `an incompatible note type stops the sync before anything changes`() {
        val wrong = col.notetypes.new("Language Islands")
        col.notetypes.addField(wrong, col.notetypes.newField("english"))
        col.notetypes.add_template(wrong, col.notetypes.newTemplate("Only").apply { qfmt = "{{english}}" })
        col.notetypes.add(wrong)

        assertFailsWith<IllegalStateException> { sync(manifest(sentence("s1"))) }
        assertThat(col.decks.idForName("言語島々::認識::ロシア"), nullValue())
    }

    @Test
    fun `an edit updates in place and keeps the scheduling`() {
        sync(manifest(sentence("s1")))
        val nid = noteOf("s1")
        val card = col.getNote(nid).cards(col).first()
        card.ivl = 37
        col.updateCard(card)

        val result = sync(manifest(sentence("s1", english = "I worked seven years in Russia.")))

        assertThat(result.result, equalTo("OK:0|1|0|0"))
        assertThat("same note", noteOf("s1"), equalTo(nid))
        assertThat(col.getNote(nid).getItem("english"), equalTo("I worked seven years in Russia."))
        assertThat("interval kept", col.getCard(card.id).ivl, equalTo(37))
    }

    @Test
    fun `an unchanged sentence counts as nothing`() {
        sync(manifest(sentence("s1")))
        assertThat(sync(manifest(sentence("s1"))).result, equalTo("OK:0|0|0|0"))
    }

    @Test
    fun `text is escaped and an absent audio leaves the field alone`() {
        sync(manifest(sentence("s1")))
        val audioBefore = col.getNote(noteOf("s1")).getItem("english-audio")

        sync(manifest(sentence("s1", english = "Tom & Jerry <3", enAudio = null)))
        val note = col.getNote(noteOf("s1"))
        assertThat(note.getItem("english"), equalTo("Tom &amp; Jerry &lt;3"))
        assertThat(note.getItem("english-audio"), equalTo(audioBefore))

        sync(manifest(sentence("s1", english = "Tom & Jerry <3", enAudio = "")))
        assertThat(col.getNote(noteOf("s1")).getItem("english-audio"), equalTo(""))
    }

    @Test
    fun `an island rename renames both decks and keeps the cards in them`() {
        sync(manifest(sentence("s1")))
        val nid = noteOf("s1")

        val renamed = Island(russia.uuid, "露西亜")
        sync(manifest(sentence("s1"), islands = listOf(renamed)))

        assertThat(deckOf(nid, 0), equalTo("言語島々::認識::露西亜"))
        assertThat(deckOf(nid, 1), equalTo("言語島々::製作::露西亜"))
        assertThat(col.decks.idForName("言語島々::認識::ロシア"), nullValue())
    }

    @Test
    fun `delta deletes what it names, full deletes what it no longer lists, untagged notes never`() {
        val untagged = addBasicNote("hand", "made").id
        sync(manifest(sentence("s1"), sentence("s2", position = 2), sentence("s3", position = 3)))

        assertThat(sync(manifest(deleted = listOf("s1"))).result, equalTo("OK:0|0|0|1"))
        assertThat(col.findNotes("tag:li::uuid::s1"), equalTo(emptyList()))

        assertThat(sync(manifest(sentence("s2", position = 2), full = true)).result, equalTo("OK:0|0|0|1"))
        assertThat(col.findNotes("tag:li::uuid::s3"), equalTo(emptyList()))
        assertThat(col.findNotes("tag:li::uuid::s2").size, equalTo(1))
        assertThat("an untagged note survives full mode", col.findNotes("nid:$untagged").size, equalTo(1))
    }

    @Test
    fun `adoption takes over a hand-made note, history and all, and fixes swapped cards`() {
        // a hand-made note with its cards in the swapped trees, like 46 of the 相撲 notes
        sync(manifest(sentence("seed", island = sumo)))
        val notetype = col.notetypes.byName("Language Islands")!!
        val handMade = col.newNote(notetype)
        handMade.setItem("english", "The yokozuna won.")
        handMade.setItem("japanese", "横綱が勝ちました。")
        col.addNote(handMade, col.decks.id("言語島々::製作::相撲"))
        val cards = handMade.cards(col)
        col.setDeck(cards.filter { it.ord == 1 }.map { it.id }, col.decks.id("言語島々::認識::相撲"))
        val reviewed = cards.first().also { it.ivl = 12 }
        col.updateCard(reviewed)

        val result =
            sync(
                manifest(
                    sentence("seed", island = sumo),
                    sentence("adopted", island = sumo, english = "The yokozuna won.", japanese = "横綱が勝ちました。"),
                    adopt = mapOf("adopted" to handMade.id),
                ),
            )

        assertThat(result.result, equalTo("OK:0|1|2|0"))
        assertThat("same note", noteOf("adopted"), equalTo(handMade.id))
        assertThat(deckOf(handMade.id, 0), equalTo("言語島々::認識::相撲"))
        assertThat(deckOf(handMade.id, 1), equalTo("言語島々::製作::相撲"))
        assertThat("history kept", col.getCard(reviewed.id).ivl, equalTo(12))
    }

    @Test
    fun `an adopt of a missing note is reported, not guessed`() {
        val result = sync(manifest(adopt = mapOf("ghost" to 4242L)))
        assertThat(result.errorLines, equalTo("ghost\tadopt: note 4242 does not exist"))
    }

    @Test
    fun `media is content-named and replaced audio leaves no orphan`() {
        val first = sentence("s1")
        sync(manifest(first))
        val oldName =
            col
                .getNote(noteOf("s1"))
                .getItem("japanese-audio")
                .removePrefix("[sound:")
                .removeSuffix("]")

        sync(manifest(first), audio = audioFor(first).mapValues { (k, v) -> if (k.endsWith("-ja.ogg")) "re-rendered".toByteArray() else v })

        val newName =
            col
                .getNote(noteOf("s1"))
                .getItem("japanese-audio")
                .removePrefix("[sound:")
                .removeSuffix("]")
        assertThat(newName, not(equalTo(oldName)))
        assertThat("the old audio is no longer in the media folder", File(col.media.dir, oldName).exists(), equalTo(false))
        assertThat(File(col.media.dir, newName).exists(), equalTo(true))
    }

    @Test
    fun `a sentence whose audio is missing from the archive is reported and the rest goes ahead`() {
        val result = sync(manifest(sentence("s1"), sentence("s2", position = 2)), audio = audioFor(sentence("s1")))
        assertThat(result.result, equalTo("OK:1|0|0|0"))
        assertThat(result.errorLines, equalTo("s2\taudio audio/s2-ja.ogg is not in the archive"))
    }

    @Test
    fun `a deleted island's decks go only once they are empty`() {
        sync(manifest(sentence("s1")))
        val kept = sync(manifest(deletedIslands = listOf(russia.uuid)))
        assertThat(kept.errors.size, equalTo(2))
        assertThat(col.decks.idForName("言語島々::認識::ロシア") != null, equalTo(true))

        sync(manifest(sentence("s1"), deleted = listOf("s1")))
        sync(manifest(deletedIslands = listOf(russia.uuid)))
        assertThat(col.decks.idForName("言語島々::認識::ロシア"), nullValue())
        assertThat(col.decks.idForName("言語島々::製作::ロシア"), nullValue())
    }

    @Test
    fun `the list carries every note with its fields by name and each card's home deck`() {
        sync(manifest(sentence("s1")))
        val (json, count) = ShiroikumaIslands.listJson(col)
        val root = JSONObject(json)
        assertThat(count, equalTo(1))
        assertThat(root.getString("format"), equalTo("shiroikuma-anki-islands"))
        val note = root.getJSONArray("notes").getJSONObject(0)
        assertThat(note.getJSONObject("fields").getString("japanese"), equalTo("ロシアで働きました。"))
        assertThat(note.getJSONArray("tags").getString(0), equalTo("li::uuid::s1"))
        val decks = (0 until 2).map { note.getJSONArray("cards").getJSONObject(it).getString("deck") }
        assertThat(decks, hasItem("言語島々::製作::ロシア"))
    }

    @Test
    fun `the list is empty without the note type`() {
        val (json, count) = ShiroikumaIslands.listJson(col)
        assertThat(count, equalTo(0))
        assertThat(JSONObject(json).getJSONArray("notes").length(), equalTo(0))
    }

    @Test
    fun `the zip is read with its manifest and audio by path`() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                """{"version":1,"mode":"full","islands":[{"uuid":"i","name":"趣味"}],
                   "sentences":[{"uuid":"s","island":"i","position":1,"english":"e","japanese":"j",
                                 "ja_audio":"audio/s-ja.ogg","en_audio":null}],
                   "adopt":{"s":7}}""".toByteArray(),
            )
            zip.putNextEntry(ZipEntry("audio/s-ja.ogg"))
            zip.write(byteArrayOf(1, 2, 3))
        }
        val (manifest, files) = ShiroikumaIslands.readZip(ByteArrayInputStream(bytes.toByteArray()))
        assertThat(manifest.full, equalTo(true))
        assertThat(manifest.root, equalTo("言語島々"))
        assertThat(manifest.sentences.single().enAudio, nullValue())
        assertThat(manifest.adopt, equalTo(mapOf("s" to 7L)))
        assertThat(files.keys, equalTo(setOf("audio/s-ja.ogg")))
    }

    @Test
    fun `a newer manifest version is refused`() {
        assertFailsWith<IllegalArgumentException> { ShiroikumaIslands.parseManifest("""{"version":2}""") }
        assertFailsWith<IllegalArgumentException> { ShiroikumaIslands.parseManifest("""{"mode":"sideways"}""") }
    }
}
