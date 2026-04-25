package pl.czak.learnlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import org.jetbrains.skia.Image as SkiaImage
import pl.czak.learnlauncher.domain.model.ConversationBox

@Composable
actual fun ImageDisplay(
    imageBytes: ByteArray?,
    zoomedBubble: ConversationBox?,
    modifier: Modifier,
    onImageSizeKnown: ((imgWidth: Int, imgHeight: Int, containerWidth: Float, containerHeight: Float) -> Unit)?
) {
    if (imageBytes == null) return

    val imageBitmap = remember(imageBytes, zoomedBubble) {
        try {
            val skiaImage = SkiaImage.makeFromEncoded(imageBytes)
            // TODO: implement zoom crop for iOS if needed
            skiaImage.toComposeImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    imageBitmap?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.onGloballyPositioned { coords ->
                val containerSize = coords.size.toSize()
                onImageSizeKnown?.invoke(
                    bmp.width, bmp.height,
                    containerSize.width, containerSize.height
                )
            }
        )
    }
}
