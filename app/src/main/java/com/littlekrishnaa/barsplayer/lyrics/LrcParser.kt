package com.littlekrishnaa.barsplayer.lyrics

data class SyncedLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    private val LRC_REGEX = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

    fun parse(lrcContent: String): List<SyncedLine> {
        val lines = mutableListOf<SyncedLine>()
        lrcContent.lineSequence().forEach { rawLine ->
            val match = LRC_REGEX.find(rawLine.trim())
            if (match != null) {
                val (minStr, secStr, fracStr, text) = match.destructured
                val min = minStr.toLongOrNull() ?: 0L
                val sec = secStr.toLongOrNull() ?: 0L
                val ms = if (fracStr.length == 2) {
                    fracStr.toLongOrNull()?.times(10) ?: 0L
                } else {
                    fracStr.toLongOrNull() ?: 0L
                }
                val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                lines.add(SyncedLine(timeMs = totalMs, text = text.trim()))
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
