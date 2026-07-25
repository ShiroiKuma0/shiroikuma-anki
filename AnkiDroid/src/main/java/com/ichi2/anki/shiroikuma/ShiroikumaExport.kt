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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /** Export files are recognized in the directory by this prefix + `.zip` */
    const val FILE_PREFIX = "shiroikuma-anki-"

    private const val EXIMPORT_PREFS = "sk_eximport" // device-local; never exported
    private const val KEY_DIR_URI = "dir_uri"
    private const val COLPKG_ENTRY = "collection.colpkg"
    private const val FONTS_DIR_ENTRY = "fonts/"

    /** A selectable category; `id` is the entry name (`<id>.json`) inside the zip. */
    enum class Cat(
        val id: String,
        @StringRes val labelRes: Int,
    ) {
        COLLECTION("collection", R.string.sk_eim_cat_collection),
        UI("ui", R.string.sk_eim_cat_ui),
        CONTROLS("controls", R.string.sk_eim_cat_controls),
        APP_SETTINGS("app_settings", R.string.sk_eim_cat_app),
    }

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

    /** The newest export file in the directory, by modification time */
    fun latestExport(context: Context): DocumentFile? =
        exportDir(context)?.let { dir ->
            runCatching {
                dir
                    .listFiles()
                    .filter {
                        it.isFile &&
                            it.name?.startsWith(FILE_PREFIX) == true &&
                            it.name?.endsWith(".zip") == true
                    }.maxByOrNull { it.lastModified() }
            }.getOrNull()
        }

    fun formatTimestamp(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMs))

    /** Datetime-stamped export filename, e.g. `shiroikuma-anki-export_2026-07-24_10-00-00.zip` */
    fun exportFileName(time: Time = TimeManager.time): String =
        FILE_PREFIX + "export_" +
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(time.currentDate) + ".zip"

    /**
     * Writes a zip of the selected categories to [openOutput]'s stream.
     * The collection is exported through the backend first (it needs a real
     * filesystem path), staged in the cache dir and streamed into the zip.
     *
     * [onProgress] receives live meter lines — the backend's own export
     * progress (media counts) while the colpkg is produced, then a byte
     * meter while it streams into the zip. May be called from any thread.
     */
    suspend fun export(
        context: Context,
        cats: Set<Cat>,
        onProgress: (String) -> Unit = {},
        openOutput: () -> OutputStream,
    ) {
        val colpkg = if (Cat.COLLECTION in cats) exportCollectionToCache(context, onProgress) else null
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
                        if (cat == Cat.COLLECTION) {
                            zip.putNextEntry(ZipEntry(COLPKG_ENTRY))
                            copyWithByteMeter(context, colpkg!!, zip, onProgress)
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
     * Exports the collection (with media) to a temp .colpkg in the cache dir;
     * the backend cannot write to a SAF stream directly. The backend's own
     * exporting progress (media file counts) is polled every 100ms and
     * forwarded to [onProgress] so the long media stage shows a live meter.
     */
    private suspend fun exportCollectionToCache(
        context: Context,
        onProgress: (String) -> Unit,
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
            updateUi = { text?.let(onProgress) },
        ) {
            withCol {
                close(forFullSync = true)
                backend.exportCollectionPackage(outPath = out.path, includeMedia = true, legacy = false)
                reopen()
            }
        }
        return out
    }

    /** Streams [source] into [zip], reporting "x MB / y MB" every few megabytes. */
    private fun copyWithByteMeter(
        context: Context,
        source: File,
        zip: ZipOutputStream,
        onProgress: (String) -> Unit,
    ) {
        val total = source.length()
        val buffer = ByteArray(1 shl 19)
        var copied = 0L
        var lastReported = -1L
        source.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                zip.write(buffer, 0, read)
                copied += read
                if (copied - lastReported >= (4L shl 20) || copied == total) {
                    lastReported = copied
                    onProgress(
                        context.getString(
                            R.string.sk_eim_writing_zip,
                            Formatter.formatShortFileSize(context, copied),
                            Formatter.formatShortFileSize(context, total),
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
