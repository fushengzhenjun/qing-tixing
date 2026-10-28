package com.qingtx.qingtiixing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingtx.qingtiixing.data.Category
import com.qingtx.qingtiixing.data.DayEvents
import com.qingtx.qingtiixing.data.Fmt
import com.qingtx.qingtiixing.data.NextFire
import com.qingtx.qingtiixing.data.Reminder
import com.qingtx.qingtiixing.data.ReminderType
import kotlinx.coroutines.delay
import java.util.Calendar

private val CATEGORY_COLORS = mapOf(
    Category.HEALTH to Color(0xFF1C9E69),
    Category.WORK to Color(0xFF2F6FE0),
    Category.STUDY to Color(0xFFDF8726),
    Category.LIFE to Color(0xFFD3557C),
    Category.OTHER to Color(0xFF798087)
)

private fun catColor(c: Category): Color = CATEGORY_COLORS[c] ?: Color(0xFF798087)

private enum class ViewMode(val label: String) {
    LIST("列表"),
    WEEK("周"),
    TODO("清单")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    items: List<Reminder>,
    versionName: String,
    warnText: String?,
    appBgUri: String,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onToggle: (Reminder) -> Unit,
    onSetDone: (id: String, dateKey: String, done: Boolean) -> Unit,
    onSettings: () -> Unit,
    onWarnClick: () -> Unit
) {
    /* 筛选：null 表示全部，否则只显示该分类 */
    var filterCat by remember { mutableStateOf<Category?>(null) }
    /* 选中的日期：默认今天 */
    var picked by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var calYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var calMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    /* 视图：列表 / 周 / 清单 */
    var mode by remember { mutableStateOf(ViewMode.LIST) }

    /* 时钟每秒走一格 */
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val pickedCal = remember(picked) {
        Calendar.getInstance().apply { timeInMillis = picked }
    }
    val shown = remember(items, picked, filterCat) {
        Fmt.sortedForDisplay(DayEvents.on(items, pickedCal))
            .filter { filterCat == null || it.category == filterCat }
    }
    val totalOnDay = remember(items, picked) { DayEvents.on(items, pickedCal).size }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardSurface = if (appBgUri.isBlank()) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    }

    BackgroundLayer(
        uri = appBgUri.ifBlank { null },
        /* 遮罩要够重：大时钟和日期没有卡片托着，直接压在图上，
           0.55 的白在花一点的图里根本读不清（实测过截图）。
           加深到 0.72 之后背景还看得出来，但文字稳了。 */
        scrim = if (isDark) Color.Black.copy(alpha = 0.66f) else Color.White.copy(alpha = 0.72f)
    ) {
    Scaffold(
        containerColor = if (appBgUri.isBlank()) MaterialTheme.colorScheme.background else Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("轻铃", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "新建提醒")
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            /* ---------- 大时钟 ---------- */
            item {
                ClockHeader(nowMillis)
            }

            if (warnText != null) {
                item {
                    WarnBanner(warnText, onWarnClick)
                }
            }

            /* ---------- 分类筛选 ---------- */
            item {
                CategoryRow(
                    active = filterCat,
                    onPickAll = { filterCat = null },
                    onPick = { filterCat = if (filterCat == it) null else it }
                )
            }

            /* ---------- 大日历 ---------- */
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        MonthCalendar(
                            year = calYear,
                            month = calMonth,
                            selected = pickedCal,
                            items = items,
                            onPick = { d ->
                                picked = d.timeInMillis
                                calYear = d.get(Calendar.YEAR)
                                calMonth = d.get(Calendar.MONTH) + 1
                            },
                            onPrev = {
                                if (calMonth == 1) {
                                    calMonth = 12; calYear -= 1
                                } else calMonth -= 1
                            },
                            onNext = {
                                if (calMonth == 12) {
                                    calMonth = 1; calYear += 1
                                } else calMonth += 1
                            },
                            onToday = {
                                val t = Calendar.getInstance()
                                picked = t.timeInMillis
                                calYear = t.get(Calendar.YEAR)
                                calMonth = t.get(Calendar.MONTH) + 1
                            }
                        )
                    }
                }
            }

            /* ---------- 视图切换 ---------- */
            item {
                ViewTabs(mode) { mode = it }
            }

            when (mode) {
                ViewMode.LIST -> {
                    item {
                        Row(
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dayLabel(pickedCal),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (totalOnDay > 0) "共 $totalOnDay 项" else "",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (shown.isEmpty()) {
                        item { DayEmpty() }
                    } else {
                        items(shown, key = { it.id }) { r ->
                            ReminderCard(
                                r = r,
                                cardColor = cardSurface,
                                onClick = { onEdit(r.id) },
                                onToggle = { onToggle(r) }
                            )
                        }
                    }
                }

                ViewMode.WEEK -> {
                    item {
                        WeekHeader(
                            pickedCal = pickedCal,
                            onShift = { days ->
                                picked += days * 24L * 3600_000L
                                val c = Calendar.getInstance().apply { timeInMillis = picked }
                                calYear = c.get(Calendar.YEAR)
                                calMonth = c.get(Calendar.MONTH) + 1
                            },
                            onToday = {
                                val t = Calendar.getInstance()
                                picked = t.timeInMillis
                                calYear = t.get(Calendar.YEAR)
                                calMonth = t.get(Calendar.MONTH) + 1
                            }
                        )
                    }
                    item {
                        WeekStrip(
                            anchor = pickedCal,
                            selected = pickedCal,
                            items = items,
                            onPick = { d ->
                                picked = d.timeInMillis
                                calYear = d.get(Calendar.YEAR)
                                calMonth = d.get(Calendar.MONTH) + 1
                            }
                        )
                    }
                    item {
                        Spacer(Modifier.height(10.dp))
                        DayTimeline(day = pickedCal, items = items, onEdit = onEdit)
                    }
                }

                ViewMode.TODO -> {
                    item {
                        TodoList(
                            day = pickedCal,
                            items = items,
                            onToggleDone = { r, done ->
                                onSetDone(r.id, DayEvents.key(pickedCal), done)
                            },
                            onEdit = onEdit
                        )
                    }
                }
            }
        }
    }
    }
}

