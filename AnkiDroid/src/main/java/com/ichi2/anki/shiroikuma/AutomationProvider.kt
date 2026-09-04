// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.pm.PackageInfoCompat
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Fork: the **data door** of the sister-app automation contract (v2) — export
 * this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * **A broadcast cannot tell you who sent it.** v1's answer to that was the
 * shared secret, which cannot survive the wipe this feature exists to recover
 * from. A provider gets the caller's identity from the framework — see
 * [AutomationCallers] for what is actually checked, and why a package-name
 * prefix would have been *worse* than the token it replaced.
 *
 * **And a list needs a synchronous answer.** 応用管理 draws a row per installed
 * app before any export exists; a broadcast round trip per app to fill a list
 * is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. [call] validates, starts a foreground service and returns — a
 * collection with media is tens or hundreds of megabytes over minutes, and
 * inside a binder call that would block the caller, report no progress, refuse
 * cancellation and die silently if this process were killed.
 *
 * ## Why a descriptor and not a path
 *
 * A backup is not a stable directory while it is being assembled: 応用管理
 * writes into a temporary path and renames on commit, and it encrypts and
 * checksums **per file it knows about**. A file this app dropped in itself
 * would be renamed out from under it, would sit in plaintext inside an
 * encrypted backup, and would be unverified rather than verified-and-failing.
 * A descriptor is also a capability that **expires when it is closed**.
 *
 * It also means the automation path no longer needs `MANAGE_EXTERNAL_STORAGE`:
 * that was only ever required because v1 handed apps an absolute path.
 *
 * **[METHOD_IMPORT] exists only here and never gets a broadcast action** — an
 * import overwrites the collection, and [StateExportReceiver] is exported with
 * no permission, so an import there would let any app on the phone wipe this
 * one.
 */
class AutomationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`,
     * the same vocabulary the broadcast contract uses, so a caller has one
     * grammar to parse rather than two.
     *
     * A refusal is **returned, never thrown**: an exception across a binder
     * reaches the caller as a `RuntimeException` carrying our stack trace,
     * which tells 白い熊 nothing and tells a misbehaving caller rather more
     * than it should.
     */
    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val ctx = context?.applicationContext ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer
        // whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then this app's own switches — a token is ignored unless we ask for one.
        AutomationAuth.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        Timber.i("automation provider: %s from %s", method, callingPackage)
        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }
            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive,
     * deliberately: 応用管理 must draw a row before an export exists, and at
     * restore must judge compatibility **before** streaming tens of megabytes
     * into an app that would reject them — which it cannot do if the header is
     * buried inside an encrypted archive.
     *
     * `requires_launch_first` is false: `AnkiDroidApp` sets the collection path
     * up as the process starts, and the import blocklist keeps `deckPath` out
     * of a backup, so a freshly installed 白い熊 暗記 can take one without
     * having been opened.
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.getPackageInfoCompat(ctx.packageName, PackageInfoFlagsCompat.EMPTY)
        val contains =
            ShiroikumaExport.Cat.entries
                .filter { it.defaultOn }
                .map { ctx.getString(it.labelRes) }
        val header =
            JSONObject()
                .put("app_id", ctx.packageName)
                .put("version_code", pkg?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L)
                .put("version_name", pkg?.versionName.orEmpty())
                .put("format", FORMAT)
                .put("min_format_readable", MIN_FORMAT_READABLE)
                .put("requires_launch_first", false)
                .put("contains", JSONArray(contains))
        return "OK:$header"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method: the one in
     * [extras] belongs to the binder transaction and is closed when [call]
     * returns, so a service reading it afterwards would find it shut. That is a
     * bug you only see under load, so it is not left to the service to
     * remember.
     */
    private fun start(
        ctx: Context,
        extras: Bundle?,
        importing: Boolean,
    ): Bundle {
        // The deprecated one-arg form deliberately: the typed overload is API
        // 33 and 白い熊's Mate XT answers `SDK_INT = 31` on an Android-13-based
        // EMUI, so any version-derived dispatch here is a guess that can be
        // wrong in both directions. The one-arg form works on every platform
        // this app runs on.
        @Suppress("DEPRECATION")
        val fd =
            extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
                ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        return runCatching {
            AutomationDataService.start(ctx, jobId, dup, importing, extras)
            ok("OK:$jobId")
        }.getOrElse { e ->
            // the service never started, so nothing else will ever close this
            AutomationJobs.finish(jobId)
            runCatching { dup.close() }
            Timber.w(e, "automation provider could not start the data service")
            fail("ERROR:${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }

    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever call()ed still has to answer these.
    // Refusing loudly beats an empty cursor, which reads downstream as "there
    // is no data" rather than "wrong door".
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor = throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri = throw UnsupportedOperationException("automation is call() only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = throw UnsupportedOperationException("automation is call() only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /** This app's archive format; bumped when an older build could no longer read what we write. */
        const val FORMAT = ShiroikumaExport.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally
         * fine, because an app migrates its own storage; newer data into an
         * older app is not. This field is what lets a caller refuse the second
         * case at discovery time, before anything is streamed.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
