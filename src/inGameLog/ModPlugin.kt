package inGameLog

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import inGameLog.core.LVSettings
import inGameLog.core.LVSettings.redirectSTDERRToLogs
import inGameLog.core.ParseLog
import org.apache.log4j.Logger
import org.apache.log4j.spi.LoggingEvent
import org.lazywizard.console.Console
import java.io.PrintStream

class ModPlugin : BaseModPlugin() {
    init {
        val rootLogger = Logger.getRootLogger()

        if (rootLogger.getAppender("LV_LogMessageAppender") == null) {
            val appender = LogMessageAppender().apply { name = "LV_LogMessageAppender" }
            rootLogger.addAppender(appender)
        }

        modSpec = Global.getSettings().modManager.enabledModsCopy.find { it.modPluginClassName == this::class.java.name }!!
    }


    companion object {
        private lateinit var modSpec: ModSpecAPI
        @JvmStatic
        fun getModSpec(): ModSpecAPI = modSpec
        @JvmStatic
        fun getModName(): String = modSpec.name.trim()
        @JvmStatic
        fun getModId(): String = modSpec.id
    }

    override fun onApplicationLoad() {
        DrawMessageScrollerTopLeft.doInit()

        LVSettings.onApplicationLoad()

        if(redirectSTDERRToLogs) {
            if (System.err !is PrintStream || System.err.toString().contains("ErrToLog4j").not()) {
                System.setErr(PrintStream(ErrToLog4jStream(), true))
            }
        }

        // Cause the lazy class loader to load these classes preemptively to prevent issues.
        try {
            Class.forName("org.apache.log4j.Layout")
            Class.forName("org.apache.log4j.spi.LoggingEvent")
            Class.forName("org.apache.log4j.Priority")
            if (LVSettings.isConsoleModEnabled)
                Class.forName(Console::class.java.name)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(e)
        }

        val eventsToReplay: List<LoggingEvent> = try {
            val logStream = Global.getSettings().openStream("starsector.log")
            val logContent = ParseLog.tailFromLastMarkerText(logStream, "0    [")
            ParseLog.parseLogContent(logContent)
        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Failed to read log file", e)
            emptyList()
        }

        eventsToReplay.forEach {
            LogMessageAppender.displayLoggedMessage(it)
        }
    }


    override fun onGameLoad(newGame: Boolean) {
        val sector = Global.getSector()

        DrawMessageScrollerTopLeft.onGameLoad()
        if (!sector.hasTransientScript(DrawMessageScrollerTopLeft::class.java))
            sector.addTransientScript(DrawMessageScrollerTopLeft())
    }
}