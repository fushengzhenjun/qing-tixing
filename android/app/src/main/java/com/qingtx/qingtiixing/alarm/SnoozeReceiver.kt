package com.qingtx.qingtiixing.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qingtx.qingtiixing.data.Store

/** 通知上的「5 / 10 分钟后」按钮 */
class SnoozeReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_MINUTES = "minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 5)
        Store.init(context)
        val r = Store.get(id) ?: return
        Scheduler(context).scheduleSnooze(r, minutes)
    }
}
