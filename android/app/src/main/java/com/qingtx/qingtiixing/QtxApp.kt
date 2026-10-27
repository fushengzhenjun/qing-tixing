package com.qingtx.qingtiixing

import android.app.Application
import com.qingtx.qingtiixing.alarm.Notifier
import com.qingtx.qingtiixing.alarm.Scheduler
import com.qingtx.qingtiixing.data.Store

class QtxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.createChannels(this)
        /* 每次启动补排一次：
           应用升级、用户清过数据、或某些 ROM 重启后清掉闹钟，都靠这里恢复。 */
        runCatching { Scheduler(this).rescheduleAll() }
    }
}
