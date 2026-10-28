package com.qingtx.qingtiixing.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qingtx.qingtiixing.data.ReminderType
import com.qingtx.qingtiixing.data.Settings
import com.qingtx.qingtiixing.data.Store

/**
 * 闹钟到点。
 *
 * 单次提醒响过就置为停用（列表里留个记录，用户看得到「已响过」而不是无声消失）；
 * 重复提醒响完立刻排下一次，这样即使 App 一直没打开，第二天照样准点。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "com.qingtx.qingtiixing.action.FIRE"
        const val ACTION_SNOOZE_FIRE = "com.qingtx.qingtiixing.action.SNOOZE_FIRE"
        /* 试发一条：只用来确认「闹钟→通知」这条链是通的，不读写任何提醒数据 */
        const val ACTION_TEST = "com.qingtx.qingtiixing.action.TEST"
        const val EXTRA_ID = "id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        /* 试发：走的是真的 AlarmManager 唤醒 + 真的通知，只是不碰用户数据。
           用户装了之后能立刻验证「到点到底会不会响」，不用等到真有事 */
        if (intent.action == ACTION_TEST) {
            Store.init(context)
            Settings.init(context)
            val now = java.util.Calendar.getInstance()
            val demo = com.qingtx.qingtiixing.data.Reminder(
                id = "test-notification",
                title = "测试提醒",
                type = ReminderType.ONCE,
                atMillis = System.currentTimeMillis(),
                hour = now.get(java.util.Calendar.HOUR_OF_DAY),
                minute = now.get(java.util.Calendar.MINUTE)
            )
            /* 测试不走暂停/免打扰判断：用户点这个按钮就是想看到效果，
               被自己的夜间免打扰挡掉反而会以为软件坏了 */
            Notifier.show(context, demo)
            return
        }

        val id = intent.getStringExtra(EXTRA_ID) ?: return
        Store.init(context)
        Settings.init(context)
        val r = Store.get(id) ?: return

        val isSnooze = intent.action == ACTION_SNOOZE_FIRE

        /* 「稍后提醒」必须放行：单次提醒响过之后已经置为停用，
           如果这里还按 enabled 拦一道，用户点了「5 分钟后」就再也不会有第二次提醒。 */
        if (!r.enabled && !isSnooze) return

        /* 全部暂停：这次不响，但规则要照常往前走。
           否则重复提醒会一直卡在同一个时间点上，恢复后连着补响一串。
           「稍后提醒」不受影响——那是用户刚主动点的。 */
        if (!isSnooze && Settings.current.pausedNow()) {
            val now = System.currentTimeMillis()
            when (r.type) {
                ReminderType.ONCE -> {
                    /* 单次提醒的时间点已经过去，没法补，只能标记为已响过 */
                    Store.upsert(r.copy(enabled = false, lastFiredAt = now))
                    Scheduler(context).cancel(r.id)
                }
                else -> {
                    val updated = r.copy(lastFiredAt = now)
                    Store.upsert(updated)
                    Scheduler(context).schedule(updated)
                }
            }
            return
        }

        /* 夜间免打扰：照常弹通知、照常记录，只是不出声不振动 */
        Notifier.show(context, r, silent = Settings.current.inDnd())

        /* 「稍后提醒」只是把通知再弹一次，不能改变提醒本身的规则 */
        if (isSnooze) return

        val now = System.currentTimeMillis()
        when (r.type) {
            ReminderType.ONCE -> {
                Store.upsert(r.copy(enabled = false, lastFiredAt = now))
                Scheduler(context).cancel(r.id)
            }
            else -> {
                val updated = r.copy(lastFiredAt = now)
                Store.upsert(updated)
                Scheduler(context).schedule(updated)
            }
        }
    }
}
