package com.mobiledesktoppet.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mobiledesktoppet.app.data.db.ReminderEntity
import com.mobiledesktoppet.app.di.ServiceLocator
import com.mobiledesktoppet.app.reminder.ReminderSchedulerImpl
import com.mobiledesktoppet.app.ui.theme.PetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 提醒管理页：模板选择/自定义（P1-1）+ 已有提醒的启停与删除。
 */
class ReminderScreen(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val navigate: (String) -> Unit
) {

    private lateinit var reminderListContainer: LinearLayout

    fun createView(): View {
        val context = activity
        val root = PetTheme.verticalLayout(context)

        // 顶栏
        val topBar = PetTheme.horizontalLayout(context)
        topBar.addView(
            TextView(context).apply {
                text = "⏰ 提醒"
                textSize = 18f
                setTextColor(PetTheme.ON_SURFACE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        topBar.addView(PetTheme.outlineButton(context, "← 返回") { navigate(MainActivity.ROUTE_MAIN) })
        root.addView(topBar)

        // 模板区
        root.addView(PetTheme.title(context, "添加提醒（按模板）"))
        val templates = ServiceLocator.reminderTemplates
        if (templates.isEmpty()) {
            root.addView(PetTheme.body(context, "提醒模板加载失败（assets/reminder_templates.json 缺失）。"))
        }
        // 模板按钮每行 3 个流式排列
        var currentRow: LinearLayout? = null
        for ((index, template) in templates.withIndex()) {
            if (index % 3 == 0) {
                currentRow = PetTheme.horizontalLayout(context)
                root.addView(currentRow)
            }
            currentRow?.addView(
                PetTheme.outlineButton(context, template.name) { addReminderFromTemplate(template) }
            )
        }

        // 已有提醒列表
        root.addView(PetTheme.title(context, "我的提醒"))
        reminderListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(reminderListContainer)

        requestNotificationPermissionIfNeeded()
        refreshReminderList()
        return root
    }

    // ---------- 模板 → 新提醒 ----------

    private fun addReminderFromTemplate(template: com.mobiledesktoppet.app.data.ReminderTemplate) {
        val context = activity
        val intervalInput = android.widget.EditText(context).apply {
            hint = "间隔（分钟）"
            setText(template.defaultIntervalMinutes.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(context)
            .setTitle(template.name)
            .setView(intervalInput)
            .setPositiveButton("添加") { _, _ ->
                val minutes = intervalInput.text.toString().toIntOrNull()
                    ?: template.defaultIntervalMinutes
                val config = ReminderEntity(
                    id = UUID.randomUUID().toString(),
                    templateKey = template.key,
                    intervalMinutes = minutes.coerceAtLeast(1),
                    message = template.message.replace("{petName}", ServiceLocator.growthRules.defaultName),
                    enabled = true,
                    nextTriggerAt = null
                )
                scope.launch {
                    runCatching {
                        val repository = ServiceLocator.petRepository
                        repository.upsertReminder(config)
                        // 计算首次触发时间并排程
                        repository.planNextTrigger(config)?.let {
                            ReminderSchedulerImpl(context.applicationContext).schedule(it)
                        }
                    }
                    refreshReminderList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 列表刷新 ----------

    private fun refreshReminderList() {
        val context = activity
        scope.launch {
            val reminders = runCatching { ServiceLocator.petRepository.getReminders() }
                .getOrDefault(emptyList())
            // UI 更新回主线程
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                reminderListContainer.removeAllViews()
                if (reminders.isEmpty()) {
                    reminderListContainer.addView(
                        PetTheme.body(context, "还没有提醒哦~点上方模板添加一个吧！")
                    )
                }
                for (reminder in reminders) {
                    reminderListContainer.addReminderRow(reminder)
                }
            }
        }
    }

    private fun LinearLayout.addReminderRow(reminder: ReminderEntity) {
        val context = activity
        val row = PetTheme.horizontalLayout(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = PetTheme.dp(context, 16).toFloat()
            }
            setPadding(PetTheme.dp(context, 12), PetTheme.dp(context, 8), PetTheme.dp(context, 12), PetTheme.dp(context, 8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, PetTheme.dp(context, 6), 0, 0) }
            gravity = Gravity.CENTER_VERTICAL
        }
        val templateName = ServiceLocator.reminderTemplates
            .firstOrNull { it.key == reminder.templateKey }?.name ?: reminder.templateKey
        row.addView(
            TextView(context).apply {
                textSize = 14f
                setTextColor(PetTheme.ON_SURFACE)
                text = "${templateName} · 每${reminder.intervalMinutes}分钟\n" +
                    (if (reminder.enabled) "已启用" else "已停用") +
                    (reminder.nextTriggerAt?.let {
                        " · 下次 " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                            .format(java.util.Date(it))
                    } ?: "")
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        row.addView(
            PetTheme.outlineButton(context, if (reminder.enabled) "停用" else "启用") {
                scope.launch {
                    runCatching {
                        val repository = ServiceLocator.petRepository
                        val updated = reminder.copy(enabled = !reminder.enabled)
                        repository.upsertReminder(updated)
                        val scheduler = ReminderSchedulerImpl(context.applicationContext)
                        if (updated.enabled) {
                            repository.planNextTrigger(updated)?.let { scheduler.schedule(it) }
                        } else {
                            scheduler.cancel(updated.id)
                        }
                    }
                    refreshReminderList()
                }
            }
        )
        row.addView(PetTheme.button(context, "删除") {
            scope.launch {
                runCatching {
                    val repository = ServiceLocator.petRepository
                    repository.deleteReminder(reminder.id)
                    ReminderSchedulerImpl(context.applicationContext).cancel(reminder.id)
                }
                refreshReminderList()
            }
        })
        addView(row)
    }

    // ---------- 通知权限（API 33+） ----------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_POST_NOTIFICATIONS
                )
            }
        }
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 2001
    }
}
