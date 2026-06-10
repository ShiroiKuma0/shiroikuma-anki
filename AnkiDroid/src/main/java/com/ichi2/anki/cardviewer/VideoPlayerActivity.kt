// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.VideoView
import com.ichi2.anki.R
import timber.log.Timber

/**
 * Fullscreen native playback of a card's video files, launched by [VideoPlayer].
 *
 * Restores the pre-2.17 native player: the WebView cannot load `file://` media
 * from the `http://127.0.0.1` base URL it renders cards under, so an HTML
 * `<video>` element shows a broken player (upstream issue #20668).
 *
 * Destroying the activity (playback completion, error, or the user navigating
 * away) notifies [onPlaybackCompleted] so the media queue plays the next tag.
 */
class VideoPlayerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null) {
            // not possible inside AnkiDroid; may happen if launched externally
            Timber.w("video path was null")
            finish()
            return
        }
        Timber.i("Video player launched")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.video_player)
        findViewById<VideoView>(R.id.video_surface).apply {
            setOnCompletionListener { finish() }
            setOnErrorListener { _, what, extra ->
                Timber.w("video error: what %d, extra %d", what, extra)
                finish()
                true // do not also call the completion listener
            }
            setVideoPath(path)
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onPlaybackCompleted?.invoke()
        onPlaybackCompleted = null
    }

    companion object {
        /** Absolute path of the video file to play */
        const val EXTRA_PATH = "path"

        /**
         * Invoked once when the activity is destroyed, however playback ended.
         * Set by [VideoPlayer] before launching to resume the media queue.
         */
        var onPlaybackCompleted: (() -> Unit)? = null
    }
}
