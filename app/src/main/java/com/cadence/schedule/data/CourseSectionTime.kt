package com.cadence.schedule.data

/**
 * 节次时间区间字符串（"HH:mm-HH:mm"）与单个时刻之间的解析。
 *
 * 这段逻辑此前散落三处、写法各异：
 * - [Course.getEffectiveStartTime] / [Course.getEffectiveEndTime]：按第一个 "-" 切分再 trim；
 * - [CourseTimeResolver]：用 substringAfter/substringBefore 取一侧；
 * - [Course.periodIndex] 等处：内联 substringBefore("-") 再 trim。
 *
 * 三者在正常输入下结果一致，但一旦数据里带了空格或缺失分隔符，行为就会分叉——
 * 而"上课时间显示不对"正是这类分叉的典型症状。这里只做解析，不涉及仓库与 Android 依赖，
 * 因此可以纯 JVM 测试。
 */
object CourseSectionTime {

    /**
     * 取区间的开始时刻。
     *
     * 语义与旧实现一致：以**第一个** "-" 为界，两侧 trim；没有分隔符时整串即为开始时刻。
     * （不要用 `split("-")`：`"08:00-08:45-extra"` 会被切成 3 段。）
     */
    fun start(range: String?): String? {
        val raw = range ?: return null
        return raw.substringBefore("-").trim().ifEmpty { null }
    }

    /**
     * 取区间的结束时刻。
     *
     * 语义与旧实现一致：缺失分隔符时回退为整串（兼容只配了单个时刻的脏数据）。
     */
    fun end(range: String?): String? {
        val raw = range ?: return null
        return raw.substringAfter("-", raw).trim().ifEmpty { null }
    }
}
