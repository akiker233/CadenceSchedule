package com.haooz.chedule.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验 app/build.gradle.kts 中 `EduIndexPolicy.decide` 的分支判定。
 *
 * 为什么需要这个测试：`assets/eduloader/school_index.pb` 在 .gitignore 内，
 * 全新检出时必须联网拉取；一旦拉取失败又没有可用索引，产出的 APK 首启就没有学校列表，
 * 而 [com.haooz.chedule.data.school.SchoolRepository.getSchools] 只会静默返回空列表——
 * 用户看不到任何报错。这条分支在真实构建里很难安全触发（要删掉索引 + 让网络失败），
 * 因此把判定逻辑复刻到这里做穷举验证。
 *
 * ⚠️ 本对象是 app/build.gradle.kts 中同名对象的**副本**：
 * 构建脚本无法被单元测试直接引用（它是 Gradle Kotlin DSL，需要 project 上下文），
 * 因此修改构建脚本里的 EduIndexPolicy 时，必须同步修改此处，否则本测试失去意义。
 */
private object EduIndexPolicyMirror {
    const val REUSE = "reuse"
    const val DOWNLOADED = "downloaded"
    const val NO_FALLBACK = "no-fallback"

    fun isUsable(length: Long, minValidBytes: Long): Boolean = length >= minValidBytes

    fun decide(
        existingBytes: Long,
        minValidBytes: Long,
        downloadSucceeded: Boolean,
        resultBytes: Long,
    ): String = when {
        isUsable(existingBytes, minValidBytes) -> REUSE
        downloadSucceeded && isUsable(resultBytes, minValidBytes) -> DOWNLOADED
        else -> NO_FALLBACK
    }
}

class EduIndexPolicyTest {

    private val min = 16L * 1024
    private val big = 20L * 1024
    private val small = 1024L
    private val okSize = 30L * 1024

    /** 已有合法索引：直接复用，不发起网络请求 */
    @Test
    fun usableExistingIndex_isReused() {
        assertEquals(
            EduIndexPolicyMirror.REUSE,
            EduIndexPolicyMirror.decide(big, min, downloadSucceeded = false, resultBytes = big),
        )
    }

    /** 无索引且下载成功：写入新索引 */
    @Test
    fun missingIndex_downloadSuccess_producesDownloaded() {
        assertEquals(
            EduIndexPolicyMirror.DOWNLOADED,
            EduIndexPolicyMirror.decide(0, min, downloadSucceeded = true, resultBytes = okSize),
        )
    }

    /** 无索引且下载失败：必须 NO_FALLBACK，让构建失败而不是发布无索引的 APK */
    @Test
    fun missingIndex_downloadFailure_failsBuild() {
        assertEquals(
            EduIndexPolicyMirror.NO_FALLBACK,
            EduIndexPolicyMirror.decide(0, min, downloadSucceeded = false, resultBytes = 0),
        )
    }

    /**
     * 现有索引未达可用下限（半截文件或残留的防盗链 HTML）且下载失败：
     * 必须让构建失败——这种"非空但不可用"的文件正是最容易被误当成可用索引的情形。
     */
    @Test
    fun undersizedExistingIndex_downloadFailure_failsBuild() {
        assertEquals(
            EduIndexPolicyMirror.NO_FALLBACK,
            EduIndexPolicyMirror.decide(small, min, downloadSucceeded = false, resultBytes = small),
        )
    }

    /** 下载"成功"但结果过小：半截响应不可用，必须让构建失败 */
    @Test
    fun downloadSuccessWithUndersizedResult_failsBuild() {
        assertEquals(
            EduIndexPolicyMirror.NO_FALLBACK,
            EduIndexPolicyMirror.decide(0, min, downloadSucceeded = true, resultBytes = small),
        )
    }

    /** 边界：恰好达到下限即视为可用 */
    @Test
    fun exactlyMinBytes_isUsable() {
        assertTrue(EduIndexPolicyMirror.isUsable(min, min))
        assertEquals(
            EduIndexPolicyMirror.REUSE,
            EduIndexPolicyMirror.decide(min, min, downloadSucceeded = false, resultBytes = min),
        )
    }

    /** 边界：比下限少 1 字节不可用 */
    @Test
    fun oneByteBelowMin_isNotUsable() {
        assertFalse(EduIndexPolicyMirror.isUsable(min - 1, min))
    }
}
