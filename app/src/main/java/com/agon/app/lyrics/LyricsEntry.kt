package com.agon.app.lyrics

data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val hasTrailingSpace: Boolean = true
)

data class LyricsEntry(
    val time: Long,          // ms
    val text: String,
    val words: List<WordTimestamp>? = null,
    val isBackground: Boolean = false
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()
    companion object {
        val HEAD = LyricsEntry(0L, "")
    }
}
