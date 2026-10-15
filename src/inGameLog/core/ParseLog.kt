package inGameLog.core

import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.spi.LoggingEvent
import java.io.InputStream

internal object ParseLog {
    // e.g. "2841 [Thread-4] ERROR org.magiclib.Foo  - message..."
    private val logHeader = Regex("""^(\d+)\s+\[([^\]]*)]\s+(\w+)\s+(\S+)\s+-\s?(.*)$""")

    private class RawEntry(
        //val thread: String,
        val level: Level,
        val logger: String,
        val message: StringBuilder,
    )

    fun parseLogContent(logContent: String, onlyEqualOrGreaterTo: Level = Level.ALL): List<LoggingEvent> {
        if (logContent.isEmpty()) return emptyList()

        val entries = mutableListOf<RawEntry>()

        for (line in logContent.lines()) {
            val match = logHeader.matchEntire(line)
            if (match != null) {
                val (_, thread, levelName, logger, message) = match.destructured
                entries += RawEntry(
                    //thread = thread,
                    level = Level.toLevel(levelName, Level.INFO),
                    logger = logger,
                    message = StringBuilder(message),
                )
            } else if (entries.isNotEmpty()) {
                // Continuation line of the previous entry
                entries.last().message.append('\n').append(line)
            }
            // Lines before the first header (if any) are ignored
        }

        return entries.mapNotNull {
            if(!it.level.isGreaterOrEqual(onlyEqualOrGreaterTo))
                return@mapNotNull null

            LoggingEvent(
                "",
                Logger.getLogger(it.logger),
                it.level,
                it.message.toString().trimEnd(),
                null
            )
        }
    }

    /**
     * Returns the content from the LAST occurrence of [marker] to the end of the stream (marker included).
     *
     * If the marker never appears, returns the entire stream.
     */
    fun tailFromLastMarker(input: InputStream, marker: String): String {
        val innerMarker = marker.toByteArray(Charsets.UTF_8)
        val m = innerMarker.size

        var data = ByteArray(8192)   // Bytes since the last marker (or everything so far)
        var size = 0
        val buf = ByteArray(8192)

        input.use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break

                for (i in 0 until n) {
                    if (size == data.size) data = data.copyOf(data.size * 2)
                    data[size++] = buf[i]

                    if (size >= m) {
                        var match = true
                        for (j in 0 until m) {
                            if (data[size - m + j] != innerMarker[j]) { match = false; break }
                        }
                        if (match) {
                            // Discard everything before the marker
                            for (j in 0 until m) data[j] = innerMarker[j]
                            size = m
                        }
                    }
                }
            }
        }

        return String(data, 0, size, Charsets.UTF_8)
    }
}