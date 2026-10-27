package com.qingtx.qingtiixing.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    notifGranted: Boolean,
    exactOk: Boolean,
    batteryOk: Boolean,
    onRequestNotif: () -> Unit,
    onRequestExact: () -> Unit,
    onRequestBattery: () -> Unit,
    onBack: () -> Unit
) {
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
                        "这些系统会额外「优化」后台应用。除了上面的电池优化，还要在系统设置里给轻提醒打开：\n" +
                            "· 自启动 / 允许后台活动\n" +
                            "· 通知管理里的「横幅通知」\n" +
                            "· 最近任务里给轻提醒「加锁」，避免一键清理时被杀掉",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("轻提醒", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
