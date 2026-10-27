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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingtx.qingtiixing.data.DayEvents
import com.qingtx.qingtiixing.data.Fmt
import com.qingtx.qingtiixing.data.Reminder
import java.util.Calendar

private val WEEK_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

/** 这一周的周一（ISO：周一为一周之始，和电脑版一致） */
fun mondayOf(day: Calendar): Calendar {
    val c = Calendar.getInstance().apply { timeInMillis = day.timeInMillis }
    val iso = DayEvents.isoDay(c)
    c.add(Calendar.DAY_OF_YEAR, -(iso - 1))
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c
}

/**
 * 周条：一横排 7 天。
 *
 * 电脑版是 7 列 × 时间轴的网格，手机上塞不下 7 列，所以改成
 * 「周条选天 + 下面看那一天的完整时间轴」，信息量一样，字能看清。
 */
@Composable
fun WeekStrip(
    anchor: Calendar,
    selected: Calendar,
    items: List<Reminder>,
    onPick: (Calendar) -> Unit
) {
    val today = Calendar.getInstance()
    val monday = mondayOf(anchor)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 0 until 7) {
            val d = Calendar.getInstance().apply {
                timeInMillis = monday.timeInMillis
                add(Calendar.DAY_OF_YEAR, i)
            }
            val isToday = DayEvents.isSameDay(d, today)
            val isSelected = DayEvents.isSameDay(d, selected)
            val evs = DayEvents.on(items, d)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onPick(d) }
                    .padding(vertical = 7.dp)
            ) {
                Text(
                    WEEK_NAMES[i],
                    fontSize = 11.sp,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${d.get(Calendar.DAY_OF_MONTH)}",
                    fontSize = 14.5.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    if (evs.isEmpty()) {
                        Spacer(Modifier.size(3.dp))
                    } else {
                        evs.take(3).forEach { r ->
                            Box(
                                Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else Color(r.category.color)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 某一天的时间轴：按触发时刻从上到下排。
 *
 * 没做 24 小时网格——手机上一屏放不下，而且大多数日子只有两三件事，
 * 空网格除了占地方没有别的用。
 */
@Composable
fun DayTimeline(
    day: Calendar,
    items: List<Reminder>,
    onEdit: (String) -> Unit
) {
    val evs = DayEvents.on(items, day).sortedBy { DayEvents.minuteOf(it, day) }
    if (evs.isEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("这天没有安排", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("切到别的日子看看", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        evs.forEachIndexed { index, r ->
            val minute = DayEvents.minuteOf(r, day)
            val hh = minute / 60
            val mm = minute % 60
            val time = (if (hh < 10) "0$hh" else "$hh") + ":" + (if (mm < 10) "0$mm" else "$mm")
            val accent = Color(r.category.color)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onEdit(r.id) }
            ) {
                Text(
                    time,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(46.dp)
                        .padding(top = 14.dp),
                    textAlign = TextAlign.End
                )
                /* 竖线 + 圆点：时间轴的骨架 */
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(22.dp)
                ) {
                    Box(
                        Modifier
                            .padding(top = 16.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    if (index != evs.lastIndex) {
                        Box(
                            Modifier
                                .width(1.5.dp)
                                .height(44.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
                Row(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.title.ifBlank { "提醒" },
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            Fmt.rule(r),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
