package com.cadence.schedule.ui.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.cadence.schedule.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * 更新包签名校验。
 *
 * 本应用不经应用商店分发，更新完全依赖自带更新器：从 GitHub/Gitee Release 读下载链接 →
 * HTTP 下载 → FileProvider 或 Shizuku 安装。这条链路原先只校验 ZIP 魔数与体积，
 * **不校验签名**，一旦更新源账号被冒用、或下载经中间人改写，用户拿到的就是一个来路不明的包。
 * 平台侧只会在「签名与已装应用不一致」时拒绝覆盖安装，属于事后兜底，不是纵深防御。
 *
 * 因此在写入可安装状态之前，必须先用本文件把签名核对一遍。
 *
 * 期望指纹来源（[BuildConfig.EXPECTED_SIGNER_SHA256]）：
 * - 配置了 `-Pnexio.expectedSignerSha256=...`：严格使用该指纹，**跨签名来源也拦得住**；
 * - 未配置：回退为「与当前安装包同签名」。这仍能挡住"下载过程中被换成另一个包"，
 *   但挡不住本机已被替换成同包名异签名版本的情形——发布构建请务必配置上面的属性。
 */
internal object ApkSignatureVerifier {

    private const val TAG = "ApkSignatureVerifier"

    sealed interface Result {
        /** 校验通过。[sha256] 为实际签名指纹，便于日志追溯 */
        data class Valid(val sha256: String) : Result

        /** 校验不通过。[reason] 面向用户，可直接展示 */
        data class Invalid(val reason: String) : Result
    }

    /**
     * 校验 [apk] 的签名是否与预期一致。
     *
     * 任何无法确证的情况（解析失败、取不到签名、指纹为空）一律判为 [Result.Invalid]，
     * 即**默认拒绝**：更新通道宁可少装一次，也不能装错一次。
     */
    fun verify(context: Context, apk: File): Result {
        if (!apk.isFile || apk.length() == 0L) {
            return Result.Invalid("更新包不存在或为空")
        }

        val apkDigest = readApkSignerSha256(context, apk)
            ?: return Result.Invalid("无法读取更新包签名，已拒绝安装")

        val expected = expectedSignerSha256(context)
        if (expected.isEmpty()) {
            return Result.Invalid("未配置期望签名指纹，已拒绝安装")
        }

        return if (matches(apkDigest, expected)) {
            Log.i(TAG, "更新包签名校验通过: $apkDigest")
            Result.Valid(apkDigest)
        } else {
            Log.e(TAG, "更新包签名不匹配: apk=$apkDigest expected=$expected")
            Result.Invalid("更新包签名校验未通过，已拒绝安装")
        }
    }

    /** 指纹比对：大小写与冒号分隔均不影响结果（纯函数，便于单测） */
    fun matches(apkSha256: String, expectedSha256: String): Boolean {
        val a = apkSha256.replace(":", "").trim().uppercase()
        val b = expectedSha256.replace(":", "").trim().uppercase()
        return a.isNotEmpty() && b.isNotEmpty() && a == b
    }

    /** 读取 APK 内第一个签名证书的 SHA-256 指纹 */
    private fun readApkSignerSha256(context: Context, apk: File): String? {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
                ?: return null
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo ?: return null
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            signatures?.firstOrNull()?.toByteArray()?.let { sha256(it) }
        } catch (e: Exception) {
            Log.e(TAG, "读取更新包签名失败", e)
            null
        }
    }

    /**
     * 期望指纹：优先用构建期注入的常量；未注入时回退为当前安装包的签名。
     * 回退路径保证「下载被掉包」这类最常见的场景仍被拦住。
     */
    private fun expectedSignerSha256(context: Context): String {
        val configured = BuildConfig.EXPECTED_SIGNER_SHA256
        if (configured.isNotEmpty()) return configured
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val info = context.packageManager.getPackageInfo(context.packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo ?: return ""
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            signatures?.firstOrNull()?.toByteArray()?.let { sha256(it) }.orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "读取当前应用签名失败", e)
            ""
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02X".format(it) }
    }
}
