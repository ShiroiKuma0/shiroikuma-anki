// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.content.Intent
import com.ichi2.anki.common.android.appContext
import kotlinx.coroutines.CancellableContinuation
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Plays a card's video files natively in a fullscreen [VideoPlayerActivity].
 *
 * Fork change (upstream issue #20668): videos used to be played by locating a
 * `<video>` element in the card WebView via JavaScript, but the WebView blocks
 * the element's `file://` source from the `http://127.0.0.1` base URL, so
 * playback never started. This restores the native player used up to 2.16.5.
 *
 * [onVideoFinished] resumes the media queue once the activity is destroyed.
 */
class VideoPlayer {
    private var continuation: CancellableContinuation<Unit>? = null

    fun playVideo(
        continuation: CancellableContinuation<Unit>,
        videoFile: File,
    ) {
        this.continuation = continuation
        Timber.i("launching VideoPlayerActivity")
        VideoPlayerActivity.onPlaybackCompleted = ::onVideoFinished
        val intent =
            Intent(appContext, VideoPlayerActivity::class.java)
                .putExtra(VideoPlayerActivity.EXTRA_PATH, videoFile.absolutePath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun onVideoFinished() {
        Timber.v("video ended")
        continuation?.takeUnless { it.isCompleted }?.resume(Unit)
        continuation = null
    }

    fun onVideoPaused() {
        Timber.i("video paused")
        continuation?.takeUnless { it.isCompleted }?.resumeWithException(MediaException(MediaErrorBehavior.STOP_MEDIA))
        continuation = null
    }
}
