package logViewer.core

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import logViewer.DrawMessageScrollerTopLeft
import logViewer.LogMessageAppender
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.lazywizard.console.Console

class LogViewerPlugin : BaseModPlugin() {
    override fun onApplicationLoad() {
        DrawMessageScrollerTopLeft.doInit()

        LVSettings.onApplicationLoad()

        if (LVSettings.addLogsToConsoleModConsoleLevel != Level.OFF || LVSettings.addLogsToDisplayMessageLevel != Level.OFF) {
            // Cause the lazy class loader to load these classes preemptively to prevent issues.
            try {
                Class.forName("org.apache.log4j.Layout")
                Class.forName("org.apache.log4j.spi.LoggingEvent")
                Class.forName("org.apache.log4j.Priority")
                if (LVSettings.isConsoleModEnabled)
                    Class.forName(Console::class.java.name)
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }

            val rootLogger = Logger.getRootLogger()

            if (rootLogger.getAppender("LV_LogMessageAppender") == null) {
                val appender = LogMessageAppender().apply { name = "LV_LogMessageAppender" }
                rootLogger.addAppender(appender)
            }
        }
    }

    override fun onGameLoad(newGame: Boolean) {
        val sector = Global.getSector()

        DrawMessageScrollerTopLeft.onGameLoad()
        if (!sector.hasTransientScript(DrawMessageScrollerTopLeft::class.java))
            sector.addTransientScript(DrawMessageScrollerTopLeft())
    }
}