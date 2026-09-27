// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ichi2.anki.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import timber.log.Timber
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fork: where a data-door export or import actually runs — the service half of
 * [AutomationProvider].
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this runs for minutes. A binder call would
 * hold 応用管理 while it is drawing a list, report no progress and refuse
 * cancellation — and a backgrounded app writing for minutes is frozen
 * mid-stream on 白い熊's phone, which yields a truncated archive underneath a
 * success reply: the worst possible failure, because it is indistinguishable
 * from a good backup until the day it is restored.
 *
 * A partial wakelock is held around the work for the same reason: EMUI dozes
 * the CPU with the screen off. (The collection no longer travels, so the work
 * is small now; the service and the wakelock stay, since they cost nothing
 * and the contract promises them.)
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the
 * original belongs to the binder transaction and is closed the moment `call()`
 * returns. This service owns the copy and closes it in a `finally` — leaking
 * one would hold the caller's file open indefinitely, and a caller cannot
 * checksum or encrypt a file that is still open.
 */
class AutomationDataService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // GO FOREGROUND FIRST — before reading anything, before any early
        // return. Once startForegroundService() has been called the platform
        // requires a startForeground() whatever we then decide, so a caller
        // retrying with a stale job id would otherwise crash the very app it is
        // backing up. `importing` is read defensively off a nullable intent for
        // the same reason: the notification's wording is not worth a branch
        // that could return first.
        goForeground(intent?.getBooleanExtra(EXTRA_IMPORTING, false) == true)

        val jobId = intent?.getStringExtra(EXTRA_JOB) ?: return stop(startId)
        // a stale job id — a retry of something already finished — stops
        // silently: it is the normal race, not an error
        val fd = HANDOVER.remove(jobId) ?: return stop(startId)
        val importing = intent.getBooleanExtra(EXTRA_IMPORTING, false)
        val items = intent.getStringExtra(AutomationProvider.KEY_ITEMS)?.trim().orEmpty()
        val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)?.trim().orEmpty()

        val replied = AtomicBoolean(false)

        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a
            // synchronous failure and an asynchronous success must never both
            // fire. The same guard the broadcast contract has always carried.
            if (!replied.compareAndSet(false, true)) return
            AutomationJobs.finish(jobId)
            Timber.i("automation data %s → %s", jobId, result.take(200))
            if (replyAction.isEmpty() || replyPackage.isEmpty()) return
            sendBroadcast(replyIntent(this, replyAction, replyPackage, jobId, result))
        }

        val wakeLock =
            getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                ?.apply { runCatching { acquire(WAKELOCK_TIMEOUT_MS) } }

        scope.launch {
            // the correlation id of a data-door job is its job_id, and the
            // sender is the one the §1 receiver uses — heartbeat included
            val progress = AutomationProgress(this@AutomationDataService, jobId, progressAction, replyPackage)
            val heartbeat = launch { progress.beat() }
            try {
                fd.use { open ->
                    if (importing) runImport(open, items, ::reply) else runExport(jobId, open, items, progress, ::reply)
                }
            } catch (e: Exception) {
                if (AutomationJobs.isCancelled(jobId) ||
                    e is ShiroikumaExport.ExportCancelledException ||
                    e is BackendInterruptedException
                ) {
                    reply("ERROR:cancelled")
                } else {
                    Timber.w(e, "automation data job failed")
                    reply("ERROR:${e.message ?: e.javaClass.simpleName}")
                }
            } finally {
                heartbeat.cancel()
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                ServiceCompat.stopForeground(this@AutomationDataService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Writes the selected categories straight into the caller's descriptor.
     *
     * The bytes are counted as they go rather than stat'ed afterwards: the
     * caller owns the file and we may not be able to see it at all — it can be
     * an anonymous pipe, or a descriptor into a directory this app cannot list.
     */
    private suspend fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String,
        progress: AutomationProgress,
        reply: (String) -> Unit,
    ) {
        val cats =
            try {
                ShiroikumaExport.parseItems(items)
            } catch (e: IllegalArgumentException) {
                reply("ERROR:${e.message}")
                return
            }
        var written = 0L
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            val counting =
                object : OutputStream() {
                    override fun write(b: Int) {
                        out.write(b)
                        written++
                    }

                    override fun write(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ) {
                        out.write(b, off, len)
                        written += len
                    }
                }
            ShiroikumaExport.export(
                context = this,
                cats = cats,
                onProgress = { progress.send(it, written) },
                isCancelled = { AutomationJobs.isCancelled(jobId) },
                openOutput = { counting },
            )
        }
        if (AutomationJobs.isCancelled(jobId)) {
            reply("ERROR:cancelled")
            return
        }
        progress.send(
            ShiroikumaExport.Progress(
                "${ShiroikumaExport.UNIT_CATEGORIES} ${cats.size}/${cats.size} — ${StateExportReceiver.humanSize(written)}",
                cats.size.toLong(),
                cats.size.toLong(),
                ShiroikumaExport.UNIT_CATEGORIES,
            ),
            bytes = written,
            force = true,
        )
        reply("OK:$written|${cats.size} categories")
    }

    /**
     * Reads the archive the caller opened and applies it.
     *
     * Settings only ([ShiroikumaExport.importSettings]): nothing is written
     * outside this app's private data, and nothing here needs all-files
     * access — a restore onto a clean phone has none yet, and is asked for it
     * when the app is first opened. The restored `deckPath` is what lets that
     * first open find the collection that never left shared storage.
     *
     * Absent categories are skipped, so an archive from an older build
     * restores what it actually carries (its `collection.colpkg`, if any, is
     * skipped unread). 応用管理 force-stops this app the instant the success
     * reply lands — deliberately, because a running process writes its cached
     * `SharedPreferences` back out at orderly shutdown and would silently undo
     * the import that just happened.
     */
    private suspend fun runImport(
        fd: ParcelFileDescriptor,
        items: String,
        reply: (String) -> Unit,
    ) {
        // an import restores what the archive carries: everything the doors
        // carry, unless the caller narrowed it deliberately
        val cats =
            if (items.isEmpty()) {
                ShiroikumaExport.AUTOMATION_CATS
            } else {
                try {
                    ShiroikumaExport.parseItems(items)
                } catch (e: IllegalArgumentException) {
                    reply("ERROR:${e.message}")
                    return
                }
            }
        val summary =
            ShiroikumaExport.importSettings(this, cats) {
                ParcelFileDescriptor.AutoCloseInputStream(fd)
            }
        val restored = summary.lines().count { it.isNotBlank() }
        if (restored == 0) {
            reply("ERROR:archive carries no categories")
            return
        }
        reply("OK:$restored restored")
    }

    private fun notification(importing: Boolean): Notification {
        NotificationManagerCompat
            .from(this)
            .createNotificationChannel(
                NotificationChannelCompat
                    .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(getString(R.string.sk_automation_channel))
                    .build(),
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(if (importing) R.string.sk_automation_importing else R.string.sk_automation_exporting))
            .setSmallIcon(R.drawable.ic_star_notify)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * Must undo [goForeground]: we are already in the foreground by the time
     * any caller reaches this, so leaving without dropping it strands an
     * ongoing notification over a service that has stopped.
     */
    private fun stop(startId: Int): Int {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    /**
     * Must happen inside 5 s of the service starting or the system kills us for
     * the same class of reason it kills an overrunning receiver.
     *
     * `dataSync` rather than `specialUse`: this IS a data sync, the permission
     * is already declared, and `specialUse` is an API 34 literal that older
     * platforms reject outright. Asking for a type the platform does not
     * recognise throws rather than degrading, and 白い熊's EMUI reports
     * `SDK_INT = 31` on an Android-13-based platform — so the version is not a
     * question worth asking here. Try typed, fall back to untyped, and carry on
     * either way: losing the notification is worth less than losing the export.
     */
    private fun goForeground(importing: Boolean) {
        val notification = notification(importing)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: Exception) {
            Timber.w(e, "typed startForeground refused; retrying untyped")
            runCatching { startForeground(NOTIFICATION_ID, notification) }
                .onFailure { Timber.w(it, "startForeground refused entirely") }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "shiroikuma_automation_data"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"
        private const val WAKELOCK_TAG = "AnkiDroid:sk-automation-data"
        private const val WAKELOCK_TIMEOUT_MS = 60L * 60L * 1000L

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle
         * for one: a `ParcelFileDescriptor` in an extra is duplicated by the
         * system on delivery and the copy's lifetime stops being ours to reason
         * about. A map keyed by the job id keeps exactly one open descriptor
         * with exactly one owner — this service, which closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /**
         * The one terminal reply: [AutomationProvider.KEY_RESULT] as the
         * contract has always carried it, and beside it — never inside it —
         * [AutomationProvider.KEY_LOCATION]: where the job wrote or read. An
         * import writes `shared_prefs/` and `files/shiroikuma_fonts/`, an
         * export reads them, so the honest single path is the private data
         * directory that holds both. The collection directory is never named:
         * no job here writes or reads it.
         */
        fun replyIntent(
            context: Context,
            replyAction: String,
            replyPackage: String,
            jobId: String,
            result: String,
        ): Intent =
            Intent(replyAction).apply {
                setPackage(replyPackage)
                // without this a backgrounded caller never hears the answer,
                // and on a clean phone it may not have been launched at all
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                putExtra(AutomationProvider.KEY_RESULT, result)
                putExtra(AutomationProvider.KEY_LOCATION, context.dataDir.absolutePath)
            }

        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ) {
            HANDOVER[jobId] = fd
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AutomationDataService::class.java).apply {
                        putExtra(EXTRA_JOB, jobId)
                        putExtra(EXTRA_IMPORTING, importing)
                        putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
                        putExtra(AutomationProvider.KEY_REPLY_ACTION, extras?.getString(AutomationProvider.KEY_REPLY_ACTION))
                        putExtra(AutomationProvider.KEY_REPLY_PACKAGE, extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE))
                        putExtra(AutomationProvider.KEY_PROGRESS_ACTION, extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION))
                    },
                )
            } catch (e: Exception) {
                // the service never started, so nothing would ever close this
                HANDOVER.remove(jobId)
                throw e
            }
        }
    }
}
