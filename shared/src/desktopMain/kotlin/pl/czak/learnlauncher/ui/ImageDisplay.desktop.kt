package pl.czak.learnlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.IRect
import pl.czak.learnlauncher.domain.model.ConversationBox

@Composable
actual fun ImageDisplay(
    imageBytes: ByteArray?,
    zoomedBubble: ConversationBox?,
    modifier: Modifier,
    onImageSizeKnown: ((imgWidth: Int, imgHeight: Int, containerWidth: Float, containerHeight: Float) -> Unit)?
) {
    if (imageBytes == null) return

    val result = remember(imageBytes, zoomedBubble) {
        try {
            val skiaImage = SkiaImage.makeFromEncoded(imageBytes)
            val finalImage = if (zoomedBubble != null) {
                val w = skiaImage.width
                val h = skiaImage.height
                val cropRect = IRect.makeLTRB(
                    (zoomedBubble.x * w).toInt().coerceIn(0, w),
                    (zoomedBubble.y * h).toInt().coerceIn(0, h),
                    ((zoomedBubble.x + zoomedBubble.width) * w).toInt().coerceIn(0, w),
                    ((zoomedBubble.y + zoomedBubble.height) * h).toInt().coerceIn(0, h)
                )
                SkiaImage.makeFromEncoded(skiaImage.encodeToData()!!.bytes).let { src ->
                    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(
                        cropRect.width, cropRect.height
                    )
                    surface.canvas.drawImage(src, -cropRect.left.toFloat(), -cropRect.top.toFloat())
                    surface.makeImageSnapshot()
                }
            } else {
                skiaImage
            }
            Pair(finalImage.toComposeImageBitmap(), finalImage)
        } catch (e: Exception) {
            null
        }
    }

    result?.let { (bitmap, skiaImg) ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.onGloballyPositioned { coords ->
                val containerSize = coords.size.toSize()
                onImageSizeKnown?.invoke(
                    skiaImg.width, skiaImg.height,
                    containerSize.width, containerSize.height
                )
            }
        )
    }
}
