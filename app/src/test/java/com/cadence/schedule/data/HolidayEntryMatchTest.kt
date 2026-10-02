package com.cadence.schedule.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 假期记录的日期匹配回归测试。
 *
 * 背景：`Entry.matches` 原先每次调用都 `LocalDate.parse` 三个字符串（还带 runCatching
 * 异常开销），而它在渲染路径上会被逐条、逐日、逐周地大量调用——一次滚动可能上千次。
 * 现已改为按实例惰性缓存解析结果，并新增接收已解析日期的重载。
 *
 * 本测试的作用有两层：
 *  1. 锁定两个重载的行为等价（重构后不能出现分叉）；
 *  2. 覆盖日期边界与脏数据，避免"看着像但差一天"的静默错误。
 */
class HolidayEntryMatchTest {

    private fun entry(
        date: String,
        endDate: String = "",
        type: Int = HolidayManager.TYPE_HOLIDAY,
        custom: Boolean = false,
    ) = HolidayManager.Entry(
        date = date,
        endDate = endDate,
        name = "测试",
        type = type,
        custom = custom,
    )

    // ---------- 单日假期 ----------

    @Test
    fun singleDayEntry_matchesItsOwnDateOnly() {
        val e = entry("2026-10-01")
        assertTrue(e.matches("2026-10-01"))
        assertFalse(e.matches("2026-09-30"))
        assertFalse(e.matches("2026-10-02"))
    }

    /** endDate 为空时视为单日，且应复用已解析的起始日而不是再解析一次 */
    @Test
    fun blankEndDate_isTreatedAsSingleDay() {
        val e = entry("2026-10-01", endDate = "")
        assertTrue(e.matches("2026-10-01"))
        assertFalse(e.matches("2026-10-02"))
    }

    // ---------- 跨日假期（含跨年）----------

    @Test
    fun rangedEntry_matchesEveryDayInRange() {
        val e = entry("2026-10-01", endDate = "2026-10-07")
        assertTrue(e.matches("2026-10-01"))
        assertTrue(e.matches("2026-10-04"))
        assertTrue(e.matches("2026-10-07"))
        assertFalse(e.matches("2026-09-30"))
        assertFalse(e.matches("2026-10-08"))
    }

    @Test
    fun crossYearRange_matchesAcrossBoundary() {
        val e = entry("2025-12-30", endDate = "2026-01-02")
        assertTrue(e.matches("2025-12-30"))
        assertTrue(e.matches("2025-12-31"))
        assertTrue(e.matches("2026-01-01"))
        assertTrue(e.matches("2026-01-02"))
        assertFalse(e.matches("2026-01-03"))
    }

    // ---------- 两个重载必须等价 ----------

    @Test
    fun stringAndLocalDateOverloads_areEquivalent() {
        val cases = listOf(
            entry("2026-10-01"),
            entry("2026-10-01", endDate = "2026-10-07"),
            entry("2025-12-30", endDate = "2026-01-02"),
        )
        val dates = listOf(
            "2026-10-01", "2026-10-04", "2026-10-08",
            "2025-12-31", "2026-01-01", "2026-01-03",
        )
        for (e in cases) {
            for (d in dates) {
                val viaString = e.matches(d)
                val viaDate = e.matches(LocalDate.parse(d))
                assertTrue(
                    "两重载结果不一致: entry=(${e.date}..${e.endDate}) date=$d " +
                        "string=$viaString date=$viaDate",
                    viaString == viaDate,
                )
            }
        }
    }

    /** 重复调用必须返回同一结果（惰性缓存不能引入"首次之后行为变化"） */
    @Test
    fun repeatedCalls_areStable() {
        val e = entry("2026-10-01", endDate = "2026-10-07")
        val first = e.matches("2026-10-04")
        repeat(50) { assertTrue(e.matches("2026-10-04") == first) }
        assertTrue(first)
    }

    // ---------- 脏数据 ----------

    @Test
    fun malformedTarget_doesNotMatchAndDoesNotThrow() {
        val e = entry("2026-10-01")
        assertFalse(e.matches("not-a-date"))
        assertFalse(e.matches(""))
        assertFalse(e.matches(null))
    }

    @Test
    fun malformedEntryDates_doNotMatchAndDoNotThrow() {
        assertFalse(entry("not-a-date").matches("2026-10-01"))
        assertFalse(entry("2026-10-01", endDate = "bad").matches("2026-10-01"))
    }

    /** 组合用：只有进行中的假期才算"当前是假期" */
    @Test
    fun rangeBoundaries_areInclusive() {
        val e = entry("2026-05-01", endDate = "2026-05-05")
        assertTrue("起始日应包含", e.matches("2026-05-01"))
        assertTrue("结束日应包含", e.matches("2026-05-05"))
        assertFalse("结束日次日应排除", e.matches("2026-05-06"))
    }
}
