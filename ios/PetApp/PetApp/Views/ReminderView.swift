import SwiftUI
import UserNotifications

/// 提醒管理页：模板选择/自定义 + 已有提醒启停与删除（P1-1）。
struct ReminderView: View {

    @EnvironmentObject private var environment: AppEnvironment
    @State private var reminders: [ReminderConfig] = []
    @State private var authorizationDenied = false

    var body: some View {
        List {
            if authorizationDenied {
                Section {
                    Text("通知权限未授予：请到 系统设置 → 通知 → 桌宠 打开权限后重试。")
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
            Section("按模板添加") {
                ForEach(environment.reminderTemplates) { template in
                    Button {
                        addReminder(from: template)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(template.name)
                                    .foregroundStyle(.primary)
                                Text("默认每 \(template.defaultIntervalMinutes) 分钟")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "plus.circle")
                                .foregroundStyle(.purple)
                        }
                    }
                }
            }

            Section("我的提醒") {
                if reminders.isEmpty {
                    Text("还没有提醒哦~点上方模板添加一个吧！")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                ForEach(reminders) { reminder in
                    ReminderRowView(
                        reminder: reminder,
                        onToggle: { toggle(reminder) },
                        onDelete: { delete(reminder) }
                    )
                }
            }
        }
        .navigationTitle("⏰ 提醒")
        .task { reload() }
    }

    // MARK: - 操作

    private func addReminder(from template: AssetsLoader.ReminderTemplate) {
        Task {
            let profile = await environment.repository.getProfile()
            var config = ReminderConfig.fromTemplate(template, petName: profile.name)
            config.nextTriggerAt = ReminderPlanner.nextTriggerTime(
                config, fromMillis: Date.now.millisecondsSince1970
            )
            await environment.repository.upsertReminder(config)
            await environment.reminderScheduler.schedule(config)
            reload()
        }
    }

    private func toggle(_ reminder: ReminderConfig) {
        Task {
            var updated = reminder
            updated.enabled.toggle()
            await environment.repository.upsertReminder(updated)
            if updated.enabled {
                var planned = updated
                planned.nextTriggerAt = ReminderPlanner.nextTriggerTime(
                    updated, fromMillis: Date.now.millisecondsSince1970
                )
                await environment.repository.upsertReminder(planned)
                await environment.reminderScheduler.schedule(planned)
            } else {
                environment.reminderScheduler.cancel(id: updated.id)
            }
            reload()
        }
    }

    private func delete(_ reminder: ReminderConfig) {
        Task {
            await environment.repository.deleteReminder(id: reminder.id)
            environment.reminderScheduler.cancel(id: reminder.id)
            reload()
        }
    }

    private func reload() {
        Task {
            // 授权状态检查（仅提示，不强制）
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            authorizationDenied = settings.authorizationStatus == .denied
            reminders = await environment.repository.getReminders()
        }
    }
}

/// 单条提醒行：启用状态 + 下次触发时间 + 启停/删除。
private struct ReminderRowView: View {

    let reminder: ReminderConfig
    let onToggle: () -> Void
    let onDelete: () -> Void

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(reminder.templateKey) · 每 \(reminder.intervalMinutes) 分钟")
                    .font(.subheadline)
                Text(statusText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(action: onToggle) {
                Text(reminder.enabled ? "停用" : "启用")
                    .font(.caption)
            }
            .buttonStyle(.bordered)
            Button(role: .destructive, action: onDelete) {
                Text("删除")
                    .font(.caption)
            }
            .buttonStyle(.bordered)
        }
    }

    private var statusText: String {
        if !reminder.enabled { return "已停用" }
        if let next = reminder.nextTriggerAt {
            return "已启用 · 下次 " + Self.timeFormatter.string(from: Date.fromMilliseconds(next))
        }
        return "已启用"
    }
}
