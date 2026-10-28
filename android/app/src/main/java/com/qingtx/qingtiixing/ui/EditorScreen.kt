package com.qingtx.qingtiixing.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingtx.qingtiixing.data.Category
import com.qingtx.qingtiixing.data.Fmt
import com.qingtx.qingtiixing.data.Reminder
import com.qingtx.qingtiixing.data.ReminderType
import com.qingtx.qingtiixing.data.Ring
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    existing: Reminder?,
    onBack: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (String) -> Unit
) {
    val ctx = LocalContext.current

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: ReminderType.ONCE) }
    var hour by remember { mutableIntStateOf(existing?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var atMillis by remember {
        mutableLongStateOf(existing?.atMillis?.takeIf { it > 0 } ?: defaultOnceTime())
    }
    var days by remember { mutableStateOf(existing?.daysOfWeek ?: emptyList()) }
    var category by remember { mutableStateOf(existing?.category ?: Category.OTHER) }
    var ring by remember { mutableStateOf(existing?.ring ?: Ring.CHIME) }
    var vibrate by remember { mutableStateOf(existing?.vibrate ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    /* 每月 / 每年 / 每隔N天 / 循环 各自的参数 */
    var dayOfMonth by remember { mutableIntStateOf(existing?.dayOfMonth ?: 1) }
    var monthOfYear by remember { mutableIntStateOf(existing?.monthOfYear ?: 1) }
    var everyDays by remember { mutableIntStateOf(existing?.everyDays ?: 2) }
    var loopStartH by remember { mutableIntStateOf(existing?.loopStartH ?: 9) }
    var loopStartM by remember { mutableIntStateOf(existing?.loopStartM ?: 0) }
    var loopEndH by remember { mutableIntStateOf(existing?.loopEndH ?: 18) }
    var loopEndM by remember { mutableIntStateOf(existing?.loopEndM ?: 0) }
    var everyMin by remember { mutableIntStateOf(existing?.everyMin ?: 45) }

    fun commit() {
        val target = (existing ?: Reminder()).copy(
            title = title.trim().ifBlank { "提醒" },
            type = type,
            hour = hour,
            minute = minute,
            atMillis = if (type == ReminderType.ONCE) atMillis else 0L,
            daysOfWeek = if (type == ReminderType.WEEKLY) days.sorted() else emptyList(),
            dayOfMonth = dayOfMonth,
            monthOfYear = monthOfYear,
            everyDays = everyDays,
            anchorMillis = existing?.anchorMillis?.takeIf { it > 0 }
                ?: System.currentTimeMillis(),
            loopStartH = loopStartH,
            loopStartM = loopStartM,
            loopEndH = loopEndH,
            loopEndM = loopEndM,
            everyMin = everyMin,
            category = category,
            ring = ring,
            vibrate = vibrate,
            enabled = true
        )
        when {
            type == ReminderType.ONCE && atMillis <= System.currentTimeMillis() ->
                error = "这个时间已经过去了，换个时间"
            type == ReminderType.WEEKLY && days.isEmpty() ->
                error = "每周提醒至少要选一天"
            type == ReminderType.LOOP && (loopEndH * 60 + loopEndM) <= (loopStartH * 60 + loopStartM) ->
                error = "结束时间要晚于开始时间"
            else -> {
                error = null
                onSave(target)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新建提醒" else "编辑提醒", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { commit() }) {
                        Text("保存", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 40) title = it },
                label = { Text("提醒什么事") },
                placeholder = { Text("如：站起来活动、交房租") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("类型")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TypeChip("单次", type == ReminderType.ONCE) { type = ReminderType.ONCE }
                TypeChip("每天", type == ReminderType.DAILY) { type = ReminderType.DAILY }
                TypeChip("工作日", type == ReminderType.WEEKDAY) { type = ReminderType.WEEKDAY }
                TypeChip("每周", type == ReminderType.WEEKLY) { type = ReminderType.WEEKLY }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TypeChip("每月", type == ReminderType.MONTHLY) { type = ReminderType.MONTHLY }
                TypeChip("每年", type == ReminderType.YEARLY) { type = ReminderType.YEARLY }
                TypeChip("每隔N天", type == ReminderType.NDAYS) { type = ReminderType.NDAYS }
                TypeChip("循环", type == ReminderType.LOOP) { type = ReminderType.LOOP }
            }

            SectionLabel("时间")
            if (type == ReminderType.ONCE) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(
                        label = Fmt.dateTime(atMillis).substringBefore(" "),
                        onClick = {
                            pickDate(ctx, atMillis) { picked ->
                                val old = Calendar.getInstance().apply { timeInMillis = atMillis }
                                val c = Calendar.getInstance().apply {
                                    timeInMillis = picked
                                    set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY))
                                    set(Calendar.MINUTE, old.get(Calendar.MINUTE))
                                }
                                atMillis = c.timeInMillis
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    BigButton(
                        label = Fmt.hm(
                            Calendar.getInstance().apply { timeInMillis = atMillis }.get(Calendar.HOUR_OF_DAY),
                            Calendar.getInstance().apply { timeInMillis = atMillis }.get(Calendar.MINUTE)
                        ),
                        onClick = {
                            pickTime(
                                ctx,
                                Calendar.getInstance().apply { timeInMillis = atMillis }.get(Calendar.HOUR_OF_DAY),
                                Calendar.getInstance().apply { timeInMillis = atMillis }.get(Calendar.MINUTE)
                            ) { h, m ->
                                val c = Calendar.getInstance().apply {
                                    timeInMillis = atMillis
                                    set(Calendar.HOUR_OF_DAY, h)
                                    set(Calendar.MINUTE, m)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                atMillis = c.timeInMillis
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "快捷：",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChip("10 分钟后") { atMillis = System.currentTimeMillis() + 10 * 60_000L }
                    SmallChip("30 分钟后") { atMillis = System.currentTimeMillis() + 30 * 60_000L }
                    SmallChip("1 小时后") { atMillis = System.currentTimeMillis() + 60 * 60_000L }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChip("明早 8 点") {
                        val c = Calendar.getInstance()
                        c.add(Calendar.DAY_OF_YEAR, 1)
                        c.set(Calendar.HOUR_OF_DAY, 8)
                        c.set(Calendar.MINUTE, 0)
                        c.set(Calendar.SECOND, 0)
                        atMillis = c.timeInMillis
                    }
                    Spacer(Modifier.weight(1f))
                }
            } else if (type == ReminderType.LOOP) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(
                        label = "开始 " + Fmt.hm(loopStartH, loopStartM),
                        onClick = {
                            pickTime(ctx, loopStartH, loopStartM) { h, m ->
                                loopStartH = h; loopStartM = m
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    BigButton(
                        label = "结束 " + Fmt.hm(loopEndH, loopEndM),
                        onClick = {
                            pickTime(ctx, loopEndH, loopEndM) { h, m ->
                                loopEndH = h; loopEndM = m
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "每多久提醒一次",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChip("15 分钟") { everyMin = 15 }
                    SmallChip("30 分钟") { everyMin = 30 }
                    SmallChip("45 分钟") { everyMin = 45 }
                    SmallChip("60 分钟") { everyMin = 60 }
                }
                Spacer(Modifier.height(8.dp))
                NumberStepper(everyMin, 1, 720, "分钟") { everyMin = it }
            } else {
                BigButton(
                    label = Fmt.hm(hour, minute),
                    onClick = { pickTime(ctx, hour, minute) { h, m -> hour = h; minute = m } },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (type == ReminderType.WEEKLY) {
                SectionLabel("哪几天")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        val on = days.contains(d)
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    days = if (on) days - d else days + d
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Fmt.dayLabel(d),
                                fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            when (type) {
                ReminderType.MONTHLY -> {
                    SectionLabel("每月几号")
                    Text(
                        "没有这一天的月份会自动跳过（比如 2 月没有 31 号）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    NumberStepper(dayOfMonth, 1, 31, "号") { dayOfMonth = it }
                }
                ReminderType.YEARLY -> {
                    SectionLabel("每年哪一天")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberStepper(monthOfYear, 1, 12, "月") { monthOfYear = it }
                        Spacer(Modifier.width(16.dp))
                        NumberStepper(dayOfMonth, 1, 31, "日") { dayOfMonth = it }
                    }
                }
                ReminderType.NDAYS -> {
                    SectionLabel("每隔几天")
                    NumberStepper(everyDays, 1, 365, "天") { everyDays = it }
                }
                else -> {}
            }

            SectionLabel("分类")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.take(3).forEach { c ->
                    TypeChip(c.label, category == c) { category = c }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.drop(3).forEach { c ->
                    TypeChip(c.label, category == c) { category = c }
                }
            }

            SectionLabel("铃声")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ring.entries.forEach { rg ->
                    TypeChip(rg.label, ring == rg) { ring = rg }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "到点时同时振动",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }

            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            if (existing != null) {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = { onDelete(existing.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("删除这条提醒")
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

/** 「每隔 N 天」「每月几号」这类数字，用加减调比弹输入框省事 */
@Composable
private fun NumberStepper(value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBtn("-", value > min) { onChange((value - 1).coerceAtLeast(min)) }
        Text(
            "$value $unit",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        StepBtn("+", value < max) { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun StepBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
        fontSize = 12.5.sp,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = modifier.height(52.dp)
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun defaultOnceTime(): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.HOUR_OF_DAY, 1)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun pickTime(ctx: Context, hour: Int, minute: Int, onPick: (Int, Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick(h, m) }, hour, minute, true).show()
}

private fun pickDate(ctx: Context, millis: Long, onPick: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    DatePickerDialog(
        ctx,
        { _, y, mo, d ->
            val n = Calendar.getInstance().apply {
                set(y, mo, d, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), 0)
                set(Calendar.MILLISECOND, 0)
            }
            onPick(n.timeInMillis)
        },
        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
    ).show()
}
