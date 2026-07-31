// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.ProgressContext
import com.ichi2.anki.R
import com.ichi2.anki.common.time.Time
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.reopen
import com.ichi2.anki.withProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Fork: the category-based export/import behind the Export / Import panel at
 * the top of the 白い熊 暗記 UI page (mirrors the Kōjiki flow).
 *
 * The export is a single zip:
 * ```
 * manifest.json        {format, version, app, createdTs, categories}
 * collection.colpkg    the whole collection incl. media (backend colpkg)
 * ui.json              sk_* prefs (colours, fonts, sizes) — type-tagged JSON
 * fonts/<role>_font    the imported font files
 * controls.json        binding_* / previewer_* prefs
 * app_settings.json    every remaining default pref (minus credentials)
 * ```
 * Every category is an independent entry; import applies the *selected*
 * categories and silently skips those absent from the file, so exports from
 * older or newer builds keep working.
 */
object ShiroikumaExport {
    const val FORMAT = "shiroikuma-anki-export"
    const val VERSION = 1

    /**
     * Export files are recognized in the directory by this prefix + `.zip`.
     * The family convention (白い熊, 2026-07-25): every sister app writes
     * `<english-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip` — no version, no infix,
     * no suffix — because all apps' backups share one directory and must sort
     * and read uniformly.
     */
    const val FILE_PREFIX = "shiroikuma-anki_"

    /** Backups written before the family convention (`shiroikuma-anki-export_…`) */
    const val LEGACY_FILE_PREFIX = "shiroikuma-anki-export_"

    private const val EXIMPORT_PREFS = "sk_eximport" // device-local; never exported
    private const val KEY_DIR_URI = "dir_uri"
    private const val COLPKG_ENTRY = "collection.colpkg"
    private const val FONTS_DIR_ENTRY = "fonts/"

    /** Thrown when the user taps "Cancel export" */
    class ExportCancelledException : Exception("export cancelled")

    /**
     * A live media tally: file count and total bytes, filled while the count
     * runs so callers can render the running numbers before it finishes.
     * Started when the Export / Import panel opens; the export meter reads
     * the same instance instead of restarting the count.
     */
    class MediaTally {
        val files = AtomicInteger(0)
        val bytes = AtomicLong(0)

        @Volatile
        var done = false
    }

    /** Counts the collection's media folder into [tally] — live, file by file. */
    suspend fun tallyMedia(tally: MediaTally) {
        val mediaDir = withCol { media.dir }
        withContext(Dispatchers.IO) {
            runCatching {
                Files.newDirectoryStream(mediaDir.toPath()).use { stream ->
                    for (entry in stream) {
                        if (!isActive) return@use
                        tally.files.incrementAndGet()
                        tally.bytes.addAndGet(runCatching { Files.size(entry) }.getOrDefault(0L))
                    }
                }
            }
            tally.done = true
        }
    }

    /**
     * A selectable category; `id` is the entry name (`<id>.json`) inside the
     * zip. [defaultOn] is the automation contract's optional fourth
     * `LIST_CATEGORIES` field: whether a caller's backup-item picker starts
     * this one ticked. Every category is worth keeping, so they are all `on`;
     * only the media sub-option ([MEDIA_DEFAULT_ON]) is not.
     */
    enum class Cat(
        val id: String,
        @StringRes val labelRes: Int,
        val defaultOn: Boolean = true,
    ) {
        COLLECTION("collection", R.string.sk_eim_cat_collection),
        UI("ui", R.string.sk_eim_cat_ui),
        CONTROLS("controls", R.string.sk_eim_cat_controls),
        APP_SETTINGS("app_settings", R.string.sk_eim_cat_app),
        ;

        companion object {
            fun byId(id: String): Cat? = entries.firstOrNull { it.id == id }
        }
    }

    /**
     * The one sub-option: the media files inside [Cat.COLLECTION]. In the
     * automation contract's `items` list a parent id on its own means "that
     * category's own data only", so `collection` exports the collection
     * without media and `collection,collection.media` exports it with them.
     */
    const val MEDIA_ITEM_ID = "collection.media"

    /**
     * The media sub-option starts **unticked** — in every picker seeded from
     * us, the app's own and the automation caller's alike. It is by far the
     * largest part of the export, and re-obtainable by syncing the collection
     * from AnkiWeb; the collection itself stays on.
     */
    const val MEDIA_DEFAULT_ON = false

