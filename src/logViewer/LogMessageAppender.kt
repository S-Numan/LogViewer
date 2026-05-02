package logViewer

import com.fs.starfarer.api.util.Misc
import logViewer.core.LVSettings
import logViewer.core.LogViewerPlugin
import logViewer.core.LogViewerPlugin.Companion.MAX_CACHE_SIZE
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Level
import org.apache.log4j.spi.LoggingEvent
import org.lazywizard.console.Console
import java.awt.Color

internal class LogMessageAppender : AppenderSkeleton() {
    companion object {

        fun displayLoggedMessage(event: LoggingEvent) {
            // Ignore logs from Console class to prevent infinite loops
            if (event.loggerName == Console::class.java.name) return

            // TODO, remove this next GraphicsLib update
            if (event.renderedMessage.contains("enableFullExplosionEffects")) // Hack to rid of never to be fixed before next starsector update graphics lib issue.
                return

            val level = event.getLevel()

            if (LVSettings.addLogsToConsoleModConsoleLevel != Level.OFF && level.isGreaterOrEqual(LVSettings.addLogsToConsoleModConsoleLevel)) {
                if (LVSettings.isConsoleModEnabled) {
                    val msg = buildString {
                        append("[${level}] ")
                        append("${event.loggerName} - ")
                        append(event.renderedMessage)

                        if (event.throwableInformation?.throwable.toString().isEmpty())
                            return@buildString

                        event.throwableStrRep?.let {
                            append("\n")
                            append(it.joinToString("\n"))
                        }

                    }

                    Console.showMessage(msg, Level.ALL)
                }
            }

            if (LVSettings.addLogsToDisplayMessageLevel != Level.OFF && level.isGreaterOrEqual(LVSettings.addLogsToDisplayMessageLevel)) {
                if (event.throwableInformation?.throwable is NoDisplayThrowable)
                    return
                when (level) {
                    Level.WARN -> DisplayMessage.showMessageCustom(event.renderedMessage, Color.yellow)
                    Level.ERROR, Level.FATAL -> DisplayMessage.showMessageCustom(event.renderedMessage, Color.red)
                    else -> DisplayMessage.showMessageCustom(event.renderedMessage, Misc.getTextColor())
                }
            }
        }
    }

    override fun append(event: LoggingEvent) {
        if (!LogViewerPlugin.applicationLoaded) {
            val level = event.getLevel()
            if(!level.isGreaterOrEqual(Level.WARN)) // Skip caching that which is below WARN.
                return

            synchronized(LogViewerPlugin.cachedEvents) {
                val cache = LogViewerPlugin.cachedEvents
                if (cache.size >= MAX_CACHE_SIZE) {
                    cache.removeFirst()
                    LogViewerPlugin.reachedMaxCache = true
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