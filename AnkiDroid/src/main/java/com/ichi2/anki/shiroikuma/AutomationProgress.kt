// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Fork: the automation contract's progress broadcasts — **one** sender, shared
 * by both doors.
 *
 * [StateExportReceiver]'s `EXPORT_STATE` and [AutomationDataService]'s provider
 * jobs report through this same object deliberately: 自由作業盤 treats every
 * broadcast as a heartbeat and fails an app that goes quiet, so two senders
 * would mean two watchdogs — and the one that drifts is the one nobody watches.
 *
 * What the contract asks for, and what this does:
 * - **Real numbers, never a percentage.** The text and the structured
 *   `current`/`total`/`unit` triple both come from [ShiroikumaExport.Progress];
 *   `bytes` is the second counter, sent whenever it is known.
 * - **Throttled to one every 500 ms**, with the final line always forced.
 * - **A heartbeat at least every 25 s** ([beat]) even when the numbers have not
 *   moved. A single long step — the backend zipping a large collection — would
 *   otherwise look like a dead process, and an app silent for two minutes has
 *   its slot failed.
 *
 * The correlation id goes out as **both** `reply_id` and `job_id`: the §1
 * receiver knows a request by its `reply_id`, the §2a data door by its
 * `job_id`, and one shape that satisfies both beats a branch that has to be
 * kept in step.
 */
class AutomationProgress(
    private val context: Context,
    private val correlationId: String,
    private val action: String,
    private val replyPackage: String,
) {
    private val lastSentAt = AtomicLong(0)
    private val last = AtomicReference<Pair<ShiroikumaExport.Progress, Long?>?>(null)

    private val appLabel: String =
        runCatching {
            context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        }.getOrDefault(context.packageName)

    /** True when the caller asked for progress at all; a silent caller costs us nothing. */
    private val wanted: Boolean get() = action.isNotEmpty() && replyPackage.isNotEmpty()

    fun send(
        progress: ShiroikumaExport.Progress,
        bytes: Long? = null,
        force: Boolean = false,
    ) {
        last.set(progress to bytes)
        if (!wanted) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSentAt.get() < PROGRESS_INTERVAL_MS) return
        lastSentAt.set(now)
        context.sendBroadcast(
            Intent(action).apply {
                setPackage(replyPackage)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(StateExportReceiver.EXTRA_REPLY_ID, correlationId)
                putExtra(AutomationProvider.KEY_JOB_ID, correlationId)
                putExtra(StateExportReceiver.EXTRA_PROGRESS_APP, appLabel)
                putExtra(StateExportReceiver.EXTRA_PROGRESS_TEXT, progress.text)
                putExtra(StateExportReceiver.EXTRA_PROGRESS_CURRENT, progress.current)
                putExtra(StateExportReceiver.EXTRA_PROGRESS_TOTAL, progress.total)
                putExtra(StateExportReceiver.EXTRA_PROGRESS_UNIT, progress.unit)
                bytes?.let { putExtra(EXTRA_PROGRESS_BYTES, it) }
            },
        )
    }

    /**
     * Re-sends the last line whenever nothing else has gone out for
     * [HEARTBEAT_INTERVAL_MS]. Meant to be launched beside the work and
     * cancelled with it; [delay] is the cancellation point.
     */
    suspend fun beat(): Nothing {
        while (true) {
            delay(HEARTBEAT_INTERVAL_MS)
            if (!wanted) continue
            if (SystemClock.elapsedRealtime() - lastSentAt.get() < HEARTBEAT_INTERVAL_MS) continue
            val (progress, bytes) = last.get() ?: continue
            send(progress, bytes, force = true)
        }
    }

    companion object {
        /** The contract's second counter: bytes written so far. */
        const val EXTRA_PROGRESS_BYTES = "bytes"

        private const val PROGRESS_INTERVAL_MS = 500L

        /**
         * Comfortably inside the caller's 30 s "still alive" window and its
         * two-minute give-up, without adding traffic to an export that is
         * already reporting.
         */
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
    }
}
