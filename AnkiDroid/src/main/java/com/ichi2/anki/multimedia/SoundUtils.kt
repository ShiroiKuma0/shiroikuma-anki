/*
 *  Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 *  Copyright (c) 2014 Timothy rae <perceptualchaos2@gmail.com>
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.multimedia

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.AvRef
import com.ichi2.anki.libanki.AvTag
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Sound
import com.ichi2.anki.libanki.Sound.AV_PLAYLINK_RE
import com.ichi2.anki.libanki.Sound.replaceAvRefsWith
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.SoundOrVideoTag.Type
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.utils.CollectionPreferences
import org.intellij.lang.annotations.Language

/**
 * Takes content with [AvRef]s and expands them to reference the media file
 *
 * Audio and video are both replaced with <a href="playsound:">: tapping the
 * play button (or autoplay) routes through the media queue, which plays audio
 * in-process and opens video in the native fullscreen player. A `<video>`
 * element cannot be used: the WebView blocks its `file://` source from the
 * `http://127.0.0.1` base URL (issue #20668).
 *
 * @param content card content to be rendered that may contain embedded audio
 *
 * @return content with [AvRef]s replaced with HTML to play the file
 */
fun expandSounds(
    content: String,
    renderOutput: TemplateRenderOutput,
    showAudioPlayButtons: Boolean,
) = replaceAvRefsWith(content, renderOutput) { _, playTag ->

    fun AvRef.asHtmlAudio(): String {
        if (!showAudioPlayButtons) return ""
        val playsound = "playsound:${this.side}:${this.index}"

        @Language("HTML")
        val result = """<a class="replay-button soundLink" href=$playsound><span>
                        <svg class="playImage" viewBox="0 0 64 64" version="1.1">
                            <circle cx="32" cy="32" r="29" fill="lightgrey"/>
                            <path d="M56.502,32.301l-37.502,20.101l0.329,-40.804l37.173,20.703Z" fill="black"/>Replay
                        </svg>
                    </span></a>"""
        return result
    }

    playTag.asHtmlAudio()
}

/** Extract av tag from playsound:q:x link */
suspend fun getAvTag(
    card: Card,
    url: String,
): AvTag? =
    AV_PLAYLINK_RE.matchEntire(url)?.let {
        val values = it.groupValues
        val questionSide = values[1] == "q"
        val index = values[2].toInt()
        val tags =
            CollectionManager.withCol {
                if (questionSide) {
                    card.questionAvTags(this)
                } else {
                    card.answerAvTags(this)
                }
            }
        if (index < tags.size) {
            tags[index]
        } else {
            null
        }
    }

/**
 * Return card text with play buttons added, or stripped.
 *
 * @param text A string, maybe containing `[anki:play]` tags to replace
 * @param renderOutput Context: whether a file is audio or video
 *
 * @see AvRef
 */
suspend fun replaceAvRefsWithPlayButtons(
    text: String,
    renderOutput: TemplateRenderOutput,
): String {
    val hidePlayButtons = CollectionPreferences.getHidePlayAudioButtons()
    return expandSounds(text, renderOutput, showAudioPlayButtons = !hidePlayButtons)
}

/**
 * Classifies the tag by filename extension alone. Audio-or-video containers
 * (mp4 etc.) are always treated as video: probing the content with
 * `hasVideoThumbnail` returned false negatives on short clips, which were then
 * played as audio-only (issue #20668). The native video player handles an
 * audio-only container correctly, so video is the safe default.
 */
fun SoundOrVideoTag.getTagType(): Type {
    val extension = filename.substringAfterLast(".", "")
    return when (extension) {
        in Sound.VIDEO_ONLY_EXTENSIONS, in Sound.AUDIO_OR_VIDEO_EXTENSIONS -> Type.VIDEO
        // assume audio if we don't know
        else -> Type.AUDIO
    }
}
