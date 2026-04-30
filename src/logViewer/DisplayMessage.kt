package logViewer

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.util.Misc
import logViewer.core.LVMisc.getCallerClass
import logViewer.core.LVMisc.isConsoleOpen
import logViewer.core.LVSettings
import org.apache.log4j.Level
import org.lazywizard.console.Console
import java.awt.Color

object DisplayMessage {
    //Prevent error spam
    private val recentErrors = mutableMapOf<String, Long>()
    private const val ERROR_SPAM_INTERVAL_MS = 4000 // 4 seconds

    @JvmStatic
    @JvmOverloads
    fun showError(short: String, full: String, e: Exception? = null) {
        val now = System.currentTimeMillis()

        // Combine both short + full messages to uniquely identify the error
        val key = "$short::$full"
        val lastTime = recentErrors[key] ?: 0L

        // Skip if the same error occurred recently
        if (now - lastTime < ERROR_SPAM_INTERVAL_MS) {
            return
        }

        // Record this error occurrence
        recentErrors[key] = now

        // Clean up stale entries occasionally
        if (recentErrors.size > 100) {
            val cutoff = now - ERROR_SPAM_INTERVAL_MS
            recentErrors.entries.removeIf { it.value < cutoff }
        }

        val callerClass: Class<*> = getCallerClass() ?: javaClass
        // Console or logger output
        if (LVSettings.isConsoleModEnabled) {
            if (e != null) {
                Console.showException(full, e)
            } else {
                Console.showMessage(callerClass.name + " - " + full, Level.ERROR)
            }
        } else {
            logMessage(callerClass, full, Level.ERROR, displayMessage = false)
        }

        // Show short message to player
        showMessageCustom(short, Color.RED)
    }

    @JvmStatic
    @JvmOverloads
    fun showError(short: String, e: Exception? = null) {
        showError(short, short, e)
    }

    @JvmStatic
    @JvmOverloads
    fun showMessage(
        short: String,
        color: Color?,
        highlight: String,
        highlightColor: Color = Misc.getHighlightColor(),
    ) {
        var defaultColor = color

        val gameState = Global.getCurrentState()
        if (gameState == GameState.CAMPAIGN) {
            if (defaultColor == null)
                defaultColor = Misc.getTooltipTitleAndLightHighlightColor()

            val ui = Global.getSector().campaignUI
            ui.messageDisplay.addMessage(short, defaultColor, highlight, highlightColor)
        } else if (gameState == GameState.COMBAT) {
            if (defaultColor == null)
                defaultColor = Misc.getTextColor()

            val engine = Global.getCombatEngine()
            val ui = engine.combatUI

            val highlightIndex = short.indexOf(highlight)
            if (highlight.isEmpty() || highlightIndex == -1) { // Highlight text not found.
                ui.addMessage(1, defaultColor, short)
            } else {
                val before = short.substring(0, highlightIndex)
                val after = short.substring(highlightIndex + highlight.length)

                ui.addMessage(
                    0,
                    defaultColor, before,
                    highlightColor, highlight,
                    defaultColor, after
                )
            }
        } else { // Title-Screen state?
            DrawMessageScrollerTopLeft.addMessage(short, color ?: Misc.getTextColor())
        }

        // Show messages in console if console is open
        if(isConsoleOpen())
            Console.showMessage(short)

        //Global.getSoundPlayer().playUISound("ui_noise_static_message_quiet", 1f, 1f)
    }

    @JvmStatic
    @JvmOverloads
    fun showMessage(short: String, color: Color? = null) {
        showMessage(short, color, "")
    }

    @JvmStatic
    @JvmOverloads
    fun showMessage(short: String, highlight: String, highlightColor: Color = Misc.getHighlightColor()) {
        showMessage(short, null, highlight, highlightColor)
    }

    @JvmStatic
    @JvmOverloads
    fun showMessageCustom(message: String, color: Color = Misc.getTextColor()) {
        val state = Global.getCurrentState()
        if(state == GameState.COMBAT) {
            val engine = Global.getCombatEngine()
            val ui = engine?.combatUI
            ui?.addMessage(1, color, message)
        } else {
            DrawMessageScrollerTopLeft.addMessage(message, color)
        }
    }


    /**
     * Logs a message with the specified level.
     *
     * If [displayMessage] is false, do not show on screen even if the setting to display logged messages would be true.
     */
    @JvmStatic
    @JvmOverloads
    fun logMessage(javaClass: Class<*>, message: String, level: Level, displayMessage: Boolean = true) {
        if (displayMessage) {
            when (level) {
                Level.INFO -> Global.getLogger(javaClass).info(message)
                Level.WARN -> Global.getLogger(javaClass).warn(message)
                Level.ERROR -> Global.getLogger(javaClass).error(message)
                Level.FATAL -> Global.getLogger(javaClass).fatal(message)
                Level.DEBUG -> Global.getLogger(javaClass).debug(message)
            }
        } else {
            when (level) {
                Level.INFO -> Global.getLogger(javaClass).info(message, NoDisplayThrowable())
                Level.WARN -> Global.getLogger(javaClass).warn(message, NoDisplayThrowable())
                Level.ERROR -> Global.getLogger(javaClass).error(message, NoDisplayThrowable())
                Level.FATAL -> Global.getLogger(javaClass).fatal(message, NoDisplayThrowable())
                Level.DEBUG -> Global.getLogger(javaClass).debug(message, NoDisplayThrowable())
            }
        }
    }
}