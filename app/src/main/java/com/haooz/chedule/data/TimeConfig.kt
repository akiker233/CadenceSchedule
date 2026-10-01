package com.haooz.chedule.data

/** 特殊课程内部按星期划分的子块；同一 SpecialBlock 内各子块星期区间互不重叠 */
data class SpecialItem(
    val id: Long = 0L,
    val name: String = "",
    val startDay: Int = 1,       // 1..7
    val endDay: Int = 1          // >= startDay
) {
    companion object {
        /**
         * 兜底还原：Gson 泛型丢失会把元素解析成 Map；UnsafeAllocator 使默认值不生效，
         * 即使是 SpecialItem 实例也可能字段为 null，故统一重建。
         */
        // USELESS_ELVIS：以下 ?: 编译期看似走左值，但 Gson 反序列化后字段可能是 null
        @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
        internal fun fromRaw(raw: Any?): SpecialItem? = when (raw) {
            is SpecialItem -> SpecialItem(
                id = raw.id,
                name = raw.name ?: "",
                startDay = if (raw.startDay in 1..7) raw.startDay else 1,
                endDay = if (raw.endDay in 1..7) raw.endDay else 1
            )

            is Map<*, *> -> SpecialItem(
                id = (raw["id"] as? Number)?.toLong() ?: 0L,
                name = raw["name"] as? String ?: "",
                startDay = (raw["startDay"] as? Number)?.toInt()?.takeIf { it in 1..7 } ?: 1,
                endDay = (raw["endDay"] as? Number)?.toInt()?.takeIf { it in 1..7 } ?: 1
            )

            else -> null
        }
    }
}

/** 无编号特殊时段（早读/大课间等），按起止时间定位并挤出让出空间，不计入课程提醒 */
data class SpecialBlock(
    val id: Long = 0L,
    val name: String = "",
    val startTime: String = "08:00",
    val endTime: String = "08:40",
    // 旧 JSON 缺失该字段时 Gson 置 null（默认值不生效），故可空并走 safeItems
    val items: List<SpecialItem>? = null
) {
    /**
     * 先擦成 List<*> 再逐元素还原。**不要**写 filterIsInstance<SpecialItem>：
     * 接收者已是 List<SpecialItem>，R8 可能把过滤当恒等变换消除，坏元素会漏进 UI。
     */
    val safeItems: List<SpecialItem>
        get() = (items as List<*>?).orEmpty().mapNotNull { SpecialItem.fromRaw(it) }

    companion object {
        /**
         * 兜底还原：UnsafeAllocator 使缺失字段为 null，原样返回会在非空 String 参数处 NPE。
         */
        // USELESS_ELVIS：以下 ?: 编译期看似走左值，但 Gson 反序列化后字段可能是 null
        @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
        internal fun fromRaw(raw: Any?): SpecialBlock? = when (raw) {
            is SpecialBlock -> SpecialBlock(
                id = raw.id,
                name = raw.name ?: "",
                startTime = raw.startTime ?: "08:00",
                endTime = raw.endTime ?: "08:40",
                items = (raw.items as List<*>?).orEmpty().mapNotNull { SpecialItem.fromRaw(it) }
            )

            is Map<*, *> -> SpecialBlock(
                id = (raw["id"] as? Number)?.toLong() ?: 0L,
                name = raw["name"] as? String ?: "",
                startTime = raw["startTime"] as? String ?: "08:00",
                endTime = raw["endTime"] as? String ?: "08:40",
                items = (raw["items"] as? List<*>)?.mapNotNull { SpecialItem.fromRaw(it) }
            )

            else -> null
        }
    }
}

