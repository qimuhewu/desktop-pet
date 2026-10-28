import SwiftUI
import CoreGraphics

/// SwiftUI 程序绘制兜底小动物（与 Android FallbackPetRenderer 对齐）：
/// Canvas 代码绘制小猫（耳朵/眼睛眨动/腮红/姿态差异），时间驱动动画，无外部模型依赖。
struct FallbackPetView: View {

    var pose: PetMotion = .idle
    var frameRate: Int = 30

    var body: some View {
        TimelineView(.animation(minimumInterval: max(1.0 / Double(max(5, min(60, frameRate))), 1.0 / 60.0))) { context in
            Canvas { ctx, size in
                drawPet(context: ctx, size: size, date: context.date)
            }
        }
        .allowsHitTesting(false)
    }

    // MARK: - 绘制

    private func drawPet(context: GraphicsContext, size: CGSize, date: Date) {
        let t = date.timeIntervalSince1970
        let w = size.width
        let h = size.height
        guard w > 0, h > 0 else { return }

        let bodyRadius = min(w, h) * 0.32
        let center = CGPoint(x: w / 2, y: h / 2)

        // 时间驱动动画参数（与 Android 端公式一致）
        let breath = sin(t * 2.0) * 0.02
        let blinkPeriod: TimeInterval = 3.8
        let blink: Double = (t.truncatingRemainder(dividingBy: blinkPeriod)) < 0.16 ? 1 : 0
        let walkBob: Double = pose == .walk ? abs(sin(t * 6.0)) * Double(h) * 0.04 : 0
        let bob = sin(t * 1.6) * Double(h) * 0.015 + walkBob
        let scale = 1.0 + breath

        let transform = CGAffineTransform(translationX: center.x, y: center.y + CGFloat(bob))
            .scaledBy(x: CGFloat(scale), y: CGFloat(scale))

        let bodyColor = Color(red: 0.969, green: 0.843, blue: 0.580) // 0xFFF7D794
        let innerEarColor = Color(red: 0.973, green: 0.647, blue: 0.761) // 0xFFF8A5C2
        let lineColor = Color(red: 0.365, green: 0.251, blue: 0.216) // 0xFF5D4037
        let cheekColor = Color(red: 0.973, green: 0.647, blue: 0.761)
        let happyColor = Color(red: 0.969, green: 0.561, blue: 0.702) // 0xFFF78FB3

        // 耳朵（左右三角）
        let earHeight = bodyRadius * 1.1
        var earPath = Path()
        earPath.move(to: CGPoint(x: -bodyRadius * 0.75, y: -bodyRadius * 0.55))
        earPath.addLine(to: CGPoint(x: -bodyRadius * 0.45, y: -bodyRadius - earHeight * 0.6))
        earPath.addLine(to: CGPoint(x: -bodyRadius * 0.15, y: -bodyRadius * 0.75))
        earPath.closeSubpath()
        earPath.move(to: CGPoint(x: bodyRadius * 0.15, y: -bodyRadius * 0.75))
        earPath.addLine(to: CGPoint(x: bodyRadius * 0.45, y: -bodyRadius - earHeight * 0.6))
        earPath.addLine(to: CGPoint(x: bodyRadius * 0.75, y: -bodyRadius * 0.55))
        earPath.closeSubpath()
        context.fill(earPath.applying(transform), with: .color(bodyColor))

        var innerEarPath = Path()
        innerEarPath.move(to: CGPoint(x: -bodyRadius * 0.62, y: -bodyRadius * 0.6))
        innerEarPath.addLine(to: CGPoint(x: -bodyRadius * 0.45, y: -bodyRadius - earHeight * 0.28))
        innerEarPath.addLine(to: CGPoint(x: -bodyRadius * 0.28, y: -bodyRadius * 0.72))
        innerEarPath.closeSubpath()
        innerEarPath.move(to: CGPoint(x: bodyRadius * 0.28, y: -bodyRadius * 0.72))
        innerEarPath.addLine(to: CGPoint(x: bodyRadius * 0.45, y: -bodyRadius - earHeight * 0.28))
        innerEarPath.addLine(to: CGPoint(x: bodyRadius * 0.62, y: -bodyRadius * 0.6))
        innerEarPath.closeSubpath()
        context.fill(innerEarPath.applying(transform), with: .color(innerEarColor))

        // 圆脸
        let faceRect = CGRect(
            x: -bodyRadius, y: -bodyRadius, width: bodyRadius * 2, height: bodyRadius * 2
        ).applying(transform)
        context.fill(Path(ellipseIn: faceRect), with: .color(bodyColor))

        // 眼睛（眨眼压扁）
        let eyeOffsetX = bodyRadius * 0.38
        let eyeOffsetY = -bodyRadius * 0.1
        let eyeWhiteRadius = bodyRadius * 0.14
        let eyePupilRadiusY = bodyRadius * (0.16 - 0.13 * blink)
        for side in [-1.0, 1.0] {
            let eyeCenter = CGPoint(x: CGFloat(side) * eyeOffsetX, y: eyeOffsetY).applying(transform)
            context.fill(
                Path(ellipseIn: CGRect(
                    x: eyeCenter.x - eyeWhiteRadius, y: eyeCenter.y - eyeWhiteRadius,
                    width: eyeWhiteRadius * 2, height: eyeWhiteRadius * 2)),
                with: .color(.white))
            if eyePupilRadiusY > 1 {
                let pupil = CGRect(
                    x: eyeCenter.x - eyeWhiteRadius * 0.5, y: eyeCenter.y - eyePupilRadiusY * 0.5,
                    width: eyeWhiteRadius, height: eyePupilRadiusY)
                context.fill(Path(ellipseIn: pupil), with: .color(lineColor))
            }
        }

        // 嘴（进食=咀嚼矩形，其他=微笑弧线）
        let mouthY = bodyRadius * 0.35
        var mouthPath = Path()
        if pose == .eat {
            let chew = (sin(t * 10.0) * 0.5 + 0.5) * bodyRadius * 0.12
            mouthPath.addRoundedRect(
                in: CGRect(x: -bodyRadius * 0.2, y: mouthY, width: bodyRadius * 0.4, height: bodyRadius * 0.1 + chew),
                cornerSize: CGSize(width: 4, height: 4))
            context.stroke(mouthPath.applying(transform), with: .color(lineColor), lineWidth: 3)
        } else {
            mouthPath.move(to: CGPoint(x: -bodyRadius * 0.18, y: mouthY))
            mouthPath.addQuadCurve(
                to: CGPoint(x: bodyRadius * 0.18, y: mouthY),
                control: CGPoint(x: 0, y: mouthY + bodyRadius * 0.18))
            context.stroke(mouthPath.applying(transform), with: .color(lineColor), lineWidth: 3)
        }

        // 腮红（害羞更明显）
        for side in [-1.0, 1.0] {
            let cheekCenter = CGPoint(x: CGFloat(side) * bodyRadius * 0.6, y: bodyRadius * 0.22).applying(transform)
            let cheekRadius = bodyRadius * 0.12
            context.fill(
                Path(ellipseIn: CGRect(
                    x: cheekCenter.x - cheekRadius, y: cheekCenter.y - cheekRadius,
                    width: cheekRadius * 2, height: cheekRadius * 2)),
                with: .color(cheekColor.opacity(pose == .shy ? 0.85 : 0.4)))
        }

        // 开心姿态：头顶冒爱心粒子（小圆点近似）
        if pose == .happy {
            for i in 0..<3 {
                let phase = (t * 0.8 + Double(i) * 0.33).truncatingRemainder(dividingBy: 1.0)
                let fx = sin(Double(i) * 2.1) * Double(bodyRadius) * 0.5
                let fy = -Double(bodyRadius) - Double(earHeight) * 0.7 - phase * Double(bodyRadius) * 0.8
                let point = CGPoint(x: fx, y: fy).applying(transform)
                let radius = bodyRadius * 0.08
                context.fill(
                    Path(ellipseIn: CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2)),
                    with: .color(happyColor.opacity(max(0, 1.0 - phase))))
            }
        }
    }
}
