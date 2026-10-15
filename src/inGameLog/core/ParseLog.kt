package inGameLog.core

import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.spi.LoggingEvent
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ParseLog {
    // e.g. "2841 [Thread-4] ERROR org.magiclib.Foo  - message..."
    private val logHeader = Regex("""^(\d+)\s+\[([^\]]*)]\s+(\w+)\s+(\S+)\s+-\s?(.*)$""")

    private class RawEntry(
        val thread: String,
        val level: Level,
        val logger: String,
        val message: StringBuilder,
    )

    fun parseLogContent(logContent: String): List<LoggingEvent> {
        if (logContent.isEmpty()) return emptyList()

        val entries = mutableListOf<RawEntry>()

        for (line in logContent.lines()) {
            val match = logHeader.matchEntire(line)
            if (match != null) {
                val (_, thread, levelName, logger, message) = match.destructured
                entries += RawEntry(
                    thread = thread,
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

    /**
     * Returns the content from the LAST occurrence of [marker] to the end of the stream (marker included).
     *
     * If the marker never appears, returns the entire stream.
     */
    fun tailFromLastMarker(
        input: InputStream,
        marker: ByteArray
    ): ByteArray {
        require(marker.isNotEmpty())
        val kept = ByteArrayOutputStream() // Bytes since the last marker start (or everything so far)
        val buf = ByteArray(8192)

        val window = ByteArray(marker.size) // After each new byte, compare this window to the marker.
        var filled = 0

        input.use { stream ->
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break

                for (i in 0 until n) {
                    val b = buf[i]
                    kept.write(b.toInt())

                    if (filled < window.size) {
                        window[filled++] = b
                    } else {
                        System.arraycopy(window, 1, window, 0, window.size - 1)
                        window[window.size - 1] = b
                    }

                    if (filled == window.size && window.contentEquals(marker)) {
                        // Found a newer occurrence of the marker, so discard everything before it and start over.
                        kept.reset()
                        kept.write(marker)
                    }
                }
            }
        }
        return kept.toByteArray()
    }

    fun tailFromLastMarkerText(input: InputStream, marker: String): String =
        String(tailFromLastMarker(input, marker.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
}