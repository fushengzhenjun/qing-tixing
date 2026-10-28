package com.qingtx.qingtiixing.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.qingtx.qingtiixing.MainActivity
import com.qingtx.qingtiixing.R
import com.qingtx.qingtiixing.data.Fmt
import com.qingtx.qingtiixing.data.Reminder
import com.qingtx.qingtiixing.data.Ring

/**
 * 通知。
 *
 * Android 8 之后「有没有声音、什么声音、振不振」是**渠道**级别的属性，
 * 代码里给单条通知设置已经无效了。所以这里按铃声建三个渠道：
 *   清脆（闹钟音，走 ALARM 音频用途，响度不受媒体音量影响）
 *   柔和（通知音）
 *   静音（无声，不振动）
 */
object Notifier {

    const val CH_CHIME = "reminders_chime"
    const val CH_SOFT = "reminders_soft"
    const val CH_SILENT = "reminders_silent"

    fun channelFor(ring: Ring): String = when (ring) {
        Ring.CHIME -> CH_CHIME
        Ring.SOFT -> CH_SOFT
        Ring.NONE -> CH_SILENT
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        fun channel(id: String, name: String, desc: String, sound: Boolean, alarmSound: Boolean, vibrate: Boolean) {
            val c = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = desc
                enableVibration(vibrate)
                enableLights(true)
                if (!sound) {
                    setSound(null, null)
                } else {
                    val uri = RingtoneManager.getDefaultUri(
                        if (alarmSound) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
                    )
                    val attrs = AudioAttributes.Builder()
                        .setUsage(if (alarmSound) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(uri, attrs)
                }
            }
            nm.createNotificationChannel(c)
        }

        channel(CH_CHIME, "提醒 · 清脆", "到点响铃，用系统闹钟音", sound = true, alarmSound = true, vibrate = true)
        channel(CH_SOFT, "提醒 · 柔和", "提示音，不打断手头的事", sound = true, alarmSound = false, vibrate = true)
        channel(CH_SILENT, "提醒 · 静音", "只弹通知，不出声不振动", sound = false, alarmSound = false, vibrate = false)
    }

    /**
     * @param silent 夜间免打扰时段内强制走静音渠道。渠道的声音是建渠道时定死的，
     *               运行时改不了，所以只能换渠道；通知本身照常弹出、照常记录。
     */
    fun show(context: Context, r: Reminder, silent: Boolean = false) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val open = PendingIntent.getActivity(
            context,
            r.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("reminderId", r.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun snoozeAction(minutes: Int): PendingIntent {
            val i = Intent(context, SnoozeReceiver::class.java).apply {
                putExtra(SnoozeReceiver.EXTRA_ID, r.id)
                putExtra(SnoozeReceiver.EXTRA_MINUTES, minutes)
            }
            return PendingIntent.getBroadcast(
                context,
                r.id.hashCode() * 31 + minutes,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val body = if (r.type == com.qingtx.qingtiixing.data.ReminderType.ONCE) Fmt.rule(r) else Fmt.rule(r)

        /* 全屏提醒页：锁屏或别的 app 开着时把提醒整个抬到前台，
           对应电脑版那个「独立小窗盖在最上层」。
           夜间免打扰时不拉全屏——半夜亮屏比响一声更烦人。 */
        val fullScreen = PendingIntent.getActivity(
            context,
            r.id.hashCode() + 7,
            Intent(context, com.qingtx.qingtiixing.AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(com.qingtx.qingtiixing.AlarmActivity.EXTRA_ID, r.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, if (silent) CH_SILENT else channelFor(r.ring))
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(r.title.ifBlank { "提醒" })
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .addAction(0, "5 分钟后", snoozeAction(5))
            .addAction(0, "10 分钟后", snoozeAction(10))

        if (!silent) {
            builder.setFullScreenIntent(fullScreen, true)
        }
        val n = builder.build()

        runCatching { nm.notify(r.id.hashCode(), n) }
    }
}
