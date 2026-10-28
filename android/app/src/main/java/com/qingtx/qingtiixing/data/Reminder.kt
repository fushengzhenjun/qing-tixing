package com.qingtx.qingtiixing.data

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

/** 提醒类型。倒计时在编辑器里做成「从现在起 N 分钟」，落到 ONCE。 */
enum class ReminderType(val label: String) {
    ONCE("单次"),
    DAILY("每天"),
    WEEKDAY("工作日"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    YEARLY("每年"),
    NDAYS("每隔 N 天"),
    LOOP("循环")
}

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
    /** 每天 / 工作日 / 每周 / 每月 / 每年 / 每隔N天 / 循环 的触发时刻 */
    val hour: Int = 9,
    val minute: Int = 0,
    /** ONCE 的绝对时间（毫秒） */
    val atMillis: Long = 0L,
    /** WEEKLY 用，1=周一 … 7=周日（ISO 习惯，和电脑版一致） */
    val daysOfWeek: List<Int> = emptyList(),
    /** MONTHLY 的几号（1-31）；YEARLY 的日（1-31） */
    val dayOfMonth: Int = 1,
    /** YEARLY 的月（1-12） */
    val monthOfYear: Int = 1,
    /** NDAYS 的间隔天数 */
    val everyDays: Int = 2,
    /** NDAYS 的起点：从这一天开始每 everyDays 天一次 */
    val anchorMillis: Long = 0L,
    /** LOOP：几点开始 */
    val loopStartH: Int = 9,
    val loopStartM: Int = 0,
    /** LOOP：几点结束 */
    val loopEndH: Int = 18,
    val loopEndM: Int = 0,
    /** LOOP：隔几分钟一次 */
    val everyMin: Int = 45,
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
            ReminderType.WEEKDAY -> nextAt(from, r.hour, r.minute) { Holiday.isWorkday(it) }
            ReminderType.WEEKLY -> {
                if (r.daysOfWeek.isEmpty()) null
                else nextAt(from, r.hour, r.minute) { r.daysOfWeek.contains(isoDay(it.get(Calendar.DAY_OF_WEEK))) }
            }
            ReminderType.MONTHLY -> nextMonthly(r, from)
            ReminderType.YEARLY -> nextYearly(r, from)
            ReminderType.NDAYS -> nextNDays(r, from)
            ReminderType.LOOP -> nextLoop(r, from)
        }
    }

    /** 每月某日。该月没有这一天（如 2 月 30 号）就跳到下一个有该日的月份。 */
    private fun nextMonthly(r: Reminder, from: Long): Long? {
        val cursor = Calendar.getInstance().apply { timeInMillis = from }
        val target = r.dayOfMonth.coerceIn(1, 31)
        repeat(24) {
            if (target <= cursor.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                val cand = Calendar.getInstance().apply {
                    clear()
                    set(
                        cursor.get(Calendar.YEAR),
                        cursor.get(Calendar.MONTH),
                        target,
                        r.hour,
                        r.minute,
                        0
                    )
                }
                if (cand.timeInMillis > from) return cand.timeInMillis
            }
            cursor.add(Calendar.MONTH, 1)
        }
        return null
    }

    /** 每年某月某日。2 月 29 日在平年跳过。 */
    private fun nextYearly(r: Reminder, from: Long): Long? {
        val cursor = Calendar.getInstance().apply { timeInMillis = from }
        val month = (r.monthOfYear - 1).coerceIn(0, 11)
        val day = r.dayOfMonth.coerceIn(1, 31)
        repeat(9) {
            val cand = Calendar.getInstance().apply {
                clear()
                set(cursor.get(Calendar.YEAR), month, 1, r.hour, r.minute, 0)
            }
            if (day <= cand.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                cand.set(Calendar.DAY_OF_MONTH, day)
                if (cand.timeInMillis > from) return cand.timeInMillis
            }
            cursor.add(Calendar.YEAR, 1)
        }
        return null
    }

    /** 每隔 N 天：以 anchor 那天为起点，往后按整数倍找。 */
    private fun nextNDays(r: Reminder, from: Long): Long? {
        val step = if (r.everyDays < 1) 1 else r.everyDays
        val anchor = Calendar.getInstance().apply {
            timeInMillis = if (r.anchorMillis > 0) r.anchorMillis else r.createdAt
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val cursor = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(step + 2) {
            val diff = ((cursor.timeInMillis - anchor.timeInMillis) / 86_400_000L).toInt()
            if (diff >= 0 && diff % step == 0) {
                val cand = Calendar.getInstance().apply {
                    timeInMillis = cursor.timeInMillis
                    set(Calendar.HOUR_OF_DAY, r.hour)
                    set(Calendar.MINUTE, r.minute)
                }
                if (cand.timeInMillis > from) return cand.timeInMillis
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    /**
     * 循环：每天从 loopStart 到 loopEnd，隔 everyMin 分钟响一次。
     * 今天的都过完了就排到明天开场。
     */
    private fun nextLoop(r: Reminder, from: Long): Long? {
        if (r.everyMin < 1) return null
        val startMin = r.loopStartH * 60 + r.loopStartM
        val endMin = r.loopEndH * 60 + r.loopEndM
        if (endMin <= startMin) return null

        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)

        val nextMin = if (nowMin < startMin) {
            startMin
        } else {
            startMin + ((nowMin - startMin) / r.everyMin + 1) * r.everyMin
        }
        if (nextMin in startMin..endMin) {
            c.set(Calendar.HOUR_OF_DAY, nextMin / 60)
            c.set(Calendar.MINUTE, nextMin % 60)
            if (c.timeInMillis > from) return c.timeInMillis
        }

        /* 今天收工了，明天开场 */
        c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, startMin / 60)
        c.set(Calendar.MINUTE, startMin % 60)
        return c.timeInMillis
    }

    /** Calendar 的周日是 1、周一是 2……这里统一成 1=周一 … 7=周日 */
    fun isoDay(calendarDay: Int): Int = if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1

    private fun nextAt(from: Long, hour: Int, minute: Int, dayOk: (Calendar) -> Boolean): Long? {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        /* 往后找 40 天：国庆连休 7 天再加上调休，8 天不够用 */
        repeat(40) {
            if (dayOk(c)) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
