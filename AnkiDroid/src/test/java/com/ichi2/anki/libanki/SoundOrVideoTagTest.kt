// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.SoundOrVideoTag.Type
import com.ichi2.anki.libanki.SoundOrVideoTag.Type.VIDEO
import com.ichi2.anki.multimedia.getTagType
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SoundOrVideoTagTest {
    @Test
    fun mp3IsAudio() {
        val tag = SoundOrVideoTag("test.mp3")
        assertThat(tag.getTagType(), equalTo(Type.AUDIO))
    }

    @Test
    fun audioIsDefault() {
        // if we don't know, assume it's audio
        // the audio player (Android) can handle a failure better than the video player
        val tag = SoundOrVideoTag("test.txt")
        assertThat(tag.getTagType(), equalTo(Type.AUDIO))
    }

    @Test
    fun mp4IsVideo() {
        // 20668: classification is by extension only; the file content is never
        // probed, so a short clip without a video thumbnail is still a video
        val tag = SoundOrVideoTag("test.mp4")
        assertThat(tag.getTagType(), equalTo(VIDEO))
    }

    @Test
    fun videoExtensionsAreVideo() {
        for (filename in listOf("a.mov", "a.mkv", "a.webm", "a.mpg", "a.mpeg")) {
            assertThat(filename, SoundOrVideoTag(filename).getTagType(), equalTo(VIDEO))
        }
    }
}
