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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingtx.qingtiixing.data.DayEvents
import com.qingtx.qingtiixing.data.Reminder
import java.util.Calendar

/**
 * 清单：一天要做的事，勾一下算完成。
 *
 * 勾选只影响这一天，不改提醒本身的规则——所以「每天喝水」今天勾掉了，
 * 明天照样会提醒。doneDates 里记的就是这些勾过的日子。
 */
@Composable
fun TodoList(
    day: Calendar,
    items: List<Reminder>,
    onToggleDone: (Reminder, Boolean) -> Unit,
    onEdit: (String) -> Unit
) {
    val evs = DayEvents.on(items, day)
    val done = evs.filter { DayEvents.isDone(it, day) }
    val todo = evs.filterNot { DayEvents.isDone(it, day) }
    val sorted = todo.sortedBy { DayEvents.minuteOf(it, day) } + done

    val total = evs.size
    val doneCount = done.size
    val pct = if (total > 0) doneCount.toFloat() / total else 0f

    Column(Modifier.padding(horizontal = 16.dp)) {

        /* 进度 */
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (total == 0) "今天没有待办" else "完成 $doneCount / $total",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (total > 0 && doneCount == total) {
                Text(
                    "全部搞定",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (total > 0) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(pct)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (sorted.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("今天没有待办", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "切到「周」看看后面几天的安排",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        sorted.forEach { r ->
            val isDone = DayEvents.isDone(r, day)
            val accent = Color(r.category.color)
            val minute = DayEvents.minuteOf(r, day)
            val hh = minute / 60
            val mm = minute % 60
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isDone,
                        onCheckedChange = { onToggleDone(r, it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = accent,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isDone) accent.copy(alpha = 0.3f) else accent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { onEdit(r.id) }
                    ) {
                        Text(
                            r.title.ifBlank { "提醒" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (isDone) TextDecoration.LineThrough else null
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            (if (hh < 10) "0$hh" else "$hh") + ":" + (if (mm < 10) "0$mm" else "$mm"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "勾选只算今天的，不影响这条提醒以后照常响",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
    }
}
