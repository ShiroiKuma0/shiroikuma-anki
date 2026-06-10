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
