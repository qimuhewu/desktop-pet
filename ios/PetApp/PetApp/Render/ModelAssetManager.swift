import Foundation

/// 模型资产管理（P0-6 自有模型替换架构，与 Android render/ModelAssetManager.kt 对齐）：
/// - 解析/校验 Application Support/PetModels/active 的标准 Cubism 模型；
/// - 校验失败或无模型返回 nil → 调用方一律走兜底渲染，业务不得崩溃（架构文档 §8 #6）；
/// - 支持用户导入自有模型（document picker 回调，P2 完整流程预留）。
final class ModelAssetManager {

    /// 校验结果。
    struct ValidationResult {
        let ok: Bool
        let model3JsonURL: URL?
        let errors: [String]
    }

    /// 导入结果。
    enum ImportResult {
        case success(URL)
        case failure(String)
    }

    /// 模型根目录：Application Support/PetModels/（active/ 为当前启用模型）。
    var modelsRootURL: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("PetModels", isDirectory: true)
    }

    private var activeDirectoryURL: URL {
        modelsRootURL.appendingPathComponent("active", isDirectory: true)
    }

    /// 解析当前启用模型目录（无模型/校验失败 → nil，调用方走兜底渲染）。
    func resolveActiveModelDir() -> URL? {
        guard FileManager.default.fileExists(atPath: activeDirectoryURL.path) else { return nil }
        let result = validate(directory: activeDirectoryURL)
        return result.ok ? result.model3JsonURL?.deletingLastPathComponent() : nil
    }

    /// 校验模型目录：标准 Cubism 结构（model3.json + moc3 + 贴图）。
    func validate(directory: URL) -> ValidationResult {
        var errors: [String] = []
        var isDirectory: ObjCBool = false
        guard FileManager.default.fileExists(atPath: directory.path, isDirectory: &isDirectory),
              isDirectory.boolValue else {
            return ValidationResult(ok: false, model3JsonURL: nil, errors: ["目录不存在: \(directory.path)"])
        }

        // 1. model3.json（入口文件）
        let contents = (try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: nil)) ?? []
        guard let model3 = contents.first(where: { $0.lastPathComponent.hasSuffix(".model3.json") }) else {
            return ValidationResult(ok: false, model3JsonURL: nil, errors: ["缺少 *.model3.json（模型描述文件）"])
        }

        // 2. 解析 model3.json → FileReferences
        guard let data = try? Data(contentsOf: model3),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let fileRefs = json["FileReferences"] as? [String: Any] else {
            return ValidationResult(ok: false, model3JsonURL: model3, errors: ["model3.json 解析失败或缺少 FileReferences"])
        }

        // 3. moc3 本体
        if let moc = fileRefs["Moc"] as? String {
            if !FileManager.default.fileExists(atPath: directory.appendingPathComponent(moc).path) {
                errors.append("moc3 文件不存在: \(moc)")
            }
        } else {
            errors.append("FileReferences.Moc 缺失")
        }

        // 4. 贴图（Textures 数组逐项存在性检查）
        if let textures = fileRefs["Textures"] as? [String], !textures.isEmpty {
            for tex in textures {
                if !FileManager.default.fileExists(atPath: directory.appendingPathComponent(tex).path) {
                    errors.append("贴图文件不存在: \(tex)")
                }
            }
        } else {
            errors.append("FileReferences.Textures 缺失或为空")
        }

        return ValidationResult(ok: errors.isEmpty, model3JsonURL: model3, errors: errors)
    }

    /// 导入用户自有模型（document picker 提供的文件 URL 列表）：
    /// 拷贝到临时目录 → validate → 通过后原子替换 active/。
    func importModel(fileURLs: [URL]) -> ImportResult {
        let tempDirectory = modelsRootURL
            .appendingPathComponent("importing_\(Int(Date().timeIntervalSince1970 * 1000))", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            for source in fileURLs {
                let fileName = source.lastPathComponent
                let target = tempDirectory.appendingPathComponent(fileName)
                // 安全访问（security-scoped resource）
                let scoped = source.startAccessingSecurityScopedResource()
                defer { if scoped { source.stopAccessingSecurityScopedResource() } }
                try FileManager.default.copyItem(at: source, to: target)
            }
            let result = validate(directory: tempDirectory)
            guard result.ok else {
                try? FileManager.default.removeItem(at: tempDirectory)
                return .failure("模型校验失败: \(result.errors.joined(separator: "; "))")
            }
            if FileManager.default.fileExists(atPath: activeDirectoryURL.path) {
                try FileManager.default.removeItem(at: activeDirectoryURL)
            }
            try FileManager.default.moveItem(at: tempDirectory, to: activeDirectoryURL)
            return .success(activeDirectoryURL)
        } catch {
            try? FileManager.default.removeItem(at: tempDirectory)
            return .failure("导入异常: \(error.localizedDescription)")
        }
    }
}
