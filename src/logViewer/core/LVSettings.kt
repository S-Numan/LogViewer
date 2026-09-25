package logViewer.core

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import lunalib.lunaSettings.LunaSettings
import org.apache.log4j.Level
import java.io.IOException

object LVSettings {
    fun getLevel(level: String): Level = when (level) {
        "FATAL" -> Level.FATAL
        "ERROR" -> Level.ERROR
        "WARN" -> Level.WARN
        "INFO" -> Level.INFO
        "DEBUG" -> Level.DEBUG
        "ALL" -> Level.ALL
        else -> Level.OFF
    }

    fun onApplicationLoad() {
        modSpec = Global.getSettings().modManager.enabledModsCopy.find { it.modPluginClassName == LogViewerPlugin::class.java.name }!!

        if (Global.getSettings().modManager.isModEnabled("lunalib") && !LunaSettings.hasSettingsListenerOfClass(LunaSettingsListener::class.java))
            LunaSettings.addSettingsListener(LunaSettingsListener())
        else {
            try {
                val userSettings = Global.getSettings().loadJSON("userSettings.json", getModID())
                val consoleLevel = userSettings.getString("addLogsToConsoleModConsoleLevel")
                val displayLevel = userSettings.getString("addLogsToDisplayMessageLevel")

                addLogsToDisplayMessageLevel = getLevel(displayLevel)
                addLogsToConsoleModConsoleLevel = getLevel(consoleLevel)
                redirectSTDERRToLogs = userSettings.getBoolean("redirectSTDERRToLogs")
            } catch (ex: IOException) {
                Global.getLogger(this.javaClass).fatal("unable to read userSettings.json", ex)
            }
        }

        isConsoleModEnabled = Global.getSettings().modManager.isModEnabled("lw_console")
    }

    private lateinit var modSpec: ModSpecAPI
    fun getModSpec(): ModSpecAPI = modSpec
    fun getModName(): String = modSpec.name.trim()
    fun getModID(): String = modSpec.id

    var isConsoleModEnabled = false

    var addLogsToConsoleModConsoleLevel: Level = Level.WARN
    var addLogsToDisplayMessageLevel: Level = Level.ERROR
    var redirectSTDERRToLogs = false
}