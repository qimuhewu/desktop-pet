import Foundation

/// 全局常量集中定义（架构文档 §8 共享知识 #1）。
enum AppInfo {
    /// 主 App BundleID。
    static let bundleId = "com.mobiledesktoppet.app"

    /// 小组件 Extension BundleID。
    static let widgetBundleId = "com.mobiledesktoppet.app.PetWidget"

    /// 灵动岛 Extension BundleID。
    static let liveActivityBundleId = "com.mobiledesktoppet.app.PetLiveActivity"

    /// 双端统一 App Group（PNG 序列与共享数据目录）。
    static let appGroupId = "group.com.mobiledesktoppet.shared"

    /// App Group 内 PNG 序列子目录（docs/04 §7）。
    static let spritesSubdirectory = "sprites"

    /// 深链 scheme（提醒通知跳转提醒管理页）。
    static let deepLinkScheme = "petapp"

    /// 深链：提醒管理页。
    static var reminderDeepLink: URL? {
        URL(string: "\(deepLinkScheme)://reminder")
    }

    /// App Group 容器目录（缺失时回退到 Application Support，保证模拟器/预览可用）。
    static var appGroupContainerURL: URL {
        if let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) {
            return url
        }
        let fallback = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return fallback.appendingPathComponent("AppGroupFallback", isDirectory: true)
    }

    /// App Group 内 sprites 目录。
    static var spritesDirectoryURL: URL {
        appGroupContainerURL.appendingPathComponent(spritesSubdirectory, isDirectory: true)
    }
}
