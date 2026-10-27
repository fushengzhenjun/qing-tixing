package com.qingtx.qingtiixing.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 提醒数据存储。
 *
 * 为什么不用 Room：第一版只有一张表、数据量几十条，一个 JSON 文件足够，
 * 而且能直接和电脑版的导出格式对齐（以后做互导不用再转一次）。
 * 写在 filesDir 下，卸载才会清除，升级不会丢。
 *
 * 读写都会在后台线程之外被调用（UI 线程也会调），文件很小，
 * 用 @Synchronized + 整体读写保证不会读到半截。
 *
 * flow 是给界面用的：提醒到点后被 AlarmReceiver 改成「已停用」，
 * 界面如果只在 onResume 读一次，就会一直显示旧状态（实测过，开关还是开的）。
 */
object Store {

    private const val FILE_NAME = "reminders.json"

    private var appContext: Context? = null

    private val _flow = MutableStateFlow<List<Reminder>>(emptyList())

    /** 界面订阅它，任何写入（界面自己或闹钟回调）都会立刻反映到列表上 */
    val flow: StateFlow<List<Reminder>> = _flow

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file: File?
        get() = appContext?.let { File(it.filesDir, FILE_NAME) }

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            _flow.value = all()
        }
    }

    @Synchronized
    fun all(): MutableList<Reminder> {
        val f = file ?: return mutableListOf()
        if (!f.exists()) return mutableListOf()
        return try {
            json.decodeFromString(ListSerializer(Reminder.serializer()), f.readText()).toMutableList()
        } catch (e: Exception) {
            /* 文件坏了不能让整个应用起不来：留个备份，按空处理 */
            runCatching { f.renameTo(File(f.parentFile, "$FILE_NAME.broken")) }
            mutableListOf()
        }
    }

    @Synchronized
    fun save(list: List<Reminder>) {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(ListSerializer(Reminder.serializer()), list))
        }
        _flow.value = list.toList()
    }

    @Synchronized
    fun get(id: String): Reminder? = all().firstOrNull { it.id == id }

    @Synchronized
    fun upsert(r: Reminder) {
        val list = all()
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) list[i] = r else list.add(r)
        save(list)
    }

    @Synchronized
    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    /** 清单视图里勾一下：只改那一天的完成状态，不动提醒规则 */
    @Synchronized
    fun setDone(id: String, dateKey: String, done: Boolean) {
        val r = get(id) ?: return
        val s = r.doneDates.toMutableSet()
        if (done) s.add(dateKey) else s.remove(dateKey)
        upsert(r.copy(doneDates = s.toList()))
    }
}
