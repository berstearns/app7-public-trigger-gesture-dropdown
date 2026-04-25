package pl.czak.learnlauncher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.czak.learnlauncher.currentTimeMillis
import pl.czak.learnlauncher.data.model.ImageItem
import pl.czak.learnlauncher.data.model.RegionTranslation
import pl.czak.learnlauncher.data.repository.AnnotationRepository
import pl.czak.learnlauncher.data.repository.LearnerDataRepository
import pl.czak.learnlauncher.data.repository.SessionRepository
import pl.czak.learnlauncher.data.repository.TokenRegionRepository
import pl.czak.learnlauncher.data.repository.TranslationRepository
import pl.czak.learnlauncher.data.source.ImageDataSource
import pl.czak.learnlauncher.domain.model.ConversationBox
import pl.czak.learnlauncher.domain.model.DoubleTapAction
import pl.czak.learnlauncher.domain.model.RegionAnnotation
import pl.czak.learnlauncher.domain.model.RegionType
import pl.czak.learnlauncher.domain.navigation.ImageSequenceNavigator
import pl.czak.learnlauncher.domain.session.CompletedSession
import pl.czak.learnlauncher.domain.session.SessionElapsed
import pl.czak.learnlauncher.domain.session.SessionTimeTracker
import pl.czak.learnlauncher.ui.AppInfo

sealed class ZoomModeState {
    data object Viewing : ZoomModeState()
    data class AnnotatingToken(val token: ConversationBox) : ZoomModeState()
    data class ShowingTranslation(val translation: RegionTranslation?) : ZoomModeState()
}

sealed class Screen {
    data object Login : Screen()
    data object Main : Screen()
    data object Settings : Screen()
    data object Flags : Screen()
    data object Chat : Screen()
    data object AnnotationLog : Screen()
}

