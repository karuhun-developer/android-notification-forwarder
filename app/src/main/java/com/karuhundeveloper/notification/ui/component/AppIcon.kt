package com.karuhundeveloper.notification.ui.component

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.karuhundeveloper.notification.ui.theme.RelayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(160)

@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val cacheKey = "$packageName@$sizePx"

    val icon by produceState<ImageBitmap?>(
        initialValue = iconCache.get(cacheKey),
        cacheKey
    ) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager
                        .getApplicationIcon(packageName)
                        .toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        .asImageBitmap()
                }.getOrNull()
            }
            if (loaded != null) iconCache.put(cacheKey, loaded)
            value = loaded
        }
    }

    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(RelayTheme.colors.surfaceRaised)
            .border(1.dp, RelayTheme.colors.hairline, shape),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = RelayTheme.colors.inkMuted
            )
        }
    }
}
