package pl.czak.learnlauncher.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import pl.czak.learnlauncher.domain.model.ConversationBox

@Composable
actual fun ImageDisplay(
    imageBytes: ByteArray?,
    zoomedBubble: ConversationBox?,
    modifier: Modifier,
    onImageSizeKnown: ((imgWidth: Int, imgHeight: Int, containerWidth: Float, containerHeight: Float) -> Unit)?
) {
    if (imageBytes == null) return

    val bitmap = remember(imageBytes, zoomedBubble) {
        val fullBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return@remember null

        if (zoomedBubble != null) {
            val x = (zoomedBubble.x * fullBitmap.width).toInt().coerceIn(0, fullBitmap.width - 1)
            val y = (zoomedBubble.y * fullBitmap.height).toInt().coerceIn(0, fullBitmap.height - 1)
            val w = (zoomedBubble.width * fullBitmap.width).toInt().coerceAtMost(fullBitmap.width - x)
            val h = (zoomedBubble.height * fullBitmap.height).toInt().coerceAtMost(fullBitmap.height - y)
            if (w > 0 && h > 0) {
                android.graphics.Bitmap.createBitmap(fullBitmap, x, y, w, h)
            } else {
                fullBitmap
            }
        } else {
            fullBitmap
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.onGloballyPositioned { coords ->
                val containerSize = coords.size.toSize()
                onImageSizeKnown?.invoke(
                    it.width, it.height,
                    containerSize.width, containerSize.height
                )
            }
        )
    }
}