class LauncherViewModel(
    private var imageDataSource: ImageDataSource,
    private var imageNavigator: ImageSequenceNavigator,
    private val sessionTimeTracker: SessionTimeTracker,
    val annotationRepository: AnnotationRepository,
    val sessionRepository: SessionRepository,
    private val learnerDataRepository: LearnerDataRepository,
    private val tokenRegionRepository: TokenRegionRepository,
    private val translationRepository: TranslationRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private var lastTrackedChapter: String? = null
    private var currentComicId: String? = null

    private fun positionKey(): String =
        currentComicId?.let { "image_navigator_position__$it" } ?: "image_navigator_position"

    // Navigation
    val currentScreen = MutableStateFlow<Screen>(Screen.Main)

    // Image state
    private val _currentImage = MutableStateFlow<ImageItem?>(null)
    val currentImage: StateFlow<ImageItem?> = _currentImage

    private val _currentImageBytes = MutableStateFlow<ByteArray?>(null)
    val currentImageBytes: StateFlow<ByteArray?> = _currentImageBytes

    // Navigation state
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward

    private val _canGoNextChapter = MutableStateFlow(false)
    val canGoNextChapter: StateFlow<Boolean> = _canGoNextChapter

    private val _canGoPreviousChapter = MutableStateFlow(false)
    val canGoPreviousChapter: StateFlow<Boolean> = _canGoPreviousChapter

    private val _currentChapterName = MutableStateFlow("")
    val currentChapterName: StateFlow<String> = _currentChapterName

    private val _chapterNames = MutableStateFlow<List<String>>(emptyList())
    val chapterNames: StateFlow<List<String>> = _chapterNames

    // Annotation state — "off" | "single_tap" | "double_tap"
    private val _annotationTriggerGesture = MutableStateFlow("double_tap")
    val annotationTriggerGesture: StateFlow<String> = _annotationTriggerGesture

    private val _showAnnotationDots = MutableStateFlow(true)
    val showAnnotationDots: StateFlow<Boolean> = _showAnnotationDots

    private val _annotationBarPosition = MutableStateFlow("bottom")
    val annotationBarPosition: StateFlow<String> = _annotationBarPosition

    // "always" | "on_double_tap" — controls when the annotation bar is visible.
    private val _annotationBarVisibilityMode = MutableStateFlow("on_double_tap")
    val annotationBarVisibilityMode: StateFlow<String> = _annotationBarVisibilityMode

    // Bumped to a fresh timestamp when arrow navigation should hide the bar (on_double_tap mode).
    private val _hideAnnotationBarTrigger = MutableStateFlow(0L)
    val hideAnnotationBarTrigger: StateFlow<Long> = _hideAnnotationBarTrigger

    // Desktop-only: Spacebar opens the annotation bar for the currently highlighted bubble.
    private val _annotationOnSpace = MutableStateFlow(false)
    val annotationOnSpace: StateFlow<Boolean> = _annotationOnSpace

    private val _doubleTapAction = MutableStateFlow<DoubleTapAction?>(null)
    val doubleTapAction: StateFlow<DoubleTapAction?> = _doubleTapAction

    // Authoritative annotation bar state. Promoted from MainScreen so any entry point
    // (UI, keyboard) can observe and toggle it.
    private val _annotationBarOpen = MutableStateFlow(false)
    val annotationBarOpen: StateFlow<Boolean> = _annotationBarOpen

    private val _pendingBubbleAction = MutableStateFlow<DoubleTapAction.AnnotateBubble?>(null)
    val pendingBubbleAction: StateFlow<DoubleTapAction.AnnotateBubble?> = _pendingBubbleAction

    private val _pendingTokenAction = MutableStateFlow<DoubleTapAction.AnnotateToken?>(null)
    val pendingTokenAction: StateFlow<DoubleTapAction.AnnotateToken?> = _pendingTokenAction

    private val _currentImageAnnotations = MutableStateFlow<List<Pair<ConversationBox, String>>>(emptyList())
    val currentImageAnnotations: StateFlow<List<Pair<ConversationBox, String>>> = _currentImageAnnotations

    // Conversation boxes for current image (all regions)
    private val _currentConversationBoxes = MutableStateFlow<List<ConversationBox>>(emptyList())
    val currentConversationBoxes: StateFlow<List<ConversationBox>> = _currentConversationBoxes

    // Currently highlighted bubble (yellow highlight on double-tap)
    private val _highlightedBubble = MutableStateFlow<ConversationBox?>(null)
    val highlightedBubble: StateFlow<ConversationBox?> = _highlightedBubble

    // Zoom state
    private val _isZoomed = MutableStateFlow(false)
    val isZoomed: StateFlow<Boolean> = _isZoomed

    private val _currentTokenRegions = MutableStateFlow<List<ConversationBox>>(emptyList())
    val currentTokenRegions: StateFlow<List<ConversationBox>> = _currentTokenRegions

    private val _currentTokenAnnotations = MutableStateFlow<List<Pair<ConversationBox, String>>>(emptyList())
    val currentTokenAnnotations: StateFlow<List<Pair<ConversationBox, String>>> = _currentTokenAnnotations

    private var zoomedBubbleIndex: Int? = null
    private var zoomedBubbleBox: ConversationBox? = null

    // Selected token in zoom mode (highlighted on tap)
    private val _selectedTokenIndex = MutableStateFlow<Int?>(null)
    val selectedTokenIndex: StateFlow<Int?> = _selectedTokenIndex

    private val _zoomModeState = MutableStateFlow<ZoomModeState>(ZoomModeState.Viewing)
    val zoomModeState: StateFlow<ZoomModeState> = _zoomModeState

    // Translation
    private val _showTranslationOverlay = MutableStateFlow(false)
    val showTranslationOverlay: StateFlow<Boolean> = _showTranslationOverlay

    private val _currentTranslation = MutableStateFlow<RegionTranslation?>(null)
    val currentTranslation: StateFlow<RegionTranslation?> = _currentTranslation

    // Session timer
    private val _sessionElapsed = MutableStateFlow(SessionElapsed(null, null, null))
    val sessionElapsed: StateFlow<SessionElapsed> = _sessionElapsed

    // App list
    private var _allApps: List<AppInfo> = emptyList()
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps
    private val _hiddenApps: MutableSet<String> = mutableSetOf()
    private var _currentQuery: String = ""

    // Show apps overlay
    private val _showAppsOverlay = MutableStateFlow(false)
    val showAppsOverlay: StateFlow<Boolean> = _showAppsOverlay

    init {
        loadSettings()
        loadHiddenApps()
        startSessionTimer()

        imageNavigator.initialize(imageDataSource.getImageItems())
        _chapterNames.value = imageNavigator.getChapterNames()

        restoreSavedPositionOrAdvance()
    }

    private fun restoreSavedPositionOrAdvance() {
        val savedIndex = settingsStore.getInt(positionKey(), -1)
        if (savedIndex >= 0) {
            val image = imageNavigator.restorePosition(savedIndex)
            if (image != null) {
                _currentImage.value = image
                _currentImageBytes.value = imageDataSource.loadImageBytes(image.filename, image.chapter)
                updateNavigationState()
                viewModelScope.launch { updateAnnotationsForCurrentImage() }
                return
            }
        }
        nextPage()
    }

    private fun startSessionTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _sessionElapsed.value = sessionTimeTracker.getElapsedMs()
            }
        }
    }

    private fun loadSettings() {
        val storedGesture = settingsStore.getString("annotation_trigger_gesture", null)
        _annotationTriggerGesture.value = storedGesture ?: run {
            // Migrate legacy boolean: true (or absent) -> double_tap, false -> off
            if (settingsStore.getBoolean("double_tap_annotation", true)) "double_tap" else "off"
        }
        _showAnnotationDots.value = settingsStore.getBoolean("show_annotation_dots", true)
        _annotationBarPosition.value = settingsStore.getString("annotation_bar_position", "bottom") ?: "bottom"
        _annotationBarVisibilityMode.value =
            settingsStore.getString("annotation_bar_visibility_mode", "on_double_tap") ?: "on_double_tap"
        _annotationOnSpace.value = settingsStore.getBoolean("annotation_on_space", false)
    }

    private fun loadHiddenApps() {
        val hidden = settingsStore.getString("hidden_apps", null)
        if (hidden != null && hidden.isNotEmpty()) {
            _hiddenApps.addAll(hidden.split(","))
        }
    }

    fun reloadSettingsIfChanged() {
        loadSettings()
    }

    fun toggleAnnotationDots(enabled: Boolean) {
        val oldValue = _showAnnotationDots.value.toString()
        _showAnnotationDots.value = enabled
        settingsStore.putBoolean("show_annotation_dots", enabled)
        viewModelScope.launch {
            learnerDataRepository.logSettingsChange("show_annotation_dots", oldValue, enabled.toString())
        }
    }

    fun setAnnotationTriggerGesture(mode: String) {
        val normalized = when (mode) {
            "off", "single_tap", "double_tap" -> mode
            else -> "double_tap"
        }
        val oldValue = _annotationTriggerGesture.value
        _annotationTriggerGesture.value = normalized
        settingsStore.putString("annotation_trigger_gesture", normalized)
        viewModelScope.launch {
            learnerDataRepository.logSettingsChange("annotation_trigger_gesture", oldValue, normalized)
        }
    }

    fun setAnnotationBarPosition(position: String) {
        val oldValue = _annotationBarPosition.value
        _annotationBarPosition.value = position
        settingsStore.putString("annotation_bar_position", position)
        viewModelScope.launch {
            learnerDataRepository.logSettingsChange("annotation_bar_position", oldValue, position)
        }
    }

    fun setAnnotationBarVisibilityMode(mode: String) {
        val oldValue = _annotationBarVisibilityMode.value
        _annotationBarVisibilityMode.value = mode
        settingsStore.putString("annotation_bar_visibility_mode", mode)
        viewModelScope.launch {
            learnerDataRepository.logSettingsChange("annotation_bar_visibility_mode", oldValue, mode)
        }
    }

    fun toggleAnnotationOnSpace(enabled: Boolean) {
        val oldValue = _annotationOnSpace.value.toString()
        _annotationOnSpace.value = enabled
        settingsStore.putBoolean("annotation_on_space", enabled)
        viewModelScope.launch {
            learnerDataRepository.logSettingsChange("annotation_on_space", oldValue, enabled.toString())
        }
    }

    // App list management
    fun setApps(apps: List<AppInfo>) {
        _allApps = apps
        refilter()
    }

    fun filterApps(query: String) {
        _currentQuery = query
        refilter()
    }

    fun hideApp(packageName: String) {
        _hiddenApps.add(packageName)
        settingsStore.putString("hidden_apps", _hiddenApps.joinToString(","))
        refilter()
    }

    fun resetHiddenApps() {
        _hiddenApps.clear()
        settingsStore.putString("hidden_apps", "")
        refilter()
    }

    fun getHiddenAppsCount(): Int = _hiddenApps.size

    private fun refilter() {
        val q = _currentQuery.lowercase()
        _apps.value = _allApps
            .filter { it.packageName !in _hiddenApps }
            .let { visible ->
                if (q.isEmpty()) visible
                else visible.filter { it.label.lowercase().contains(q) }
            }
    }

    fun showAppsOverlay() {
        _currentQuery = ""
        refilter()
        _showAppsOverlay.value = true
    }

    fun hideAppsOverlay() {
        _currentQuery = ""
        _showAppsOverlay.value = false
    }

    // Asset switching (for downloaded comics)
    fun switchAsset(newImageDataSource: ImageDataSource, comicId: String? = null) {
        imageDataSource = newImageDataSource
        imageNavigator = ImageSequenceNavigator()
        imageNavigator.initialize(imageDataSource.getImageItems())
        _chapterNames.value = imageNavigator.getChapterNames()

        currentComicId = comicId
        lastTrackedChapter = null

        _isZoomed.value = false
        _zoomModeState.value = ZoomModeState.Viewing
        _currentImageAnnotations.value = emptyList()
        _currentTokenRegions.value = emptyList()
        _currentTokenAnnotations.value = emptyList()

        restoreSavedPositionOrAdvance()
    }

    fun clearAsset() {
        imageDataSource = object : ImageDataSource {
            override fun getImageItems() = emptyList<pl.czak.learnlauncher.data.model.ImageItem>()
            override fun getConversationBoxes(imageId: String) = emptyList<ConversationBox>()
            override fun loadImageBytes(filename: String, chapter: String) = null
        }
        imageNavigator = ImageSequenceNavigator()
        imageNavigator.initialize(emptyList())
        _chapterNames.value = emptyList()
        _currentChapterName.value = ""
        _currentImage.value = null
        _currentImageBytes.value = null
        _currentConversationBoxes.value = emptyList()
        _highlightedBubble.value = null
        _currentImageAnnotations.value = emptyList()
        _currentTokenRegions.value = emptyList()
        _currentTokenAnnotations.value = emptyList()
        _isZoomed.value = false
        _zoomModeState.value = ZoomModeState.Viewing
        currentComicId = null
        settingsStore.putInt("image_navigator_position", -1)
        lastTrackedChapter = null
        _canGoBack.value = false
        _canGoForward.value = false
        _canGoNextChapter.value = false
        _canGoPreviousChapter.value = false
    }

    private fun onPageLanded(pageId: String, pageTitle: String, chapterName: String) {
        val completedSessions = mutableListOf<CompletedSession>()
        if (chapterName != lastTrackedChapter) {
            completedSessions.addAll(sessionTimeTracker.startChapterSession(chapterName))
            lastTrackedChapter = chapterName
        }
        sessionTimeTracker.startPageSession(pageId, pageTitle)?.let { completedSessions.add(it) }
        if (completedSessions.isNotEmpty()) {
            viewModelScope.launch { sessionRepository.addSessions(completedSessions) }
        }
        viewModelScope.launch { updateAnnotationsForCurrentImage() }
        viewModelScope.launch {
            learnerDataRepository.logSessionEvent("PAGE_ENTER", chapterName, pageId, pageTitle)
        }
    }

    fun onAppResumed() {
        sessionTimeTracker.startAppSession()
        _currentImage.value?.let { image ->
            lastTrackedChapter = image.chapter
            sessionTimeTracker.startChapterSession(image.chapter)
            sessionTimeTracker.startPageSession(image.id, image.title)
        }
        viewModelScope.launch { learnerDataRepository.logSessionEvent("APP_START") }
    }

    fun onAppPaused() {
        val completedSessions = sessionTimeTracker.endAppSession()
        if (completedSessions.isNotEmpty()) {
            viewModelScope.launch { sessionRepository.addSessions(completedSessions) }
        }
        lastTrackedChapter = null
        _sessionElapsed.value = SessionElapsed(null, null, null)
        viewModelScope.launch { learnerDataRepository.logSessionEvent("APP_STOP") }
    }

    private suspend fun updateAnnotationsForCurrentImage() {
        val imageId = _currentImage.value?.id ?: return
        val boxes = imageDataSource.getConversationBoxes(imageId)
        _currentConversationBoxes.value = boxes
        _highlightedBubble.value = null
        val annotations = annotationRepository.getForImage(imageId)
        _currentImageAnnotations.value = annotations
            .filter { it.regionType == RegionType.BUBBLE }
            .distinctBy { it.boxIndex }
            .mapNotNull { ann ->
                boxes.getOrNull(ann.boxIndex)?.let { box -> Pair(box, ann.label) }
            }
    }

    private fun updateNavigationState() {
        _canGoBack.value = imageNavigator.canGoBack
        _canGoForward.value = imageNavigator.canGoForward
        _canGoNextChapter.value = imageNavigator.canGoNextChapter
        _canGoPreviousChapter.value = imageNavigator.canGoPreviousChapter
        _currentChapterName.value = imageNavigator.currentChapterName
        settingsStore.putInt(positionKey(), imageNavigator.position)
    }

    fun nextPage() {
        val image = imageNavigator.next()
        if (image != null) {
            _currentImage.value = image
            _currentImageBytes.value = imageDataSource.loadImageBytes(image.filename, image.chapter)
            onPageLanded(image.id, image.title, image.chapter)
        }
        updateNavigationState()
        viewModelScope.launch {
            learnerDataRepository.logPageInteraction(
                "SWIPE_NEXT", _currentImage.value?.chapter, _currentImage.value?.id
            )
        }
    }

    fun previousPage() {
        val image = imageNavigator.previous()
        if (image != null) {
            _currentImage.value = image
            _currentImageBytes.value = imageDataSource.loadImageBytes(image.filename, image.chapter)
            onPageLanded(image.id, image.title, image.chapter)
        }
        updateNavigationState()
        viewModelScope.launch {
            learnerDataRepository.logPageInteraction(
                "SWIPE_PREV", _currentImage.value?.chapter, _currentImage.value?.id
            )
        }
    }

    fun onImageAnnotateTapped(normalizedX: Float, normalizedY: Float) {
        if (_annotationTriggerGesture.value == "off") return
        _currentImage.value?.let { image ->
            val boxes = imageDataSource.getConversationBoxes(image.id)
            if (boxes.isNotEmpty()) {
                val hitIndex = boxes.indexOfFirst { it.contains(normalizedX, normalizedY) }
                if (hitIndex >= 0) {
                    _highlightedBubble.value = boxes[hitIndex]
                    _doubleTapAction.value = DoubleTapAction.AnnotateBubble(
                        nx = normalizedX, ny = normalizedY,
                        box = boxes[hitIndex], boxIndex = hitIndex
                    )
                    viewModelScope.launch {
                        learnerDataRepository.logPageInteraction(
                            "DOUBLE_TAP", image.chapter, image.id,
                            normalizedX, normalizedY, "bubble_hit"
                        )
                    }
                    return@let
                }
            }
            viewModelScope.launch {
                learnerDataRepository.logPageInteraction(
                    "DOUBLE_TAP", image.chapter, image.id,
                    normalizedX, normalizedY, "bubble_miss"
                )
            }
            if (normalizedX >= 0.5f) {
                nextPage()
                _doubleTapAction.value = DoubleTapAction.MarkUnderstoodAndAdvance
            } else {
                previousPage()
                _doubleTapAction.value = DoubleTapAction.GoToPrevious
            }
        }
    }

    fun onZoomDoubleTapped(normalizedX: Float, normalizedY: Float): Boolean {
        val imageId = _currentImage.value?.id ?: return false
        val bubbleIndex = zoomedBubbleIndex ?: return false
        val tokens = _currentTokenRegions.value
        val hitTokenIndex = tokens.indexOfFirst { token ->
            normalizedX >= token.x && normalizedX <= token.x + token.width &&
            normalizedY >= token.y && normalizedY <= token.y + token.height
        }
        if (hitTokenIndex >= 0) {
            val token = tokens[hitTokenIndex]
            _doubleTapAction.value = DoubleTapAction.AnnotateToken(
                nx = normalizedX, ny = normalizedY,
                token = token, parentBubbleIndex = bubbleIndex, tokenIndex = hitTokenIndex
            )
            _zoomModeState.value = ZoomModeState.AnnotatingToken(token)
            viewModelScope.launch {
                learnerDataRepository.logPageInteraction(
                    "ZOOM_DOUBLE_TAP", null, imageId,
                    normalizedX, normalizedY, "token_hit_$hitTokenIndex"
                )
            }
            return true
        } else {
            showTranslationOverlay()
            return true
        }
    }

    private fun showTranslationOverlay() {
        val imageId = _currentImage.value?.id ?: return
        val bubbleIndex = zoomedBubbleIndex ?: return
        val bubble = zoomedBubbleBox
        viewModelScope.launch {
            var translation = translationRepository.getTranslation(imageId, bubbleIndex)
            // Pre-fill from OCR text if no saved translation exists
            if (translation == null && bubble?.text != null) {
                translation = RegionTranslation(
                    id = "",
                    imageId = imageId,
                    bubbleIndex = bubbleIndex,
                    originalText = bubble.text ?: "",
                    meaningTranslation = "",
                    literalTranslation = "",
                )
            }
            _currentTranslation.value = translation
            _showTranslationOverlay.value = true
            _zoomModeState.value = ZoomModeState.ShowingTranslation(translation)
            learnerDataRepository.logPageInteraction(
                "ZOOM_DOUBLE_TAP", null, imageId, hitResult = "translation_show"
            )
        }
    }

    fun hideTranslationOverlay() {
        _showTranslationOverlay.value = false
        _currentTranslation.value = null
        _zoomModeState.value = ZoomModeState.Viewing
    }

    fun saveTokenAnnotation(
        token: ConversationBox,
        parentBubbleIndex: Int,
        tokenIndex: Int,
        label: String,
        tapX: Float,
        tapY: Float
    ) {
        val imageId = _currentImage.value?.id ?: return
        val annotation = RegionAnnotation(
            imageId = imageId, boxIndex = tokenIndex,
            boxX = token.x, boxY = token.y, boxWidth = token.width, boxHeight = token.height,
            label = label, timestamp = currentTimeMillis(),
            tapX = tapX, tapY = tapY,
            regionType = RegionType.TOKEN,
            parentBubbleIndex = parentBubbleIndex, tokenIndex = tokenIndex
        )
        viewModelScope.launch {
            annotationRepository.addAnnotation(annotation)
            updateTokenAnnotationsForCurrentBubble()
        }
        _zoomModeState.value = ZoomModeState.Viewing
    }

    fun saveAnnotation(box: ConversationBox, boxIndex: Int, label: String, tapX: Float, tapY: Float) {
        val imageId = _currentImage.value?.id ?: return
        val annotation = RegionAnnotation(
            imageId = imageId, boxIndex = boxIndex,
            boxX = box.x, boxY = box.y, boxWidth = box.width, boxHeight = box.height,
            label = label, timestamp = currentTimeMillis(),
            tapX = tapX, tapY = tapY
        )
        viewModelScope.launch {
            annotationRepository.addAnnotation(annotation)
            updateAnnotationsForCurrentImage()
        }
    }

    fun consumeDoubleTapAction() {
        _doubleTapAction.value = null
    }

    fun clearHighlightedBubble() {
        _highlightedBubble.value = null
    }

    fun openAnnotationBarForBubble(action: DoubleTapAction.AnnotateBubble) {
        _pendingBubbleAction.value = action
        _pendingTokenAction.value = null
        _annotationBarOpen.value = true
    }

    fun openAnnotationBarForToken(action: DoubleTapAction.AnnotateToken) {
        _pendingTokenAction.value = action
        _pendingBubbleAction.value = null
        _annotationBarOpen.value = true
    }

    fun closeAnnotationBar() {
        _annotationBarOpen.value = false
        _pendingBubbleAction.value = null
        _pendingTokenAction.value = null
    }

    /** Toggles the annotation bar for the currently highlighted bubble.
     *  Returns true if a toggle happened (bar was shown or hidden). */
    fun toggleAnnotationBarForHighlighted(): Boolean {
        if (_annotationBarOpen.value) {
            closeAnnotationBar()
            return true
        }
        val hb = _highlightedBubble.value ?: return false
        val idx = _currentConversationBoxes.value.indexOf(hb)
        if (idx < 0) return false
        openAnnotationBarForBubble(
            DoubleTapAction.AnnotateBubble(
                nx = hb.x + hb.width / 2f,
                ny = hb.y + hb.height / 2f,
                box = hb,
                boxIndex = idx
            )
        )
        return true
    }

    fun onImageLongPressed(nx: Float, ny: Float): Boolean {
        if (_isZoomed.value) return false
        val image = _currentImage.value ?: return false
        val boxes = imageDataSource.getConversationBoxes(image.id)
        val hitIndex = boxes.indexOfFirst { it.contains(nx, ny) }
        if (hitIndex < 0) return false

        val hit = boxes[hitIndex]
        _isZoomed.value = true
        zoomedBubbleIndex = hitIndex
        zoomedBubbleBox = hit
        loadTokenRegionsForBubble(image.id, hitIndex)
        return true
    }

    private fun loadTokenRegionsForBubble(imageId: String, bubbleIndex: Int) {
        // 1. Prefer OCR tokens embedded in the ConversationBox itself
        val bubble = zoomedBubbleBox
        val ocrTokens = bubble?.ocrTokenRegions(bubbleIndex)
        println("[LauncherVM] loadTokenRegions: bubble=$bubbleIndex text=${bubble?.text} tokens=${bubble?.tokens?.size ?: 0} ocrRegions=${ocrTokens?.size ?: 0}")
        if (!ocrTokens.isNullOrEmpty()) {
            _currentTokenRegions.value = ocrTokens
        } else {
            // 2. Try token regions from the active image data source (comic-level token_regions.json)
            val dsTokens = imageDataSource.getTokensForBubble(imageId, bubbleIndex)
            if (dsTokens.isNotEmpty()) {
                _currentTokenRegions.value = dsTokens
            } else {
                // 3. Fallback to global token region repository (bundled assets)
                _currentTokenRegions.value = tokenRegionRepository.getTokensForBubble(imageId, bubbleIndex)
            }
        }
        println("[LauncherVM] loadTokenRegions: result=${_currentTokenRegions.value.size} regions")
        _selectedTokenIndex.value = null
        viewModelScope.launch { updateTokenAnnotationsForCurrentBubble() }
    }

    private suspend fun updateTokenAnnotationsForCurrentBubble() {
        val imageId = _currentImage.value?.id ?: return
        val bubbleIndex = zoomedBubbleIndex ?: return
        val tokens = _currentTokenRegions.value
        val annotations = annotationRepository.getTokenAnnotationsForBubble(imageId, bubbleIndex)
        _currentTokenAnnotations.value = annotations
            .distinctBy { it.tokenIndex }
            .mapNotNull { ann ->
                tokens.getOrNull(ann.tokenIndex ?: -1)?.let { token -> Pair(token, ann.label) }
            }
    }

    private fun clearZoomState() {
        _currentTokenRegions.value = emptyList()
        _currentTokenAnnotations.value = emptyList()
        _selectedTokenIndex.value = null
        zoomedBubbleIndex = null
        zoomedBubbleBox = null
        _zoomModeState.value = ZoomModeState.Viewing
        _showTranslationOverlay.value = false
        _currentTranslation.value = null
    }

    /** Tap in zoom mode: select token if hit, deselect if already selected, exit zoom if no token hit. */
    fun onZoomTapped(nx: Float, ny: Float): Boolean {
        val tokens = _currentTokenRegions.value
        val hitIndex = tokens.indexOfFirst { it.contains(nx, ny) }
        if (hitIndex >= 0) {
            // Toggle selection
            _selectedTokenIndex.value = if (_selectedTokenIndex.value == hitIndex) null else hitIndex
            return true
        }
        // Tap outside tokens — exit zoom
        exitZoom()
        return false
    }

    fun exitZoom() {
        if (!_isZoomed.value) return
        _isZoomed.value = false
        clearZoomState()
    }

    fun nextChapter() {
        val image = imageNavigator.nextChapter()
        if (image != null) {
            _currentImage.value = image
            _currentImageBytes.value = imageDataSource.loadImageBytes(image.filename, image.chapter)
            onPageLanded(image.id, image.title, image.chapter)
        }
        updateNavigationState()
        viewModelScope.launch {
            learnerDataRepository.logPageInteraction(
                "CHAPTER_NEXT", _currentImage.value?.chapter, _currentImage.value?.id
            )
        }
    }

    fun previousChapter() {
        val image = imageNavigator.previousChapter()
        if (image != null) {
            _currentImage.value = image
            _currentImageBytes.value = imageDataSource.loadImageBytes(image.filename, image.chapter)
            onPageLanded(image.id, image.title, image.chapter)
        }
        updateNavigationState()
        viewModelScope.launch {
            learnerDataRepository.logPageInteraction(
                "CHAPTER_PREV", _currentImage.value?.chapter, _currentImage.value?.id
            )
        }
    }

    fun goToChapter(chapterIndex: Int) {
        val image = imageNavigator.goToChapter(chapterIndex)
        if (image != null) {
            _currentImage.value = image
            _currentImageBytes.value = imageDataSource.loadImageBytes(image.filename, image.chapter)
            onPageLanded(image.id, image.title, image.chapter)
        }
        updateNavigationState()
        viewModelScope.launch {
            learnerDataRepository.logPageInteraction(
                "CHAPTER_SELECT", _currentImage.value?.chapter, _currentImage.value?.id
            )
        }
    }

    fun goToImageId(imageId: String): Boolean {
        val image = imageNavigator.goToImageId(imageId) ?: return false
        _currentImage.value = image
        _currentImageBytes.value = imageDataSource.loadImageBytes(image.filename, image.chapter)
        onPageLanded(image.id, image.title, image.chapter)
        updateNavigationState()
        if (_isZoomed.value) exitZoom()
        return true
    }

    val zoomedBubble: ConversationBox?
        get() = zoomedBubbleBox

    /**
     * Select a bubble by index. Returns true if a bubble was hit.
     *
     * When [fromArrow] is true (keyboard arrow navigation), only the highlighted bubble is
     * updated and a hide-bar signal is emitted. When false (e.g. simulated double-tap), a full
     * AnnotateBubble action is emitted so the annotation bar appears.
     */
    fun selectBubbleByIndex(index: Int, fromArrow: Boolean = false): Boolean {
        val image = _currentImage.value ?: return false
        val boxes = _currentConversationBoxes.value
        if (index < 0 || index >= boxes.size) return false
        val box = boxes[index]
        val cx = box.x + box.width / 2
        val cy = box.y + box.height / 2
        _highlightedBubble.value = box
        if (fromArrow) {
            _hideAnnotationBarTrigger.value = currentTimeMillis()
        } else {
            _doubleTapAction.value = DoubleTapAction.AnnotateBubble(
                nx = cx, ny = cy, box = box, boxIndex = index
            )
        }
        viewModelScope.launch {
            learnerDataRepository.logPageInteraction(
                "KEY_SELECT", image.chapter, image.id, cx, cy, "bubble_hit"
            )
        }
        return true
    }

    /** Get the index of the currently highlighted bubble, or -1 */
    fun getHighlightedBubbleIndex(): Int {
        val highlighted = _highlightedBubble.value ?: return -1
        return _currentConversationBoxes.value.indexOf(highlighted)
    }

    /** Annotate the currently highlighted bubble with the given label. Returns true if annotated. */
    fun annotateHighlightedBubble(label: String): Boolean {
        val highlighted = _highlightedBubble.value ?: return false
        val idx = _currentConversationBoxes.value.indexOf(highlighted)
        if (idx < 0) return false
        val cx = highlighted.x + highlighted.width / 2
        val cy = highlighted.y + highlighted.height / 2
        saveAnnotation(highlighted, idx, label, cx, cy)
        _doubleTapAction.value = null
        closeAnnotationBar()
        clearHighlightedBubble()
        return true
    }
}

interface SettingsStore {
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String?)
}