    /** What an automation `items` list selects: categories, and whether media rides along. */
    data class Selection(
        val cats: Set<Cat>,
        val includeMedia: Boolean,
    )

    /**
     * Parses the automation `items` extra — a comma-separated list of the ids
     * from `LIST_CATEGORIES`. Absent/empty selects everything.
     *
     * @throws IllegalArgumentException an id is not one of ours
     */
    fun parseItems(items: String): Selection {
        val ids = items.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return Selection(Cat.entries.toSet(), includeMedia = true)
        val cats = LinkedHashSet<Cat>()
        var media = false
        for (id in ids) {
            if (id == MEDIA_ITEM_ID) {
                // a child implies its parent
                media = true
                cats += Cat.COLLECTION
                continue
            }
            cats += Cat.byId(id) ?: throw IllegalArgumentException("unknown category in items: $items")
        }
        return Selection(cats, media)
    }

    /**
     * One live meter line: the display text, plus the same numbers structured
     * so a caller can drive a bar or a notification with them. Real counts
     * only — never a percentage (白い熊's automation contract).
     */
    data class Progress(
        val text: String,
        val current: Long,
        val total: Long,
        val unit: String,
    )

    const val UNIT_CATEGORIES = "区分"
    const val UNIT_MEDIA = "メディア"
    const val UNIT_BYTES = "bytes"

    private fun isUiKey(key: String) = key.startsWith("sk_")

    private fun isControlsKey(key: String) = key.startsWith("binding_") || key.startsWith("previewer_")

    /** The default-prefs key filter of a settings category; null for the collection */
    fun keyFilter(cat: Cat): ((String) -> Boolean)? =
        when (cat) {
            Cat.COLLECTION -> null
            Cat.UI -> ::isUiKey
            Cat.CONTROLS -> ::isControlsKey
            Cat.APP_SETTINGS -> { key -> !isUiKey(key) && !isControlsKey(key) }
        }

    // The export directory: a persisted SAF tree URI in its own device-local
    // prefs file, deliberately outside the default store so it never travels
    // in an export (another device holds no permission for the URI).

    private fun eximportPrefs(context: Context) = context.getSharedPreferences(EXIMPORT_PREFS, Context.MODE_PRIVATE)

