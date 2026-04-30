package logViewer

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.campaign.MessageDisplayAPI
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import logViewer.core.LVMisc.getScreenPanel
import logViewer.core.ReflectionUtils.getFieldsMatching
import logViewer.core.ReflectionUtils.invoke
import logViewer.core.TimeKeeper
import org.lazywizard.lazylib.ui.FontException
import org.lazywizard.lazylib.ui.LazyFont
import java.awt.Color

internal class DrawMessageScrollerTopLeft : EveryFrameScript, BaseEveryFrameCombatPlugin() {
    companion object {
        private const val TOP_BUFFER = 10f
        private const val CAMPAIGN_TOP_BUFFER = 14f // Buffer that only applies if in campaign state. Mostly to avoid overlapping the existing campaign message at the top of the screen.
        private const val LEFT_BUFFER = 170f
        private const val MAX_MESSAGES = 6
        private const val MESSAGE_LIFETIME = 5f
        private const val FADE_START = 4.0f


        private val messageQueue = ArrayDeque<Pair<String, Color>>()

        private data class FeedMessage(
            val text: String,
            val color: Color,
            var age: Float = 0f,
            var yOffset: Float = 0f,
            var drawable: LazyFont.DrawableString? = null
        )

        private val activeMessages = mutableListOf<FeedMessage>()

        private var font: LazyFont? = null
        private var toDraw: LazyFont.DrawableString? = null

        private var init = false
        private var fadingOut = false
        private var curState = GameState.TITLE
        private var justLoadedGame = 0

        fun doInit() {
            if (font == null) {
                try {
                    font = LazyFont.loadFont("graphics/fonts/orbitron24aabold.fnt")
                } catch (e: FontException) {
                    Global.getLogger(this.javaClass).error("Failed to load font", e)
                    return
                }
            }

            init = true
        }

        fun onGameLoad() {
            justLoadedGame = 2 // Delay a tick for the screen panel+
           //clearCurrent()
        }

        private fun clearCurrent() {
            if (toDraw != null) {

                toDraw = null
                messageQueue.clear()

                fadingOut = false
            }
        }

        fun renderStatic() {
            //val screenWidth = Global.getSettings().screenWidth
            val screenHeight = Global.getSettings().screenHeight
            val isCampaign = curState == GameState.CAMPAIGN

            val baseX = LEFT_BUFFER
            val baseY = (screenHeight - TOP_BUFFER) - if(isCampaign) CAMPAIGN_TOP_BUFFER else 0f

            for (msg in activeMessages) {
                val drawable = msg.drawable ?: continue

                val x = baseX//(screenWidth - drawable.width) / 2f
                val y = baseY - msg.yOffset

                val alpha = when {
                    msg.age > FADE_START -> {
                        val fadeProgress = (msg.age - FADE_START) / (MESSAGE_LIFETIME - FADE_START)
                        (255 * (1f - fadeProgress.coerceIn(0f, 1f))).toInt()
                    }
                    else -> 255
                }

                drawable.baseColor = Color(msg.color.red, msg.color.green, msg.color.blue, alpha)
                drawable.draw(x, y)
            }
        }

        val cachedMessages = mutableListOf<Pair<String, Color>>()
        /** Call this to add a message */
        fun addMessage(text: String, color: Color, excludeExistingMessages: Boolean = true) {
            // Hack to prevent string from being too large and slowing down the game. Ideally the size of the text rendered on screen would be checked then clipped, but this is just easier.
            val text = if (text.length > 300) text.substring(0, 300) else text

            // No duplicates
            if (excludeExistingMessages && activeMessages.find { it.text == text } != null) {
                return
            }

            //val screenWidth = Global.getSettings().screenWidth

            val drawable = try {
                font?.createText(text, color, 24f)//?.apply {
                    //maxWidth = screenWidth - LEFT_BUFFER
                //}
            } catch(_: Exception) {
                cachedMessages.add(text to color)
                return
            }

            val newMsg = FeedMessage(text, color, 0f, 0f, drawable)

            // Push existing messages upward
            val lineHeight = 26f
            for (msg in activeMessages) {
                msg.yOffset += lineHeight
            }

            activeMessages.add(0, newMsg) // newest at bottom

            Global.getSoundPlayer().playUISound("ui_noise_static_message_quiet", 1f, 1f)
        }
    }

    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true
    override fun advance(amount: Float) {
        stateChangeChecker()
        if (curState != GameState.CAMPAIGN) return

        if (justLoadedGame > 0) {
            justLoadedGame--
            if (justLoadedGame == 0) {
                val screenPanel = getScreenPanel()
                @Suppress("UNCHECKED_CAST")
                if (screenPanel != null && (screenPanel.invoke("getChildrenCopy") as List<UIComponentAPI>).none { LVCampaignMessageRenderer::class.java.isInstance((it as? CustomPanelAPI)?.plugin) })
                    LVCampaignMessageRenderer()
            }
        }

        advanceAmount(TimeKeeper.campaignDelta(amount))
    }


    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        stateChangeChecker()
        if (curState != GameState.TITLE && curState != GameState.COMBAT) return

        advanceAmount(TimeKeeper.combatDelta(amount))
    }

    fun stateChangeChecker() {
        val state = Global.getCurrentState()

        if (state != curState) {
            //clearCurrent()
            curState = state

            if(curState == GameState.CAMPAIGN) {
                val screenPanel = getScreenPanel()
                @Suppress("UNCHECKED_CAST")
                if (screenPanel != null && (screenPanel.invoke("getChildrenCopy") as List<UIComponentAPI>).none { LVCampaignMessageRenderer::class.java.isInstance((it as? CustomPanelAPI)?.plugin) })
                    LVCampaignMessageRenderer()
            }
        }
    }

    private fun advanceAmount(amount: Float) {
        if (!init) doInit()

        cachedMessages.reversed().forEach {
            try {
                addMessage(it.first, it.second)
            } catch(_: Exception) { return@forEach }
            cachedMessages.remove(it)
        }

        val iterator = activeMessages.iterator()
        while (iterator.hasNext()) {
            val msg = iterator.next()
            msg.age += amount

            // Remove old messages
            if (msg.age > MESSAGE_LIFETIME) {
                iterator.remove()
            }
        }

        // Clamp list size
        while (activeMessages.size > MAX_MESSAGES) {
            activeMessages.removeLast()
        }
    }

    override fun renderInUICoords(viewport: ViewportAPI?) {
        renderStatic()
    }
}

private class LVCampaignMessageRenderer : BaseCustomUIPanelPlugin() {
    private val screenPanel = getScreenPanel()
    var panel: CustomPanelAPI

    init {
        val settings = Global.getSettings()
        panel = settings.createCustom(settings.screenWidth, settings.screenHeight, this)

        screenPanel?.addComponent(panel)?.inTL(0f, 0f)
    }

    override fun render(alphaMult: Float) {
        DrawMessageScrollerTopLeft.renderStatic()
    }

    override fun advance(amount: Float) {
        if(screenPanel == null)
            return

        // Move above other components if not top, but try to avoid fighting for control by excluding CustomPanelAPIs and MessageDisplayAPIs

        @Suppress("UNCHECKED_CAST")
        val children = screenPanel.invoke("getChildrenCopy") as List<UIComponentAPI>

        val lastValid = children.lastOrNull { component ->
            when (component) {
                is CustomPanelAPI -> false
                is MessageDisplayAPI -> false
                else -> true
            }
        }

        if (lastValid !== panel) {
            screenPanel.bringComponentToTop(panel)
        }
    }
}