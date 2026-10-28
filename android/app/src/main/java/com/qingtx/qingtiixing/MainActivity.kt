package com.qingtx.qingtiixing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qingtx.qingtiixing.alarm.Scheduler
import com.qingtx.qingtiixing.data.Reminder
import com.qingtx.qingtiixing.data.Settings as AppPrefs
import com.qingtx.qingtiixing.data.Store
import com.qingtx.qingtiixing.data.ThemeMode
import com.qingtx.qingtiixing.data.Transfer
import com.qingtx.qingtiixing.ui.EditorScreen
import com.qingtx.qingtiixing.ui.HomeScreen
import com.qingtx.qingtiixing.ui.QtxTheme
import com.qingtx.qingtiixing.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppPrefs.init(this)
        setContent {
            val prefs by AppPrefs.flow.collectAsState()
            val dark = when (prefs.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            QtxTheme(dark = dark) {
                AppRoot()
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Editor(val id: String?) : Screen
    data object Settings : Screen
}

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val versionName = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    /* 提醒列表直接订阅 Store 的 flow：闹钟响过之后 Receiver 会把单次提醒改成停用，
       界面不用等 onResume 就能立刻看到状态变化 */
    val list by Store.flow.collectAsState()
    val prefs by AppPrefs.flow.collectAsState()

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var notifGranted by remember { mutableStateOf(hasNotif(ctx)) }
    var exactOk by remember { mutableStateOf(Scheduler(ctx).canExact()) }
    var batteryOk by remember { mutableStateOf(isBatteryFree(ctx)) }
    var backupFiles by remember { mutableStateOf(Transfer.backups(ctx)) }

    fun refresh() {
        notifGranted = hasNotif(ctx)
        exactOk = Scheduler(ctx).canExact()
        batteryOk = isBatteryFree(ctx)
        /* 每天第一次进应用时存一份：只在文件不存在时写，所以频繁调用也不费事 */
        Transfer.autoBackup(ctx, Store.all())
        backupFiles = Transfer.backups(ctx)
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    /* 首次进入就问通知权限：不问的话，用户到点收不到任何东西还以为软件坏了 */
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /* 从系统设置页返回时刷新权限状态 */
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    when (val s = screen) {
        is Screen.Home -> {
            val warn = when {
                /* 暂停是用户自己按下的，优先级高于权限提醒——不然他会以为软件坏了 */
                prefs.pausedNow() -> "全部提醒已暂停，到点不会响 → 点这里恢复"
                !notifGranted -> "通知权限没开，到点了不会提醒你 → 点这里开启"
                !exactOk -> "精确闹钟未允许，提醒可能延迟 → 点这里允许"
                !batteryOk -> "系统可能冻结后台，提醒会不准 → 点这里设置"
                else -> null
            }
            HomeScreen(
                items = list,
                versionName = versionName,
                warnText = warn,
                appBgUri = prefs.appBgUri,
                onAdd = { screen = Screen.Editor(null) },
                onEdit = { id -> screen = Screen.Editor(id) },
                onToggle = { r: Reminder ->
                    val updated = r.copy(enabled = !r.enabled)
                    Store.upsert(updated)
                    Scheduler(ctx).schedule(updated)
                    refresh()
                },
                onSetDone = { id, key, done ->
                    Store.setDone(id, key, done)
                    /* 单次提醒勾了完成就没有"下一次"了，直接停掉并取消闹钟；
                       重复提醒不停用，交给 AlarmReceiver 判断当天不再响 */
                    if (done) {
                        val r = Store.get(id)
                        if (r != null && r.type == com.qingtx.qingtiixing.data.ReminderType.ONCE) {
                            Store.upsert(r.copy(enabled = false))
                            Scheduler(ctx).cancel(r.id)
                        }
                    }
                },
                onSettings = { screen = Screen.Settings },
                onWarnClick = { screen = Screen.Settings }
            )
        }

        is Screen.Editor -> {
            val existing = s.id?.let { id -> list.firstOrNull { it.id == id } }
            EditorScreen(
                existing = existing,
                onBack = { screen = Screen.Home },
                onSave = { r ->
                    Store.upsert(r)
                    Scheduler(ctx).schedule(r)
                    refresh()
                    screen = Screen.Home
                },
                onDelete = { id ->
                    Store.remove(id)
                    Scheduler(ctx).cancel(id)
                    refresh()
                    screen = Screen.Home
                }
            )
        }

        is Screen.Settings -> SettingsScreen(
            versionName = versionName,
            notifGranted = notifGranted,
            exactOk = exactOk,
            batteryOk = batteryOk,
            prefs = prefs,
            onRequestNotif = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppNotificationSettings(ctx)
                }
            },
            onRequestExact = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                }
            },
            onRequestBattery = { requestBatteryExemption(ctx) },
            onPauseToggle = { on ->
                /* 打开时先给个默认值：暂停 1 小时。用户可以在下面几档里改 */
                AppPrefs.setPauseAll(on, if (on) AppPrefs.untilFrom(60) else 0L)
            },
            onPauseDuration = { minutes -> AppPrefs.setPauseAll(true, AppPrefs.untilFrom(minutes)) },
            onDndToggle = { AppPrefs.setDndOn(it) },
            onDndRange = { start, end -> AppPrefs.setDndRange(start, end) },
            onTheme = { AppPrefs.setTheme(it) },
            onHolidayAware = { AppPrefs.setHolidayAware(it) },
            onFullScreenAlarm = { AppPrefs.setFullScreenAlarm(it) },
            onPickAppBg = { AppPrefs.setAppBg(it) },
            onClearAppBg = { AppPrefs.setAppBg("") },
            onPickAlarmBg = { AppPrefs.setAlarmBg(it) },
            onClearAlarmBg = { AppPrefs.setAlarmBg("") },
            itemCount = list.size,
            backupCount = backupFiles.size,
            backupLatest = backupFiles.firstOrNull()?.let { Transfer.backupTime(it) } ?: "",
            onExportJson = { uri -> exportText(ctx, uri, Transfer.toDesktopJson(list), "已导出 ${list.size} 条") },
            onExportIcs = { uri ->
                val r = Transfer.toIcs(list)
                if (r.count == 0) {
                    "没有可导出的提醒"
                } else {
                    val tail = if (r.skipped > 0) "（${r.skipped} 条跳过）" else ""
                    exportText(ctx, uri, r.text, "已导出 ${r.count} 条$tail")
                }
            },
            onImportFile = { uri ->
                val text = readText(ctx, uri)
                if (text == null) {
                    "读不到这个文件"
                } else {
                    runCatching {
                        val res = Transfer.fromJson(text)
                        replaceAll(ctx, res.list)
                        val tail = if (res.skipped > 0) "，跳过 ${res.skipped} 条" else ""
                        "已从${res.source}导入 ${res.list.size} 条$tail"
                    }.getOrElse { "文件格式不对，没有改动" }
                }
            },
            onRestoreLatest = {
                val latest = backupFiles.firstOrNull()
                if (latest == null) {
                    "还没有备份"
                } else {
                    runCatching {
                        val res = Transfer.fromJson(latest.readText())
                        replaceAll(ctx, res.list)
                        "已恢复到 ${Transfer.backupTime(latest)} 的 ${res.list.size} 条"
                    }.getOrElse { "备份文件读不出来" }
                }
            },
            onClearAll = {
                if (list.isEmpty()) {
                    "本来就没有提醒"
                } else {
                    /* 清空前打一份带时分秒的快照：autoBackup 一天只写一次，
                       用户上午加了几条、下午清空，那份快照里就没有下午的 */
                    Transfer.snapshotNow(ctx, list)
                    val n = list.size
                    runCatching { Store.save(emptyList()) }
                    list.forEach { Scheduler(ctx).cancel(it.id) }
                    backupFiles = Transfer.backups(ctx)
                    "已清空 $n 条，可在「从备份恢复」里找回"
                }
            },
            onTestFire = {
                Scheduler(ctx).scheduleTest(10)
                "10 秒后会响一条测试提醒，可以切到桌面或锁屏看看效果"
            },
            onBackupNow = {
                if (list.isEmpty()) {
                    "还没有提醒，没什么可备份的"
                } else {
                    Transfer.snapshotNow(ctx, list)
                    backupFiles = Transfer.backups(ctx)
                    backupFiles.firstOrNull()?.let { "已备份 ${list.size} 条 · ${Transfer.backupTime(it)}" }
                        ?: "备份没写成功，检查一下存储空间"
                }
            },
            onOpenNotifSettings = { openAppNotificationSettings(ctx) },
            onCopyDiagnostics = {
                val text = "轻铃 v$versionName\n" +
                    "Android ${Build.VERSION.RELEASE}（SDK ${Build.VERSION.SDK_INT}）\n" +
                    "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "提醒 ${list.size} 条 · 备份 ${backupFiles.size} 份"
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                val ok = cm != null && runCatching {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("轻铃反馈信息", text))
                }.isSuccess
                if (ok) "已复制，可以粘到反馈里" else "复制失败，这台设备不让写剪贴板"
            },
            onBack = { screen = Screen.Home }
        )
    }
}