private fun dayLabel(d: Calendar): String {
    val names = listOf("日", "一", "二", "三", "四", "五", "六")
    val today = Calendar.getInstance()
    val prefix = if (DayEvents.isSameDay(d, today)) {
        "今天"
    } else {
        val t = Calendar.getInstance()
        t.add(Calendar.DAY_OF_YEAR, 1)
        if (DayEvents.isSameDay(d, t)) "明天" else "周" + names[d.get(Calendar.DAY_OF_WEEK) - 1]
    }
    return "${prefix} · ${d.get(Calendar.MONTH) + 1}月${d.get(Calendar.DAY_OF_MONTH)}日"
}

@Composable
private fun ClockHeader(nowMillis: Long) {
    val c = remember(nowMillis) {
        Calendar.getInstance().apply { timeInMillis = nowMillis }
    }
    val hh = c.get(Calendar.HOUR_OF_DAY)
    val mm = c.get(Calendar.MINUTE)
    val ss = c.get(Calendar.SECOND)
    val names = listOf("日", "一", "二", "三", "四", "五", "六")
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                (if (hh < 10) "0$hh" else "$hh") + ":" + (if (mm < 10) "0$mm" else "$mm"),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (ss < 10) "0$ss" else "$ss",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(
            "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 周${names[c.get(Calendar.DAY_OF_WEEK) - 1]}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryRow(
    active: Category?,
    onPickAll: () -> Unit,
    onPick: (Category) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterPill("全部", active == null, MaterialTheme.colorScheme.primary, onPickAll)
        Category.values().forEach { c ->
            FilterPill(c.label, active == c, catColor(c)) { onPick(c) }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WarnBanner(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReminderCard(
    r: Reminder,
    cardColor: Color,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val next = if (r.enabled) NextFire.next(r, System.currentTimeMillis()) else null
    val accent = catColor(r.category)
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            /* 左侧分类色条：比一个小圆点更容易一眼分辨 */
            Box(
                Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .background(if (r.enabled) accent else accent.copy(alpha = 0.25f))
            )
            Row(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 13.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = if (r.enabled) 0.16f else 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = if (r.enabled) 1f else 0.4f))
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        r.title.ifBlank { "提醒" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (r.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    val sub = when {
                        !r.enabled -> "已停用"
                        next == null -> Fmt.rule(r)
                        r.type == ReminderType.ONCE -> Fmt.whenText(next)
                        else -> "${Fmt.rule(r)} · ${Fmt.whenText(next)}"
                    }
                    Text(
                        sub,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = r.enabled, onCheckedChange = { onToggle() })
            }
        }
    }
}

@Composable
private fun ViewTabs(mode: ViewMode, onPick: (ViewMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ViewMode.values().forEach { m ->
            val sel = mode == m
            Text(
                m.label,
                fontSize = 13.sp,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                color = if (sel) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (sel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onPick(m) }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun WeekHeader(pickedCal: Calendar, onShift: (Int) -> Unit, onToday: () -> Unit) {
    val monday = mondayOf(pickedCal)
    val sunday = Calendar.getInstance().apply {
        timeInMillis = monday.timeInMillis
        add(Calendar.DAY_OF_YEAR, 6)
    }
    val range = "${monday.get(Calendar.MONTH) + 1}月${monday.get(Calendar.DAY_OF_MONTH)}日 – " +
        "${sunday.get(Calendar.MONTH) + 1}月${sunday.get(Calendar.DAY_OF_MONTH)}日"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            range,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            "本周",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToday)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        IconButton(onClick = { onShift(-7) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一周", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { onShift(7) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "下一周", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DayEmpty() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("这天没有提醒", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(
            "点右下角 + 加一条",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
