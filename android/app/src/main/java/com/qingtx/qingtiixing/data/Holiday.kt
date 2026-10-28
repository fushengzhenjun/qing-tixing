package com.qingtx.qingtiixing.data

import java.util.Calendar

/**
 * 法定节假日 / 调休补班。
 *
 * 数据来源：国务院办公厅关于 2026 年部分节假日安排的通知（国办发明电〔2025〕7 号），
 * 与电脑版内置的是同一份表。新增年份时按同样格式往 OFF_RANGES / WORK_DAYS 里加一段即可，
 * 判断逻辑不用改；没收录的年份退回到「周一到周五」。
 */
object Holiday {

    /** 「工作日跟随法定节假日」开关，由 Settings 同步过来 */
    var aware: Boolean = true

    private val OFF_RANGES = listOf(
        "2026-01-01" to "2026-01-03",
        "2026-02-15" to "2026-02-23",
        "2026-04-04" to "2026-04-06",
        "2026-05-01" to "2026-05-05",
        "2026-06-19" to "2026-06-21",
        "2026-09-25" to "2026-09-27",
        "2026-10-01" to "2026-10-07"
    )

    /** 周末但需要上班的日子 */
    private val WORK_DAYS = listOf(
        "2026-01-04", "2026-02-14", "2026-02-28",
        "2026-05-09", "2026-09-20", "2026-10-10"
    )

    private val off = HashSet<String>()
    private val work = HashSet<String>()
    private val years = HashSet<Int>()

    init {
        WORK_DAYS.forEach { work.add(it) }
        OFF_RANGES.forEach { (s, e) ->
            var cur = parse(s)
            val end = parse(e)
            while (cur.timeInMillis <= end.timeInMillis) {
                off.add(DayEvents.key(cur))
                val next = Calendar.getInstance().apply { timeInMillis = cur.timeInMillis }
                next.add(Calendar.DAY_OF_YEAR, 1)
                cur = next
            }
        }
        (OFF_RANGES.map { it.first } + WORK_DAYS).forEach {
            it.substring(0, 4).toIntOrNull()?.let { y -> years.add(y) }
        }
    }

    private fun parse(s: String): Calendar {
        val p = s.split("-")
        return Calendar.getInstance().apply {
            clear()
            set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        }
    }

    /** 这一年有没有收录，没收录就只能按周一到周五算 */
    fun covered(c: Calendar): Boolean = years.contains(c.get(Calendar.YEAR))

    /**
     * 这天要不要上班：调休补班 → 上班；法定假日 → 不上；其余看周一到周五。
     * 开关关掉时退化成纯周一到周五。
     */
    fun isWorkday(c: Calendar): Boolean {
        if (!aware) {
            val dow = c.get(Calendar.DAY_OF_WEEK)
            return dow != Calendar.SUNDAY && dow != Calendar.SATURDAY
        }
        val k = DayEvents.key(c)
        if (work.contains(k)) return true
        if (off.contains(k)) return false
        val dow = c.get(Calendar.DAY_OF_WEEK)
        return dow != Calendar.SUNDAY && dow != Calendar.SATURDAY
    }
}