/** 写文件到用户选的位置。返回给设置页显示的一句话。 */
private fun exportText(ctx: android.content.Context, uri: Uri, text: String, okMsg: String): String =
    runCatching {
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: return@runCatching "打不开这个位置"
        okMsg
    }.getOrElse { "保存失败，换个位置再试" }

private fun readText(ctx: android.content.Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
}.getOrNull()

/**
 * 整批替换。
 *
 * 必须先 cancel 旧的闹钟再写新数据：PendingIntent 是按提醒 id 注册的，
 * 被删掉的那些如果不清掉，会一直挂在系统闹钟表里，到点还响一次「幽灵提醒」。
 */
private fun replaceAll(ctx: android.content.Context, newList: List<Reminder>) {
    val sched = Scheduler(ctx)
    Store.all().forEach { sched.cancel(it.id) }
    Store.save(newList)
    sched.rescheduleAll()
}

private fun hasNotif(ctx: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun isBatteryFree(ctx: android.content.Context): Boolean {
    val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

private fun requestBatteryExemption(ctx: android.content.Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${ctx.packageName}")
    )
    /* 有的 ROM 没有这个页面，退回到电池优化列表，让用户自己找 */
    val ok = runCatching { ctx.startActivity(direct) }.isSuccess
    if (!ok) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

private fun openAppNotificationSettings(ctx: android.content.Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        )
    }
}
