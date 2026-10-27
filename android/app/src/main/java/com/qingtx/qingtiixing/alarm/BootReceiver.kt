package com.qingtx.qingtiixing.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qingtx.qingtiixing.data.Store

/**
 * 开机 / 系统改时间 / 应用被升级之后，把提醒重新排一遍。
 *
 * AlarmManager 里的闹钟在重启后会全部丢失——没有这个接收器，
 * 用户关机一次，之后所有提醒就再也不响了（而且毫无提示）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Scheduler(context).rescheduleAll()
    }
}
