package com.qingtx.qingtiixing.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale

/**
 * 数据互通：导出 / 导入 / .ics / 自动备份。
 *
 * 关键点在于**字段名要和电脑版对齐**，否则「电脑上记、手机上也能看」就只是句口号。
 * 电脑版存盘的形状是：
 *   { app:"qingtx", version:2, reminders:[ {mode, repTime, rmode, days, ...} ] }
 * 和安卓自己的 Reminder 结构差得很远（安卓是 type + hour/minute 的扁平结构），
 * 所以两边都要做一层映射，导出时按电脑版的字段写，导入时两种都认。
 *
 * 日期一律用 UTC 写进 .ics：用户换到任何时区，显示的仍是当地的正确时刻。
 */
object Transfer {

    /* ==================== JSON 导出（电脑版可直接导入） ==================== */

    fun toDesktopJson(list: List<Reminder>): String {
        val arr = JSONArray()
        list.forEach { arr.put(toDesktopObject(it)) }
        val root = JSONObject()
            .put("app", "qingtx")
            .put("version", 2)
            .put("reminders", arr)
        return root.toString(2)
    }

    private fun toDesktopObject(r: Reminder): JSONObject {
        val o = JSONObject()
        o.put("id", r.id)
        o.put("title", r.title)
        o.put("cat", catKey(r.category))
        o.put("ring", ringKey(r.ring))
        o.put("vibrate", r.vibrate)
        o.put("enabled", r.enabled)
        o.put("doneDates", JSONArray(r.doneDates))
        o.put("mutedDates", JSONArray())
        o.put("createdAt", r.createdAt)

        when (r.type) {
            ReminderType.ONCE -> {
                /* 安卓没有「倒计时」这个独立类型，编辑器把它落成 ONCE；
                   如果时间点已经过去，说明它当初是倒计时，写 timer 让电脑版理解得更准 */
                val past = r.atMillis in 1 until System.currentTimeMillis()
                o.put("mode", if (past) "timer" else "once")
                if (past) o.put("dueAt", r.atMillis) else o.put("at", r.atMillis)
            }
            ReminderType.LOOP -> {
                o.put("mode", "loop")
                o.put("startH", r.loopStartH)
                o.put("startM", r.loopStartM)
                o.put("endH", r.loopEndH)
                o.put("endM", r.loopEndM)
                o.put("firstH", r.loopStartH)
                o.put("firstM", r.loopStartM)
                o.put("every", r.everyMin)
                o.put("days", JSONArray())
            }
            else -> {
                o.put("mode", "repeat")
                o.put("repTime", hm(r.hour, r.minute))
                when (r.type) {
                    ReminderType.DAILY -> o.put("rmode", "daily")
                    ReminderType.WEEKDAY -> o.put("rmode", "weekday")
                    ReminderType.WEEKLY -> {
                        o.put("rmode", "week")
                        /* 电脑版的 days 是 JS getDay()：0=周日 … 6=周六；
                           安卓是 ISO：1=周一 … 7=周日，这里必须转 */
                        val jsDays = r.daysOfWeek.map { if (it == 7) 0 else it }
                        o.put("days", JSONArray(jsDays.sorted()))
                    }
                    ReminderType.NDAYS -> {
                        o.put("rmode", "ndays")
                        o.put("n", r.everyDays)
                        o.put("startDate", dateKey(r.anchorMillis))
                    }
                    ReminderType.MONTHLY -> {
                        o.put("rmode", "monthly")
                        o.put("monthDay", r.dayOfMonth)
                    }
                    ReminderType.YEARLY -> {
                        o.put("rmode", "yearly")
                        o.put("yearMonth", r.monthOfYear)
                        o.put("yearDay", r.dayOfMonth)
                    }
                    else -> o.put("rmode", "daily")
                }
            }
        }
        return o
    }

    /* ==================== JSON 导入（电脑版 / 安卓自己的都认） ==================== */

    class ImportResult(val list: List<Reminder>, val skipped: Int, val source: String)

