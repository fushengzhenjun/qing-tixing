package com.qingtx.qingtiixing.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/* 配色与电脑版一致：墨绿主色 + 米白底。改这里之前先对一下 轻铃.html 的 :root 变量。
   secondaryContainer 必须显式给值：Material3 的 FilterChip 选中态用的就是它，
   不写会退回框架默认的紫色，和品牌绿打架（第一版就是这样）。 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF137A63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2F0EA),
    onPrimaryContainer = Color(0xFF0F6350),
    secondary = Color(0xFFD27F2B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F0EA),
    onSecondaryContainer = Color(0xFF0F6350),
    background = Color(0xFFF6F5F1),
    onBackground = Color(0xFF1D211F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D211F),
    surfaceVariant = Color(0xFFEFEEE9),
    onSurfaceVariant = Color(0xFF6B726D),
    outline = Color(0xFFE4E2DB),
    outlineVariant = Color(0xFFEFEDE7),
    error = Color(0xFFC4453A),
    errorContainer = Color(0xFFF8E7E4)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3DA889),
    onPrimary = Color(0xFF06110E),
    primaryContainer = Color(0xFF20342D),
    onPrimaryContainer = Color(0xFF9FE3CC),
    secondary = Color(0xFFE09753),
    onSecondary = Color(0xFF1A1208),
    secondaryContainer = Color(0xFF20342D),
    onSecondaryContainer = Color(0xFF9FE3CC),
    background = Color(0xFF121514),
    onBackground = Color(0xFFECEEEA),
    surface = Color(0xFF1B1F1C),
    onSurface = Color(0xFFECEEEA),
    surfaceVariant = Color(0xFF232825),
    onSurfaceVariant = Color(0xFF97A097),
    outline = Color(0xFF292E2A),
    outlineVariant = Color(0xFF232825),
    error = Color(0xFFE0695C),
    errorContainer = Color(0xFF3A2320)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun QtxTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        shapes = AppShapes
    ) {
        /* 铺一层底色：内容开了 edge-to-edge，不铺的话导航栏后面会露出一条白边 */
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
