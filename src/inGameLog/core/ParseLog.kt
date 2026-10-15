package inGameLog.core

import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.spi.LoggingEvent
import java.io.InputStream
import java.util.ArrayDeque

internal object ParseLog {
    // e.g. "2841 [Thread-4] ERROR org.magiclib.Foo  - message..."
    private val logHeader = Regex("""^(\d+)\s+\[([^\]]*)]\s+(\w+)\s+(\S+)\s+-\s?(.*)$""")

    /** Upper bound on replayed events; the oldest are dropped first. */
    const val DEFAULT_MAX_EVENTS = 25000

    /** How far in ms can the elapsed time fall below the highest value seen so far before we treat it as a new session. */
    const val DEFAULT_SESSION_RESET_DROP_MS = 2000L

    private class RawEntry(
        val level: Level,
        val logger: String,
        val message: StringBuilder,
    )

    fun parse(
        input: InputStream,
        minLevel: Level = Level.ALL,
        maxEvents: Int = DEFAULT_MAX_EVENTS,
        sessionResetDropMs: Long = DEFAULT_SESSION_RESET_DROP_MS,
        charset: java.nio.charset.Charset = Charsets.UTF_8,
    ): List<LoggingEvent> {
        val entries = ArrayDeque<RawEntry>()
        var maxElapsed = -1L
        var current: RawEntry? = null   // null while inside a filtered-out entry

        input.bufferedReader(charset).use { reader ->
            reader.forEachLine { line ->
                val match = logHeader.matchEntire(line)
                if (match != null) {
                    val elapsed = match.groupValues[1].toLongOrNull() ?: 0L
                    if (elapsed < maxElapsed - sessionResetDropMs) {
                        entries.clear()          // new session started
                        maxElapsed = elapsed
                    } else if (elapsed > maxElapsed) {
                        maxElapsed = elapsed
                    }

                    val level = Level.toLevel(match.groupValues[3], Level.INFO)
                    current = if (level.isGreaterOrEqual(minLevel)) {
                        RawEntry(level, match.groupValues[4], StringBuilder(match.groupValues[5])).also {
                            entries.addLast(it)
                            if (entries.size > maxEvents) entries.pollFirst()
                        }
                    } else null
                } else {
                    // Continuation line (stack trace, multi-line message) of the previous entry.
                    // Lines before the first header, or under a filtered-out entry, are ignored.
                    current?.message?.append('\n')?.append(line)
                }
            }
        }

        return entries.map {
            LoggingEvent(
                "",
                Logger.getLogger(it.logger),
                it.level,
                it.message.toString().trimEnd(),
                null
            )
        }
    }
}