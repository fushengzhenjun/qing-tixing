package com.qingtx.qingtiixing

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.qingtx.qingtiixing.alarm.Scheduler
import com.qingtx.qingtiixing.data.Fmt
import com.qingtx.qingtiixing.data.Reminder
import com.qingtx.qingtiixing.data.Store
import com.qingtx.qingtiixing.ui.QtxTheme
import java.util.Calendar

/**
 * 到点提醒的全屏页。
 *
 * 通知本身只会在状态栏挂一条，用户可能根本没看见。这里通过通知的
 * fullScreenIntent 把它整个抬到前台——锁屏、别的 app 开着，都能盖上来，
 * 对应电脑版那个「独立小窗盖在最上层」。
 *
 * 声音不在这里播：通知渠道已经响过了，这边再响一次会变成双重铃声。
 */
class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ID = "reminderId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* 锁屏上直接亮屏显示，不用先解锁 */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Store.init(this)
        val id = intent.getStringExtra(EXTRA_ID)
        val r = id?.let { Store.get(it) }
        if (r == null) {
            finish()
            return
        }

        setContent {
            QtxTheme(dark = true) {
                AlarmScreen(
                    r = r,
                    onDismiss = {
                        cancelNotification(r)
                        finish()
                    },
                    onSnooze = { minutes ->
                        Scheduler(this).scheduleSnooze(r, minutes)
                        cancelNotification(r)
                        finish()
                    }
                )
            }
        }
    }

    private fun cancelNotification(r: Reminder) {
        runCatching { NotificationManagerCompat.from(this).cancel(r.id.hashCode()) }
    }
}

@Composable
private fun AlarmScreen(
    r: Reminder,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    val now = Calendar.getInstance()
    val hh = now.get(Calendar.HOUR_OF_DAY)
    val mm = now.get(Calendar.MINUTE)
    val time = (if (hh < 10) "0$hh" else "$hh") + ":" + (if (mm < 10) "0$mm" else "$mm")

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E1A16), Color(0xFF0A0D0C))
                )
            )
            .padding(horizontal = 28.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(96.dp))

            /* 铃铛 */
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3DA889).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = Color(0xFF3DA889),
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                time,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFECEEEA)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "到点了",
                fontSize = 13.sp,
                color = Color(0xFF97A097)
            )

            Spacer(Modifier.height(30.dp))
            Text(
                r.title.ifBlank { "提醒" },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFECEEEA),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                Fmt.rule(r),
                fontSize = 13.5.sp,
                color = Color(0xFF97A097),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            /* 稍后提醒 */
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SnoozeButton("5 分钟后") { onSnooze(5) }
                SnoozeButton("10 分钟后") { onSnooze(10) }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF3DA889))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "我知道了",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF06110E)
                )
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun SnoozeButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF232825))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 15.dp)
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFECEEEA)
        )
    }
}
