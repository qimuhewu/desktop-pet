package com.mobiledesktoppet.app.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.mobiledesktoppet.app.di.ServiceLocator
import com.mobiledesktoppet.app.service.BubbleView
import com.mobiledesktoppet.app.ui.theme.PetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 主界面：全屏宠物舞台 + 状态条 + 底部操作栏（PRD §5.4）。
 *
 * 舞台复用渲染层：有模型 → Live2D；否则程序绘制兜底（业务无感知）。
 */
class MainScreen(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val navigate: (String) -> Unit
) {

    private lateinit var titleView: TextView
    private lateinit var hungerBar: ProgressBar
    private lateinit var moodBar: ProgressBar
    private lateinit var intimacyBar: ProgressBar
    private lateinit var hungerLabel: TextView
    private lateinit var moodLabel: TextView
    private lateinit var intimacyLabel: TextView
    private var stageBubble: BubbleView? = null
    private val dialogueEngine = ServiceLocator.dialogueEngine

    fun createView(): View {
        val context = activity
        val root = PetTheme.verticalLayout(context)

        // ---------- 顶栏：名字/等级/设置入口 ----------
        val topBar = PetTheme.horizontalLayout(context)
        titleView = TextView(context).apply {
            textSize = 18f
            setTextColor(PetTheme.ON_SURFACE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(titleView)
        topBar.addView(
            PetTheme.outlineButton(context, "⚙ 设置") { navigate(MainActivity.ROUTE_SETTINGS) }
        )
        root.addView(topBar)

        // ---------- 主舞台：全屏宠物展示 ----------
        val stage = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val (renderer, isLive2d) = ServiceLocator.createPetRenderer(context)
        val stageSize = PetTheme.dp(context, 260)
        val petView = renderer.platformView()
        stage.addView(
            petView,
            FrameLayout.LayoutParams(stageSize, stageSize, Gravity.CENTER)
        )
        renderer.playMotion("idle")
        // 主界面渲染器随界面销毁释放（GL/动画资源）
        petView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                renderer.release()
            }
        })

        // 舞台点按互动（点按 → 随机台词 + 动作 + 点按增益）
        val bubble = BubbleView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.TOP
            )
        }
        stageBubble = bubble
        stage.addView(bubble)
        petView.setOnClickListener {
            val reaction = dialogueEngine.pickTapReaction()
            val motions = listOf("happy", "shy", "idle")
            renderer.playMotion(motions[kotlin.random.Random.nextInt(motions.size)])
            bubble.show(reaction.text)
            scope.launch {
                runCatching { ServiceLocator.petRepository.recordInteraction() }
            }
        }
        root.addView(stage)

        // ---------- 状态条：饥饿/心情/亲密度 ----------
        val hungerPair = buildStatusRow(root, "饥饿")
        hungerBar = hungerPair.first
        hungerLabel = hungerPair.second
        val moodPair = buildStatusRow(root, "心情")
        moodBar = moodPair.first
        moodLabel = moodPair.second
        val intimacyPair = buildStatusRow(root, "亲密")
        intimacyBar = intimacyPair.first
        intimacyLabel = intimacyPair.second

        // ---------- 底部操作栏 ----------
        val bottomBar = PetTheme.horizontalLayout(context).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        bottomBar.addView(PetTheme.button(context, "🍖 喂食") { navigate(MainActivity.ROUTE_FEED) })
        bottomBar.addView(spacer(context))
        bottomBar.addView(PetTheme.button(context, "💬 聊天") { navigate(MainActivity.ROUTE_CHAT) })
        bottomBar.addView(spacer(context))
        bottomBar.addView(PetTheme.outlineButton(context, "⏰ 提醒") { navigate(MainActivity.ROUTE_REMINDER) })
        root.addView(bottomBar)

        // 订阅档案流刷新顶栏与状态条
        scope.launch {
            ServiceLocator.petRepository.observeProfile().collect { profile ->
                if (profile == null) return@collect
                titleView.text = "🐱 ${profile.name}  Lv.${profile.level}  EXP ${profile.exp}/" +
                    com.mobiledesktoppet.app.domain.GrowthEngine.expToNextLevel(profile.level, ServiceLocator.growthRules)
                hungerBar.progress = profile.hunger.toInt()
                moodBar.progress = profile.mood.toInt()
                intimacyBar.progress = profile.intimacy.toInt()
                hungerLabel.text = profile.hunger.toInt().toString()
                moodLabel.text = profile.mood.toInt().toString()
                intimacyLabel.text = profile.intimacy.toInt().toString()
            }
        }

        return root
    }

    // 状态行构建：返回(进度条, 数值标签)
    private fun buildStatusRow(root: LinearLayout, label: String): Pair<ProgressBar, TextView> {
        val context = activity
        val row = PetTheme.horizontalLayout(context)
        row.addView(
            TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(PetTheme.ON_SURFACE)
            }
        )
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 50
            progressTintList = android.content.res.ColorStateList.valueOf(PetTheme.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(PetTheme.dp(context, 12), 0, PetTheme.dp(context, 12), 0) }
        }
        row.addView(bar)
        val valueLabel = TextView(context).apply {
            textSize = 14f
            setTextColor(PetTheme.ON_SURFACE)
            text = "50"
            minWidth = PetTheme.dp(context, 32)
        }
        row.addView(valueLabel)
        root.addView(row)
        return bar to valueLabel
    }

    private fun spacer(context: android.content.Context): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(PetTheme.dp(context, 12), 1)
        }
    }

    /** 面板显示喂食反馈台词（FeedScreen 调用反馈气泡的对外入口预留）。 */
    fun showBubble(text: String) {
        stageBubble?.show(text)
    }
}
