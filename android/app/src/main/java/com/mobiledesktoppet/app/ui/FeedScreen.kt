package com.mobiledesktoppet.app.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.mobiledesktoppet.app.di.ServiceLocator
import com.mobiledesktoppet.app.service.OverlayPetService
import com.mobiledesktoppet.app.ui.theme.PetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 喂食页：食物列表（foods.json）+ 喂食动画反馈（时序图 5.2）。
 */
class FeedScreen(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val navigate: (String) -> Unit
) {

    fun createView(): View {
        val context = activity
        val root = PetTheme.verticalLayout(context)

        // 顶栏
        val topBar = PetTheme.horizontalLayout(context)
        topBar.addView(
            TextView(context).apply {
                text = "🍖 喂食"
                textSize = 18f
                setTextColor(PetTheme.ON_SURFACE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        topBar.addView(PetTheme.outlineButton(context, "← 返回") { navigate(MainActivity.ROUTE_MAIN) })
        root.addView(topBar)

        root.addView(
            PetTheme.body(context, "不同食物提供不同增益；喂食后亲密度与经验都会提升哦~")
        )

        // 食物列表
        val foods = ServiceLocator.foods
        if (foods.isEmpty()) {
            root.addView(PetTheme.body(context, "食物表加载失败（assets/foods.json 缺失），请重新同步共享资产。"))
            return root
        }
        for (food in foods) {
            root.addView(foodRow(food))
        }
        return root
    }

    private fun foodRow(food: com.mobiledesktoppet.app.domain.GrowthEngine.FoodItem): View {
        val context = activity
        val row = PetTheme.horizontalLayout(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = PetTheme.dp(context, 16).toFloat()
            }
            setPadding(PetTheme.dp(context, 12), PetTheme.dp(context, 10), PetTheme.dp(context, 12), PetTheme.dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, PetTheme.dp(context, 6), 0, 0) }
            gravity = Gravity.CENTER_VERTICAL
        }

        val info = TextView(context).apply {
            textSize = 15f
            setTextColor(PetTheme.ON_SURFACE)
            text = "${food.name}\n饥饿+${food.hungerGain}  心情+${food.moodGain}  亲密+${food.intimacyGain}  EXP+${food.expGain}"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(info)
        row.addView(
            PetTheme.button(context, "喂食") {
                scope.launch {
                    val result = runCatching {
                        ServiceLocator.petRepository.feed(food.id)
                    }.getOrNull()
                    if (result != null) {
                        val reaction = ServiceLocator.dialogueEngine.pickFeedReaction(
                            result.hungerAfter.toInt(), food.name
                        )
                        OverlayPetService.notifyStateChanged()
                        AlertDialog.Builder(context)
                            .setTitle(if (result.leveledUp) "🎉 升级到 Lv.${result.newLevel}！" else "谢谢投喂")
                            .setMessage(reaction)
                            .setPositiveButton("好耶~", null)
                            .show()
                    } else {
                        AlertDialog.Builder(context)
                            .setTitle("投喂失败")
                            .setMessage("没有找到这种食物，稍后再试试吧~")
                            .setPositiveButton("好的", null)
                            .show()
                    }
                }
            }
        )
        return row
    }
}