    fun fromJson(text: String): ImportResult {
        val trimmed = text.trim()
        val arr: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("reminders") ?: throw IllegalArgumentException("没有 reminders 字段")
        }

        val out = mutableListOf<Reminder>()
        var skipped = 0
        var desktopShaped = 0

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            /* 认形状：电脑版一定有 mode 字段，安卓自己的一定有 type 字段 */
            val r = when {
                o.has("mode") -> {
                    desktopShaped++
                    fromDesktopObject(o)
                }
                o.has("type") -> fromAndroidObject(o)
                else -> null
            }
            if (r == null) skipped++ else out.add(r)
        }
        if (out.isEmpty() && arr.length() > 0) throw IllegalArgumentException("没有可导入的提醒")
        return ImportResult(out, skipped, if (desktopShaped > 0) "电脑版" else "轻铃安卓版")
    }

    private fun fromDesktopObject(o: JSONObject): Reminder? {
        val title = o.optString("title", "").ifBlank { "提醒" }
        val cat = categoryOf(o.optString("cat", "other"))
        val ring = ringOf(o.optString("ring", "soft"))
        val vibrate = o.optBoolean("vibrate", true)
        val enabled = o.optBoolean("enabled", true)
        val id = o.optString("id", "").ifBlank { java.util.UUID.randomUUID().toString() }
        val done = o.optJSONArray("doneDates").toStringSet()

        val base = Reminder(
            id = id,
            title = title,
            category = cat,
            ring = ring,
            vibrate = vibrate,
            enabled = enabled,
            doneDates = done
        )

        return when (o.optString("mode")) {
            "once" -> {
                val at = o.optLong("at", 0L)
                if (at <= 0L) return null
                val c = Calendar.getInstance().apply { timeInMillis = at }
                base.copy(
                    type = ReminderType.ONCE,
                    atMillis = at,
                    hour = c.get(Calendar.HOUR_OF_DAY),
                    minute = c.get(Calendar.MINUTE)
                )
            }
            "timer" -> {
                val at = o.optLong("dueAt", 0L)
                if (at <= 0L) return null
                val c = Calendar.getInstance().apply { timeInMillis = at }
                base.copy(
                    type = ReminderType.ONCE,
                    atMillis = at,
                    hour = c.get(Calendar.HOUR_OF_DAY),
                    minute = c.get(Calendar.MINUTE)
                )
            }
            "loop" -> {
                val hms = o.optInt("startH", 9) to o.optInt("startM", 0)
                val hme = o.optInt("endH", 18) to o.optInt("endM", 0)
                val every = o.optInt("every", 45).coerceIn(1, 1440)
                if (hme.first * 60 + hme.second <= hms.first * 60 + hms.second) return null
                base.copy(
                    type = ReminderType.LOOP,
                    loopStartH = hms.first,
                    loopStartM = hms.second,
                    loopEndH = hme.first,
                    loopEndM = hme.second,
                    everyMin = every,
                    hour = hms.first,
                    minute = hms.second
                )
            }
            "repeat" -> {
                val hm = parseHm(o.optString("repTime", "09:00"))
                val common = base.copy(hour = hm.first, minute = hm.second)
                when (o.optString("rmode", "daily")) {
                    "daily" -> common.copy(type = ReminderType.DAILY)
                    "weekday" -> common.copy(type = ReminderType.WEEKDAY)
                    "week" -> {
                        val js = o.optJSONArray("days").toIntList()
                        /* 0=周日 要转成 ISO 的 7，其他 +0 之外原样（1=周一…6=周六） */
                        val iso = js.map { if (it == 0) 7 else it }.filter { it in 1..7 }.distinct().sorted()
                        if (iso.isEmpty()) return null
                        common.copy(type = ReminderType.WEEKLY, daysOfWeek = iso)
                    }
                    "ndays" -> {
                        val n = o.optInt("n", 2).coerceIn(1, 365)
                        val anchor = parseDate(o.optString("startDate", "")) ?: System.currentTimeMillis()
                        common.copy(type = ReminderType.NDAYS, everyDays = n, anchorMillis = anchor)
                    }
                    "monthly" -> common.copy(
                        type = ReminderType.MONTHLY,
                        dayOfMonth = o.optInt("monthDay", 1).coerceIn(1, 31)
                    )
                    "yearly" -> common.copy(
                        type = ReminderType.YEARLY,
                        monthOfYear = o.optInt("yearMonth", 1).coerceIn(1, 12),
                        dayOfMonth = o.optInt("yearDay", 1).coerceIn(1, 31)
                    )
                    else -> null
                }
            }
            else -> null
        }
    }

    /** 安卓自己导出的文件（理论上用户也可能拿它当备份），用 kotlinx 结构直接读 */
    private fun fromAndroidObject(o: JSONObject): Reminder? = try {
        val typeName = o.optString("type")
        val type = ReminderType.entries.firstOrNull { it.name == typeName } ?: return null
        val cat = Category.entries.firstOrNull { it.name == o.optString("category") } ?: Category.OTHER
        val ring = Ring.entries.firstOrNull { it.name == o.optString("ring") } ?: Ring.SOFT
        Reminder(
            id = o.optString("id", "").ifBlank { java.util.UUID.randomUUID().toString() },
            title = o.optString("title", "提醒").ifBlank { "提醒" },
            type = type,
            category = cat,
            enabled = o.optBoolean("enabled", true),
            hour = o.optInt("hour", 9),
            minute = o.optInt("minute", 0),
            atMillis = o.optLong("atMillis", 0L),
            daysOfWeek = o.optJSONArray("daysOfWeek").toIntList(),
            dayOfMonth = o.optInt("dayOfMonth", 1),
            monthOfYear = o.optInt("monthOfYear", 1),
            everyDays = o.optInt("everyDays", 2),
            anchorMillis = o.optLong("anchorMillis", 0L),
            loopStartH = o.optInt("loopStartH", 9),
            loopStartM = o.optInt("loopStartM", 0),
            loopEndH = o.optInt("loopEndH", 18),
            loopEndM = o.optInt("loopEndM", 0),
            everyMin = o.optInt("everyMin", 45),
            ring = ring,
            vibrate = o.optBoolean("vibrate", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            lastFiredAt = o.optLong("lastFiredAt", 0L),
            doneDates = o.optJSONArray("doneDates").toStringSet()
        )
    } catch (e: Exception) {
        null
    }

    /* ==================== .ics 导出 ==================== */

    class IcsResult(val text: String, val count: Int, val skipped: Int)

    fun toIcs(list: List<Reminder>): IcsResult {
        val stamp = utc(System.currentTimeMillis())
        val lines = mutableListOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//QingLing//Reminders//CN",
            "CALSCALE:GREGORIAN", "METHOD:PUBLISH", "X-WR-CALNAME:轻铃"
        )
        var count = 0
        var skipped = 0

        list.forEach { r ->
            if (!r.enabled) { skipped++; return@forEach }

            val start: Long
            var rrule: String? = null

            when (r.type) {
                ReminderType.ONCE -> {
                    if (r.atMillis <= 0L) { skipped++; return@forEach }
                    start = r.atMillis
                }
                ReminderType.DAILY -> {
                    start = todayAt(r.hour, r.minute); rrule = "FREQ=DAILY"
                }
                ReminderType.WEEKDAY -> {
                    start = todayAt(r.hour, r.minute); rrule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
                }
                ReminderType.WEEKLY -> {
                    if (r.daysOfWeek.isEmpty()) { skipped++; return@forEach }
                    val map = arrayOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
                    rrule = "FREQ=WEEKLY;BYDAY=" +
                        r.daysOfWeek.sorted().joinToString(",") { map[(it - 1).coerceIn(0, 6)] }
                    start = todayAt(r.hour, r.minute)
                }
                ReminderType.MONTHLY -> {
                    start = todayAt(r.hour, r.minute)
                    rrule = "FREQ=MONTHLY;BYMONTHDAY=${r.dayOfMonth.coerceIn(1, 31)}"
                }
                ReminderType.YEARLY -> {
                    start = todayAt(r.hour, r.minute)
                    rrule = "FREQ=YEARLY;BYMONTH=${r.monthOfYear.coerceIn(1, 12)};BYMONTHDAY=${r.dayOfMonth.coerceIn(1, 31)}"
                }
                ReminderType.NDAYS -> {
                    val anchor = if (r.anchorMillis > 0) r.anchorMillis else System.currentTimeMillis()
                    val n = r.everyDays.coerceAtLeast(1)
                    /* 锚点那天的同一时刻起，每 n 天一次 */
                    val c = Calendar.getInstance().apply {
                        timeInMillis = anchor
                        set(Calendar.HOUR_OF_DAY, r.hour)
                        set(Calendar.MINUTE, r.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    start = c.timeInMillis
                    rrule = "FREQ=DAILY;INTERVAL=$n"
                }
                ReminderType.LOOP -> {
                    /* 一段时间里每 N 分钟一次，一条 RRULE 表达不了；宁可跳过，
                       也不要凭空导出一条「每天」误导用户的日历 */
                    skipped++; return@forEach
                }
            }

            lines.add("BEGIN:VEVENT")
            lines.add("UID:${r.id}@qingling")
            lines.add("DTSTAMP:$stamp")
            lines.add("DTSTART:${utc(start)}")
            lines.add("DTEND:${utc(start + 30 * 60_000L)}")
            lines.add("SUMMARY:${icsEsc(r.title.ifBlank { "提醒" })}")
            lines.add("CATEGORIES:${icsEsc(r.category.label)}")
            if (rrule != null) lines.add("RRULE:$rrule")
            lines.add("BEGIN:VALARM")
            lines.add("TRIGGER:-PT0M")
            lines.add("ACTION:DISPLAY")
            lines.add("DESCRIPTION:${icsEsc(r.title.ifBlank { "提醒" })}")
            lines.add("END:VALARM")
            lines.add("END:VEVENT")
            count++
        }

        lines.add("END:VCALENDAR")
        return IcsResult(lines.joinToString("\r\n") { fold(it) } + "\r\n", count, skipped)
    }

    fun icsFileName(): String {
        val c = Calendar.getInstance()
        return String.format(
            Locale.US, "轻铃-%04d-%02d-%02d.ics",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun jsonFileName(): String {
        val c = Calendar.getInstance()
        return String.format(
            Locale.US, "轻铃备份-%04d-%02d-%02d.json",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** RFC 5545：单行 75 字节上限，续行以空格开头。按字节算，中文才不会被切坏 */
    private fun fold(line: String): String {
        val sb = StringBuilder()
        var bytes = 0
        var first = true
        for (ch in line) {
            val b = when {
                ch.code < 128 -> 1
                ch.code < 2048 -> 2
                else -> 3
            }
            if (bytes + b > 73) {
                sb.append("\r\n ")
                bytes = 1
                first = false
            }
            sb.append(ch)
            bytes += b
        }
        return sb.toString()
    }

    private fun utc(ms: Long): String {
        val c = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
        return String.format(
            Locale.US, "%04d%02d%02dT%02d%02d%02dZ",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
        )
    }

    private fun icsEsc(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    /* ==================== 自动备份 ==================== */

    private const val BACKUP_DIR = "backups"
    private const val BACKUP_KEEP = 14

    private fun backupDir(ctx: Context): File =
        File(ctx.filesDir, BACKUP_DIR).apply { mkdirs() }

    /**
     * 每天存一份，只留最近 14 天。
     *
     * 三个必须守住的规则（和电脑版一致）：
     *  · 空数据不覆盖非空备份 —— 用户误清空时，旧的这份就是救命的那份
     *  · 同一天多次触发只写一次，避免频繁落盘
     *  · 写入用临时文件再改名，半截写坏了还有上一份可用
     */
    @Synchronized
    fun autoBackup(ctx: Context, list: List<Reminder>) {
        if (list.isEmpty()) return
        val dir = backupDir(ctx)
        val today = dateKey(System.currentTimeMillis())
        val target = File(dir, "reminders-$today.json")
        if (target.exists()) return

        runCatching {
            val tmp = File(dir, "reminders-$today.json.tmp")
            tmp.writeText(toDesktopJson(list))
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
        pruneBackups(dir)
    }

    /* 两种备份：每天一份的 reminders-，以及操作前打的 manual-（带时分秒）。
       manual 必须用另一种命名，否则同一天第二次写会把当天那份覆盖掉 */
    private val BACKUP_RE = Regex("^(reminders|manual)-\\d{4}-\\d{2}-\\d{2}(-\\d{6})?\\.json$")

    private fun pruneBackups(dir: File) {
        val files = dir.listFiles { f -> BACKUP_RE.matches(f.name) } ?: return
        if (files.size <= BACKUP_KEEP) return
        /* 按修改时间排，留最近的 14 份。按文件名排会把 manual 排错位 */
        files.sortedBy { it.lastModified() }
            .take(files.size - BACKUP_KEEP)
            .forEach { runCatching { it.delete() } }
    }

    /**
     * 立刻存一份带时分秒的快照，用在「清空全部」这类不可逆操作之前。
     *
     * 不能复用 autoBackup：它一天只写一次。用户上午加了几条、下午清空，
     * 快照里就没有下午那几条，等于承诺了能找回却找不回。
     */
    @Synchronized
    fun snapshotNow(ctx: Context, list: List<Reminder>) {
        if (list.isEmpty()) return
        val dir = backupDir(ctx)
        val c = Calendar.getInstance()
        val name = String.format(
            Locale.US, "manual-%04d-%02d-%02d-%02d%02d%02d.json",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
        )
        runCatching {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(toDesktopJson(list))
            tmp.renameTo(File(dir, name))
        }
        pruneBackups(dir)
    }

    /** 备份列表，最近修改的在前 */
    fun backups(ctx: Context): List<File> =
        backupDir(ctx).listFiles { f -> BACKUP_RE.matches(f.name) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** 备份的时间，界面上显示用 */
    fun backupTime(f: File): String {
        val c = Calendar.getInstance().apply { timeInMillis = f.lastModified() }
        return String.format(
            Locale.US, "%02d-%02d %02d:%02d",
            c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)
        )
    }

    /* ==================== 杂项映射 ==================== */

    private fun catKey(c: Category): String = when (c) {
        Category.HEALTH -> "health"
        Category.WORK -> "work"
        Category.STUDY -> "study"
        Category.LIFE -> "life"
        Category.OTHER -> "other"
    }

    private fun categoryOf(k: String): Category = when (k) {
        "health" -> Category.HEALTH
        "work" -> Category.WORK
        "study" -> Category.STUDY
        "life" -> Category.LIFE
        else -> Category.OTHER
    }

    private fun ringKey(r: Ring): String = when (r) {
        Ring.CHIME -> "chime"
        Ring.SOFT -> "soft"
        Ring.NONE -> "none"
    }

    /** 电脑版有「门铃 / 闹钟」，安卓只有三档，统一收到最接近的 */
    private fun ringOf(k: String): Ring = when (k) {
        "chime" -> Ring.CHIME
        "soft" -> Ring.SOFT
        "none" -> Ring.NONE
        "alarm" -> Ring.CHIME
        else -> Ring.SOFT
    }

    private fun hm(h: Int, m: Int) = String.format(Locale.US, "%02d:%02d", h, m)

    private fun parseHm(s: String): Pair<Int, Int> {
        val p = s.split(":")
        val h = p.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val m = p.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return h to m
    }

    private fun dateKey(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun parseDate(s: String): Long? {
        if (s.isBlank()) return null
        val p = s.split("-")
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val mo = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        return runCatching {
            Calendar.getInstance().apply {
                set(y, mo - 1, d, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull()
    }

    private fun todayAt(h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optInt(it, -1).takeIf { v -> v >= 0 } }
    }

    private fun JSONArray?.toStringSet(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it, "").takeIf { v -> v.isNotBlank() } }
    }
}
