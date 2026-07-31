// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.ichi2.anki.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Fork: the sister-app **state-export automation contract** — the wire shape
 * every 白い熊 app exposes so 自由作業盤's 保存復元 project can back them all
 * up in one headless run (reference implementations: renrakusaki's
 * `BackupContactsReceiver`, the EMUI-proven round-trip, and 自由作業盤's own
 * `StateExportReceiver`).
 *
 * Three actions, all token-gated by [AutomationAuth]:
 * - `<pkg>.action.EXPORT_STATE` — runs the Export / Import panel's own export
 *   ([ShiroikumaExport.export]) with no UI, writing **one** zip. Extras (all
 *   String): `token` (required), `path` (optional absolute directory, wins
 *   over the configured SAF directory), `items` (optional comma list of the
 *   ids from `LIST_CATEGORIES`; absent/empty = everything),
 *   `progress_action` (optional), plus the reply trio `reply_action` /
 *   `reply_package` / `reply_id`.
 * - `<pkg>.action.LIST_CATEGORIES` — instant; enumerates the exportable
 *   categories as `id<TAB>label<TAB>parent<TAB>on|off` lines: the parent id
 *   of a sub-option (empty for a top-level item), then whether the caller's
 *   picker starts it ticked.
 * - `<pkg>.action.CANCEL_EXPORT` — stops the export in flight and deletes the
 *   file it was writing. Extras: `token` (required) and an optional
 *   `reply_id` (absent = whatever is running; two exports at once are
 *   forbidden by the contract). Fire-and-forget: it answers nothing itself,
 *   the stopped export answers `ERROR:cancelled` to *its* request, and a
 *   cancel arriving with nothing running is a silent no-op.
 *
 * The reply is a **fresh broadcast** to `reply_package`, action
 * `reply_action`, extras `reply_id` (echoed verbatim) + `result`:
 * `OK:<path>|<bytes>|<human size>|<n> categories`, `OK:` + the category
 * lines, or `ERROR:<reason>`. Exactly one terminal reply per request,
 * [AtomicBoolean]-guarded so an async success and a synchronous error can
 * never both fire.
 *
 * Hard-won and not to be "improved": no `ResultReceiver`, no `PendingIntent`,
 * no `Messenger`, and never a reliance on the ordered-broadcast result — EMUI
 * severs both channels between third-party apps (verified on 白い熊's
 * Mate XT, 2026-07-23). [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] matters too:
 * without it a backgrounded caller never hears the reply.
 */
class StateExportReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.trim().orEmpty()
        val pathOverride = intent.getStringExtra(EXTRA_PATH)?.trim().orEmpty()
        val items = intent.getStringExtra(EXTRA_ITEMS)?.trim().orEmpty()

        val replied = AtomicBoolean(false)

        fun reply(result: String) {
            if (!replied.compareAndSet(false, true)) return
            Timber.i("automation %s → %s", action, result.take(200))
            if (replyAction.isEmpty() || replyPackage.isEmpty()) return
            app.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra(EXTRA_RESULT, result)
                },
            )
        }

        // Gate first — "disabled" and "bad token" stay distinct errors.
        if (!AutomationAuth.enabled(app)) {
            reply("ERROR:automation disabled")
            return
        }
        if (!AutomationAuth.isTokenValid(app, token)) {
            reply("ERROR:bad token")
            return
        }

        when (action) {
            listCategoriesAction(app) -> reply("OK:" + categoryLines(app))
            cancelExportAction(app) -> cancelRunningExport(replyId)
            exportStateAction(app) -> {
                val selection =
                    try {
                        ShiroikumaExport.parseItems(items)
                    } catch (e: IllegalArgumentException) {
                        reply("ERROR:${e.message}")
                        return
                    }
                exportAsync(app, selection, pathOverride, replyId, replyPackage, progressAction, ::reply)
            }
            else -> reply("ERROR:unknown action: $action")
        }
    }

    /**
     * `id<TAB>label<TAB>parent<TAB>on|off` per line — the contract's four
     * positional fields. The media sub-option carries its parent's id, so the
     * caller can render it indented under Collection and select it
     * independently; a top-level item leaves that field empty. The last field
     * is our answer to "does this start ticked", so the caller's picker never
     * has to guess.
     */
    private fun categoryLines(context: Context): String =
        buildString {
            fun line(
                id: String,
                label: String,
                parent: String,
                defaultOn: Boolean,
            ) {
                append(id)
                    .append('\t')
                    .append(label)
                    .append('\t')
                    .append(parent)
                    .append('\t')
                    .append(if (defaultOn) "on" else "off")
                    .append('\n')
            }
            for (cat in ShiroikumaExport.Cat.entries) {
                line(cat.id, context.getString(cat.labelRes), "", cat.defaultOn)
                if (cat == ShiroikumaExport.Cat.COLLECTION) {
                    line(
                        ShiroikumaExport.MEDIA_ITEM_ID,
                        context.getString(R.string.sk_eim_include_media),
                        cat.id,
                        ShiroikumaExport.MEDIA_DEFAULT_ON,
                    )
                }
            }
        }.trimEnd('\n')

    /**
     * `CANCEL_EXPORT`: raise the flag the export polls and let it unwind at
     * the next entry boundary — never an interrupt mid-write, never a kill.
     * The terminal `ERROR:cancelled` belongs to the *export's* request, so
     * this one is answered with nothing at all; arriving when nothing is
     * running, or after the export already finished, is a silent no-op.
     */
    private fun cancelRunningExport(replyId: String) {
        val run = runningExport.get()
        if (run == null || (replyId.isNotEmpty() && replyId != run.replyId)) {
            Timber.i("automation cancel: no matching export in flight")
            return
        }
        Timber.i("automation cancel: stopping export %s", run.replyId)
        run.cancelled = true
    }

    /** The export in flight, and the one flag that stops it. */
    private class RunningExport(
        val replyId: String,
    ) {
        @Volatile
        var cancelled = false
    }

    /**
     * The export holds the broadcast open with `goAsync()` and runs on IO.
     * (The collection export is the slow part; should 白い熊's collection ever
     * grow past the ~10 minute broadcast window, this is the point that has to
     * become a foreground service.)
     */
    private fun exportAsync(
        app: Context,
        selection: ShiroikumaExport.Selection,
        pathOverride: String,
        replyId: String,
        replyPackage: String,
        progressAction: String,
        reply: (String) -> Unit,
    ) {
        val appLabel = app.packageManager.getApplicationLabel(app.applicationInfo).toString()
        val lastProgressAt = AtomicLong(0)

        fun sendProgress(
            progress: ShiroikumaExport.Progress,
            force: Boolean = false,
        ) {
            if (progressAction.isEmpty() || replyPackage.isEmpty()) return
            // throttled to one every 500ms; the final line is always forced
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastProgressAt.get() < PROGRESS_INTERVAL_MS) return
            lastProgressAt.set(now)
            app.sendBroadcast(
                Intent(progressAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra(EXTRA_PROGRESS_APP, appLabel)
                    putExtra(EXTRA_PROGRESS_TEXT, progress.text)
                    putExtra(EXTRA_PROGRESS_CURRENT, progress.current)
                    putExtra(EXTRA_PROGRESS_TOTAL, progress.total)
                    putExtra(EXTRA_PROGRESS_UNIT, progress.unit)
                },
            )
        }

        val pending = goAsync()
        val run = RunningExport(replyId)
        runningExport.set(run)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val fileName = ShiroikumaExport.exportFileName()
                val cats = selection.cats
                // the directory is resolved first: a bad one must fail before
                // the media count, not after minutes of work
                val target = resolveTarget(app, pathOverride, fileName)
                // everything past this point can leave a file behind, so one
                // guard covers the lot: cancelled or failed, the directory is
                // left exactly as it was found
                val (bytes, shownPath) =
                    try {
                        // count the media up front so the meter has a real total
                        // from its first line (the panel starts the same tally
                        // on open)
                        val tally =
                            ShiroikumaExport.MediaTally().also {
                                if (selection.includeMedia && ShiroikumaExport.Cat.COLLECTION in cats) {
                                    runCatching { ShiroikumaExport.tallyMedia(it) }
                                        .onFailure { e -> Timber.w(e, "media tally failed") }
                                }
                            }
                        if (run.cancelled) throw ShiroikumaExport.ExportCancelledException()
                        when (target) {
                            is Target.PlainFile -> {
                                target.file.outputStream().use { out ->
                                    ShiroikumaExport.export(
                                        app,
                                        cats,
                                        onProgress = { sendProgress(it) },
                                        isCancelled = { run.cancelled },
                                        includeMedia = selection.includeMedia,
                                        mediaTally = tally,
                                        openOutput = { out },
                                    )
                                }
                                target.file.length() to target.file.absolutePath
                            }
                            is Target.SafFile -> {
                                app.contentResolver.openOutputStream(target.doc.uri).use { out ->
                                    requireNotNull(out) { "cannot open $fileName for writing" }
                                    ShiroikumaExport.export(
                                        app,
                                        cats,
                                        onProgress = { sendProgress(it) },
                                        isCancelled = { run.cancelled },
                                        includeMedia = selection.includeMedia,
                                        mediaTally = tally,
                                        openOutput = { out },
                                    )
                                }
                                target.doc.length() to (absolutePathOf(target.doc) ?: target.doc.uri.toString())
                            }
                        }
                    } catch (e: Exception) {
                        deletePartial(target)
                        throw e
                    }
                sendProgress(
                    ShiroikumaExport.Progress(
                        "${ShiroikumaExport.UNIT_CATEGORIES} ${cats.size}/${cats.size} — ${humanSize(bytes)}",
                        cats.size.toLong(),
                        cats.size.toLong(),
                        ShiroikumaExport.UNIT_CATEGORIES,
                    ),
                    force = true,
                )
                reply("OK:$shownPath|$bytes|${humanSize(bytes)}|${cats.size} categories")
            } catch (e: Exception) {
                // the same AtomicBoolean guards both, so a cancel racing a
                // finished export can never turn an OK into an error
                if (run.cancelled || e is ShiroikumaExport.ExportCancelledException || e is BackendInterruptedException) {
                    Timber.i("automation export cancelled")
                    reply("ERROR:cancelled")
                } else {
                    Timber.w(e, "automation export failed")
                    reply("ERROR:${e.message ?: e.javaClass.simpleName}")
                }
            } finally {
                runningExport.compareAndSet(run, null)
                pending.finish()
            }
        }
    }

    /**
     * Removes the file the export was writing. A cancelled export must leave
     * the backup directory exactly as it found it — no short archive, no
     * stray file for the caller to mistake for a backup.
     */
    private fun deletePartial(target: Target) {
        runCatching {
            when (target) {
                is Target.PlainFile -> target.file.delete()
                is Target.SafFile -> target.doc.delete()
            }
        }.onFailure { Timber.w(it, "could not delete the partial export") }
    }

    private sealed interface Target {
        /** All-Files-Access route: the `path` extra, written with plain java.io */
        class PlainFile(
            val file: File,
        ) : Target

        /** The app's own configured SAF export directory */
        class SafFile(
            val doc: DocumentFile,
        ) : Target
    }

    /**
     * Directory precedence per the contract: the `path` extra, then the app's
     * configured export directory, then `ERROR:no-directory`. `path` needs
     * All-Files-Access; without it we fall back to the configured directory
     * rather than failing, and only report `no-storage-access` when there is
     * no fallback either.
     */
    private fun resolveTarget(
        app: Context,
        pathOverride: String,
        fileName: String,
    ): Target {
        if (pathOverride.isNotEmpty() && canWriteAnyPath()) {
            val dir = File(pathOverride)
            dir.mkdirs()
            require(dir.isDirectory) { "not a directory: $pathOverride" }
            return Target.PlainFile(File(dir, fileName))
        }
        val dir =
            ShiroikumaExport.exportDir(app)
                ?: throw IllegalStateException(
                    if (pathOverride.isEmpty()) "no-directory" else "no-storage-access",
                )
        val doc =
            dir.createFile("application/zip", fileName)
                ?: throw IllegalStateException("cannot create $fileName in the export directory")
        return Target.SafFile(doc)
    }

    private fun canWriteAnyPath(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /**
     * `content://…/tree/primary%3A〇%2F…` → `/storage/emulated/0/〇/…`, so the
     * reply carries a real path even on the SAF route. Null when the document
     * id is not a storage-volume path.
     */
    private fun absolutePathOf(doc: DocumentFile): String? {
        val parts =
            runCatching { DocumentsContract.getDocumentId(doc.uri).split(":", limit = 2) }
                .getOrNull()
                ?.takeIf { it.size == 2 }
                ?: return null
        val (volume, relative) = parts
        @Suppress("DEPRECATION")
        val root = if (volume == "primary") Environment.getExternalStorageDirectory().path else "/storage/$volume"
        return "$root/$relative"
    }

    companion object {
        /** `<applicationId>.action.EXPORT_STATE` — the manifest filter uses `${applicationId}` too */
        fun exportStateAction(context: Context) = "${context.packageName}.action.EXPORT_STATE"

        fun listCategoriesAction(context: Context) = "${context.packageName}.action.LIST_CATEGORIES"

        fun cancelExportAction(context: Context) = "${context.packageName}.action.CANCEL_EXPORT"

        /**
         * The export in flight, if any. Static because every broadcast lands
         * on a *fresh* receiver instance: the cancel could not otherwise reach
         * the export it has to stop. The contract forbids two exports at once,
         * so a `CANCEL_EXPORT` without a `reply_id` is unambiguous.
         */
        private val runningExport = AtomicReference<RunningExport?>(null)

        // Contract extras — deliberately bare names, shared verbatim by every sister app.
        const val EXTRA_TOKEN = "token"
        const val EXTRA_PATH = "path"
        const val EXTRA_ITEMS = "items"
        const val EXTRA_PROGRESS_ACTION = "progress_action"
        const val EXTRA_REPLY_ACTION = "reply_action"
        const val EXTRA_REPLY_PACKAGE = "reply_package"
        const val EXTRA_REPLY_ID = "reply_id"
        const val EXTRA_RESULT = "result"
        const val EXTRA_PROGRESS_APP = "app"
        const val EXTRA_PROGRESS_TEXT = "text"
        const val EXTRA_PROGRESS_CURRENT = "current"
        const val EXTRA_PROGRESS_TOTAL = "total"
        const val EXTRA_PROGRESS_UNIT = "unit"

        private const val PROGRESS_INTERVAL_MS = 500L

        /**
         * The caller cannot stat the file, so we hand it both the bytes and a
         * display size. Locale-independent on purpose: `4.6 MB` reads the same
         * in every sister app's reply, and the byte count is the exact figure.
         */
        fun humanSize(bytes: Long): String =
            when {
                bytes >= 1L shl 30 -> "%.2f GB".format(Locale.US, bytes / (1L shl 30).toDouble())
                bytes >= 1L shl 20 -> "%.1f MB".format(Locale.US, bytes / (1L shl 20).toDouble())
                bytes >= 1L shl 10 -> "%.1f KB".format(Locale.US, bytes / (1L shl 10).toDouble())
                else -> "$bytes B"
            }
    }
}
