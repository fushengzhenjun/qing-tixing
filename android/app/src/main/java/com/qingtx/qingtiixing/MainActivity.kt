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

    fun refresh() {
        notifGranted = hasNotif(ctx)
        exactOk = Scheduler(ctx).canExact()
        batteryOk = isBatteryFree(ctx)
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
                onSetDone = { id, key, done -> Store.setDone(id, key, done) },
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
            onPickAppBg = { AppPrefs.setAppBg(it) },
            onClearAppBg = { AppPrefs.setAppBg("") },
            onPickAlarmBg = { AppPrefs.setAlarmBg(it) },
            onClearAlarmBg = { AppPrefs.setAlarmBg("") },
            onBack = { screen = Screen.Home }
        )
    }
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
