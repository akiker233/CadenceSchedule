package com.cadence.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 课程「生效周次」推导与「节次时间区间」解析的回归测试。
 *
 * 两处逻辑此前都是重复实现：
 * - 生效周次：CourseRepository 里写了两份（调课/合并用一份、算已占用周用一份）；
 * - 时间区间解析：Course 与 CourseTimeResolver 各写了一套切分方式。
 *
 * 这类重复的危险不在于"当前写错"，而在于改一处漏一处后表现为
 * "提示的冲突周与实际占用的周不一致"或"上课时间显示不对"，且很难定位。
 */
class CourseWeekAndTimeTest {

    private fun course(
        startWeek: Int = 1,
        endWeek: Int = 16,
        weekType: Int = Course.WEEK_TYPE_ALL,
        selectedWeeks: List<Int> = emptyList(),
        startSection: Int = 1,
        endSection: Int = 2,
        isCustomTime: Boolean = false,
        customStartTime: String? = null,
        customEndTime: String? = null,
    ) = Course(
        id = "c1",
        name = "高等数学",
        classroom = "A101",
        teacher = "张老师",
        dayOfWeek = 1,
        startSection = startSection,
        endSection = endSection,
        startWeek = startWeek,
        endWeek = endWeek,
        weekType = weekType,
        colorRes = 0xFF2196F3,
        selectedWeeks = selectedWeeks,
        isCustomTime = isCustomTime,
        customStartTime = customStartTime,
        customEndTime = customEndTime,
    )

    // ---------- discreteWeeksIn：离散周次推导 ----------

    @Test
    fun allWeeks_returnsFullRange() {
        assertEquals((1..16).toList(), course(startWeek = 1, endWeek = 16).discreteWeeksIn())
    }

    @Test
    fun oddWeeks_keepOddNumbersOnly() {
        assertEquals(
            listOf(1, 3, 5, 7, 9, 11, 13, 15),
            course(startWeek = 1, endWeek = 16, weekType = Course.WEEK_TYPE_ODD).discreteWeeksIn(),
        )
    }

    @Test
    fun evenWeeks_keepEvenNumbersOnly() {
        assertEquals(
            listOf(2, 4, 6, 8, 10, 12, 14, 16),
            course(startWeek = 1, endWeek = 16, weekType = Course.WEEK_TYPE_EVEN).discreteWeeksIn(),
        )
    }

    /** 单周但起始周是偶数：第一个单周是 3（关键边界，易被写成"从 startWeek 开始跳步"） */
    @Test
    fun oddWeeks_startingOnEvenWeek_skipsToNextOdd() {
        assertEquals(
            listOf(3, 5, 7),
            course(startWeek = 2, endWeek = 8, weekType = Course.WEEK_TYPE_ODD).discreteWeeksIn(),
        )
    }

    /** 双周但起始周是奇数：第一个双周是 4 */
    @Test
    fun evenWeeks_startingOnOddWeek_skipsToNextEven() {
        assertEquals(
            listOf(4, 6, 8),
            course(startWeek = 3, endWeek = 8, weekType = Course.WEEK_TYPE_EVEN).discreteWeeksIn(),
        )
    }

    /** 范围内没有任何符合奇偶的周：必须返回空，而不是把 startWeek 硬塞进去 */
    @Test
    fun oddWeeks_singleEvenWeek_returnsEmpty() {
        assertEquals(
            emptyList<Int>(),
            course(startWeek = 4, endWeek = 4, weekType = Course.WEEK_TYPE_ODD).discreteWeeksIn(),
        )
    }

    /** selectedWeeks 优先于 start/end/weekType */
    @Test
    fun selectedWeeks_takePrecedenceOverRange() {
        assertEquals(
            listOf(2, 9),
            course(weekType = Course.WEEK_TYPE_ODD, selectedWeeks = listOf(2, 9)).discreteWeeksIn(),
        )
    }

    /** selectedWeeks 超出 maxWeek 的部分应被过滤（用于"总周数被调小"后的收窄） */
    @Test
    fun selectedWeeks_respectMaxWeek() {
        assertEquals(
            listOf(2, 5),
            course(selectedWeeks = listOf(2, 5, 18)).discreteWeeksIn(maxWeek = 10),
        )
    }

    @Test
    fun rangeBeyondMaxWeek_isClamped() {
        assertEquals(
            listOf(1, 2, 3),
            course(startWeek = 1, endWeek = 16).discreteWeeksIn(maxWeek = 3),
        )
    }

    /** startWeek > endWeek 的脏数据不应产生异常或越界序列 */
    @Test
    fun invertedRange_returnsEmpty() {
        assertEquals(emptyList<Int>(), course(startWeek = 9, endWeek = 4).discreteWeeksIn())
    }

