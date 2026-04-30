package logViewer.core

import logViewer.core.LVSettings.getLevel
import lunalib.lunaSettings.LunaSettings
import lunalib.lunaSettings.LunaSettingsListener
import org.apache.log4j.Level

internal class LunaSettingsListener : LunaSettingsListener {
    init {
        settingsChanged(LVSettings.getModID())
    }

    //Gets called whenever settings are saved in the campaign or the main menu.
    override fun settingsChanged(modID: String) {
        if (modID != LVSettings.getModID())
            return

        val disable = LunaSettings.getBoolean(modID, "disable")!!

        if (!disable) {
            val displayLevel = LunaSettings.getString(modID, "addLogsToDisplayMessageLevel")!!
            val consoleLevel = LunaSettings.getString(modID, "addLogsToConsoleModConsoleLevel")!!

            LVSettings.addLogsToDisplayMessageLevel = getLevel(displayLevel)
            LVSettings.addLogsToConsoleModConsoleLevel = getLevel(consoleLevel)
        } else {
            LVSettings.addLogsToConsoleModConsoleLevel = Level.OFF
            LVSettings.addLogsToDisplayMessageLevel = Level.OFF
        }
    }
}