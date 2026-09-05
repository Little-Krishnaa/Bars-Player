package com.littlekrishnaa.barsplayer.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `buildSystemPrompt contains hardcoded JSON schema instruction`() {
        val result = PromptBuilder.buildSystemPrompt("Some persona")
        assertTrue("Harus ada instruksi JSON output", result.contains("\"lines\""))
        assertTrue("Harus ada instruksi time_ms", result.contains("time_ms"))
        assertTrue("Harus ada instruksi 1:1 line constraint", result.contains("1:1"))
    }

    @Test
    fun `buildSystemPrompt contains persona prompt`() {
        val persona = "My custom hip-hop persona xyz"
        val result = PromptBuilder.buildSystemPrompt(persona)
        assertTrue(result.contains(persona))
    }

    @Test
    fun `buildSystemPrompt hardcoded part always present regardless of persona`() {
        val result1 = PromptBuilder.buildSystemPrompt("persona A")
        val result2 = PromptBuilder.buildSystemPrompt("persona B")
        assertTrue(result1.contains("JSON"))
        assertTrue(result2.contains("JSON"))
    }

    @Test
    fun `buildUserContent includes artist and title metadata`() {
        val lines = listOf(
            SyncedLine(0L, "First line"),
            SyncedLine(3000L, "Second line")
        )
        val request = TranslationRequest(
            artist = "Jay-Z",
            title = "Empire State of Mind",
            lines = lines,
            personaPrompt = "hip-hop"
        )
        val result = PromptBuilder.buildUserContent(request)
        assertTrue(result.contains("Jay-Z"))
        assertTrue(result.contains("Empire State of Mind"))
    }

    @Test
    fun `buildUserContent contains all time_ms from lines`() {
        val lines = listOf(
            SyncedLine(1000L, "Line one"),
            SyncedLine(5000L, "Line two")
        )
        val request = TranslationRequest(
            artist = "Artist",
            title = "Title",
            lines = lines,
            personaPrompt = ""
        )
        val result = PromptBuilder.buildUserContent(request)
        assertTrue(result.contains("1000"))
        assertTrue(result.contains("5000"))
        assertTrue(result.contains("Line one"))
        assertTrue(result.contains("Line two"))
    }

    @Test
    fun `buildUserContent escapes double quotes in lyrics`() {
        val lines = listOf(SyncedLine(0L, "He said \"yo\""))
        val request = TranslationRequest(
            artist = "A",
            title = "B",
            lines = lines,
            personaPrompt = ""
        )
        val result = PromptBuilder.buildUserContent(request)
        assertFalse("Raw unescaped quote should not appear", result.contains("He said \"yo\""))
        assertTrue("Escaped quote should appear", result.contains("He said \\\"yo\\\""))
    }

    @Test
    fun `buildUserContent includes optional year and region when provided`() {
        val request = TranslationRequest(
            artist = "A",
            title = "B",
            year = "1998",
            region = "US",
            lines = emptyList(),
            personaPrompt = ""
        )
        val result = PromptBuilder.buildUserContent(request)
        assertTrue(result.contains("1998"))
        assertTrue(result.contains("US"))
    }

    @Test
    fun `buildUserContent omits year and region when null`() {
        val request = TranslationRequest(
            artist = "A",
            title = "B",
            year = null,
            region = null,
            lines = emptyList(),
            personaPrompt = ""
        )
        val result = PromptBuilder.buildUserContent(request)
        assertFalse(result.contains("Year:"))
        assertFalse(result.contains("Region:"))
    }
}
