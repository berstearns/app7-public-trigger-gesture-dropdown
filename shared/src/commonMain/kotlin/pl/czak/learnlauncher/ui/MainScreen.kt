package pl.czak.learnlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.czak.learnlauncher.currentTimeMillis
import pl.czak.learnlauncher.ui.components.ImageFitRect
import pl.czak.learnlauncher.domain.model.DoubleTapAction
import pl.czak.learnlauncher.ui.components.AnnotationOverlay
import pl.czak.learnlauncher.ui.components.ChapterDropdown
import pl.czak.learnlauncher.ui.components.SessionTimers
import pl.czak.learnlauncher.ui.components.TranslationOverlay
import pl.czak.learnlauncher.viewmodel.LauncherViewModel
import pl.czak.learnlauncher.viewmodel.Screen
import kotlin.math.abs

@Composable
fun MainScreen(
    viewModel: LauncherViewModel,
    onLaunchApp: ((String) -> Unit)? = null,
    onLoadApps: (() -> List<AppInfo>)? = null,
    onOpenHomeSettings: (() -> Unit)? = null
) {
    val currentImage by viewModel.currentImage.collectAsState()
    val imageBytes by viewModel.currentImageBytes.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val canGoNextChapter by viewModel.canGoNextChapter.collectAsState()
    val canGoPreviousChapter by viewModel.canGoPreviousChapter.collectAsState()
    val currentChapterName by viewModel.currentChapterName.collectAsState()
    val chapterNames by viewModel.chapterNames.collectAsState()
    val doubleTapAction by viewModel.doubleTapAction.collectAsState()
    val isZoomed by viewModel.isZoomed.collectAsState()
    val showAnnotationDots by viewModel.showAnnotationDots.collectAsState()
    val annotations by viewModel.currentImageAnnotations.collectAsState()
    val tokenRegions by viewModel.currentTokenRegions.collectAsState()
    val tokenAnnotations by viewModel.currentTokenAnnotations.collectAsState()
    val selectedTokenIndex by viewModel.selectedTokenIndex.collectAsState()
    val showTranslationOverlay by viewModel.showTranslationOverlay.collectAsState()
    val currentTranslation by viewModel.currentTranslation.collectAsState()
    val sessionElapsed by viewModel.sessionElapsed.collectAsState()
    val showAppsOverlay by viewModel.showAppsOverlay.collectAsState()
    val filteredApps by viewModel.apps.collectAsState()
    val conversationBoxes by viewModel.currentConversationBoxes.collectAsState()
    val highlightedBubble by viewModel.highlightedBubble.collectAsState()
    val annotationBarPosition by viewModel.annotationBarPosition.collectAsState()
    val annotationBarVisibilityMode by viewModel.annotationBarVisibilityMode.collectAsState()
    val hideAnnotationBarTrigger by viewModel.hideAnnotationBarTrigger.collectAsState()
    val showAnnotationBar by viewModel.annotationBarOpen.collectAsState()
    val annotationTriggerGesture by viewModel.annotationTriggerGesture.collectAsState()
    val pendingBubbleAction by viewModel.pendingBubbleAction.collectAsState()
    val pendingTokenAction by viewModel.pendingTokenAction.collectAsState()

    var imageViewSize by remember { mutableStateOf(IntSize.Zero) }
    var imageFitRect by remember { mutableStateOf(ImageFitRect(0f, 0f, 1f, 1f)) }
    var appFilterQuery by remember { mutableStateOf("") }

    // Triple-tap detection for emergency exit
    var emergencyTapCount by remember { mutableIntStateOf(0) }
    var lastEmergencyTapTime by remember { mutableLongStateOf(0L) }

    // Load apps on first composition if callback available
    LaunchedEffect(onLoadApps) {
        onLoadApps?.let { loader ->
            viewModel.setApps(loader())
        }
    }

    // Observe double tap actions
    LaunchedEffect(doubleTapAction) {
        doubleTapAction?.let { action ->
            when (action) {
                is DoubleTapAction.AnnotateBubble -> viewModel.openAnnotationBarForBubble(action)
                is DoubleTapAction.AnnotateToken -> viewModel.openAnnotationBarForToken(action)
                else -> {}
            }
            viewModel.consumeDoubleTapAction()
        }
    }

    // Arrow-nav: in on_double_tap mode, hide the bar. In always mode the bar stays and
    // the effect below re-syncs the pending action to the new highlighted bubble.
    LaunchedEffect(hideAnnotationBarTrigger) {
        if (hideAnnotationBarTrigger > 0L && annotationBarVisibilityMode != "always") {
            viewModel.closeAnnotationBar()
        }
    }

    // Always-show mode: keep the bar visible for the currently highlighted bubble.
    LaunchedEffect(annotationBarVisibilityMode, highlightedBubble) {
        if (annotationBarVisibilityMode == "always") {
            val hb = highlightedBubble
            if (hb != null) {
                val idx = viewModel.getHighlightedBubbleIndex()
                if (idx >= 0) {
                    viewModel.openAnnotationBarForBubble(
                        DoubleTapAction.AnnotateBubble(
                            nx = hb.x + hb.width / 2f,
                            ny = hb.y + hb.height / 2f,
                            box = hb,
                            boxIndex = idx
                        )
                    )
                }
            } else {
                viewModel.closeAnnotationBar()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Page title
                Text(
                    text = currentImage?.title?.substringAfter(" - ", currentImage?.title ?: "") ?: "",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )

                // Chapter dropdown
                if (chapterNames.isNotEmpty()) {
                    ChapterDropdown(
                        currentChapter = currentChapterName,
                        chapters = chapterNames,
                        onChapterSelected = { viewModel.goToChapter(it) }
                    )
                }
            }

            // Session timers
            SessionTimers(elapsed = sessionElapsed)

            // Image viewer with swipe detection
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { imageViewSize = it }
                    .pointerInput(isZoomed, imageFitRect, annotationTriggerGesture) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val nx = imageFitRect.toNormalizedX(offset.x, size.width.toFloat())
                                val ny = imageFitRect.toNormalizedY(offset.y, size.height.toFloat())
                                if (isZoomed) {
                                    viewModel.onZoomDoubleTapped(nx, ny)
                                } else if (annotationTriggerGesture == "double_tap") {
                                    viewModel.onImageAnnotateTapped(nx, ny)
                                }
                            },
                            onLongPress = { offset ->
                                val nx = imageFitRect.toNormalizedX(offset.x, size.width.toFloat())
                                val ny = imageFitRect.toNormalizedY(offset.y, size.height.toFloat())
                                if (!viewModel.onImageLongPressed(nx, ny)) {
                                    viewModel.currentScreen.value = Screen.Settings
                                }
                            },
                            onTap = { offset ->
                                val nx = imageFitRect.toNormalizedX(offset.x, size.width.toFloat())
                                val ny = imageFitRect.toNormalizedY(offset.y, size.height.toFloat())
                                if (isZoomed) {
                                    viewModel.onZoomTapped(nx, ny)
                                } else if (annotationTriggerGesture == "single_tap") {
                                    viewModel.onImageAnnotateTapped(nx, ny)
                                }
                            }
                        )
                    }
                    .pointerInput(isZoomed, showAnnotationBar, showAppsOverlay) {
                        if (isZoomed || showAnnotationBar || showAppsOverlay) return@pointerInput
                        var startY = 0f
                        var handled = false
                        detectDragGestures(
                            onDragStart = { offset ->
                                startY = offset.y
                                handled = false
                            },
                            onDrag = { _, dragAmount ->
                                if (handled) return@detectDragGestures
                                val totalDy = dragAmount.y
                                // We accumulate from the drag start
                            },
                            onDragEnd = {},
                            onDragCancel = {}
                        )
                    }
            ) {
                // Image display - platform-specific rendering via expect/actual
                ImageDisplay(
                    imageBytes = imageBytes,
                    zoomedBubble = if (isZoomed) viewModel.zoomedBubble else null,
                    modifier = Modifier.fillMaxSize(),
                    onImageSizeKnown = { imgW, imgH, containerW, containerH ->
                        imageFitRect = ImageFitRect.compute(imgW, imgH, containerW, containerH)
                    }
                )

                // Annotation overlay (always show conversation box regions)
                if (showAnnotationDots || conversationBoxes.isNotEmpty() || highlightedBubble != null) {
                    AnnotationOverlay(
                        annotations = if (showAnnotationDots) annotations else emptyList(),
                        conversationBoxes = if (!isZoomed) conversationBoxes else emptyList(),
                        highlightedBubble = if (!isZoomed) highlightedBubble else null,
                        tokenAnnotations = if (isZoomed) tokenAnnotations else emptyList(),
                        tokenRegions = if (isZoomed) tokenRegions else emptyList(),
                        selectedTokenIndex = if (isZoomed) selectedTokenIndex else null,
                        isZoomMode = isZoomed,
                        fitRect = imageFitRect,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Translation overlay
                if (showTranslationOverlay) {
                    TranslationOverlay(
                        translation = currentTranslation,
                        onDismiss = { viewModel.hideTranslationOverlay() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Zoom mode UI
                if (isZoomed) {
                    val bubbleText = viewModel.zoomedBubble?.text

                    // Clean replacement text panel at bottom
                    if (bubbleText != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            // High-quality rendered text (replacement bubble)
                            Surface(
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = bubbleText,
                                    color = Color.Black,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            // Selected token text
                            val selIdx = selectedTokenIndex
                            val selToken = if (selIdx != null) tokenRegions.getOrNull(selIdx) else null
                            if (selToken?.text != null) {
                                Surface(
                                    color = Color(0xFF00BCD4),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 4.dp)
                                ) {
                                    Text(
                                        text = selToken.text ?: "",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Exit zoom button
                    TextButton(
                        onClick = { viewModel.exitZoom() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text("Exit Zoom", color = Color.White)
                    }
                }

                // Swipe overlay for vertical drag navigation
                if (!isZoomed && !showAnnotationBar && !showAppsOverlay) {
                    SwipeDetector(
                        onSwipeUp = { viewModel.nextPage() },
                        onSwipeDown = { viewModel.previousPage() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (canGoPreviousChapter) {
                    TextButton(onClick = { viewModel.previousChapter() }) {
                        Text("< Ch", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }

                // Emergency triple-tap zone
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val now = currentTimeMillis()
                            if (now - lastEmergencyTapTime < 500L) {
                                emergencyTapCount++
                            } else {
                                emergencyTapCount = 1
                            }
                            lastEmergencyTapTime = now
                            if (emergencyTapCount >= 3) {
                                emergencyTapCount = 0
                                onOpenHomeSettings?.invoke()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            canGoBack && canGoForward -> "\u25BC swipe \u25B2"
                            canGoBack -> "\u25BC swipe"
                            canGoForward -> "swipe \u25B2"
                            else -> ""
                        },
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                // See Apps button
                if (onLaunchApp != null) {
                    TextButton(onClick = { viewModel.showAppsOverlay() }) {
                        Text("Apps", color = Color(0xFF4CAF50), fontSize = 12.sp)
                    }
                }

                if (canGoNextChapter) {
                    TextButton(onClick = { viewModel.nextChapter() }) {
                        Text("Ch >", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }
        }

        // Annotation bar
        if (showAnnotationBar) {
            val onAnnotate = { label: String ->
                submitAnnotation(viewModel, label, pendingBubbleAction, pendingTokenAction)
                viewModel.closeAnnotationBar()
                viewModel.clearHighlightedBubble()
            }
            val onCancel = {
                viewModel.closeAnnotationBar()
                viewModel.clearHighlightedBubble()
            }

            val bubbleBox = pendingBubbleAction?.box ?: pendingTokenAction?.token

            when (annotationBarPosition) {
                "closest" -> {
                    // Place bar at top or bottom, whichever is closer to the bubble
                    val bubbleCenterY = bubbleBox?.let { it.y + it.height / 2f } ?: 1f
                    val alignment = if (bubbleCenterY < 0.5f) Alignment.TopCenter else Alignment.BottomCenter
                    AnnotationBarRow(
                        modifier = Modifier.align(alignment).fillMaxWidth(),
                        onAnnotate = onAnnotate,
                        onCancel = onCancel
                    )
                }
                "spread" -> {
                    // Individual buttons spread around the bubble, clamped to bounds
                    val box = bubbleBox
                    if (box != null && imageViewSize.width > 0 && imageViewSize.height > 0) {
                        val containerW = imageViewSize.width.toFloat()
                        val containerH = imageViewSize.height.toFloat()

                        SpreadAnnotationButtons(
                            bubbleLeftPx = imageFitRect.toPixelX(box.x),
                            bubbleTopPx = imageFitRect.toPixelY(box.y),
                            bubbleRightPx = imageFitRect.toPixelX(box.x + box.width),
                            bubbleBottomPx = imageFitRect.toPixelY(box.y + box.height),
                            containerWidthPx = containerW,
                            containerHeightPx = containerH,
                            onAnnotate = onAnnotate,
                            onCancel = onCancel
                        )
                    } else {
                        AnnotationBarRow(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            onAnnotate = onAnnotate,
                            onCancel = onCancel
                        )
                    }
                }
                else -> {
                    // "bottom" — default fixed at bottom
                    AnnotationBarRow(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        onAnnotate = onAnnotate,
                        onCancel = onCancel
                    )
                }
            }
        }

        // Apps overlay
        if (showAppsOverlay && onLaunchApp != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Installed Apps", color = Color.White, fontSize = 18.sp)
                        TextButton(onClick = { viewModel.hideAppsOverlay() }) {
                            Text("Continue Reading", color = Color(0xFF4CAF50))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter input
                    OutlinedTextField(
                        value = appFilterQuery,
                        onValueChange = {
                            appFilterQuery = it
                            viewModel.filterApps(it)
                        },
                        placeholder = { Text("Filter apps...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color(0xFF555555)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onLaunchApp(app.packageName)
                                        viewModel.hideAppsOverlay()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.label,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "open",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp
                                )
                            }
                            HorizontalDivider(color = Color(0xFF333333))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeDetector(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var dragHandled by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        totalDragY = 0f
                        dragHandled = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y
                        if (!dragHandled && abs(totalDragY) > 80f) {
                            dragHandled = true
                            if (totalDragY < 0) {
                                onSwipeUp()
                            } else {
                                onSwipeDown()
                            }
                        }
                    },
                    onDragEnd = {},
                    onDragCancel = {}
                )
            }
    )
}

@Composable
private fun AnnotationBarRow(
    modifier: Modifier = Modifier,
    onAnnotate: (String) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = modifier
            .background(Color(0xFF333333))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = { onAnnotate("understood") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) { Text("Got it") }

        Button(
            onClick = { onAnnotate("partially") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
        ) { Text("Partial", color = Color.Black) }

        Button(
            onClick = { onAnnotate("not_understood") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
        ) { Text("Didn't get it") }

        TextButton(onClick = onCancel) { Text("Cancel", color = Color.Gray) }
    }
}

/**
 * Places 4 annotation buttons spread around the bubble.
 *
 * Each button has a preferred side (above, below, left, right).
 * Placement rules:
 *  1. Position on preferred side, just outside the bubble edge + margin.
 *  2. Clamp to container bounds so nothing goes off-screen.
 *  3. If clamping pushes a button to overlap the bubble, relocate it to the
 *     side with the most available space instead.
 *  4. When multiple buttons end up on the same side, stack them so they
 *     don't overlap each other.
 */
@Composable
private fun SpreadAnnotationButtons(
    bubbleLeftPx: Float,
    bubbleTopPx: Float,
    bubbleRightPx: Float,
    bubbleBottomPx: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    onAnnotate: (String) -> Unit,
    onCancel: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = with(density) { 4.dp.toPx() }
    val btnH = with(density) { 36.dp.toPx() }
    val btnW = with(density) { 90.dp.toPx() }
    val smallBtnW = with(density) { 40.dp.toPx() }

    val midX = (bubbleLeftPx + bubbleRightPx) / 2f
    val midY = (bubbleTopPx + bubbleBottomPx) / 2f

    // Available space on each side
    val spaceAbove = bubbleTopPx
    val spaceBelow = containerHeightPx - bubbleBottomPx
    val spaceLeft = bubbleLeftPx
    val spaceRight = containerWidthPx - bubbleRightPx

    // Each button: (label, action, preferred side, estimated width)
    // Preferred: above, below, left, right
    data class BtnSpec(val id: String, val preferredSide: Int, val w: Float)
    val above = 0; val below = 1; val left = 2; val right = 3

    val specs = listOf(
        BtnSpec("understood", above, btnW),
        BtnSpec("not_understood", below, btnW),
        BtnSpec("partially", left, smallBtnW),
        BtnSpec("cancel", right, smallBtnW)
    )

    // Minimum space needed for a button on each side
    val minVertical = btnH + gap
    val minHorizontal = smallBtnW + gap

    // Assign each button to a side. If preferred side has no room, pick best alternative.
    val sides = arrayOf(
        spaceAbove to minVertical,
        spaceBelow to minVertical,
        spaceLeft to minHorizontal,
        spaceRight to minHorizontal
    )

    // Track how many buttons assigned to each side for stacking
    val sideSlots = IntArray(4) // count per side
    val assignments = IntArray(4) // which side each button got

    for (i in specs.indices) {
        val pref = specs[i].preferredSide
        val (space, needed) = sides[pref]
        if (space >= needed) {
            assignments[i] = pref
        } else {
            // Pick side with most space (prefer vertical for wider buttons)
            val ranked = listOf(above, below, left, right)
                .sortedByDescending { sides[it].first - sides[it].second }
            assignments[i] = ranked.first { sides[it].first >= sides[it].second || it == ranked.last() }
        }
        sideSlots[assignments[i]]++
    }

    // Now compute positions. For each side, stack buttons with gap between them.
    val sideCounters = IntArray(4) // current slot index per side
    val positions = Array(4) { floatArrayOf(0f, 0f) } // x, y per button

    for (i in specs.indices) {
        val side = assignments[i]
        val slot = sideCounters[side]
        val total = sideSlots[side]
        val w = specs[i].w

        when (side) {
            above -> {
                // Stack upward from bubble top edge
                val y = (bubbleTopPx - gap - btnH * (slot + 1) - gap * slot)
                    .coerceIn(0f, containerHeightPx - btnH)
                val x = (midX - w / 2f).coerceIn(0f, containerWidthPx - w)
                positions[i] = floatArrayOf(x, y)
            }
            below -> {
                // Stack downward from bubble bottom edge
                val y = (bubbleBottomPx + gap + (btnH + gap) * slot)
                    .coerceIn(0f, containerHeightPx - btnH)
                val x = (midX - w / 2f).coerceIn(0f, containerWidthPx - w)
                positions[i] = floatArrayOf(x, y)
            }
            left -> {
                // Stack leftward from bubble left edge
                val x = (bubbleLeftPx - gap - w * (slot + 1) - gap * slot)
                    .coerceIn(0f, containerWidthPx - w)
                // Vertically distribute if multiple on this side
                val yOffset = if (total > 1) (slot - (total - 1) / 2f) * (btnH + gap) else 0f
                val y = (midY - btnH / 2f + yOffset).coerceIn(0f, containerHeightPx - btnH)
                positions[i] = floatArrayOf(x, y)
            }
            right -> {
                // Stack rightward from bubble right edge
                val x = (bubbleRightPx + gap + (w + gap) * slot)
                    .coerceIn(0f, containerWidthPx - w)
                val yOffset = if (total > 1) (slot - (total - 1) / 2f) * (btnH + gap) else 0f
                val y = (midY - btnH / 2f + yOffset).coerceIn(0f, containerHeightPx - btnH)
                positions[i] = floatArrayOf(x, y)
            }
        }
        sideCounters[side]++
    }

    // Render buttons
    val buttonData = listOf(
        Triple("understood", Color(0xFF4CAF50)) { onAnnotate("understood") },
        Triple("not_understood", Color(0xFFF44336)) { onAnnotate("not_understood") },
        Triple("partially", Color(0xFFFFC107)) { onAnnotate("partially") },
        Triple("cancel", Color.Transparent) { onCancel() }
    )
    val labels = listOf("Got it", "No", "?", "X")
    val textColors = listOf(Color.White, Color.White, Color.Black, Color.Gray)

    for (i in 0..3) {
        val xDp = with(density) { positions[i][0].toDp() }
        val yDp = with(density) { positions[i][1].toDp() }
        val (_, color, action) = buttonData[i]

        if (i == 3) {
            // Cancel button
            TextButton(
                onClick = action,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.offset(x = xDp, y = yDp)
            ) { Text(labels[i], color = textColors[i], fontSize = 12.sp) }
        } else {
            Button(
                onClick = action,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.offset(x = xDp, y = yDp)
            ) { Text(labels[i], color = textColors[i], fontSize = 12.sp) }
        }
    }
}

private fun submitAnnotation(
    viewModel: LauncherViewModel,
    label: String,
    bubbleAction: DoubleTapAction.AnnotateBubble?,
    tokenAction: DoubleTapAction.AnnotateToken?
) {
    tokenAction?.let {
        viewModel.saveTokenAnnotation(it.token, it.parentBubbleIndex, it.tokenIndex, label, it.nx, it.ny)
        return
    }
    bubbleAction?.let {
        viewModel.saveAnnotation(it.box, it.boxIndex, label, it.nx, it.ny)
    }
}

@Composable
expect fun ImageDisplay(
    imageBytes: ByteArray?,
    zoomedBubble: pl.czak.learnlauncher.domain.model.ConversationBox?,
    modifier: Modifier,
    onImageSizeKnown: ((imgWidth: Int, imgHeight: Int, containerWidth: Float, containerHeight: Float) -> Unit)?
)
