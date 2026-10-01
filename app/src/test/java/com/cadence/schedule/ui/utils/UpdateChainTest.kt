package com.cadence.schedule.ui.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新通道的防降级与签名指纹比对测试。
 *
 * 背景：本应用不经应用商店分发，更新靠自带更新器完成。原实现只要能拿到更高版本号就允许下载安装，
 * 且下载后只校验 ZIP 魔数与体积、不校验签名。这两条一起意味着更新源被冒用即可把用户
 * "更新"到任意来路的包上。这些测试锁住修复后的判定逻辑。
 */
class UpdateChainTest {

    // ---------- 版本单调性（防降级） ----------

    @Test
    fun isDowngrade_detectsOlderRemote() {
        assertTrue(UpdateChecker.isDowngrade("1.5.0", "1.6.0.2-0928"))
        assertTrue(UpdateChecker.isDowngrade("1.6.0.1", "1.6.0.2-0928"))
    }

    @Test
    fun isDowngrade_falseForSameOrNewer() {
        assertFalse(UpdateChecker.isDowngrade("1.6.0.2-0928", "1.6.0.2-0928"))
        assertFalse(UpdateChecker.isDowngrade("1.6.0.3", "1.6.0.2-0928"))
        assertFalse(UpdateChecker.isDowngrade("1.7.0", "1.6.0.2-0928"))
    }

    /** 日期后缀不参与比较：同一版本不同日期构建不算降级 */
    @Test
    fun isDowngrade_ignoresDateSuffix() {
        assertFalse(UpdateChecker.isDowngrade("1.6.0.2-1001", "1.6.0.2-0928"))
        assertFalse(UpdateChecker.isDowngrade("1.6.0.2-0901", "1.6.0.2-0928"))
    }

    /** 前后缀 v/V 与 beta 段都要能正确解析 */
    @Test
    fun isDowngrade_handlesVPrefixAndBetaSegments() {
        assertTrue(UpdateChecker.isDowngrade("v1.6.0.1", "V1.6.0.2"))
        assertFalse(UpdateChecker.isDowngrade("v1.6.1", "1.6.0.2"))
    }

    // ---------- APK 签名指纹比对 ----------

    @Test
    fun signatureMatches_sameDigestInDifferentFormats() {
        val digest = "A1B2C3D4E5F60718293A4B5C6D7E8F90A1B2C3D4E5F60718293A4B5C6D7E8F90"
        assertTrue(ApkSignatureVerifier.matches(digest, digest))
        assertTrue(ApkSignatureVerifier.matches(digest.lowercase(), digest))
        assertTrue(
            ApkSignatureVerifier.matches(
                digest.chunked(2).joinToString(":"),
                digest,
            ),
        )
    }

    @Test
    fun signatureMatches_rejectsDifferentDigest() {
        val apkDigest = "A1B2C3D4E5F60718293A4B5C6D7E8F90A1B2C3D4E5F60718293A4B5C6D7E8F90"
        val otherDigest = "0F1E2D3C4B5A69788796A5B4C3D2E1F00F1E2D3C4B5A69788796A5B4C3D2E1F0"
        assertFalse(ApkSignatureVerifier.matches(apkDigest, otherDigest))
    }

    /** 空指纹必须判为不匹配（默认拒绝），不能因为"两边都空"而放行 */
    @Test
    fun signatureMatches_rejectsEmptyValues() {
        assertFalse(ApkSignatureVerifier.matches("", ""))
        assertFalse(ApkSignatureVerifier.matches("A1B2", ""))
        assertFalse(ApkSignatureVerifier.matches("", "A1B2"))
    }
}
