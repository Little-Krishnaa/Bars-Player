package com.littlekrishnaa.barsplayer.lyrics

object PromptBuilder {
    private const val HARDCODED_INSTRUCTIONS = """
You are an expert music lyric translator. 
Translate the provided song lyrics based on meaning and contextual nuance.
CRITICAL CONSTRAINTS:
1. Return ONLY valid JSON with no markdown wrapping, matching the schema:
{"lines":[{"time_ms":12500,"original":"...","translated":"...","note":null}]}
2. The number of translated lines must strictly match 1:1 with the original input lines to keep timestamp sync intact.
3. Keep the exact "time_ms" provided for each line.
4. "note" is optional: use it ONLY if slang, references, or wordplay require a brief explanation. Otherwise set "note": null.
"""

    fun buildSystemPrompt(personaPrompt: String): String {
        return "$HARDCODED_INSTRUCTIONS\n\nSTYLE & PERSONA INSTRUCTIONS:\n$personaPrompt"
    }

    fun buildUserContent(request: TranslationRequest): String {
        val metadata = buildString {
            append("Artist: ${request.artist}\n")
            append("Title: ${request.title}\n")
            request.year?.let { append("Year: $it\n") }
            request.region?.let { append("Region: $it\n") }
        }

        val linesJson = buildString {
            append("[\n")
            request.lines.forEachIndexed { index, line ->
                append("  {\"time_ms\": ${line.timeMs}, \"original\": \"${escapeJson(line.text)}\"}")
                if (index < request.lines.size - 1) append(",")
                append("\n")
            }
            append("]")
        }

        return "METADATA:\n$metadata\nLYRICS LINES TO TRANSLATE:\n$linesJson"
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
