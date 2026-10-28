import SwiftUI
import WidgetKit

/// 桌面 2×2 小组件：宠物形象 + 今日心情 + 去喂食深链（PRD §5.3）。
struct HomeScreenWidget: Widget {

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "PetHomeScreenWidget", provider: WidgetTimelineProvider()) { entry in
            HomeScreenWidgetView(entry: entry)
        }
        .configurationDisplayName("桌宠")
        .description("宠物形象与状态摘要，点「去喂食」打开 App。")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

/// 桌面小组件视图。
struct HomeScreenWidgetView: View {

    let entry: WidgetPetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                WidgetPetFace(entry: entry)
                    .frame(width: 56, height: 56)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(entry.name) Lv.\(entry.level)")
                        .font(.headline)
                    Text("心情 \(entry.mood) · 亲密 \(entry.intimacy)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            Text(entry.message)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Spacer(minLength: 0)
            Link(destination: URL(string: "petapp://feed")!) {
                Text("🍖 去喂食")
                    .font(.subheadline.bold())
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(Color.purple.opacity(0.15))
                    .cornerRadius(10)
            }
        }
        .padding(12)
    }
}

/// 小组件宠物头像：优先 App Group PNG 姿态，缺失时程序绘制兜底（Widget 进程无 Cubism）。
struct WidgetPetFace: View {

    let entry: WidgetPetEntry

    var body: some View {
        if let fileName = entry.spriteFileName,
           let data = SharedPetStateReader.readSpriteData(fileName: fileName),
           let uiImage = UIImage(data: data) {
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFit()
        } else {
            FallbackWidgetFace()
        }
    }
}

/// 程序绘制兜底小猫（纯 SwiftUI 形状，无外部资源依赖）。
struct FallbackWidgetFace: View {
    var body: some View {
        ZStack {
            // 耳朵
            Triangle()
                .fill(Color(red: 0.969, green: 0.843, blue: 0.580))
                .frame(width: 16, height: 16)
                .offset(x: -13, y: -20)
            Triangle()
                .fill(Color(red: 0.969, green: 0.843, blue: 0.580))
                .frame(width: 16, height: 16)
                .offset(x: 13, y: -20)
            // 脸
            Circle()
                .fill(Color(red: 0.969, green: 0.843, blue: 0.580))
            // 眼睛
            HStack(spacing: 12) {
                Circle().frame(width: 6, height: 6)
                Circle().frame(width: 6, height: 6)
            }
            .offset(y: -4)
            // 腮红
            HStack(spacing: 26) {
                Circle().fill(Color.pink.opacity(0.5)).frame(width: 8, height: 8)
                Circle().fill(Color.pink.opacity(0.5)).frame(width: 8, height: 8)
            }
            .offset(y: 8)
        }
    }
}

/// 三角形形状（耳朵）。
struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
