package logViewer

import org.apache.log4j.Logger
import java.io.OutputStream

class ErrToLog4jStream : OutputStream() {
    private val buffer = StringBuilder()
    private val logger = Logger.getLogger("STDERR")

    override fun write(b: Int) {
        val c = b.toChar()

        if (c == '\n') {
            flushBuffer()
        } else {
            buffer.append(c)
        }
    }

    override fun flush() {
        flushBuffer()
    }

    private fun flushBuffer() {
        if (buffer.isEmpty()) return

        val message = buffer.toString()
        buffer.setLength(0)

        // Convert stderr → Log4j event
        logger.error(message)
    }
}