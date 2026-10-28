package com.mobiledesktoppet.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView

/**
 * 台词气泡视图（时序图 5.1：点按触发展示台词，3 秒后自动隐藏）。
 */
class BubbleView(context: Context) : TextView(context) {

    init {
        val bg = GradientDrawable().apply {
            setColor(Color.argb(230, 255, 255, 255))
            cornerRadius = 32f
        }
        background = bg
        setTextColor(Color.rgb(61, 61, 61))
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        gravity = Gravity.CENTER
        visibility = GONE
        elevation = dp(4).toFloat()
    }

    /** 显示台词并在 3 秒后自动隐藏（重复调用会重置计时）。 */
    fun show(text: String, durationMs: Long = 3000L) {
        this.text = text
        visibility = VISIBLE
        alpha = 1f
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, durationMs)
    }

    private val hideRunnable = Runnable {
        animate().alpha(0f).setDuration(200L).withEndAction { visibility = GONE }.start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/** 迷你操作条（双击/长按弹出：[喂食][聊天][设置][收起]）。 */
class MiniActionBar(context: Context, callbacks: Callbacks) : android.widget.LinearLayout(context) {

    /** 操作条回调（由 Service 转发到 UI 层）。 */
    interface Callbacks {
        fun onFeedClicked()
        fun onChatClicked()
        fun onSettingsClicked()
        fun onCollapseClicked()
    }

    init {
        orientation = HORIZONTAL
        val bg = GradientDrawable().apply {
            setColor(Color.argb(235, 50, 50, 60))
            cornerRadius = 24f
        }
        background = bg
        setPadding(dp(10), dp(6), dp(10), dp(6))
        elevation = dp(6).toFloat()
        visibility = GONE

        val items = listOf(
            "🍖喂食" to { callbacks.onFeedClicked() },
            "💬聊天" to { callbacks.onChatClicked() },
            "⚙️设置" to { callbacks.onSettingsClicked() },
            "▾收起" to { callbacks.onCollapseClicked() }
        )
        items.forEachIndexed { index, (label, action) ->
            if (index > 0) addView(createDivider())
            addView(createButton(label, action))
        }
    }

    private fun createButton(label: String, action: () -> Unit): View {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                visibility = GONE
                action()
            }
        }
    }

    private fun createDivider(): View {
        return View(context).apply {
            layoutParams = LayoutParams(dp(1), LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            setBackgroundColor(Color.argb(80, 255, 255, 255))
        }
    }

    fun show() {
        visibility = VISIBLE
        removeCallbacks(autoHide)
        postDelayed(autoHide, 6000L)
    }

    fun dismiss() {
        visibility = GONE
        removeCallbacks(autoHide)
    }

    private val autoHide = Runnable { visibility = GONE }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
