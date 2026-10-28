package com.qingtx.qingtiixing.ui

import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingtx.qingtiixing.data.SettingsSnapshot
import com.qingtx.qingtiixing.data.ThemeMode
import kotlinx.coroutines.delay

/**
 * 设置页。
 *
 * 排布顺序按「多久会用到」来定，不按功能多少：
 *   权限状态 → 常规（暂停 / 试发）→ 提醒 → 外观 → 数据 → 清空 → 关于
 * 权限三项是提醒能不能响的前提，异常时直接展开摆在最上面；
 * 三项都正常时收成一行，不占地方。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    notifGranted: Boolean,
    exactOk: Boolean,
    batteryOk: Boolean,
    prefs: SettingsSnapshot,
    onRequestNotif: () -> Unit,
    onRequestExact: () -> Unit,
    onRequestBattery: () -> Unit,
    onPauseToggle: (Boolean) -> Unit,
    onPauseDuration: (Int) -> Unit,
    onDndToggle: (Boolean) -> Unit,
    onDndRange: (Int, Int) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onHolidayAware: (Boolean) -> Unit,
    onFullScreenAlarm: (Boolean) -> Unit,
    onPickAppBg: (String) -> Unit,
    onClearAppBg: () -> Unit,
    onPickAlarmBg: (String) -> Unit,
    onClearAlarmBg: () -> Unit,
    itemCount: Int,
    backupCount: Int,
    backupLatest: String,
    onExportJson: (android.net.Uri) -> String,
    onExportIcs: (android.net.Uri) -> String,
    onImportFile: (android.net.Uri) -> String,
    onRestoreLatest: () -> String,
    onClearAll: () -> String,
    onBackupNow: () -> String,
    onTestFire: () -> String,
    onOpenNotifSettings: () -> Unit,
    onCopyDiagnostics: () -> String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current

    /* 选图走系统的文件选择器（SAF），不需要读相册权限；
       选完必须 takePersistableUriPermission，否则重启后 URI 就失效了 */
    var bgTarget by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (bgTarget == 1) onPickAppBg(uri.toString()) else onPickAlarmBg(uri.toString())
        }
    }

    /* 结果统一走底部提示条：设置页很长，写在页面里的字很可能在屏幕外，
       用户点完「导出」看不到反馈就会以为没生效 */
    var msg by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) {
        val m = msg ?: return@LaunchedEffect
        snack.showSnackbar(m, duration = SnackbarDuration.Short)
        msg = null
    }

    /* 清空是不可逆操作，点一下直接删太容易误触，加一层确认 */
    var confirmClear by remember { mutableStateOf(false) }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) msg = onExportJson(uri) }

    val exportIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri -> if (uri != null) msg = onExportIcs(uri) }

    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) msg = onImportFile(uri) }

    /* 「还剩 X 分钟」需要自己走：SharedPreferences 里存的只是一个时间戳，
       没人通知界面时间过去了。20 秒一刷，够用且几乎不耗电。 */
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            tick += 1
        }
    }
    val pauseDesc = remember(prefs, tick) { prefs.pauseText() }

    /* 暂停档位的高亮：pauseUntil 只是个时间戳，只能按剩余时长反推用户点的是哪一档 */
    val pauseIdx = remember(prefs.pauseAll, prefs.pauseUntil, tick) {
        when {
            !prefs.pauseAll -> -1
            prefs.pauseUntil == 0L -> 3
            else -> {
                val mins = ((prefs.pauseUntil - System.currentTimeMillis()) / 60_000L).toInt()
                when {
                    mins in 50..60 -> 0
                    mins in 170..240 -> 1
                    else -> 2
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold, fontSize = 19.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            /* ---------- 权限 ---------- */
            Spacer(Modifier.height(4.dp))
            PermissionBanner(
                notifGranted = notifGranted,
                exactOk = exactOk,
                batteryOk = batteryOk,
                onRequestNotif = onRequestNotif,
                onRequestExact = onRequestExact,
                onRequestBattery = onRequestBattery
            )

            /* ---------- 常规 ---------- */
            SectionLabel("常规", top = 22.dp)

            GroupCard {
                SwitchRow(
                    title = "全部暂停",
                    desc = pauseDesc,
                    checked = prefs.pauseAll,
                    active = prefs.pauseAll,
                    onChecked = onPauseToggle
                )

                if (prefs.pauseAll) {
                    Divider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                        Text(
                            "暂停到什么时候",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(9.dp))
                        Segmented(
                            options = listOf("1 小时", "4 小时", "明早 8 点", "手动恢复"),
                            selected = pauseIdx,
                            onSelect = { onPauseDuration(when (it) { 0 -> 60; 1 -> 240; 2 -> -1; else -> 0 }) }
                        )
                    }
                }

                Divider()

                ActionRow(
                    title = "试发一条提醒",
                    desc = if (notifGranted) "10 秒后响，可以切到桌面或锁屏看效果"
                    else "先开启通知权限才发得出来",
                    action = "发送",
                    enabled = notifGranted,
                    onAction = { msg = onTestFire() }
                )
            }

            /* ---------- 提醒 ---------- */
            SectionLabel("提醒")

            GroupCard {
                SwitchRow(
                    title = "工作日跟随法定节假日",
                    desc = if (prefs.holidayAware) "放假期间不提醒，调休上班日照常提醒" else "只按周一到周五算",
                    checked = prefs.holidayAware,
                    onChecked = onHolidayAware
                )

                Divider()

                SwitchRow(
                    title = "夜间免打扰",
                    desc = if (prefs.dndOn) "时段内静音、不振动，提醒仍会记录" else "夜里到点也不吵你",
                    checked = prefs.dndOn,
                    onChecked = onDndToggle
                )

                if (prefs.dndOn) {
                    Divider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                        Text(
                            "免打扰时段",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TimePill(prefs.hhmm(prefs.dndStart)) {
                                pickTime(ctx, prefs.dndStart) { onDndRange(it, prefs.dndEnd) }
                            }
                            Text("至", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TimePill(prefs.hhmm(prefs.dndEnd)) {
                                pickTime(ctx, prefs.dndEnd) { onDndRange(prefs.dndStart, it) }
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(
                            if (prefs.dndStart == prefs.dndEnd) {
                                "开始和结束时间相同，等于没开"
                            } else {
                                "提醒照常弹出、照常记进列表，只是不出声不振动"
                            },
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider()

                SwitchRow(
                    title = "到点亮屏弹出",
                    desc = if (prefs.fullScreenAlarm) "锁屏或别的应用开着也能盖上来" else "只在通知栏提示，不自动弹页面",
                    checked = prefs.fullScreenAlarm,
                    onChecked = onFullScreenAlarm
                )

                Divider()

                ActionRow(
                    title = "铃声与振动",
                    desc = "由系统通知渠道控制，可给三种提醒分别调",
                    action = "打开",
                    onAction = onOpenNotifSettings
                )
            }

            /* ---------- 外观 ---------- */
            SectionLabel("外观")

            GroupCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Text("主题", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "深色更适合夜里看",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    val modes = ThemeMode.values()
                    Segmented(
                        options = modes.map { it.label },
                        selected = modes.indexOf(prefs.theme),
                        onSelect = { onTheme(modes[it]) }
                    )
                }

                Divider()

                BgRow(
                    title = "主界面背景",
                    desc = if (prefs.appBgUri.isBlank()) "选一张图铺在首页后面" else "已设置 · 文字会自动加深色底",
                    has = prefs.appBgUri.isNotBlank(),
                    onPick = { bgTarget = 1; picker.launch(arrayOf("image/*")) },
                    onClear = { onClearAppBg(); msg = "已移除主界面背景" }
                )

                Divider()

                BgRow(
                    title = "提醒页背景",
                    desc = if (prefs.alarmBgUri.isBlank()) "给到点弹窗单独换一张图" else "已设置 · 只影响提醒页",
                    has = prefs.alarmBgUri.isNotBlank(),
                    onPick = { bgTarget = 2; picker.launch(arrayOf("image/*")) },
                    onClear = { onClearAlarmBg(); msg = "已移除提醒页背景" }
                )
            }

            /* ---------- 数据 ---------- */
            SectionLabel("数据")

            GroupCard {
                ActionRow(
                    title = "自动备份",
                    desc = if (backupCount == 0) "每天自动存一份，现在还没有"
                    else "最近 $backupLatest · 共 $backupCount 份，只留 14 天",
                    action = "立即备份",
                    enabled = itemCount > 0,
                    onAction = { msg = onBackupNow() }
                )

                Divider()

                ActionRow(
                    title = "从备份恢复",
                    desc = if (backupCount == 0) "还没有备份，先加几条提醒" else "用最近那份覆盖当前 $itemCount 条",
                    action = "恢复",
                    enabled = backupCount > 0,
                    onAction = { msg = onRestoreLatest() }
                )

                Divider()

                ActionRow(
                    title = "导出数据文件",
                    desc = "存成 JSON，电脑版能直接导入，换手机也靠它",
                    action = "导出",
                    enabled = itemCount > 0,
                    onAction = { exportJson.launch(com.qingtx.qingtiixing.data.Transfer.jsonFileName()) }
                )

                Divider()

                ActionRow(
                    title = "导入数据文件",
                    desc = "会覆盖当前 $itemCount 条，两种格式都认",
                    action = "导入",
                    onAction = { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) }
                )

                Divider()

                ActionRow(
                    title = "导出日历",
                    desc = "存成 .ics，导入手机自带日历后两边都能看到",
                    action = "导出",
                    enabled = itemCount > 0,
                    onAction = { exportIcs.launch(com.qingtx.qingtiixing.data.Transfer.icsFileName()) }
                )
            }

            /* ---------- 危险操作：单独一组，避免和上面的正常操作挨着 ---------- */
            SectionLabel("清空")

            GroupCard {
                ActionRow(
                    title = "清空全部提醒",
                    desc = if (itemCount == 0) "本来就没有提醒"
                    else "删掉这 $itemCount 条，清空前会自动存一份备份",
                    action = "清空",
                    danger = true,
                    enabled = itemCount > 0,
                    onAction = { confirmClear = true }
                )
            }

            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("清空全部提醒？") },
                    text = {
                        Text(
                            "会删掉这 $itemCount 条提醒，闹钟一并取消。\n" +
                                "清空前会自动存一份备份，之后能在「从备份恢复」里找回。"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmClear = false
                            msg = onClearAll()
                        }) {
                            Text("清空", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) { Text("取消") }
                    }
                )
            }

            /* ---------- 关于 ---------- */
            SectionLabel("关于")

            AboutCard(
                versionName = versionName,
                onCopyDiagnostics = { msg = onCopyDiagnostics() }
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

/* ==================== 权限 ==================== */

/**
 * 权限条。
 *
 * 三项全好时只占一行；缺项时自动展开，并且把「去开启」摆在最显眼的位置。
 * 全部正常也可以点开复查——用户从系统设置里关掉某一项之后，回来还能自己确认。
 */
@Composable
private fun PermissionBanner(
    notifGranted: Boolean,
    exactOk: Boolean,
    batteryOk: Boolean,
    onRequestNotif: () -> Unit,
    onRequestExact: () -> Unit,
    onRequestBattery: () -> Unit
) {
    val allOk = notifGranted && exactOk && batteryOk
    var open by remember(allOk) { mutableStateOf(!allOk) }

    val missing = listOf(notifGranted, exactOk, batteryOk).count { !it }
    val container = if (allOk) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    val border = if (allOk) MaterialTheme.colorScheme.outlineVariant
    else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, border),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(allOk)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (allOk) "提醒已就绪" else "$missing 项没设置好，到点可能不响",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (allOk) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "通知 · 精确闹钟 · 电池优化",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Chevron(open)
            }

            if (open) {
                Divider()
                PermLine(
                    title = "通知权限",
                    hint = if (notifGranted) "已开启，到点会弹通知" else "没开的话，到点了什么都不会发生",
                    ok = notifGranted,
                    actionText = "去开启",
                    onAction = onRequestNotif
                )
                PermLine(
                    title = "精确闹钟",
                    hint = if (exactOk) "已允许，可以准点响" else "未允许，系统可能推迟几分钟到几十分钟",
                    ok = exactOk,
                    actionText = "去允许",
                    onAction = onRequestExact
                )
                PermLine(
                    title = "电池优化",
                    hint = if (batteryOk) "已排除，后台不会被冻结" else "仍受限制，长时间不用可能不响",
                    ok = batteryOk,
                    actionText = "去设置",
                    onAction = onRequestBattery
                )
            }
        }
    }
}

