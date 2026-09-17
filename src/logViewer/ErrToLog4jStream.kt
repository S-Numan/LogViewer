package logViewer

import org.apache.log4j.Logger
import java.io.OutputStream
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ErrToLog4jStream(
    private val idleMillis: Long = 20L, // just long enough to catch the next line of a fast burst
    charset: Charset = Charsets.UTF_8
) : OutputStream() {

    private val lineBuffer = StringBuilder()
    private val pending = StringBuilder()
    private val logger = Logger.getLogger("STDERR")

    private val decoder: CharsetDecoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private val decodeOutBuf = CharBuffer.allocate(1024)

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "err-to-log4j-flush").apply { isDaemon = true }
    }
    private var scheduledFlush: ScheduledFuture<*>? = null
    @Volatile private var closed = false

    init {
        // Catch buffered output that never got a chance to flush because the idle timer hadn't fired before JVM exit.
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                forceFlush()
            } catch (_: Exception) {
                // don't let shutdown-hook failures propagate
            }
        })
    }

    @Synchronized
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (closed) return
        val input = java.nio.ByteBuffer.wrap(b, off, len)
        decodeOutBuf.clear()
        while (input.hasRemaining()) {
            val result = decoder.decode(input, decodeOutBuf, false)
            decodeOutBuf.flip()
            appendDecoded(decodeOutBuf)
            decodeOutBuf.clear()
            if (result.isError) {
                // REPLACE actions mean this shouldn't normally happen, but guard against an infinite loop just in case.
                result.throwException()
            }
        }
    }

    private fun appendDecoded(chars: CharBuffer) {
        while (chars.hasRemaining()) {
            val c = chars.get()
            when (c) {
                '\n' -> {
                    handleLine(stripTrailingCr(lineBuffer))
                    lineBuffer.setLength(0)
                }
                else -> lineBuffer.append(c)
            }
        }
    }

    private fun stripTrailingCr(sb: StringBuilder): String {
        val n = sb.length
        return if (n > 0 && sb[n - 1] == '\r') sb.substring(0, n - 1) else sb.toString()
    }

    // Called by PrintStream(autoFlush=true) after every line.
    // Used purely as an idle signal: reschedule the real flush a bit further out.
    @Synchronized
    override fun flush() {
        if (closed) return
        scheduledFlush?.cancel(false)
        try {
            scheduledFlush = scheduler.schedule({ forceFlush() }, idleMillis, TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
            // scheduler was shut down concurrently; fall back to an immediate flush
            forceFlush()
        }
    }

    @Synchronized
    fun forceFlush() {
        if (lineBuffer.isNotEmpty()) {
            handleLine(stripTrailingCr(lineBuffer))
            lineBuffer.setLength(0)
        }
        flushPending()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        scheduledFlush?.cancel(false)
        forceFlush()
        scheduler.shutdown()
        super.close()
    }

    private fun handleLine(line: String) {
        if (pending.isEmpty()) {
            pending.append(line)
        } else if (isContinuation(line)) {
            pending.append('\n').append(line)
        } else {
            flushPending()
            pending.append(line)
        }
    }

    private fun isContinuation(line: String): Boolean {
        return line.startsWith(" ") ||
                line.startsWith("\t") ||
                line.startsWith("at ") ||
                line.startsWith("Caused by:") ||
                line.startsWith("Suppressed:") ||
                line.startsWith("...")
    }

    private fun flushPending() {
        if (pending.isEmpty()) return
        val message = pending.toString()
        pending.setLength(0)
        logger.error(message)
    }
}