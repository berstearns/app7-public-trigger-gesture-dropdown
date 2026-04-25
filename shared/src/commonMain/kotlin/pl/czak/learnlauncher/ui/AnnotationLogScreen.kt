package pl.czak.learnlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.czak.learnlauncher.data.repository.AnnotationRepository
import pl.czak.learnlauncher.domain.model.RegionAnnotation

@Composable
fun AnnotationLogScreen(
    annotationRepository: AnnotationRepository,
    onBack: () -> Unit,
    onNavigateToImage: (String) -> Unit
) {
    var allAnnotations by remember { mutableStateOf<List<RegionAnnotation>>(emptyList()) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        allAnnotations = annotationRepository.getAll().sortedByDescending { it.timestamp }
    }

    val filtered = if (selectedLabel == null) allAnnotations
    else allAnnotations.filter { it.label == selectedLabel }

    val labels = allAnnotations.map { it.label }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("< Back", color = Color(0xFF4CAF50))
            }
            Spacer(Modifier.weight(1f))
            Text("Annotation Log", color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedLabel == null,
                onClick = { selectedLabel = null },
                label = { Text("All") }
            )
            labels.forEach { label ->
                FilterChip(
                    selected = selectedLabel == label,
                    onClick = { selectedLabel = label },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = getLabelColor(label)
                    )
                )
            }
        }

        // List
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No annotations", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered) { annotation ->
                    AnnotationLogItem(
                        annotation = annotation,
                        onClick = { onNavigateToImage(annotation.imageId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnotationLogItem(
    annotation: RegionAnnotation,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = getLabelColor(annotation.label)
            ) {
                Text(
                    text = annotation.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = annotation.imageId.substringBeforeLast(".").replace("/", " / "),
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "Box #${annotation.boxIndex}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun getLabelColor(label: String): Color {
    return when (label.lowercase()) {
        "understood", "got it" -> Color(0xFF4CAF50)
        "partially" -> Color(0xFFFFC107)
        "not_understood", "didn't get it" -> Color(0xFFF44336)
        else -> Color(0xFF666666)
    }
}