    /** startWeek 为 0 或负数（旧数据）时从第 1 周开始，不产生 0 / 负数周 */
    @Test
    fun nonPositiveStartWeek_startsFromOne() {
        assertEquals(listOf(1, 2), course(startWeek = 0, endWeek = 2).discreteWeeksIn())
    }

    /** 0 周（未设置）不应产生任何周次 */
    @Test
    fun zeroEndWeek_returnsEmpty() {
        assertEquals(emptyList<Int>(), course(startWeek = 1, endWeek = 0).discreteWeeksIn())
    }

    /**
     * 关键一致性：调课路径与占用周路径必须得出同一组周次。
     * 两者已统一到 discreteWeeksIn，本测试锁定该不变量。
     */
    @Test
    fun discreteWeeks_matchOccupiedWeeksForSameCourse() {
        val cases = listOf(
            course(startWeek = 1, endWeek = 16),
            course(startWeek = 2, endWeek = 9, weekType = Course.WEEK_TYPE_ODD),
            course(startWeek = 3, endWeek = 9, weekType = Course.WEEK_TYPE_EVEN),
            course(selectedWeeks = listOf(1, 4, 7)),
            course(startWeek = 0, endWeek = 0),
        )
        cases.forEach { c ->
            val occupied = mutableSetOf<Int>()
            occupied.addAll(c.discreteWeeksIn())
            assertEquals(c.discreteWeeksIn().toSet(), occupied)
        }
    }

    // ---------- CourseSectionTime：区间字符串解析 ----------

    @Test
    fun startAndEnd_splitOnHyphen() {
        assertEquals("08:00", CourseSectionTime.start("08:00-08:45"))
        assertEquals("08:45", CourseSectionTime.end("08:00-08:45"))
    }

    @Test
    fun surroundingWhitespace_isTrimmed() {
        assertEquals("08:00", CourseSectionTime.start("  08:00 - 08:45  "))
        assertEquals("08:45", CourseSectionTime.end("  08:00 - 08:45  "))
    }

    /** 没有分隔符（脏数据）：整串既是开始也是结束，不能返回 null 让课程时间凭空消失 */
    @Test
    fun missingSeparator_bothSidesFallBackToWholeString() {
        assertEquals("08:00", CourseSectionTime.start("08:00"))
        assertEquals("08:00", CourseSectionTime.end("08:00"))
    }

    /**
     * 多余的分隔符：只按**第一个** "-" 切分。
     * 用 split("-") 会得到 3 段，取"最后一段"就会把结束时间解析成 extra。
     */
    @Test
    fun extraSeparators_onlyFirstOneSplits() {
        assertEquals("08:00", CourseSectionTime.start("08:00-08:45-extra"))
        assertEquals("08:45-extra", CourseSectionTime.end("08:00-08:45-extra"))
    }

    @Test
    fun nullAndBlank_returnNull() {
        assertNull(CourseSectionTime.start(null))
        assertNull(CourseSectionTime.end(null))
        assertNull(CourseSectionTime.start("   "))
        assertNull(CourseSectionTime.end(""))
        assertNull(CourseSectionTime.start("-"))
    }

    /** 自定义时间优先，且不经过节次表 */
    @Test
    fun customTime_takesPrecedenceOverSectionTable() {
        val c = course(
            startSection = 1,
            endSection = 2,
            isCustomTime = true,
            customStartTime = "07:30",
            customEndTime = "09:15",
        )
        val sectionTimes = mapOf(1 to "08:00-08:45", 2 to "08:55-09:40")
        assertEquals("07:30", c.getEffectiveStartTime(sectionTimes))
        assertEquals("09:15", c.getEffectiveEndTime(sectionTimes))
    }

    /** 自定义时间开关开着但起止为空：视为未配置，回退节次表 */
    @Test
    fun customTimeFlagWithoutValues_fallsBackToSectionTable() {
        val c = course(startSection = 1, endSection = 2, isCustomTime = true)
        val sectionTimes = mapOf(1 to "08:00-08:45", 2 to "08:55-09:40")
        assertEquals("08:00", c.getEffectiveStartTime(sectionTimes))
        assertEquals("09:40", c.getEffectiveEndTime(sectionTimes))
    }

    /** 节次不在表中：返回 null 而不是抛异常 */
    @Test
    fun missingSectionInTable_returnsNull() {
        val c = course(startSection = 99, endSection = 99)
        assertNull(c.getEffectiveStartTime(mapOf(1 to "08:00-08:45")))
        assertNull(c.getEffectiveEndTime(mapOf(1 to "08:00-08:45")))
    }
}
