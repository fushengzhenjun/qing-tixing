package com.qingtx.qingtiixing.data

import java.util.Calendar

/**
 * 「某天有哪些提醒」。
 *
 * 月历上的圆点、周视图某一列、清单视图的列表，判断标准必须是同一套，
 * 否则同一件事会在三个地方显示不一致。所以集中在这里。
 */
object DayEvents {

    /** 日期键，yyyy-MM-dd。doneDates 用的也是这个格式。 */
    fun key(day: Calendar): String {
        val y = day.get(Calendar.YEAR)
        val m = day.get(Calendar.MONTH) + 1
        val d = day.get(Calendar.DAY_OF_MONTH)
        return "$y-" + (if (m < 10) "0$m" else "$m") + "-" + (if (d < 10) "0$d" else "$d")
    }

    fun key(millis: Long): String = key(Calendar.getInstance().apply { timeInMillis = millis })

    fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
            a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH)

    /** 1=周一 … 7=周日，和电脑版一致；Calendar 里周日是 1，要转换 */
    fun isoDay(day: Calendar): Int {
        val dow = day.get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    /** 这条提醒在某天会不会响。停用的不算——月历上不该显示已经关掉的 */
    fun hits(r: Reminder, day: Calendar): Boolean {
        if (!r.enabled) return false
        val base = Calendar.getInstance().apply {
            timeInMillis = day.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = base.timeInMillis
        val end = start + 24L * 3600_000L
        return when (r.type) {
            ReminderType.ONCE -> r.atMillis >= start && r.atMillis < end
            ReminderType.DAILY -> true
            ReminderType.WEEKDAY -> Holiday.isWorkday(base)
            ReminderType.WEEKLY -> r.daysOfWeek.contains(isoDay(base))
            ReminderType.MONTHLY -> base.get(Calendar.DAY_OF_MONTH) == r.dayOfMonth
            ReminderType.YEARLY -> base.get(Calendar.MONTH) + 1 == r.monthOfYear &&
                base.get(Calendar.DAY_OF_MONTH) == r.dayOfMonth
            ReminderType.NDAYS -> {
                val step = if (r.everyDays < 1) 1 else r.everyDays
                val anchor = Calendar.getInstance().apply {
                    timeInMillis = if (r.anchorMillis > 0) r.anchorMillis else r.createdAt
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val diff = ((start - anchor.timeInMillis) / 86_400_000L).toInt()
                diff >= 0 && diff % step == 0
            }
            ReminderType.LOOP -> true
        }
    }

    fun on(list: List<Reminder>, day: Calendar): List<Reminder> =
        list.filter { hits(it, day) }

    /** 那天的触发时刻（分钟数），排时间轴用 */
    fun minuteOf(r: Reminder, day: Calendar): Int = when (r.type) {
        ReminderType.ONCE -> {
            val c = Calendar.getInstance().apply { timeInMillis = r.atMillis }
            c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        }
        ReminderType.LOOP -> r.loopStartH * 60 + r.loopStartM
        else -> r.hour * 60 + r.minute
    }

    /** 该天是否已勾完成 */
    fun isDone(r: Reminder, day: Calendar): Boolean = r.doneDates.contains(key(day))
}