data class TimeConfig(
    val id: Long = 0L,
    val name: String = "默认配置",

    val morningSections: Int = 4,
    val afternoonSections: Int = 4,
    val eveningSections: Int = 4,

    val quickTimeEnabled: Boolean = false,
    val classDuration: Int = 45,
    val shortBreak: Int = 10,

    val longBreakEnabled: Boolean = false,
    val longBreakMorning: Int = 20,
    val longBreakAfternoon: Int = 20,
    val longBreakEvening: Int = 20,
    val longBreakMorningSection: Int = 2,
    val longBreakAfternoonSection: Int = 2,
    val longBreakEveningSection: Int = 2,

    val morningStartHour: Int = 8,
    val morningStartMinute: Int = 0,
    val afternoonStartHour: Int = 14,
    val afternoonStartMinute: Int = 0,
    val eveningStartHour: Int = 18,
    val eveningStartMinute: Int = 30,

    // key 必须是 String：Gson 会把 Int key 序列化成 String，Map<Int,_> 反序列化会丢
    val sectionTimes: Map<String, String> = emptyMap(), // "morning_1" -> "HH:mm-HH:mm"

    val sectionNames: Map<String, String> = emptyMap(), // key 同 sectionTimes

    val specialBlocks: List<SpecialBlock> = emptyList()
) {

    /** 同 SpecialBlock.safeItems：先擦除类型再逐元素还原，兜住泛型丢失成 Map 的情况 */
    val safeSpecialBlocks: List<SpecialBlock>
        get() = (specialBlocks as List<*>?).orEmpty().mapNotNull { SpecialBlock.fromRaw(it) }

    /**
     * period: "morning" / "afternoon" / "evening"
     * 返回时段内相对节次号 (1-6) -> "HH:mm-HH:mm"
     */
    fun getPeriodTimes(period: String): Map<Int, String> {
        if (quickTimeEnabled) {
            return calculatePeriodTimes(period)
        }

        val result = mutableMapOf<Int, String>()
        for ((k, v) in sectionTimes) {
            val strKey = k.toString()
            if (strKey.startsWith("${period}_")) {
                val idx = strKey.removePrefix("${period}_").toIntOrNull()
                if (idx != null) result[idx] = v
            }
        }
        return result.ifEmpty { getDefaultTimesForPeriod(period) }
    }

    fun calculateSectionTimes(): Map<Int, String> {
        val morningTimes = calculatePeriodTimes("morning")
        val afternoonTimes = calculatePeriodTimes("afternoon")
        val eveningTimes = calculatePeriodTimes("evening")
        // 统一走合并函数：任意一段节次为 0 时不再把相对节次号直接相加造成撞键覆盖
        return mergeSectionTimes(
            morning = morningTimes,
            afternoon = afternoonTimes,
            evening = eveningTimes,
            morningCount = morningSections,
            afternoonCount = afternoonSections,
            eveningCount = eveningSections,
        )
    }

    private fun calculatePeriodTimes(period: String): Map<Int, String> {
        val sectionCount = when (period) {
            "morning" -> morningSections
            "afternoon" -> afternoonSections
            "evening" -> eveningSections
            else -> return emptyMap()
        }
        if (sectionCount <= 0) return emptyMap()

        val (startHour, startMinute) = when (period) {
            "morning" -> morningStartHour to morningStartMinute
            "afternoon" -> afternoonStartHour to afternoonStartMinute
            "evening" -> eveningStartHour to eveningStartMinute
            else -> return emptyMap()
        }

        val longBreak = when (period) {
            "morning" -> longBreakMorning
            "afternoon" -> longBreakAfternoon
            "evening" -> longBreakEvening
            else -> 0
        }
        val longBreakSection = when (period) {
            "morning" -> longBreakMorningSection
            "afternoon" -> longBreakAfternoonSection
            "evening" -> longBreakEveningSection
            else -> 2
        }

        return Course.calculatePeriodTimes(
            sectionCount = sectionCount,
            startHour = startHour,
            startMinute = startMinute,
            classDuration = classDuration,
            shortBreak = shortBreak,
            longBreak = if (longBreakEnabled) longBreak else 0,
            longBreakSection = longBreakSection
        )
    }

    companion object {
        /**
         * 把时段内相对节次号整体后移 [offset]，得到全局绝对节次号。
         *
         * [offset] 为 [NO_SECTION_OFFSET] 时返回空表：代表"该时段没有节次"，
         * **不能**用 0 代替——0 会让下午的相对第 1 节落到全局第 1 节，与上午第 1 节撞键，
         * 合并时互相覆盖，表现为作息时间静默错位。
         */
        fun shiftSectionKeys(times: Map<Int, String>, offset: Int): Map<Int, String> {
            if (offset == NO_SECTION_OFFSET || times.isEmpty()) return emptyMap()
            return times.entries.associate { (index, time) -> (offset + index) to time }
        }

        /**
         * 合并三段作息为全局绝对节次表。
         *
         * 每段只在**自身节数 > 0** 时放入，并整体后移到自己的全局区间：
         * 上午占 `1..morningCount`、下午占 `morningCount+1..`、晚上占 `morningCount+afternoonCount+1..`。
         * 因此三段在物理上不可能撞键，也就不存在"谁覆盖谁"的歧义。
         *
         * 关键点：节数为 0 的时段**整段丢弃**，而不是"偏移 0"。
         * 否则该段（或其按节数截取的默认值）会落到全局第 1 节，与上午撞键互相覆盖，
         * 表现为作息时间静默错位——这正是本函数要消除的缺陷。
         *
         * 若调用方传入的 map 自身就带越界键（例如默认表比节数长），会被区间过滤掉。
         */
        fun mergeSectionTimes(
            morning: Map<Int, String>,
            afternoon: Map<Int, String>,
            evening: Map<Int, String>,
            morningCount: Int,
            afternoonCount: Int,
            eveningCount: Int,
        ): Map<Int, String> {
            val result = LinkedHashMap<Int, String>(morning.size + afternoon.size + evening.size)
            if (morningCount > 0) {
                result.putAll(
                    shiftSectionKeys(morning, 0).filterKeys { it in 1..morningCount },
                )
            }
            if (afternoonCount > 0) {
                result.putAll(
                    shiftSectionKeys(afternoon, morningCount)
                        .filterKeys { it in (morningCount + 1)..(morningCount + afternoonCount) },
                )
            }
            if (eveningCount > 0 && morningCount > 0 && afternoonCount > 0) {
                val base = morningCount + afternoonCount
                result.putAll(
                    shiftSectionKeys(evening, base)
                        .filterKeys { it in (base + 1)..(base + eveningCount) },
                )
            }
            return result
        }

        /** [shiftSectionKeys] / [mergeSectionTimes] 的"该时段无节次"哨兵值 */
        const val NO_SECTION_OFFSET = Int.MIN_VALUE

        /**
         * 某时段的默认作息表，按**该时段实际的节数**取值。
         *
         * 原实现的兜底固定返回 4 节默认表（morning_1..4），当上午节数被设为 0 时会产出
         * "全局第 1 节 = 上午第 1 节时间"这种张冠李戴的条目，与上午默认 4 节时的合法情况
         * 无法区分。这里改成按节数截取，从源头消除该歧义。
         *
         * 注意：已知病态配置（上午 0 节 + 下午 0 节 + 晚上有课）下，课程按 `0+0+相对节次`
         * 编号，而默认表是各段自己的时间，仍无法精确对齐；此时旧行为是"该课没有时间"，
         * 新行为是"退化为该段默认时间"。两者都不理想，但后者不会出现空时间导致课程不可见，
         * 且该配置本身需要用户显式把两段都设为 0 才会出现。
         */
        fun defaultTimesFor(period: String, sectionCount: Int): Map<Int, String> {
            if (sectionCount <= 0) return emptyMap()
            val template = when (period) {
                "morning" -> Course.defaultMorningTimes
                "afternoon" -> Course.defaultAfternoonTimes
                "evening" -> Course.defaultEveningTimes
                else -> emptyMap()
            }
            return template.filterKeys { it in 1..sectionCount }
        }

        /** 仅用于识别坏快照（键名全对不上时判损坏），不参与取值 */
        private val FIELD_NAMES = setOf(
            "id", "name", "morningSections", "afternoonSections", "eveningSections",
            "quickTimeEnabled", "classDuration", "shortBreak",
            "sectionTimes", "sectionNames", "specialBlocks"
        )

        /** JSON 键名一个已知字段都不像时判为损坏，返回 null */
        fun parseSnapshotOrNull(gson: com.google.gson.Gson, json: String): TimeConfig? {
            val obj = runCatching {
                gson.fromJson(json, com.google.gson.JsonObject::class.java)
            }.getOrNull() ?: return null
            if (obj.keySet().none { it in FIELD_NAMES }) return null
            return runCatching { gson.fromJson(json, TimeConfig::class.java) }.getOrNull()
        }

        /**
         * 长期数据兜底：UnsafeAllocator 使非空字段可能为 null；三段节数全 0 视为损坏恢复 4/4/4；
         * 单段节数夹到 0..6（0 表示该时段无课）。
         */
        // USELESS_ELVIS：Gson 反序列化后非空字段仍可能是 null
        @Suppress("SENSELESS_COMPARISON", "ELVIS_ALWAYS_NULL", "USELESS_ELVIS")
        fun sanitize(id: Long, raw: TimeConfig): TimeConfig {
            val total = raw.morningSections + raw.afternoonSections + raw.eveningSections
            val (morning, afternoon, evening) = if (total <= 0) {
                Triple(DEFAULT_MORNING_SECTIONS, DEFAULT_AFTERNOON_SECTIONS, DEFAULT_EVENING_SECTIONS)
            } else {
                Triple(
                    raw.morningSections.coerceIn(0, 6),
                    raw.afternoonSections.coerceIn(0, 6),
                    raw.eveningSections.coerceIn(0, 6)
                )
            }
            return raw.copy(
                id = id,
                name = raw.name ?: "默认配置",
                morningSections = morning,
                afternoonSections = afternoon,
                eveningSections = evening,
                classDuration = if (raw.classDuration > 0) raw.classDuration else 45,
                shortBreak = if (raw.shortBreak >= 0) raw.shortBreak else 10,
                sectionTimes = raw.sectionTimes ?: emptyMap(),
                sectionNames = raw.sectionNames ?: emptyMap(),
                specialBlocks = raw.safeSpecialBlocks
            )
        }

        const val DEFAULT_MORNING_SECTIONS = 4
        const val DEFAULT_AFTERNOON_SECTIONS = 4
        const val DEFAULT_EVENING_SECTIONS = 4

        private fun getDefaultTimesForPeriod(period: String): Map<Int, String> = when (period) {
            "morning" -> Course.defaultMorningTimes
            "afternoon" -> Course.defaultAfternoonTimes
            "evening" -> Course.defaultEveningTimes
            else -> emptyMap()
        }

        fun fromRepository(repository: CourseRepository): TimeConfig {
            val sectionTimes = mutableMapOf<String, String>()
            for (period in listOf("morning", "afternoon", "evening")) {
                val times = repository.getPeriodTimes(period)
                for ((idx, time) in times) {
                    sectionTimes["${period}_$idx"] = time
                }
            }
            return TimeConfig(
                morningSections = repository.getMorningSections(),
                afternoonSections = repository.getAfternoonSections(),
                eveningSections = repository.getEveningSections(),
                quickTimeEnabled = repository.getQuickTimeEnabled(),
                classDuration = repository.getClassDuration(),
                shortBreak = repository.getShortBreak(),
                longBreakEnabled = repository.getLongBreakEnabled(),
                longBreakMorning = repository.getLongBreakMorning(),
                longBreakAfternoon = repository.getLongBreakAfternoon(),
                longBreakEvening = repository.getLongBreakEvening(),
                longBreakMorningSection = repository.getLongBreakMorningSection(),
                longBreakAfternoonSection = repository.getLongBreakAfternoonSection(),
                longBreakEveningSection = repository.getLongBreakEveningSection(),
                morningStartHour = repository.getMorningStartHour(),
                morningStartMinute = repository.getMorningStartMinute(),
                afternoonStartHour = repository.getAfternoonStartHour(),
                afternoonStartMinute = repository.getAfternoonStartMinute(),
                eveningStartHour = repository.getEveningStartHour(),
                eveningStartMinute = repository.getEveningStartMinute(),
                sectionTimes = sectionTimes
            )
        }
    }
}
