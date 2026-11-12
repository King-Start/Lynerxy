package com.agon.app.lyrics

val LRC_LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.*)".toRegex()
val LRC_TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()
private val LRC_TIMESTAMP_HINT = Regex("""\\[\d{1,2}:\d{2}""")

fun lyricsLooksSynced(lyrics: String?): Boolean {
    if (lyrics.isNullOrBlank()) return false
    val t = lyrics.trim().removePrefix("\uFEFF").trimStart()
    if (t.startsWith('[')) return true
    return LRC_TIMESTAMP_HINT.containsMatchIn(t.take(4096))
}

/** Parse LRC teks menjadi list LyricsEntry terurut */
fun parseLrc(lrc: String): List<LyricsEntry> {
    val entries = mutableListOf<LyricsEntry>()
    for (line in lrc.lines()) {
        val match = LRC_LINE_REGEX.matchEntire(line.trim()) ?: continue
        val timePart = match.groupValues[1]
        val text = match.groupValues[3].trim()
        LRC_TIME_REGEX.findAll(timePart).forEach { m ->
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val ms100 = m.groupValues[3].padEnd(3, '0').substring(0, 3).toLongOrNull() ?: 0L
            val timeMs = min * 60_000L + sec * 1_000L + ms100
            entries.add(LyricsEntry(timeMs, text))
        }
    }
    return entries.sorted()
}

/** Cari index lirik yang aktif berdasarkan posisi playback (ms) */
fun findCurrentLyricsIndex(entries: List<LyricsEntry>, positionMs: Long): Int {
    if (entries.isEmpty()) return -1
    var lo = 0; var hi = entries.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (entries[mid].time <= positionMs) lo = mid else hi = mid - 1
    }
    return if (entries[lo].time <= positionMs) lo else -1
}

/** Format ms ke string mm:ss */
fun formatLyricsTime(ms: Long): String {
    val m = ms / 60_000
    val s = (ms % 60_000) / 1_000
    return "%02d:%02d".format(m, s)
}

/** Strip kredit/metadata lines dari lirik biasa */
fun cleanLyricsCredits(lyrics: String): String {
    return lyrics.lines().filter { line ->
        val lower = line.trim().lowercase()
        !lower.startsWith("synced by") &&
        !lower.startsWith("lyrics by") &&
        !lower.startsWith("music by") &&
        !lower.startsWith("arranged by")
    }.joinToString("\n")
}
