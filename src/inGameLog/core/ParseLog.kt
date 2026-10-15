package inGameLog.core

import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.spi.LoggingEvent
import java.io.InputStream
import java.io.Reader
import java.util.ArrayDeque

internal object ParseLog {
    // e.g. "2841 [Thread-4] ERROR org.magiclib.Foo  - message..."
    private val logHeader = Regex("""^(\d+)\s+\[([^\]]*)]\s+(\w+)\s+(\S+)\s+-\s?(.*)$""")

    /** Upper bound on replayed events; the oldest are dropped first. */
    const val DEFAULT_MAX_EVENTS = 3000 // Only 3000 to match the console commands mod.

    /** How far in ms can the elapsed time fall below the highest value seen so far before we treat it as a new session. */
    const val DEFAULT_SESSION_RESET_DROP_MS = 2000L

    private class RawEntry(
        val level: Level,
        val logger: String,
        val message: StringBuilder,
    )

    /**
     * Accumulates the current session's entries across one or more readers fed oldest-first.
     * State (session detection, a multi-line entry split across files) carries over between feeds.
     */
    private class Collector(
        private val minLevel: Level,
        private val maxEvents: Int,
        private val sessionResetDropMs: Long,
    ) {
        private val entries = ArrayDeque<RawEntry>()
        private var maxElapsed = -1L
        private var current: RawEntry? = null   // null while inside a filtered-out entry

        fun feed(reader: Reader) {
            reader.forEachLine { line ->
                val match = logHeader.matchEntire(line) // This line is slow
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

        fun toEvents(): List<LoggingEvent> = entries.map {
            LoggingEvent(
                "",
                Logger.getLogger(it.logger),
                it.level,
                it.message.toString().trimEnd(),
                null
            )
        }
    }

    /** Parses a single stream. Does not look at rotated files; see [parseSession]. */
    fun parse(
        input: InputStream,
        minLevel: Level = Level.ALL,
        maxEvents: Int = DEFAULT_MAX_EVENTS,
        sessionResetDropMs: Long = DEFAULT_SESSION_RESET_DROP_MS,
        charset: java.nio.charset.Charset = Charsets.UTF_8,
    ): List<LoggingEvent> {
        val collector = Collector(minLevel, maxEvents, sessionResetDropMs)
        input.bufferedReader(charset).use { collector.feed(it) }
        return collector.toEvents()
    }

    /**
     * Returns the events of the CURRENT game session, following log rotation.
     * @param openLog opens a file by name, e.g. `{ Global.getSettings().openStream(it) }`. Streams are closed here.
     */
    fun parseSession(
        openLog: (fileName: String) -> InputStream,
        minLevel: Level = Level.ALL,
        maxEvents: Int = DEFAULT_MAX_EVENTS,
        sessionResetDropMs: Long = DEFAULT_SESSION_RESET_DROP_MS,
        charset: java.nio.charset.Charset = Charsets.UTF_8,
        baseName: String = "starsector.log",
        maxBackups: Int = 1,
    ): List<LoggingEvent> {
        val names = mutableListOf(baseName)   // kept oldest-first
        var firstElapsed = firstElapsedOf(openLog(baseName), charset)

        var index = 1
        while (firstElapsed != null && firstElapsed > sessionResetDropMs && index <= maxBackups) {
            val name = "$baseName.$index"
            val backupFirst = try {
                firstElapsedOf(openLog(name), charset)
            } catch (e: Exception) {
                break   // backup doesn't exist or can't be read
            }
            names.add(0, name)
            firstElapsed = backupFirst
            index++
        }

        val collector = Collector(minLevel, maxEvents, sessionResetDropMs)
        for (name in names) {
            openLog(name).bufferedReader(charset).use { collector.feed(it) }
        }
        return collector.toEvents()
    }

    /** Elapsed time of the first log header in [input] (reads only up to it), or null if there is none. */
    private fun firstElapsedOf(input: InputStream, charset: java.nio.charset.Charset): Long? =
        input.bufferedReader(charset).use { reader ->
            var result: Long? = null
            while (result == null) {
                val line = reader.readLine() ?: break
                result = logHeader.matchEntire(line)?.groupValues?.get(1)?.toLongOrNull()
            }
            result
        }
}