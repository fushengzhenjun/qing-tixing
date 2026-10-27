package com.qingtx.qingtiixing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.qingtx.qingtiixing.data.Reminder
import java.util.Calendar

private val WEEK_LABELS = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 月历。
 *
 * 格子下面那几个小圆点就是当天的提醒，颜色跟着分类走——一眼能看出这周忙不忙、
 * 忙在哪类事上。点某天即筛选出那天的安排。
 */
@Composable
fun MonthCalendar(
    year: Int,
    month: Int,
    selected: Calendar?,
    items: List<Reminder>,
    onPick: (Calendar) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val today = Calendar.getInstance()
    val first = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, 1)
    }
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    /* Calendar 里周日是 1，减 1 后正好是前面要空几格 */
    val lead = first.get(Calendar.DAY_OF_WEEK) - 1
    val totalCells = lead + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(Modifier.fillMaxWidth()) {

        /* 标题行：年月 + 上下月 + 回今天 */
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$year 年 $month 月",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "今天",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onToday)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "下个月", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        /* 星期表头 */
        Row(Modifier.fillMaxWidth()) {
            WEEK_LABELS.forEach { label ->
                Text(
                    label,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    val dayNum = index - lead + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..daysInMonth) {
                            val day = Calendar.getInstance().apply {
                                clear()
                                set(year, month - 1, dayNum)
                            }
                            val isToday = DayEvents.isSameDay(day, today)
                            val isSelected = selected != null && DayEvents.isSameDay(day, selected)
                            DayCell(
                                dayNum = dayNum,
                                isToday = isToday,
                                isSelected = isSelected,
                                dots = DayEvents.on(items, day).take(3).map { Color(it.category.color) },
                                onClick = { onPick(day) }
                            )
                        } else {
                            Spacer(Modifier.height(46.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayNum: Int,
    isToday: Boolean,
    isSelected: Boolean,
    dots: List<Color>,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .size(width = 36.dp, height = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .clickable(onClick = onClick)
    ) {
        Text(
            "$dayNum",
            fontSize = 13.5.sp,
            fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
            if (dots.isEmpty()) {
                /* 占位，保证有无圆点的格子高度一致，否则整行会上下跳 */
                Spacer(Modifier.size(3.dp))
            } else {
                dots.forEach { c ->
                    Box(
                        Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(c)
                    )
                }
            }
        }
    }
}
