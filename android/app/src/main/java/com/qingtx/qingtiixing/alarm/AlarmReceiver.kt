package com.qingtx.qingtiixing.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qingtx.qingtiixing.data.ReminderType
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
        const val EXTRA_ID = "id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        Store.init(context)
        val r = Store.get(id) ?: return

        val isSnooze = intent.action == ACTION_SNOOZE_FIRE

        /* 「稍后提醒」必须放行：单次提醒响过之后已经置为停用，
           如果这里还按 enabled 拦一道，用户点了「5 分钟后」就再也不会有第二次提醒。 */
        if (!r.enabled && !isSnooze) return

        Notifier.show(context, r)

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
