package com.mobiledesktoppet.app.render

import android.view.View

/**
 * 宠物渲染接口（与 iOS Render/PetRendererProtocol.swift 对齐，架构文档 D1 决策）。
 *
 * 双实现：
 * - [Live2DRenderer]：Cubism SDK 实现（源集隔离，缺 SDK 时为加载必失败的占位实现）；
 * - [FallbackPetRenderer]：Canvas 程序绘制兜底（缺 SDK/模型时使用，业务不崩溃）。
 */
interface PetRenderer {

    /**
     * 加载模型（标准 Cubism 结构：目录内含 *.model3.json）。
     * @return true = 加载成功；false = 失败（调用方应切换到兜底渲染）
     */
    fun loadModel(path: String): Boolean

    /** 播放动作（idle/walk/happy/shy/eat 等，缺省动作名由实现自行映射）。 */
    fun playMotion(name: String)

    /** 设置动画帧率（省电模式降低帧率，P1-5）。 */
    fun setFrameRate(fps: Int)

    /** 暂停渲染循环。 */
    fun pause()

    /** 恢复渲染循环。 */
    fun resume()

    /** 释放资源（GL 上下文、模型内存等），调用后实例不可复用。 */
    fun release()

    /** 宿主平台视图（悬浮窗/主界面 addView 用）。 */
    fun platformView(): View
}
