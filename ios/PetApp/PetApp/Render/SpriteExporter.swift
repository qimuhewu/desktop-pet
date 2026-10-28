import Foundation
import CoreGraphics
import UIKit
import ImageIO
import UniformTypeIdentifiers

/// PNG 序列导出（D3 决策）：渲染姿态 → App Group 共享目录。
///
/// - Widget / 灵动岛进程**禁止任何 Cubism 调用**，只读本目录（docs/04 §7）；
/// - PNG 命名：`pet_<pose>_<index>.png` + `manifest.json`（姿态→文件映射）；
/// - 导出图像用 CoreGraphics 程序绘制（与 FallbackPetView 同款形象）；
///   Live2D 实包固化后可替换为 Cubism 离屏渲染结果。
final class SpriteExporter {

    /// 姿态清单（每个姿态导出的帧数）。
    static let exportPlan: [(pose: PetMotion, frames: Int)] = [
        (.idle, 2), (.happy, 1), (.hungry, 1), (.sleepy, 1)
    ]

    /// App Group sprites 目录（AppInfo.spritesDirectoryURL）。
    private let spritesDirectory: URL

    init(spritesDirectory: URL = AppInfo.spritesDirectoryURL) {
        self.spritesDirectory = spritesDirectory
        try? FileManager.default.createDirectory(at: spritesDirectory, withIntermediateDirectories: true)
    }

    /// 导出全部姿态到 App Group，并写 manifest.json。返回是否全部成功。
    @discardableResult
    func exportPoses(size: CGSize = CGSize(width: 256, height: 256)) -> Bool {
        var manifestPoses: [String: [String]] = [:]
        var allOk = true

        for (pose, frames) in Self.exportPlan {
            var files: [String] = []
            for index in 0..<frames {
                let fileName = "pet_\(pose.rawValue)_\(index).png"
                let url = spritesDirectory.appendingPathComponent(fileName)
                if writePNG(at: url, pose: pose, frameIndex: index, size: size) {
                    files.append(fileName)
                } else {
                    allOk = false
                }
            }
            manifestPoses[pose.rawValue] = files
        }

        let manifest: [String: Any] = [
            "poses": manifestPoses,
            "updated_at": Date.now.millisecondsSince1970
        ]
        if let data = try? JSONSerialization.data(withJSONObject: manifest, options: [.prettyPrinted, .sortedKeys]) {
            try? data.write(to: spritesDirectory.appendingPathComponent("manifest.json"), options: .atomic)
        }
        return allOk
    }

    /// 单帧绘制并写 PNG（CoreGraphics 程序绘制小猫，与兜底形象一致）。
    private func writePNG(at url: URL, pose: PetMotion, frameIndex: Int, size: CGSize) -> Bool {
        guard let context = CGContext(
            data: nil,
            width: Int(size.width),
            height: Int(size.height),
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return false
        }

        // CG 坐标系原点在左下：翻转 y 并居中
        context.translateBy(x: size.width / 2, y: size.height / 2)
        context.scaleBy(x: 1, y: -1)

        drawCat(context: context, size: size, pose: pose, frameIndex: frameIndex)

        guard let image = context.makeImage() else { return false }
        let destination = CGImageDestinationCreateWithURL(
            url as CFURL, UTType.png.identifier as CFString, 1, nil)
        guard let destination = destination else { return false }
        CGImageDestinationAddImage(destination, image, nil)
        return CGImageDestinationFinalize(destination)
    }

