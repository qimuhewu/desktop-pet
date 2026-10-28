import SwiftUI
import WidgetKit

/// 锁屏小组件：亲密度/饥饿度摘要 + 宠物表情（iOS 16+ accessory families，PRD §5.3）。
struct LockScreenWidget: Widget {

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "PetLockScreenWidget", provider: WidgetTimelineProvider()) { entry in
            LockScreenWidgetView(entry: entry)
        }
        .configurationDisplayName("桌宠锁屏")
        .description("锁屏上的宠物状态摘要：亲密度 / 饥饿度。")
        .supportedFamilies([.accessoryCircular, .accessoryInline])
    }
}

/// 锁屏小组件视图（环形：表情+亲密度；行内：摘要文本）。
struct LockScreenWidgetView: View {

    @Environment(\.widgetFamily) private var family
    let entry: WidgetPetEntry

    var body: some View {
        switch family {
        case .accessoryInline:
            Text("🐱 \(entry.name) ♥\(entry.intimacy) 🍖\(entry.hunger)")
                .font(.caption2)
        default:
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 2) {
                    WidgetPetFace(entry: entry)
                        .frame(width: 30, height: 30)
                    Text("♥\(entry.intimacy)")
                        .font(.caption2.bold())
                        .minimumScaleFactor(0.6)
                }
                .padding(4)
            }
        }
    }
}
