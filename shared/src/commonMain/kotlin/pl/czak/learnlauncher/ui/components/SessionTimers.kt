package pl.czak.learnlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.czak.learnlauncher.domain.session.SessionElapsed

@Composable
fun SessionTimers(
    elapsed: SessionElapsed,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = "\u23F1 ${formatDuration(elapsed.appMs)}",
            color = Color(0xFF888888),
            fontSize = 11.sp
        )
        Text(
            text = "\uD83D\uDCD6 ${formatDuration(elapsed.chapterMs)}",
            color = Color(0xFF888888),
            fontSize = 11.sp
        )
        Text(
            text = "\uD83D\uDCC4 ${formatDuration(elapsed.pageMs)}",
            color = Color(0xFF888888),
            fontSize = 11.sp
        )
    }
}

private fun formatDuration(ms: Long?): String {
    if (ms == null) return "--:--"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}
