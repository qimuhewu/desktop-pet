package com.mobiledesktoppet.app.service

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * 悬浮窗触摸控制器：拖拽/点按/双击手势判定与吸附边缘逻辑（时序图 5.1）。
 *
 * 职责划分：
 * - 本类只做「手势判定 + 拖拽坐标计算 + 松手吸附目标点」；
 * - WindowManager.addView/updateViewLayout 由 [OverlayPetService] 执行。
 *
 * 手势语义：
 * - 单击 → 点按互动（随机台词 + 动作 + 气泡）；
 * - 双击/长按 → 弹出迷你操作条 [喂食][聊天][设置][收起]；
 * - 拖拽 → 跟随手指；松手 → 吸附就近边缘。
 */
class OverlayTouchController(
    private val windowManager: WindowManager,
    private val overlayView: View,
    private val layoutParams: WindowManager.LayoutParams,
    private val callbacks: Callbacks
) {

    /** 手势回调（Service 实现业务反应）。 */
    interface Callbacks {
        /** 单击宠物（触发随机可爱台词）。 */
        fun onSingleTap()

        /** 双击/长按（弹出迷你操作条）。 */
        fun onDoubleTap()

        /** 拖拽开始。 */
        fun onDragStart()

        /** 拖拽结束（x/y 为吸附后的目标坐标）。 */
        fun onDragEnd(x: Int, y: Int)
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var downLayoutX = 0
    private var downLayoutY = 0
    private var dragging = false
    private var moved = false

    /** 拖拽判定阈值（超出视为移动，避免误触）。 */
    private val touchSlopPx: Int = ViewConfigurationHolder.slop

    /** 手势检测：双击/长按。 */
    private val gestureDetector = GestureDetector(
        overlayView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                callbacks.onDoubleTap()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                callbacks.onDoubleTap()
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!moved) {
                    callbacks.onSingleTap()
                }
                return true
            }
        }
    )

    /**
     * 处理触摸事件（Service 的 onTouch 转发到这里）。
     * @return true = 事件已消费
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downLayoutX = layoutParams.x
                downLayoutY = layoutParams.y
                moved = false
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).toInt()
                val dy = (event.rawY - downRawY).toInt()
                if (!dragging && (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx)) {
                    dragging = true
                    moved = true
                    callbacks.onDragStart()
                }
                if (dragging) {
                    layoutParams.x = dx + downLayoutX
                    layoutParams.y = dy + downLayoutY
                    try {
                        windowManager.updateViewLayout(overlayView, layoutParams)
                    } catch (_: Exception) {
                        // 视图未附加时忽略
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    val snapped = snapToEdge(layoutParams.x, layoutParams.y)
                    layoutParams.x = snapped.first
                    layoutParams.y = snapped.second
                    try {
                        windowManager.updateViewLayout(overlayView, layoutParams)
                    } catch (_: Exception) {
                        // 忽略
                    }
                    callbacks.onDragEnd(layoutParams.x, layoutParams.y)
                }
            }
        }
        return true
    }

    /**
     * 吸附就近边缘：x 距屏幕左右边缘较近的一侧吸附（留 8px 边距），y 保持用户放置高度。
     */
    fun snapToEdge(x: Int, y: Int): Pair<Int, Int> {
        val metrics = overlayView.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val margin = 8
        val left = margin
        val right = screenWidth - overlayView.width - margin
        val targetX = if (right < left) left else (if (abs(x - left) <= abs(x - right)) left else right)
        val clampedY = y.coerceAtLeast(0).coerceAtMost(metrics.heightPixels - overlayView.height)
        return targetX to clampedY
    }
}

/** ViewConfiguration 延迟持有（避免控制器构造时依赖 Context 主题）。 */
private object ViewConfigurationHolder {
    val slop: Int = 24 // 约 8dp@3x，宽松阈值
}
