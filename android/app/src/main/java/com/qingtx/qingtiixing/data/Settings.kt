package com.qingtx.qingtiixing.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/** 外观档位。默认跟随系统。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

/**
 * 全局设置快照。
 *
 * 为什么不复用 [Reminder] 的存储：提醒是「数据」（可导入导出），
 * 这里是「偏好」（跟着这台手机走），两者生命周期不同，混在一起会互相牵连。
 */
data class SettingsSnapshot(
    val pauseAll: Boolean = false,
    /** 0 表示「手动恢复」：不自动解除，避免用户以为暂停了却突然响 */
    val pauseUntil: Long = 0L,
    val dndOn: Boolean = false,
    /** 一天内的分钟数，22*60+30 表示 22:30 */
    val dndStart: Int = 22 * 60 + 30,
    val dndEnd: Int = 7 * 60,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 主界面背景图的 content:// URI，空表示没设 */
    val appBgUri: String = "",
    /** 到点提醒页的背景图，和主界面分开设 */
    val alarmBgUri: String = "",
    /** 工作日是否跟随法定节假日 */
    val holidayAware: Boolean = true
) {

    fun pausedNow(now: Long = System.currentTimeMillis()): Boolean =
        pauseAll && (pauseUntil == 0L || now < pauseUntil)

    fun inDnd(now: Calendar = Calendar.getInstance()): Boolean {
        if (!dndOn) return false
        if (dndStart == dndEnd) return false
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (dndStart < dndEnd) {
            cur >= dndStart && cur < dndEnd
        } else {
            /* 跨午夜：22:30 → 07:00 */
            cur >= dndStart || cur < dndEnd
        }
    }

    /** 设置页里那行说明文字，和电脑版保持同样的措辞 */
    fun pauseText(): String {
        if (!pauseAll) return "暂停期间不发出任何提醒"
        if (pauseUntil == 0L) return "已暂停全部提醒（手动恢复）"
        val left = pauseUntil - System.currentTimeMillis()
        if (left <= 0) return "暂停即将结束，提醒已恢复"
        val m = ((left + 59_999) / 60_000).toInt()
        val h = m / 60
        val mm = m % 60
        return if (h > 0) {
            "已暂停 · 还剩 $h 小时${if (mm > 0) " $mm 分钟" else ""}自动恢复"
        } else {
            "已暂停 · 还剩 $m 分钟自动恢复"
        }
    }

    fun dndText(): String = if (dndOn) "时段内静音、不振动，提醒仍会记录" else "时段内静音、不振动，提醒仍会记录"

    fun hhmm(minuteOfDay: Int): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        return (if (h < 10) "0$h" else "$h") + ":" + (if (m < 10) "0$m" else "$m")
    }
}

/**
 * 偏好存储。
 *
 * 用 SharedPreferences 而不是 DataStore：闹钟触发走的是 BroadcastReceiver，
 * 必须同步读，等不了协程。写入频率极低，不存在阻塞主线程的问题。
 */
object Settings {

    private const val FILE = "settings"
    private const val K_PAUSE_ALL = "pauseAll"
    private const val K_PAUSE_UNTIL = "pauseUntil"
    private const val K_DND_ON = "dndOn"
    private const val K_DND_START = "dndStart"
    private const val K_DND_END = "dndEnd"
    private const val K_THEME = "theme"
    private const val K_APP_BG = "appBgUri"
    private const val K_ALARM_BG = "alarmBgUri"
    private const val K_HOLIDAY = "holidayAware"

    private var sp: SharedPreferences? = null

    private val _flow = MutableStateFlow(SettingsSnapshot())
    val flow: StateFlow<SettingsSnapshot> = _flow.asStateFlow()

    val current: SettingsSnapshot get() = _flow.value

    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp = p
        val themeName = p.getString(K_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        _flow.value = SettingsSnapshot(
            pauseAll = p.getBoolean(K_PAUSE_ALL, false),
            pauseUntil = p.getLong(K_PAUSE_UNTIL, 0L),
            dndOn = p.getBoolean(K_DND_ON, false),
            dndStart = p.getInt(K_DND_START, 22 * 60 + 30),
            dndEnd = p.getInt(K_DND_END, 7 * 60),
            theme = runCatching { ThemeMode.valueOf(themeName) }.getOrDefault(ThemeMode.SYSTEM),
            appBgUri = p.getString(K_APP_BG, "") ?: "",
            alarmBgUri = p.getString(K_ALARM_BG, "") ?: "",
            holidayAware = p.getBoolean(K_HOLIDAY, true)
        )
        Holiday.aware = _flow.value.holidayAware
    }

    /**
     * 开/关「全部暂停」。
     * @param untilMillis 0 表示手动恢复；否则到点自动解除。
     */
    fun setPauseAll(on: Boolean, untilMillis: Long) {
        val s = _flow.value.copy(pauseAll = on, pauseUntil = if (on) untilMillis else 0L)
        _flow.value = s
        sp?.edit()?.putBoolean(K_PAUSE_ALL, s.pauseAll)?.putLong(K_PAUSE_UNTIL, s.pauseUntil)?.apply()
    }

    fun setDndOn(on: Boolean) {
        val s = _flow.value.copy(dndOn = on)
        _flow.value = s
        sp?.edit()?.putBoolean(K_DND_ON, on)?.apply()
    }

    fun setDndRange(start: Int, end: Int) {
        val s = _flow.value.copy(dndStart = start, dndEnd = end)
        _flow.value = s
        sp?.edit()?.putInt(K_DND_START, start)?.putInt(K_DND_END, end)?.apply()
    }

    fun setHolidayAware(on: Boolean) {
        val s = _flow.value.copy(holidayAware = on)
        _flow.value = s
        Holiday.aware = on
        sp?.edit()?.putBoolean(K_HOLIDAY, on)?.apply()
    }

    fun setTheme(mode: ThemeMode) {
        val s = _flow.value.copy(theme = mode)
        _flow.value = s
        sp?.edit()?.putString(K_THEME, mode.name)?.apply()
    }

    fun setAppBg(uri: String) {
        val s = _flow.value.copy(appBgUri = uri)
        _flow.value = s
        sp?.edit()?.putString(K_APP_BG, uri)?.apply()
    }

    fun setAlarmBg(uri: String) {
        val s = _flow.value.copy(alarmBgUri = uri)
        _flow.value = s
        sp?.edit()?.putString(K_ALARM_BG, uri)?.apply()
    }

    /** 供 Receiver 用的快捷判断：进程可能是新起的，先确保 init 过 */
    fun pausedNow(context: Context): Boolean {
        init(context)
        return _flow.value.pausedNow()
    }

    fun inDndNow(context: Context): Boolean {
        init(context)
        return _flow.value.inDnd()
    }

    /**
     * 暂停时长换算，和电脑版一致：
     *   60 → 1 小时；240 → 4 小时；-1 → 明早 8 点；0 → 手动恢复
     */
    fun untilFrom(minutes: Int): Long {
        if (minutes == 0) return 0L
        if (minutes < 0) {
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, 8)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= System.currentTimeMillis()) {
                c.add(Calendar.DAY_OF_YEAR, 1)
            }
            return c.timeInMillis
        }
        return System.currentTimeMillis() + minutes * 60_000L
    }
}
