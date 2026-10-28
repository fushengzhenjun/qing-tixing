package com.qingtx.qingtiixing.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.qingtx.qingtiixing.MainActivity
import com.qingtx.qingtiixing.data.NextFire
import com.qingtx.qingtiixing.data.Reminder
import com.qingtx.qingtiixing.data.Store

/**
 * 排程。
 *
 * 为什么不用 WorkManager / 前台服务计时：提醒必须「到点就响」。
 *   · WorkManager 最小周期 15 分钟，且允许批量合并，会迟到
 *   · 靠 App 内定时器：进程一被回收就停
 * 所以走 AlarmManager.setAlarmClock —— 这是系统给闹钟类应用的通道，
 * 精确、且在打盹模式（Doze）下也能准时唤醒，状态栏还会显示闹钟图标。
 */
class Scheduler(private val context: Context) {

    private val am = context.getSystemService(AlarmManager::class.java)

    /** Android 12+ 精确闹钟要单独授权；没授权就只能近似，这里如实告诉界面 */
    fun canExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true

    fun schedule(r: Reminder) {
        if (!r.enabled) { cancel(r.id); return }
        val at = NextFire.next(r, System.currentTimeMillis())
        if (at == null) { cancel(r.id); return }
        setAlarm(r.id, at, snooze = false)
    }

    fun scheduleSnooze(r: Reminder, minutes: Int) {
        setAlarm(r.id, System.currentTimeMillis() + minutes * 60_000L, snooze = true)
    }

    fun cancel(id: String) {
        runCatching { am.cancel(pending(id, snooze = false)) }
    }

    /** 试发一条，10 秒后走正常唤醒链路。返回触发时刻，界面用来倒计时。 */
    fun scheduleTest(seconds: Int = 10): Long {
        val at = System.currentTimeMillis() + seconds * 1000L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TEST
        }
        val p = PendingIntent.getBroadcast(
            context,
            990001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (canExact()) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showApp("test-notification")), p)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        } catch (e: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
        }
        return at
    }

    fun rescheduleAll() {
        Store.all().forEach { if (it.enabled) schedule(it) else cancel(it.id) }
    }

    private fun pending(id: String, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = if (snooze) AlarmReceiver.ACTION_SNOOZE_FIRE else AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showApp(id: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reminderId", id)
        }
        return PendingIntent.getActivity(
            context,
            id.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(id: String, at: Long, snooze: Boolean) {
        val p = pending(id, snooze)
        try {
            if (canExact()) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showApp(id)), p)
            } else {
                /* 没拿到精确闹钟权限时退一步：尽量准，但不保证。
                   界面会提示用户去授权。 */
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        } catch (e: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
        }
    }
}
