package com.mobiledesktoppet.app.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * 主题与颜色（View 体系程序化构建，无 XML 布局；架构文档 §1.3）。
 */
object PetTheme {

    // 品牌色
    val PRIMARY = 0xFF7C4DFF.toInt()
    val PRIMARY_DARK = 0xFF5E35B1.toInt()
    val SURFACE = 0xFFF6F4FB.toInt()
    val ON_SURFACE = 0xFF3A3353.toInt()
    val ACCENT = 0xFFF8A5C2.toInt()

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** 主操作按钮（Material）。 */
    fun button(context: Context, text: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(context).apply {
            this.text = text
            setTextSize(14f)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(PRIMARY)
            cornerRadius = dp(context, 20)
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    /** 次级描边按钮。 */
    fun outlineButton(context: Context, text: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(context).apply {
            this.text = text
            setTextSize(14f)
            setTextColor(PRIMARY_DARK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            strokeColor = android.content.res.ColorStateList.valueOf(PRIMARY)
            strokeWidth = dp(context, 1)
            cornerRadius = dp(context, 20)
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    /** 页面标题。 */
    fun title(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 20f
            setTextColor(ON_SURFACE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(context, 12), 0, dp(context, 8))
        }
    }

    /** 正文文本。 */
    fun body(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(ON_SURFACE)
            setLineSpacing(dp(context, 2).toFloat(), 1f)
        }
    }

    /** 状态胶囊文本（顶栏用）。 */
    fun chip(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(PRIMARY)
                cornerRadius = dp(context, 16).toFloat()
            }
            setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
        }
    }

    /** 垂直布局快捷创建。 */
    fun verticalLayout(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 16))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    /** 水平布局快捷创建。 */
    fun horizontalLayout(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
    }
}
