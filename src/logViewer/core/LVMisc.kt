package logViewer.core

import com.fs.starfarer.api.campaign.MessageDisplayAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.state.AppDriver
import logViewer.core.ReflectionUtils.getMethodsMatching
import logViewer.core.ReflectionUtils.invoke
import org.lazywizard.console.overlay.v2.panels.ConsoleOverlayPanel

internal object LVMisc {
    fun getScreenPanel(): UIPanelAPI? {
        val state = AppDriver.getInstance().currentState
        return state.invoke("getScreenPanel") as? UIPanelAPI
    }

    fun getMessageDisplay(): UIPanelAPI? {
        val screenPanel = getScreenPanel() ?: return null
        return (screenPanel.invoke("getChildrenCopy") as List<UIComponentAPI>).reversed().firstOrNull { it is MessageDisplayAPI } as? UIPanelAPI
    }

    internal fun isConsoleOpen(): Boolean {
        if (!LVSettings.isConsoleModEnabled)
            return false
        return runCatching { ConsoleOverlayPanel.instance }.getOrNull() != null
    }

    internal fun getCallerClass(): Class<*>? {
        val thisClass = this::class.java.name
        var callerClass: String? = null

        return Throwable().stackTrace
            .asSequence()
            .map { it.className }
            .filter {
                it != thisClass
                        && !it.startsWith("java.")
                        && !it.startsWith("kotlin.")
                        && !it.contains("reflect")
            }
            .firstOrNull { className ->
                if (callerClass == null) {
                    // First non-this class = caller class
                    callerClass = className
                    false
                } else {
                    // Keep skipping while still in caller class
                    className != callerClass
                }
            }
            ?.let { runCatching { Class.forName(it) }.getOrNull() }
    }
}