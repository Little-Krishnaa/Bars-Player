package com.littlekrishnaa.barsplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parse standard lrc format returns correct times and text`() {
        val lrc = """
            [00:12.50]Hello world
            [01:05.30]Second line
            [02:00.00]Third line
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals(3, result.size)
        assertEquals(12500L, result[0].timeMs)
        assertEquals("Hello world", result[0].text)
        assertEquals(65300L, result[1].timeMs)
        assertEquals("Second line", result[1].text)
        assertEquals(120000L, result[2].timeMs)
    }

    @Test
    fun `parse returns sorted by timeMs`() {
        val lrc = """
            [02:00.00]Third
            [00:10.00]First
            [01:00.00]Second
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals("First", result[0].text)
        assertEquals("Second", result[1].text)
        assertEquals("Third", result[2].text)
    }

    @Test
    fun `parse ignores non-lrc lines`() {
        val lrc = """
            [ti:Song Title]
            [ar:Artist]
            [00:01.00]Actual lyric line
            Just a plain text line
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        // Hanya [00:01.00] yang valid, metadata tag dan plain text dibuang
        assertEquals(1, result.size)
        assertEquals("Actual lyric line", result[0].text)
    }

    @Test
    fun `parse empty string returns empty list`() {
        val result = LrcParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse 2-digit milliseconds multiplied correctly`() {
        val lrc = "[00:05.50]Test"
        val result = LrcParser.parse(lrc)
        // 5 detik + 500ms
        assertEquals(5500L, result[0].timeMs)
    }

    @Test
    fun `parse 3-digit milliseconds used directly`() {
        val lrc = "[00:05.500]Test"
        val result = LrcParser.parse(lrc)
        assertEquals(5500L, result[0].timeMs)
    }
}
