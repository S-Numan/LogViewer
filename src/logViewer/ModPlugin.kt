package logViewer

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import logViewer.core.LVSettings
import logViewer.core.LVSettings.redirectSTDERRToLogs
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

        var applicationLoaded = false
        var reachedMaxCache = false
        const val MAX_CACHE_SIZE = 2000
        val cachedEvents = ArrayDeque<LoggingEvent>()
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

        val eventsToReplay: List<LoggingEvent>

        synchronized(cachedEvents) {
            applicationLoaded = true
            eventsToReplay = cachedEvents.toList()
            cachedEvents.clear()
        }

        eventsToReplay.forEach {
            LogMessageAppender.displayLoggedMessage(it)
        }
        if(reachedMaxCache) {
            Global.getLogger(this.javaClass).warn("LogViewer: Reached max cache size of $MAX_CACHE_SIZE events. Some logs have been dropped.")
        }
    }


    override fun onGameLoad(newGame: Boolean) {
        val sector = Global.getSector()

        DrawMessageScrollerTopLeft.onGameLoad()
        if (!sector.hasTransientScript(DrawMessageScrollerTopLeft::class.java))
            sector.addTransientScript(DrawMessageScrollerTopLeft())
    }
}