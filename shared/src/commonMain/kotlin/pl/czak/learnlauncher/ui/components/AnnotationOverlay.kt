package pl.czak.learnlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import pl.czak.learnlauncher.domain.model.ConversationBox

/**
 * Describes where the image actually renders inside its container after ContentScale.Fit.
 */
data class ImageFitRect(
    val offsetX: Float,
    val offsetY: Float,
    val imageWidth: Float,
    val imageHeight: Float
) {
    fun toNormalizedX(px: Float, containerWidth: Float): Float {
        if (imageWidth <= 0f) return px / containerWidth
        return ((px - offsetX) / imageWidth).coerceIn(0f, 1f)
    }

    fun toNormalizedY(py: Float, containerHeight: Float): Float {
        if (imageHeight <= 0f) return py / containerHeight
        return ((py - offsetY) / imageHeight).coerceIn(0f, 1f)
    }

    fun toPixelX(nx: Float): Float = offsetX + nx * imageWidth
    fun toPixelY(ny: Float): Float = offsetY + ny * imageHeight
    fun toPixelW(nw: Float): Float = nw * imageWidth
    fun toPixelH(nh: Float): Float = nh * imageHeight

    companion object {
        fun compute(imgW: Int, imgH: Int, containerW: Float, containerH: Float): ImageFitRect {
            if (imgW <= 0 || imgH <= 0 || containerW <= 0f || containerH <= 0f) {
                return ImageFitRect(0f, 0f, containerW, containerH)
            }
            val imgAspect = imgW.toFloat() / imgH.toFloat()
            val containerAspect = containerW / containerH
            val (renderedW, renderedH) = if (imgAspect > containerAspect) {
                containerW to (containerW / imgAspect)
            } else {
                (containerH * imgAspect) to containerH
            }
            val offsetX = (containerW - renderedW) / 2f
            val offsetY = (containerH - renderedH) / 2f
            return ImageFitRect(offsetX, offsetY, renderedW, renderedH)
        }
    }
}

// Colors
private val BubbleRegionFill = Color(0x33FFEB3B)
private val BubbleRegionStroke = Color(0x66FFEB3B)
private val HighlightFill = Color(0x66FFEB3B)
private val HighlightStroke = Color(0xCCFFEB3B.toInt())
private val TokenOutline = Color(0x8000BCD4.toInt())
private val TokenSelectedFill = Color(0x6600BCD4.toInt())
private val TokenSelectedStroke = Color(0xFF00BCD4.toInt())
private val TokenTextBg = Color(0xDD000000.toInt())
private val TokenTextColor = Color.White

@Composable
fun AnnotationOverlay(
    annotations: List<Pair<ConversationBox, String>>,
    conversationBoxes: List<ConversationBox> = emptyList(),
    highlightedBubble: ConversationBox? = null,
    tokenAnnotations: List<Pair<ConversationBox, String>> = emptyList(),
    tokenRegions: List<ConversationBox> = emptyList(),
    selectedTokenIndex: Int? = null,
    isZoomMode: Boolean = false,
    fitRect: ImageFitRect = ImageFitRect(0f, 0f, 1f, 1f),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val fr = if (fitRect.imageWidth <= 1f) {
            ImageFitRect(0f, 0f, size.width, size.height)
        } else fitRect

        if (isZoomMode) {
            // Draw token regions
            for ((idx, token) in tokenRegions.withIndex()) {
                val isSelected = idx == selectedTokenIndex
                val topLeft = Offset(fr.toPixelX(token.x), fr.toPixelY(token.y))
                val boxSize = Size(fr.toPixelW(token.width), fr.toPixelH(token.height))

                if (isSelected) {
                    // Selected: filled highlight + thick border
                    drawRect(
                        color = TokenSelectedFill,
                        topLeft = topLeft,
                        size = boxSize
                    )
                    drawRect(
                        color = TokenSelectedStroke,
                        topLeft = topLeft,
                        size = boxSize,
                        style = Stroke(width = 3f)
                    )
                } else {
                    // Normal: outline only
                    drawRect(
                        color = TokenOutline,
                        topLeft = topLeft,
                        size = boxSize,
                        style = Stroke(width = 1.5f)
                    )
                }

                // Show indicator dot for tokens with text
                if (token.text != null) {
                    val dotCx = fr.toPixelX(token.x + token.width / 2)
                    val dotCy = fr.toPixelY(token.y) - 6f
                    val dotColor = if (isSelected) TokenSelectedStroke else TokenOutline
                    drawCircle(color = dotColor, radius = if (isSelected) 5f else 3f, center = Offset(dotCx, dotCy))
                }
            }

            // Token annotation dots
            for ((token, label) in tokenAnnotations) {
                val cx = fr.toPixelX(token.x + token.width / 2)
                val cy = fr.toPixelY(token.y + token.height / 2)
                val color = labelColor(label)
                drawCircle(color = color, radius = 6f, center = Offset(cx, cy))
            }
        } else {
            // Normal mode: yellow bubble overlays
            for (box in conversationBoxes) {
                val topLeft = Offset(fr.toPixelX(box.x), fr.toPixelY(box.y))
                val boxSize = Size(fr.toPixelW(box.width), fr.toPixelH(box.height))
                drawRect(color = BubbleRegionFill, topLeft = topLeft, size = boxSize)
                drawRect(color = BubbleRegionStroke, topLeft = topLeft, size = boxSize, style = Stroke(width = 1.5f))
            }

            // Highlighted bubble
            highlightedBubble?.let { box ->
                val topLeft = Offset(fr.toPixelX(box.x), fr.toPixelY(box.y))
                val boxSize = Size(fr.toPixelW(box.width), fr.toPixelH(box.height))
                drawRect(color = HighlightFill, topLeft = topLeft, size = boxSize)
                drawRect(color = HighlightStroke, topLeft = topLeft, size = boxSize, style = Stroke(width = 3f))
            }

            // Annotation dots
            for ((box, label) in annotations) {
                val dotRadius = 8f
                val margin = 2f
                val cx = fr.toPixelX(box.x + box.width / 2)
                val boxTopPx = fr.toPixelY(box.y)
                val cy = if (boxTopPx - fr.offsetY > dotRadius * 2 + margin) {
                    boxTopPx - dotRadius - margin
                } else {
                    fr.toPixelY(box.y + box.height) + dotRadius + margin
                }
                val color = labelColor(label)
                drawCircle(color = color, radius = dotRadius, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = dotRadius, center = Offset(cx, cy), style = Stroke(width = 1.5f))
            }
        }
    }
}

private fun labelColor(label: String): Color {
    return when (label.lowercase()) {
        "understood", "got it" -> Color(0xFF4CAF50.toInt())
        "partially" -> Color(0xFFFFC107.toInt())
        "not_understood", "didn't get it" -> Color(0xFFF44336.toInt())
        else -> Color(0xFF888888.toInt())
    }
}
