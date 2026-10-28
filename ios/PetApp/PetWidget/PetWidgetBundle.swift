import SwiftUI
import WidgetKit

/// 小组件入口 WidgetBundle：桌面 + 锁屏统一注册（类图 PetWidgetProvider 组）。
@main
struct PetWidgetBundle: WidgetBundle {
    var body: some Widget {
        HomeScreenWidget()
        LockScreenWidget()
    }
}
