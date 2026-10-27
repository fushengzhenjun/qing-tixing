package com.qingtx.qingtiixing

import com.qingtx.qingtiixing.data.NextFire
import com.qingtx.qingtiixing.data.Reminder
import com.qingtx.qingtiixing.data.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * 「下一次什么时候响」是整个应用最容易算错的地方：
 * 跨天、跨周、周末跳过、时间刚好等于当前时刻……一旦错了，用户就收不到提醒。
 * 这些是用例覆盖的点，纯 JVM 跑，不需要设备。
 */
class NextFireTest {

    /** 构造某个具体时刻，方便写断言 */
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance().apply {
            set(y, mo - 1, d, h, mi, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun parts(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)} " +
            "${c.get(Calendar.HOUR_OF_DAY)}:${c.get(Calendar.MINUTE)}"
    }

    /* 2026-10-26 是周一 */

    @Test
    fun once_returnsAtTimeWhenInFuture() {
        val target = at(2026, 10, 27, 15, 0)
        val r = Reminder(type = ReminderType.ONCE, atMillis = target)
        assertEquals(target, NextFire.next(r, at(2026, 10, 26, 9, 0)))
    }

    @Test
    fun once_returnsNullAfterItPassed() {
        val target = at(2026, 10, 26, 9, 0)
        val r = Reminder(type = ReminderType.ONCE, atMillis = target)
        assertNull(NextFire.next(r, at(2026, 10, 26, 9, 1)))
    }

    @Test
    fun disabled_neverFires() {
        val r = Reminder(type = ReminderType.DAILY, hour = 9, minute = 0, enabled = false)
        assertNull(NextFire.next(r, at(2026, 10, 26, 8, 0)))
    }

    @Test
    fun daily_beforeTime_today() {
        val r = Reminder(type = ReminderType.DAILY, hour = 9, minute = 0)
        assertEquals("2026-10-26 9:0", parts(NextFire.next(r, at(2026, 10, 26, 8, 0))!!))
    }

    @Test
    fun daily_afterTime_tomorrow() {
        val r = Reminder(type = ReminderType.DAILY, hour = 9, minute = 0)
        assertEquals("2026-10-27 9:0", parts(NextFire.next(r, at(2026, 10, 26, 9, 30))!!))
    }

    @Test
    fun daily_exactlyAtTime_goesToTomorrow() {
        /* 刚好等于当前时刻时要往后推一天，否则会立刻重复触发 */
        val r = Reminder(type = ReminderType.DAILY, hour = 9, minute = 0)
        assertEquals("2026-10-27 9:0", parts(NextFire.next(r, at(2026, 10, 26, 9, 0))!!))
    }

    @Test
    fun weekday_fridayEvening_jumpsToMonday() {
        val r = Reminder(type = ReminderType.WEEKDAY, hour = 9, minute = 0)
        /* 2026-10-30 是周五 */
        assertEquals("2026-11-2 9:0", parts(NextFire.next(r, at(2026, 10, 30, 18, 0))!!))
    }

    @Test
    fun weekday_saturdayMorning_skipsToMonday() {
        val r = Reminder(type = ReminderType.WEEKDAY, hour = 9, minute = 0)
        /* 2026-10-31 是周六 */
        assertEquals("2026-11-2 9:0", parts(NextFire.next(r, at(2026, 10, 31, 8, 0))!!))
    }

    @Test
    fun weekly_singleDay_rollsToNextWeek() {
        /* 只勾周三；从周五算，下一次是下周三 */
        val r = Reminder(type = ReminderType.WEEKLY, hour = 9, minute = 0, daysOfWeek = listOf(3))
        assertEquals("2026-11-4 9:0", parts(NextFire.next(r, at(2026, 10, 30, 12, 0))!!))
    }

    @Test
    fun weekly_sameDayBeforeTime_firesToday() {
        val r = Reminder(type = ReminderType.WEEKLY, hour = 9, minute = 0, daysOfWeek = listOf(1))
        assertEquals("2026-10-26 9:0", parts(NextFire.next(r, at(2026, 10, 26, 8, 0))!!))
    }

    @Test
    fun weekly_sunday_isDay7() {
        /* 周日最容易算错：Calendar 里周日是 1，我们自己统一成 7 */
        val r = Reminder(type = ReminderType.WEEKLY, hour = 20, minute = 30, daysOfWeek = listOf(7))
        assertEquals("2026-11-1 20:30", parts(NextFire.next(r, at(2026, 10, 26, 9, 0))!!))
    }

    @Test
    fun weekly_noDaySelected_returnsNull() {
        val r = Reminder(type = ReminderType.WEEKLY, hour = 9, minute = 0, daysOfWeek = emptyList())
        assertNull(NextFire.next(r, at(2026, 10, 26, 9, 0)))
    }

    @Test
    fun weekly_multipleDays_picksNearest() {
        /* 周一、周三、周五都勾，从周二算 → 周三 */
        val r = Reminder(
            type = ReminderType.WEEKLY, hour = 7, minute = 0,
            daysOfWeek = listOf(1, 3, 5)
        )
        assertEquals("2026-10-28 7:0", parts(NextFire.next(r, at(2026, 10, 27, 22, 0))!!))
    }

    @Test
    fun monthBoundary_dailyCrossesIntoNextMonth() {
        val r = Reminder(type = ReminderType.DAILY, hour = 9, minute = 0)
        assertEquals("2026-11-1 9:0", parts(NextFire.next(r, at(2026, 10, 31, 10, 0))!!))
    }
}
