/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
