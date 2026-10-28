package com.mobiledesktoppet.app.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mobiledesktoppet.app.di.ServiceLocator
import com.mobiledesktoppet.app.ui.theme.PetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 对话页：对话框 + 语料回复（P1-2 本地规则对话，双端语料一致）。
 */
class ChatScreen(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val navigate: (String) -> Unit
) {

    private lateinit var messagesContainer: LinearLayout
    private lateinit var inputField: EditText
    private val dialogueEngine = ServiceLocator.dialogueEngine

    fun createView(): View {
        val context = activity
        val root = PetTheme.verticalLayout(context)

        // 顶栏
        val topBar = PetTheme.horizontalLayout(context)
        topBar.addView(
            TextView(context).apply {
                text = "💬 聊天"
                textSize = 18f
                setTextColor(PetTheme.ON_SURFACE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        topBar.addView(PetTheme.outlineButton(context, "← 返回") { navigate(MainActivity.ROUTE_MAIN) })
        root.addView(topBar)

        // 消息列表（滚动区）
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        messagesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, PetTheme.dp(context, 8), 0, PetTheme.dp(context, 8))
        }
        scroll.addView(
            messagesContainer,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(scroll)

        // 开场白
        appendBubble("小希", "嗨~今天想聊点什么？", fromPet = true)

        // 输入行
        val inputRow = PetTheme.horizontalLayout(context)
        inputField = EditText(context).apply {
            hint = "和小希说点什么…"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        inputRow.addView(inputField)
        inputRow.addView(
            PetTheme.button(context, "发送") { sendMessage() }
        )
        root.addView(inputRow)

        return root
    }

    private fun sendMessage() {
        val input = inputField.text.toString()
        inputField.setText("")
        appendBubble("你", input, fromPet = false)

        scope.launch {
            // 聊天也是互动：点按增益 + 刷新 lastActiveAt（共享知识 #9）
            runCatching { ServiceLocator.petRepository.recordInteraction() }
            val reply = dialogueEngine.respond(input)
            appendBubble("小希", reply.text, fromPet = true)
        }
    }

    /** 追加一条消息气泡（宠物右侧紫色 / 用户左侧白色，简化的聊天 UI）。 */
    private fun appendBubble(sender: String, text: String, fromPet: Boolean) {
        val context = activity
        val bubble = TextView(context).apply {
            this.text = if (fromPet) "🐱 $text" else "$text"
            textSize = 14f
            setTextColor(if (fromPet) 0xFFFFFFFF.toInt() else PetTheme.ON_SURFACE)
            background = GradientDrawable().apply {
                setColor(if (fromPet) PetTheme.PRIMARY else 0xFFFFFFFF.toInt())
                cornerRadius = PetTheme.dp(context, 18).toFloat()
            }
            setPadding(PetTheme.dp(context, 12), PetTheme.dp(context, 8), PetTheme.dp(context, 12), PetTheme.dp(context, 8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (fromPet) 1f else 0f
            ).apply { setMargins(0, PetTheme.dp(context, 4), 0, 0) }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (fromPet) Gravity.END else Gravity.START
        }
        row.addView(bubble)
        messagesContainer.addView(
            row,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        // 滚动到底部
        messagesContainer.post {
            (messagesContainer.parent as? android.widget.ScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    }
}
