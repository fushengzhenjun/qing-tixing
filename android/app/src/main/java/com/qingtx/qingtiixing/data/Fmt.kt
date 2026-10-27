package com.qingtx.qingtiixing.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 文案格式化：列表副标题、通知正文都用这一套，保证措辞一致。 */
object Fmt {

    private val weekCn = arrayOf("一", "二", "三", "四", "五", "六", "日")

    fun hm(hour: Int, minute: Int): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    /** 这条提醒的重复规则，例如「每天 09:00」「每周一、三 08:30」 */
    fun rule(r: Reminder): String = when (r.type) {
        ReminderType.ONCE -> dateTime(r.atMillis)
        ReminderType.DAILY -> "每天 ${hm(r.hour, r.minute)}"
        ReminderType.WEEKDAY -> "工作日 ${hm(r.hour, r.minute)}"
        ReminderType.WEEKLY -> {
            val days = r.daysOfWeek.sorted().joinToString("、") { weekCn[(it - 1).coerceIn(0, 6)] }
            if (days.isEmpty()) "每周 ${hm(r.hour, r.minute)}" else "每周$days ${hm(r.hour, r.minute)}"
        }
    }

    /** 相对日期 + 时刻，例如「今天 15:00」「明天 09:00」「11月2日 08:00」 */
    fun whenText(millis: Long): String {
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val days = ((target.timeInMillis - today.timeInMillis) / 86_400_000L).toInt()
        val time = hm(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE))
        return when {
            days == 0 -> "今天 $time"
            days == 1 -> "明天 $time"
            days == 2 -> "后天 $time"
            else -> "${target.get(Calendar.MONTH) + 1}月${target.get(Calendar.DAY_OF_MONTH)}日 $time"
        }
    }

    fun dateTime(millis: Long): String {
        if (millis <= 0) return "未设置时间"
        val fmt = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
        return fmt.format(Date(millis))
    }

    fun dayLabel(isoDay: Int): String = "周" + weekCn[(isoDay - 1).coerceIn(0, 6)]

    /** 列表排序：启用的按下次触发时间在前，停用的沉底 */
    fun sortedForDisplay(list: List<Reminder>): List<Reminder> {
        val now = System.currentTimeMillis()
        return list.sortedWith(
            compareBy(
                { !it.enabled },
                { it.enabled.let { e -> if (e) NextFire.next(it, now) ?: Long.MAX_VALUE else Long.MAX_VALUE } }
            )
        )
    }
}
