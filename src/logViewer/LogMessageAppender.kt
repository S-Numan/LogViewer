package logViewer

import com.fs.starfarer.api.util.Misc
import logViewer.core.LVSettings
import logViewer.ModPlugin.Companion.MAX_CACHE_SIZE
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Level
import org.apache.log4j.spi.LoggingEvent
import org.lazywizard.console.Console
import org.lazywizard.lazylib.ui.LazyFont
import java.awt.Color

internal class LogMessageAppender : AppenderSkeleton() {
    companion object {

        fun displayLoggedMessage(event: LoggingEvent) {
            // Ignore logs from Console class to prevent infinite loops.
            if (LVSettings.isConsoleModEnabled && event.loggerName == Console::class.java.name) return

            // Ignore specific log from LazyFont class to prevent infinite loops. Example: org.lazywizard.lazylib.ui.LazyFont  - Character 'ￃ' is not defined in font data
            if(event.loggerName == LazyFont::class.java.name && event.renderedMessage.contains("is not defined in font data")) return

            val level = event.getLevel()

            if (event.throwableInformation?.throwable is NoDisplayThrowable)
                return

            if (LVSettings.addLogsToConsoleModConsoleLevel != Level.OFF && level.isGreaterOrEqual(LVSettings.addLogsToConsoleModConsoleLevel)) {
                if (LVSettings.isConsoleModEnabled) {
                    val msg = buildString {
                        append("[$level] ${event.loggerName} - ${event.renderedMessage}")

                        val stackTrace = event.throwableStrRep?.joinToString("\n")
                        if (!stackTrace.isNullOrEmpty())
                            append('\n').append(stackTrace)
                    }

                    Console.showMessage(msg, Level.ALL)
                }
            }

            if (LVSettings.addLogsToDisplayMessageLevel != Level.OFF && level.isGreaterOrEqual(LVSettings.addLogsToDisplayMessageLevel)) {
                val message = event.renderedMessage.ifEmpty { event.throwableStrRep?.first() } ?: "Log message was null"

                // TODO: remove this next GraphicsLib update
                if (message.contains("enableFullExplosionEffects")) // Hack to rid of never to be fixed before next starsector update graphics lib issue.
                    return

                when (level) {
                    Level.WARN -> DisplayMessage.showMessageCustom(message, Color.yellow)
                    Level.ERROR, Level.FATAL -> DisplayMessage.showMessageCustom(message, Color.red)
                    else -> DisplayMessage.showMessageCustom(message, Misc.getTextColor())
                }
            }
        }
    }

    override fun append(event: LoggingEvent) {
        if (!ModPlugin.applicationLoaded) {
            val level = event.getLevel()
            if(!level.isGreaterOrEqual(Level.WARN)) // Skip caching that which is below WARN.
                return

            synchronized(ModPlugin.cachedEvents) {
                val cache = ModPlugin.cachedEvents
                if (cache.size >= MAX_CACHE_SIZE) {
                    cache.removeFirst()
                    ModPlugin.reachedMaxCache = true
                }
                cache.addLast(event)
            }
            return
        }

        if (LVSettings.addLogsToConsoleModConsoleLevel == Level.OFF && LVSettings.addLogsToDisplayMessageLevel == Level.OFF)
            return

        displayLoggedMessage(event)
    }

    override fun close() {}

    override fun requiresLayout(): Boolean = false
}

class NoDisplayThrowable : Throwable(null, null, false, false) {
    override fun fillInStackTrace(): Throwable = this
    override fun toString(): String = ""
}