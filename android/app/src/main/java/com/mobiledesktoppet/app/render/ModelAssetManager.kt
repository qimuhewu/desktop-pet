package com.mobiledesktoppet.app.render

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException
import org.json.JSONObject

/**
 * 模型资产管理（P0-6 自有模型替换架构）：
 * - 解析/校验 App 私有目录下的 active 模型（标准 Cubism 结构）；
 * - 校验失败或无模型时返回 null → 调用方一律走兜底渲染，业务不得崩溃（架构文档 §8 #6）；
 * - 支持用户导入自有模型（SAF Uri → 拷贝校验）。
 */
class ModelAssetManager(private val context: Context) {

    /** 校验结果。 */
    data class ValidationResult(
        val ok: Boolean,
        val model3JsonFile: File? = null,
        val errors: List<String> = emptyList()
    )

    /** 导入结果（成功时携带安装后的模型根目录）。 */
    sealed class ImportResult {
        data class Success(val modelDir: File) : ImportResult()
        data class Failure(val reason: String) : ImportResult()
    }

    /** 模型根目录：files/models/（active/ 为当前启用模型）。 */
    val modelsRoot: File
        get() = File(context.filesDir, "models")

    private val activeDir: File
        get() = File(modelsRoot, "active")

    /**
     * 解析当前启用模型目录（无模型/校验失败 → null，调用方走兜底渲染）。
     */
    fun resolveActiveModelDir(): File? {
        val dir = activeDir
        if (!dir.isDirectory) return null
        val result = validate(dir)
        return if (result.ok) result.model3JsonFile?.parentFile else null
    }

    /** 校验模型目录：标准 Cubism 结构（model3.json + moc3 + 贴图）。 */
    fun validate(modelDir: File): ValidationResult {
        val errors = mutableListOf<String>()
        if (!modelDir.isDirectory) {
            return ValidationResult(false, null, listOf("目录不存在: ${modelDir.absolutePath}"))
        }

        // 1. model3.json（入口文件）
        val model3 = modelDir.listFiles { f -> f.isFile && f.name.endsWith(".model3.json") }
            ?.firstOrNull()
        if (model3 == null) {
            errors.add("缺少 *.model3.json（模型描述文件）")
            return ValidationResult(false, null, errors)
        }

        // 2. 解析 model3.json → FileReferences
        val json = try {
            JSONObject(model3.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            return ValidationResult(false, model3, listOf("model3.json 解析失败: ${e.message}"))
        }
        val fileRefs = json.optJSONObject("FileReferences")
        if (fileRefs == null) {
            errors.add("model3.json 缺少 FileReferences")
            return ValidationResult(false, model3, errors)
        }

        // 3. moc3 本体
        val moc3Path = fileRefs.optString("Moc", "")
        if (moc3Path.isEmpty()) {
            errors.add("FileReferences.Moc 缺失")
        } else {
            val moc3 = File(modelDir, moc3Path)
            if (!moc3.isFile) errors.add("moc3 文件不存在: $moc3Path")
        }

        // 4. 贴图（Textures 数组逐项存在性检查）
        val textures = fileRefs.optJSONArray("Textures")
        if (textures == null || textures.length() == 0) {
            errors.add("FileReferences.Textures 缺失或为空")
        } else {
            for (i in 0 until textures.length()) {
                val texPath = textures.optString(i, "")
                if (texPath.isNotEmpty() && !File(modelDir, texPath).isFile) {
                    errors.add("贴图文件不存在: $texPath")
                }
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult(true, model3, emptyList())
        } else {
            ValidationResult(false, model3, errors)
        }
    }

    /**
     * 导入用户自有模型（P2 预留接口，UI 用 SAF DocumentTree Uri）：
     * 递归拷贝到临时目录 → validate → 通过后原子替换 active/。
     */
    fun importModel(treeUri: Uri): ImportResult {
        val tempDir = File(modelsRoot, "importing_${System.currentTimeMillis()}")
        return try {
            copyDocumentTree(treeUri, tempDir)
            val result = validate(tempDir)
            if (!result.ok) {
                tempDir.deleteRecursively()
                ImportResult.Failure("模型校验失败: ${result.errors.joinToString("; ")}")
            } else {
                if (activeDir.exists()) activeDir.deleteRecursively()
                if (tempDir.renameTo(activeDir)) {
                    ImportResult.Success(activeDir)
                } else {
                    ImportResult.Failure("模型目录替换失败")
                }
            }
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            Log.e(TAG, "模型导入失败", e)
            ImportResult.Failure("导入异常: ${e.message}")
        }
    }

    /** 递归拷贝 SAF DocumentTree 到目标目录。 */
    private fun copyDocumentTree(treeUri: Uri, target: File) {
        val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("无法解析 DocumentTree: $treeUri")
        copyRecursively(rootDoc, target)
    }

    private fun copyRecursively(dir: androidx.documentfile.provider.DocumentFile, target: File) {
        target.mkdirs()
        for (child in dir.listFiles()) {
            val childTarget = File(target, child.name ?: continue)
            if (child.isDirectory) {
                copyRecursively(child, childTarget)
            } else if (child.isFile) {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    childTarget.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ModelAssetManager"
    }
}
