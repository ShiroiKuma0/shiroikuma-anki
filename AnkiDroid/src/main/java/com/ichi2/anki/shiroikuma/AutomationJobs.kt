// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Fork: the jobs the data door ([AutomationProvider]) has started, and the flag
 * each of them watches to stop.
 *
 * What this owns is the mapping from the id a caller was handed to a
 * cancellation it can act on, which must outlive the binder call that created
 * it and be reachable from a service that never saw the caller.
 */
object AutomationJobs {
    private val cancelled = ConcurrentHashMap<String, Boolean>()

    fun begin(): String = UUID.randomUUID().toString().also { cancelled[it] = false }

    /**
     * Ask a job to stop. A no-op for an id that is finished or was never real:
     * a cancel arriving after the work completed is the normal race, not an
     * error, and answering it as one would make every well-behaved caller look
     * broken.
     */
    fun cancel(jobId: String?) {
        jobId?.let { cancelled.computeIfPresent(it) { _, _ -> true } }
    }

    /** Polled at write boundaries — never mid-write, so a cancelled archive is never half a file. */
    fun isCancelled(jobId: String): Boolean = cancelled[jobId] == true

    fun finish(jobId: String) {
        cancelled.remove(jobId)
    }
}
