package com.qingtx.qingtiixing.ui

import android.app.TimePickerDialog
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingtx.qingtiixing.data.SettingsSnapshot
import com.qingtx.qingtiixing.data.ThemeMode
import kotlinx.coroutines.delay

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
    onBack: () -> Unit
) {
    val ctx = LocalContext.current

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
    val paused = prefs.pausedNow()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
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

            /* ---------- 权限：这三项不解决，提醒根本不会响，所以放最上面 ---------- */
            Text(
                "这三项都打开，提醒才会准时响",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            PermissionCard(
                title = "通知权限",
                desc = if (notifGranted) "已开启，到点会弹通知" else "没开的话，到点了什么都不会发生",
                ok = notifGranted,
                actionText = "去开启",
                onAction = onRequestNotif
            )
            Spacer(Modifier.height(10.dp))
            PermissionCard(
                title = "精确闹钟",
                desc = if (exactOk) "已允许，可以准点响" else "未允许，系统可能把提醒推迟几分钟到几十分钟",
                ok = exactOk,
                actionText = "去允许",
                onAction = onRequestExact
            )
            Spacer(Modifier.height(10.dp))
            PermissionCard(
                title = "电池优化",
                desc = if (batteryOk) "已排除，后台不会被冻结" else "仍受限制，长时间不用可能不响",
                ok = batteryOk,
                actionText = "去设置",
                onAction = onRequestBattery
            )

            /* ---------- 常规 ---------- */
            SectionLabel("常规", top = 22.dp)

            GroupCard {
                SwitchRow(
                    title = "全部暂停",
                    desc = pauseDesc,
                    checked = prefs.pauseAll,
                    onChecked = onPauseToggle
                )

                if (prefs.pauseAll) {
                    Divider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "暂停到什么时候",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallChip("1 小时") { onPauseDuration(60) }
                            SmallChip("4 小时") { onPauseDuration(240) }
                            SmallChip("明早 8 点") { onPauseDuration(-1) }
                            SmallChip("手动恢复") { onPauseDuration(0) }
                        }
                    }
                }

                Divider()

                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "外观",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "深色模式更适合夜里看",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.values().forEach { m ->
                            SelectChip(m.label, prefs.theme == m) { onTheme(m) }
                        }
                    }
                }
            }

            /* ---------- 提醒 ---------- */
            SectionLabel("提醒")

            GroupCard {
                SwitchRow(
                    title = "夜间免打扰",
                    desc = if (prefs.dndOn) "时段内静音、不振动，提醒仍会记录" else "夜里到点也不吵你",
                    checked = prefs.dndOn,
                    onChecked = onDndToggle
                )

                if (prefs.dndOn) {
                    Divider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "时段",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (prefs.dndStart == prefs.dndEnd) {
                                "开始和结束时间相同，等于没开"
                            } else {
                                "这段时间里提醒照常弹出、照常记进列表，只是不出声不振动"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            /* ---------- 国产 ROM 提示 ---------- */
            Spacer(Modifier.height(18.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("小米 / 华为 / OPPO / vivo 用户看这里", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这些系统会额外「优化」后台应用。除了上面的电池优化，还要在系统设置里给轻铃打开：\n" +
                            "· 自启动 / 允许后台活动\n" +
                            "· 通知管理里的「横幅通知」\n" +
                            "· 最近任务里给轻铃「加锁」，避免一键清理时被杀掉",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            /* ---------- 关于 ---------- */
            Spacer(Modifier.height(18.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("轻铃", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "v$versionName",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "简单、简洁、无广告的定时提醒。\n" +
                            "不联网、不收集任何数据，提醒只存在这台手机里。\n" +
                            "卸载或清除数据会一并删除提醒，重要的事建议另记一份。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

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
private fun SectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 18.dp) {
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = 8.dp, start = 4.dp)
    )
}

/** 一个卡片装多行设置，行之间用细线分隔，和电脑版的 set-group 对应 */
@Composable
private fun GroupCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(desc, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) }
    )
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
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
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PermissionCard(
    title: String,
    desc: String,
    ok: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    StatusDot(ok)
                }
                Spacer(Modifier.height(3.dp))
                Text(desc, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!ok) {
                Text(
                    actionText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusDot(ok: Boolean) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(
                if (ok) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (ok) Icons.Rounded.Check else Icons.Rounded.PriorityHigh,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(12.dp)
        )
    }
}