@Composable
private fun PermLine(
    title: String,
    hint: String,
    ok: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                hint,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!ok) {
            Spacer(Modifier.width(8.dp))
            TextAction(actionText, onAction)
        }
    }
}

/* ==================== 关于 ==================== */

@Composable
private fun AboutCard(versionName: String, onCopyDiagnostics: () -> Unit) {
    var romOpen by remember { mutableStateOf(false) }

    GroupCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("轻铃", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "v$versionName",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "简洁、无广告的定时提醒。\n" +
                    "不联网、不收集任何数据，提醒只存在这台手机里。\n" +
                    "卸载会一并删除，重要的事建议另记一份。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Divider()

        ActionRow(
            title = "复制反馈信息",
            desc = "版本、系统与提醒条数，反馈时一起贴上",
            action = "复制",
            onAction = onCopyDiagnostics
        )

        Divider()

        ActionRow(
            title = "国产手机省电设置",
            desc = "小米 / 华为 / OPPO / vivo 的额外开关",
            action = if (romOpen) "收起" else "展开",
            onAction = { romOpen = !romOpen }
        )

        if (romOpen) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(
                    "这些系统会额外「优化」后台应用。除了上面的电池优化，还要在系统设置里给轻铃打开：\n" +
                        "· 自启动 / 允许后台活动\n" +
                        "· 通知管理里的「横幅通知」\n" +
                        "· 最近任务里给轻铃「加锁」，避免一键清理时被杀掉\n" +
                        "· 省电模式里把轻铃设为「不限制」",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ==================== 基础组件 ==================== */

/** 用系统的时间选择器，比自己在 Compose 里搭一个稳，也更符合用户习惯 */
private fun pickTime(ctx: android.content.Context, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        ctx,
        { _, h, m -> onPicked(h * 60 + m) },
        initial / 60,
        initial % 60,
        true
    ).show()
}

@Composable
private fun SectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 20.dp) {
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = 8.dp, start = 6.dp)
    )
}