    /// CoreGraphics 程序绘制小猫（参数与 FallbackPetView 对齐，frameIndex 控制序列帧差异）。
    private func drawCat(context: CGContext, size: CGSize, pose: PetMotion, frameIndex: Int) {
        let bodyRadius = min(size.width, size.height) * 0.32

        let bodyColor = CGColor(srgbRed: 0.969, green: 0.843, blue: 0.580, alpha: 1)
        let innerEarColor = CGColor(srgbRed: 0.973, green: 0.647, blue: 0.761, alpha: 1)
        let lineColor = CGColor(srgbRed: 0.365, green: 0.251, blue: 0.216, alpha: 1)
        let cheekColor = CGColor(srgbRed: 0.973, green: 0.647, blue: 0.761, alpha: 0.6)

        // 序列帧：idle 帧间眨眼差异
        let blink: CGFloat = (pose == .idle && frameIndex == 1) ? 0.15 : 1.0
        let sleepy: Bool = (pose == .sleepy)

        // 耳朵
        let earHeight = bodyRadius * 1.1
        context.setFillColor(bodyColor)
        for side in [-1.0, 1.0] {
            let path = CGMutablePath()
            path.move(to: CGPoint(x: CGFloat(side) * bodyRadius * 0.15, y: -bodyRadius * 0.75))
            path.addLine(to: CGPoint(x: CGFloat(side) * bodyRadius * 0.45, y: -bodyRadius - earHeight * 0.6))
            path.addLine(to: CGPoint(x: CGFloat(side) * bodyRadius * 0.75, y: -bodyRadius * 0.55))
            path.closeSubpath()
            context.addPath(path)
            context.fillPath()
        }
        context.setFillColor(innerEarColor)
        for side in [-1.0, 1.0] {
            let path = CGMutablePath()
            path.move(to: CGPoint(x: CGFloat(side) * bodyRadius * 0.28, y: -bodyRadius * 0.72))
            path.addLine(to: CGPoint(x: CGFloat(side) * bodyRadius * 0.45, y: -bodyRadius - earHeight * 0.28))
            path.addLine(to: CGPoint(x: CGFloat(side) * bodyRadius * 0.62, y: -bodyRadius * 0.6))
            path.closeSubpath()
            context.addPath(path)
            context.fillPath()
        }

        // 圆脸
        context.setFillColor(bodyColor)
        context.fillEllipse(in: CGRect(
            x: -bodyRadius, y: -bodyRadius, width: bodyRadius * 2, height: bodyRadius * 2))

        // 眼睛（困=弧线，其他=圆点+眨眼缩放）
        for side in [-1.0, 1.0] {
            let eyeX = CGFloat(side) * bodyRadius * 0.38
            let eyeY = -bodyRadius * 0.1
            if sleepy {
                context.setStrokeColor(lineColor)
                context.setLineWidth(bodyRadius * 0.06)
                context.addArc(
                    center: CGPoint(x: eyeX, y: eyeY),
                    radius: bodyRadius * 0.14,
                    startAngle: .pi * 1.15, endAngle: .pi * 1.85, clockwise: false)
                context.strokePath()
            } else {
                context.setFillColor(UIColor.white.cgColor)
                context.fillEllipse(in: CGRect(
                    x: eyeX - bodyRadius * 0.14, y: eyeY - bodyRadius * 0.14,
                    width: bodyRadius * 0.28, height: bodyRadius * 0.28))
                context.setFillColor(lineColor)
                context.fillEllipse(in: CGRect(
                    x: eyeX - bodyRadius * 0.07, y: eyeY - bodyRadius * 0.07 * blink,
                    width: bodyRadius * 0.14, height: bodyRadius * 0.14 * blink))
            }
        }

        // 嘴（微笑弧线）
        context.setStrokeColor(lineColor)
        context.setLineWidth(bodyRadius * 0.06)
        context.addArc(
            center: CGPoint(x: 0, y: bodyRadius * 0.22),
            radius: bodyRadius * 0.2,
            startAngle: .pi * 1.15, endAngle: .pi * 1.85, clockwise: false)
        context.strokePath()

        // 腮红
        context.setFillColor(cheekColor)
        for side in [-1.0, 1.0] {
            context.fillEllipse(in: CGRect(
                x: CGFloat(side) * bodyRadius * 0.6 - bodyRadius * 0.12,
                y: bodyRadius * 0.22 - bodyRadius * 0.12,
                width: bodyRadius * 0.24, height: bodyRadius * 0.24))
        }
    }
}
