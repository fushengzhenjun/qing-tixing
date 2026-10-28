package com.qingtx.qingtiixing.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 读一张本地图片当背景。
 *
 * 按宽度采样再解码：手机相册里的图动辄 4000px 宽，直接读进来是几十 MB，
 * 而背景只需要盖住屏幕。所以先只读尺寸算采样率，再真解码。
 */
private fun decodeBackground(ctx: Context, uri: String): ImageBitmap? {
    val u = Uri.parse(uri)
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(u)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth > 0 && bounds.outWidth / sample > 2000) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.contentResolver.openInputStream(u)?.use {
            BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
        }
    }.getOrNull()
}

@Composable
fun rememberBackground(uri: String?): ImageBitmap? {
    if (uri.isNullOrBlank()) return null
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decodeBackground(ctx, uri) }
    }.value
}

/**
 * 把背景图铺在内容下面，再盖一层半透明色保证文字能读。
 *
 * @param scrim 遮罩色，浅色主题用白、深色主题用黑、提醒页用更重的黑
 */
@Composable
fun BackgroundLayer(
    uri: String?,
    scrim: Color,
    content: @Composable () -> Unit
) {
    val img = rememberBackground(uri)
    Box(Modifier.fillMaxSize()) {
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            /* 没设背景时不盖遮罩，否则整个界面会被冲淡一层 */
            Box(Modifier.fillMaxSize().background(scrim))
        }
        content()
    }
}