/** 一个卡片装多行设置，行之间用细线分隔，和电脑版的 set-group 对应 */
@Composable
private fun GroupCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** 右侧的文字按钮。点按区域给足，免得「删除」「清空」这类挨着的手指误触 */
@Composable
private fun TextAction(
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    Text(
        text,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}

/** 开关行。整行可点——手机上一行很大，只让右边那个小开关响应很别扭 */
@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    active: Boolean = false,
    onChecked: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                else Modifier
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onChecked(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                desc,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onChecked(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                /* 默认的关闭态在米白底上几乎看不见：给它一点对比，
                   一眼就能分清「现在是关着的」而不是「这行坏了」 */
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

/** 带右侧动作文字的行，数据管理、试发提醒都用它 */
@Composable
private fun ActionRow(
    title: String,
    desc: String,
    action: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
    active: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onAction)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = when {
                    danger && enabled -> MaterialTheme.colorScheme.error
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(Modifier.height(3.dp))
            Text(
                desc,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.6f)
            )
        }
        Spacer(Modifier.width(10.dp))
        /* 整行已经能点了，这里只做展示：不再套一个 clickable，
           否则点在文字上会被内层吃掉，看起来像「点了没反应」 */
        Text(
            action,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                danger -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun BgRow(
    title: String,
    desc: String,
    has: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                desc,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (has) {
            TextAction(
                "移除",
                onClear,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextAction(if (has) "更换" else "选择", onPick)
    }
}

/**
 * 分段选择器。
 *
 * 没用 Material 的 SegmentedButton：它自带的最小高度和描边在这个列表里偏重，
 * 自己画一个反而更贴电脑版那排 .seg 按钮。
 */
@Composable
private fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (on) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 12.5.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Chevron(open: Boolean) {
    Icon(
        Icons.Rounded.KeyboardArrowDown,
        contentDescription = if (open) "收起" else "展开",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(20.dp)
            .rotate(if (open) 180f else 0f)
    )
}

@Composable
private fun TimePill(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun StatusDot(ok: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
                if (ok) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (ok) Icons.Rounded.Check else Icons.Rounded.PriorityHigh,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(13.dp)
        )
    }
}
