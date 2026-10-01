package com.cadence.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节次时间合并的回归测试。
 *
 * 背景：原实现在三处（CourseRepository.getGlobalSectionTimes / getSectionTimes、
 * TimeConfig.calculateSectionTimes、SettingsViewModel.sectionTimes）各自用
 * `put(morningCount + k, v)` 拼全局绝对节次号。当上午节数为 0 时，下午的相对第 1 节
 * 落到全局第 1 节，与上午键撞车互相覆盖，表现为作息时间静默错位。
 *
 * 现在合并语义集中在 [TimeConfig.mergeSectionTimes]，这些测试锁死其行为，
 * 避免以后有人再"顺手内联"回三处重复实现。
 */
class TimeConfigSectionTimesTest {

    private val morning = mapOf(1 to "08:00-08:45", 2 to "08:55-09:40")
    private val afternoon = mapOf(1 to "14:00-14:45", 2 to "14:55-15:40")
    private val evening = mapOf(1 to "19:00-19:45", 2 to "19:55-20:40")

    /** 常规三时段：全局编号连续，无覆盖 */
    @Test
    fun normalCounts_produceContinuousAbsoluteNumbers() {
        val merged = TimeConfig.mergeSectionTimes(morning, afternoon, evening, 4, 4, 4)

        assertEquals("08:00-08:45", merged[1])
        assertEquals("08:55-09:40", merged[2])
        assertEquals("14:00-14:45", merged[5])
        assertEquals("14:55-15:40", merged[6])
        assertEquals("19:00-19:45", merged[9])
        assertEquals("19:55-20:40", merged[10])
        assertEquals(6, merged.size)
    }

    /**
     * 上午 0 节 + 下午 4 节（0 表示该时段没有节次，不是"偏移 0"）。
     * 回归点：旧实现会让下午第 1 节占用全局第 1 节，与上午键冲突。
     */
    @Test
    fun zeroMorningCount_mapsAfternoonToGlobalOne() {
        val merged = TimeConfig.mergeSectionTimes(
            morning = emptyMap(),
            afternoon = afternoon,
            evening = emptyMap(),
            morningCount = 0,
            afternoonCount = 4,
            eveningCount = 0,
        )

        assertEquals("14:00-14:45", merged[1])
        assertEquals("14:55-15:40", merged[2])
        assertEquals(2, merged.size)
    }

    /**
     * 三时段节数全为 0：不应残留任何节次时间。
     * 回归点：旧实现会把各段的相对 1、2 全部压到全局 1、2 上，产生张冠李戴的时间。
     */
    @Test
    fun allZeroCounts_produceNoTimes() {
        val merged = TimeConfig.mergeSectionTimes(morning, afternoon, evening, 0, 0, 0)
        assertTrue("节数全为 0 时不应有任何节次时间: $merged", merged.isEmpty())
    }

    /**
     * 上午 0 节、下午与晚上都有课：下午占据全局 1..，晚上不覆盖下午。
     * 回归点：旧实现里两段都从全局 1 开始，晚上会盖掉下午。
     */
    @Test
    fun onlyAfternoonSections_areNotPollutedByEvening() {
        val merged = TimeConfig.mergeSectionTimes(
            morning = emptyMap(),
            afternoon = mapOf(1 to "13:30-14:15"),
            evening = mapOf(1 to "18:30-19:15"),
            morningCount = 0,
            afternoonCount = 1,
            eveningCount = 4,
        )

        assertEquals(1, merged.size)
        assertEquals("13:30-14:15", merged[1])
    }

    /**
     * 节数为 0 的时段整段丢弃，即使调用方塞了该段的数据。
     * 这正是"默认值按时段节数生成"之后必须保证的不变量：
     * 否则上午为 0 节时，上午的兜底默认值会落到全局第 1 节，把下午的真实配置盖掉。
     */
    @Test
    fun zeroCountPeriod_isDroppedEvenWhenDataSupplied() {
        val merged = TimeConfig.mergeSectionTimes(
            morning = mapOf(1 to "默认上午第一节"),
            afternoon = mapOf(1 to "真实下午第一节"),
            evening = emptyMap(),
            morningCount = 0,
            afternoonCount = 4,
            eveningCount = 0,
        )

        assertEquals(1, merged.size)
        assertEquals("真实下午第一节", merged[1])
    }

    /** 超出该段节数的越界键会被区间过滤掉（默认表比节数长时的兜底） */
    @Test
    fun keysOutsideSectionRange_areFilteredOut() {
        val merged = TimeConfig.mergeSectionTimes(
            morning = mapOf(1 to "第一节", 4 to "越界第四节"),
            afternoon = emptyMap(),
            evening = emptyMap(),
            morningCount = 1,
            afternoonCount = 0,
            eveningCount = 0,
        )

        assertEquals(1, merged.size)
        assertEquals("第一节", merged[1])
    }

    /** shiftSectionKeys：哨兵值表示"该时段无节次"，返回空表而非原样偏移 */
    @Test
    fun shiftSectionKeys_sentinelYieldsEmptyMap() {
        assertTrue(
            TimeConfig.shiftSectionKeys(mapOf(1 to "x"), TimeConfig.NO_SECTION_OFFSET).isEmpty(),
        )
    }

    @Test
    fun shiftSectionKeys_appliesOffset() {
        val shifted = TimeConfig.shiftSectionKeys(mapOf(1 to "a", 2 to "b"), 4)
        assertEquals("a", shifted[5])
        assertEquals("b", shifted[6])
    }
}