    fun dirUri(context: Context): Uri? =
        eximportPrefs(context).getString(KEY_DIR_URI, null)?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }

    fun setDirUri(
        context: Context,
        uri: Uri,
    ) = eximportPrefs(context).edit { putString(KEY_DIR_URI, uri.toString()) }

    fun exportDir(context: Context): DocumentFile? =
        dirUri(context)
            ?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    /** One of ours: the family name, or a backup written under the old one */
    fun isExportFileName(name: String?): Boolean =
        name != null &&
            name.endsWith(".zip") &&
            (name.startsWith(FILE_PREFIX) || name.startsWith(LEGACY_FILE_PREFIX))

    /** The newest export file in the directory, by modification time */
    fun latestExport(context: Context): DocumentFile? =
        exportDir(context)?.let { dir ->
            runCatching {
                dir
                    .listFiles()
                    .filter { it.isFile && isExportFileName(it.name) }
                    .maxByOrNull { it.lastModified() }
            }.getOrNull()
        }

    fun formatTimestamp(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMs))

    /** Datetime-stamped export filename, e.g. `shiroikuma-anki_2026-07-24_10-00-00.zip` */
    fun exportFileName(time: Time = TimeManager.time): String =
        FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(time.currentDate) + ".zip"

    /**
     * Writes a zip of the selected categories to [openOutput]'s stream.
     * The collection is exported through the backend first (it needs a real
     * filesystem path), staged in the cache dir and streamed into the zip.
     *
     * [onProgress] receives live meter lines — the backend's own export
     * progress with an out-of-how-many media total (read from [mediaTally],
     * the count the panel already started) while the colpkg is produced,
     * then a byte meter while it streams into the zip. May be called from
     * any thread. [isCancelled] is polled throughout; a true answer aborts
     * the backend op and raises [ExportCancelledException].
     */
    suspend fun export(
        context: Context,
        cats: Set<Cat>,
        onProgress: (Progress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        includeMedia: Boolean = true,
        mediaTally: MediaTally? = null,
        openOutput: () -> OutputStream,
    ) {
        // the category counter: "区分 2/4 — UI (colours · fonts)"
        val ordered = Cat.entries.filter { it in cats }

        fun catProgress(cat: Cat) {
            val done = ordered.indexOf(cat) + 1
            onProgress(
                Progress(
                    context.getString(R.string.sk_eim_category_meter, done, ordered.size, context.getString(cat.labelRes)),
                    done.toLong(),
                    ordered.size.toLong(),
                    UNIT_CATEGORIES,
                ),
            )
        }

        val colpkg =
            if (Cat.COLLECTION in cats) {
                catProgress(Cat.COLLECTION)
                exportCollectionToCache(context, onProgress, isCancelled, includeMedia, mediaTally)
            } else {
                null
            }
        try {
            withContext(Dispatchers.IO) {
                ZipOutputStream(openOutput().buffered()).use { zip ->
                    val manifest =
                        JSONObject()
                            .put("format", FORMAT)
                            .put("version", VERSION)
                            .put("app", context.packageName)
                            .put("createdTs", TimeManager.time.intTimeMS())
                            .put("categories", JSONArray(cats.map { it.id }))
                    writeEntry(zip, "manifest.json", manifest.toString(2).toByteArray())
                    for (cat in Cat.entries) {
                        if (cat !in cats) continue
                        if (isCancelled()) throw ExportCancelledException()
                        if (cat != Cat.COLLECTION) catProgress(cat)
                        if (cat == Cat.COLLECTION) {
                            zip.putNextEntry(ZipEntry(COLPKG_ENTRY))
                            copyWithByteMeter(context, colpkg!!, zip, onProgress, isCancelled)
                            zip.closeEntry()
                        } else {
                            writeEntry(
                                zip,
                                "${cat.id}.json",
                                ShiroikumaUi.exportSettingsJson(context, keyFilter(cat)!!).toByteArray(),
                            )
                            if (cat == Cat.UI) writeFonts(context, zip)
                        }
                    }
                }
            }
        } finally {
            colpkg?.delete()
        }
    }

    /**
     * Applies the selected categories from an exported zip. Categories absent
     * from the file are skipped; the collection (when present and selected) is
     * applied first, then the settings, then the font files.
     *
     * @return per-category human summary lines
     * @throws IllegalArgumentException the file is not a 白い熊 暗記 export
     */
    suspend fun import(
        context: Context,
        cats: Set<Cat>,
        openInput: () -> InputStream,
    ): String {
        val staged = withContext(Dispatchers.IO) { stage(context, cats, openInput) }
        try {
            require(staged.manifestOk) { context.getString(R.string.sk_eim_import_not_ours) }
            val parts = mutableListOf<String>()
            staged.colpkg?.let {
                CollectionManager.importColpkg(it.path)
                parts += context.getString(Cat.COLLECTION.labelRes) + ": ✓"
            }
            for (cat in listOf(Cat.UI, Cat.CONTROLS, Cat.APP_SETTINGS)) {
                if (cat !in cats) continue
                val json = staged.settingsJson[cat.id] ?: continue
                val applied = ShiroikumaUi.importSettingsJson(context, json)
                parts += context.getString(cat.labelRes) + ": $applied"
            }
            if (staged.fonts.isNotEmpty()) {
                val dir = ShiroikumaUi.fontsDir(context).apply { mkdirs() }
                for ((name, bytes) in staged.fonts) {
                    File(dir, File(name).name).writeBytes(bytes) // basename only — no path traversal
                }
                ShiroikumaUi.invalidateAllFontCaches()
            }
            return parts.joinToString("\n")
        } finally {
            staged.colpkg?.delete()
        }
    }

    private class StagedImport {
        var manifestOk = false
        val settingsJson = mutableMapOf<String, String>()
        val fonts = mutableMapOf<String, ByteArray>()
        var colpkg: File? = null
    }

    /**
     * Single streaming pass over the zip: small entries into memory, the
     * colpkg (potentially media-sized) into a cache file. Nothing is applied
     * yet, so a foreign file is rejected without side effects.
     */
    private fun stage(
        context: Context,
        cats: Set<Cat>,
        openInput: () -> InputStream,
    ): StagedImport {
        val staged = StagedImport()
        ZipInputStream(openInput().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "manifest.json" ->
                        staged.manifestOk =
                            runCatching {
                                JSONObject(zis.readBytes().decodeToString()).optString("format") == FORMAT
                            }.getOrDefault(false)
                    name == COLPKG_ENTRY && Cat.COLLECTION in cats -> {
                        val tmp = File(context.cacheDir, "sk-import.colpkg")
                        tmp.outputStream().use { zis.copyTo(it) }
                        staged.colpkg = tmp
                    }
                    name.startsWith(FONTS_DIR_ENTRY) && Cat.UI in cats && !entry.isDirectory ->
                        staged.fonts[name.removePrefix(FONTS_DIR_ENTRY)] = zis.readBytes()
                    else -> {
                        val cat = Cat.entries.firstOrNull { "${it.id}.json" == name }
                        if (cat != null && cat in cats) {
                            staged.settingsJson[cat.id] = zis.readBytes().decodeToString()
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }
        return staged
    }

    /**
     * Exports the collection to a temp .colpkg in the cache dir; the backend
     * cannot write to a SAF stream directly. The backend's own exporting
     * progress (media file counts) is polled every 100ms and forwarded to
     * [onProgress] with an " / out-of-how-many" suffix read from
     * [mediaTally] — the count the panel started on open, so the meter picks
     * up the running tally (ellipsis while still counting) instead of
     * restarting it. The 100ms poll also answers [isCancelled] by aborting
     * the backend op.
     */
    private suspend fun exportCollectionToCache(
        context: Context,
        onProgress: (Progress) -> Unit,
        isCancelled: () -> Boolean,
        includeMedia: Boolean,
        mediaTally: MediaTally?,
    ): File {
        val out = File(context.cacheDir, "sk-eximport.colpkg")
        out.delete()
        val progressBackend = CollectionManager.getBackend()
        progressBackend.withProgress(
            progressContext = ProgressContext(),
            extractProgress = {
                if (progress.hasExporting()) {
                    text = progress.exporting
                }
            },
            updateUi = {
                if (isCancelled()) progressBackend.setWantsAbort()
                text?.let {
                    val counted = mediaTally?.files?.get() ?: 0
                    val suffix =
                        mediaTally?.let { tally ->
                            if (tally.done) " / $counted" else " / $counted…"
                        } ?: ""
                    onProgress(Progress(it + suffix, firstNumberIn(it), counted.toLong(), UNIT_MEDIA))
                }
            },
        ) {
            withCol {
                close(forFullSync = true)
                backend.exportCollectionPackage(outPath = out.path, includeMedia = includeMedia, legacy = false)
                reopen()
            }
        }
        if (isCancelled()) throw ExportCancelledException()
        return out
    }

    /**
     * The backend's progress lines are localized sentences ("Processed 1,234
     * media files…"); the leading count is the structured number the
     * automation contract wants alongside the text.
     */
    private fun firstNumberIn(text: String): Long =
        Regex("""\d[\d,]*""")
            .find(text)
            ?.value
            ?.replace(",", "")
            ?.toLongOrNull() ?: 0L

    /** Streams [source] into [zip], reporting "x MB / y MB" every few megabytes. */
    private fun copyWithByteMeter(
        context: Context,
        source: File,
        zip: ZipOutputStream,
        onProgress: (Progress) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val total = source.length()
        val buffer = ByteArray(1 shl 19)
        var copied = 0L
        var lastReported = -1L
        source.inputStream().use { input ->
            while (true) {
                if (isCancelled()) throw ExportCancelledException()
                val read = input.read(buffer)
                if (read < 0) break
                zip.write(buffer, 0, read)
                copied += read
                if (copied - lastReported >= (4L shl 20) || copied == total) {
                    lastReported = copied
                    onProgress(
                        Progress(
                            context.getString(
                                R.string.sk_eim_writing_zip,
                                Formatter.formatShortFileSize(context, copied),
                                Formatter.formatShortFileSize(context, total),
                            ),
                            copied,
                            total,
                            UNIT_BYTES,
                        ),
                    )
                }
            }
        }
    }

    private fun writeFonts(
        context: Context,
        zip: ZipOutputStream,
    ) {
        ShiroikumaUi.fontsDir(context).listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            zip.putNextEntry(ZipEntry(FONTS_DIR_ENTRY + file.name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        name: String,
        content: ByteArray,
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }
}
