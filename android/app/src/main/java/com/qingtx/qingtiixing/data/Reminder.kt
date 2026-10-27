package com.qingtx.qingtiixing.data

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

/** 提醒类型：核心四种。倒计时在编辑器里做成「从现在起 N 分钟」，落到 ONCE。 */
enum class ReminderType { ONCE, DAILY, WEEKDAY, WEEKLY }

/** 铃声档位。Android 8+ 每个通知渠道的声音是固定的，所以一种铃声对应一个渠道。 */
enum class Ring(val label: String) { CHIME("清脆"), SOFT("柔和"), NONE("静音") }

/** 分类只影响颜色和图标，为了让列表看起来和电脑版一致。 */
enum class Category(val label: String, val color: Long) {
    HEALTH("健康", 0xFF1C9E69),
    WORK("工作", 0xFF2F6FE0),
    STUDY("学习", 0xFFDF8726),
    LIFE("生活", 0xFFD3557C),
    OTHER("其他", 0xFF798087)
}

@Serializable
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val type: ReminderType = ReminderType.ONCE,
    val category: Category = Category.OTHER,
    val enabled: Boolean = true,
    /** 每天 / 工作日 / 每周 的触发时刻 */
    val hour: Int = 9,
    val minute: Int = 0,
    /** ONCE 的绝对时间（毫秒） */
    val atMillis: Long = 0L,
    /** WEEKLY 用，1=周一 … 7=周日（ISO 习惯，和电脑版一致） */
    val daysOfWeek: List<Int> = emptyList(),
    val ring: Ring = Ring.CHIME,
    val vibrate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastFiredAt: Long = 0L,
    /**
     * 已勾「完成」的日期，格式 yyyy-MM-dd。
     * 清单视图里勾一下就往这里加一条，只影响那一天，不动提醒本身的规则。
     */
    val doneDates: List<String> = emptyList()
)

/** 下一次该响的时刻；返回 null 表示这条已经不再触发。 */
object NextFire {

    fun next(r: Reminder, from: Long): Long? {
        if (!r.enabled) return null
        return when (r.type) {
            ReminderType.ONCE -> if (r.atMillis > from) r.atMillis else null
            ReminderType.DAILY -> nextAt(from, r.hour, r.minute) { true }
            ReminderType.WEEKDAY -> nextAt(from, r.hour, r.minute) { d ->
                d != Calendar.SATURDAY && d != Calendar.SUNDAY
            }
            ReminderType.WEEKLY -> {
                if (r.daysOfWeek.isEmpty()) null
                else nextAt(from, r.hour, r.minute) { d -> r.daysOfWeek.contains(isoDay(d)) }
            }
        }
    }

    /** Calendar 的周日是 1、周一是 2……这里统一成 1=周一 … 7=周日 */
    fun isoDay(calendarDay: Int): Int = if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1

    private fun nextAt(from: Long, hour: Int, minute: Int, dayOk: (Int) -> Boolean): Long? {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        /* 最多往后找 8 天，够覆盖「每周某一天」这种情况 */
        repeat(8) {
            if (dayOk(c.get(Calendar.DAY_OF_WEEK))) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
