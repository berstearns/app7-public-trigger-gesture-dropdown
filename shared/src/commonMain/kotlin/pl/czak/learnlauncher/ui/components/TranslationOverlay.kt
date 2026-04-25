package pl.czak.learnlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.czak.learnlauncher.data.model.RegionTranslation

@Composable
fun TranslationOverlay(
    translation: RegionTranslation?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2D2D2D),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (translation != null) {
                    // Extracted text label (from OCR)
                    if (translation.originalText.isNotBlank()) {
                        Text(
                            text = "Extracted Text",
                            color = Color(0xFF00BCD4),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = translation.originalText,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Meaning translation
                    if (translation.meaningTranslation.isNotBlank()) {
                        Text(
                            text = "Meaning",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = translation.meaningTranslation,
                            color = Color(0xFF4CAF50),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Literal translation
                    if (translation.literalTranslation.isNotBlank()) {
                        Text(
                            text = "Literal",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = translation.literalTranslation,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }

                    // If only extracted text (no translations yet)
                    if (translation.meaningTranslation.isBlank() && translation.literalTranslation.isBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No translation yet",
                            color = Color(0xFF666666),
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    Text(
                        text = "No text detected",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to dismiss",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
