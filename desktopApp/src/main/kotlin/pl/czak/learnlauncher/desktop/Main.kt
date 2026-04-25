package pl.czak.learnlauncher.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.czak.learnlauncher.App
import pl.czak.learnlauncher.data.*
import pl.czak.learnlauncher.data.auth.AuthException
import pl.czak.learnlauncher.data.auth.DesktopAuthManager
import pl.czak.learnlauncher.data.auth.DesktopSettingsStore
import pl.czak.learnlauncher.data.auth.LocalAuthApi
import pl.czak.learnlauncher.data.download.CatalogComic
import pl.czak.learnlauncher.data.download.DesktopComicCatalogApi
import pl.czak.learnlauncher.data.download.DesktopComicDownloadManager
import pl.czak.learnlauncher.data.download.DownloadState
import pl.czak.learnlauncher.data.export.DesktopJsonExportService
import pl.czak.learnlauncher.data.source.FileImageDataSource
import pl.czak.learnlauncher.data.sync.DesktopSyncService
import pl.czak.learnlauncher.domain.navigation.ImageSequenceNavigator
import pl.czak.learnlauncher.domain.session.SessionTimeTracker
import pl.czak.learnlauncher.ui.CatalogComicUi
import pl.czak.learnlauncher.ui.DownloadStatus
import pl.czak.learnlauncher.viewmodel.ChatViewModel
import pl.czak.learnlauncher.viewmodel.LauncherViewModel
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

fun main(args: Array<String>) {
    val settingsStore = DesktopSettingsStore()
    val authManager = DesktopAuthManager(settingsStore)
    val authApi = LocalAuthApi()
    val downloadManager = DesktopComicDownloadManager(settingsStore)

    val backendUrl = resolveBackendUrl(args)
    val (queueHost, queuePort) = resolveQueueConfig(args)
    println("[Desktop] Config: backendUrl=$backendUrl queueHost=$queueHost queuePort=$queuePort")

    // Repositories (in-memory)
    val annotationRepository = InMemoryAnnotationRepository()
    val sessionRepository = InMemorySessionRepository()
    val learnerDataRepository = InMemoryLearnerDataRepository()
    val tokenRegionRepository = InMemoryTokenRegionRepository()
    val translationRepository = InMemoryTranslationRepository()
    val chatRepository = InMemoryChatRepository()

    // Export service
    val exportService = DesktopJsonExportService(annotationRepository, sessionRepository, chatRepository, translationRepository)

    // Determine initial comic directory
    val comicDirOverride = parseComicDirArg(args)
    val initialComicDir = comicDirOverride ?: run {
        val savedAssetId = settingsStore.getString("selected_asset_id", null)
        if (savedAssetId != null) {
            val dir = File(downloadManager.comicsDir, savedAssetId)
            if (dir.exists() && (File(dir, "manifest.json").exists() || File(dir, "images.json").exists())) dir
            else null
        } else null
    }

    val imageDataSource = if (initialComicDir != null) FileImageDataSource(initialComicDir)
        else FileImageDataSource(downloadManager.comicsDir) // will just show empty

    // Resolve comic ID from the directory name for sync
    val initialComicId = comicDirOverride?.name
        ?: settingsStore.getString("selected_asset_id", null)
    // Persist so sync always knows the active comic
    if (initialComicId != null) {
        settingsStore.putString("selected_asset_id", initialComicId)
    }
    println("[Desktop] Active comic: ${initialComicId ?: "NONE"}")

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val viewModel = LauncherViewModel(
        imageDataSource = imageDataSource,
        imageNavigator = ImageSequenceNavigator(),
        sessionTimeTracker = SessionTimeTracker(),
        annotationRepository = annotationRepository,
        sessionRepository = sessionRepository,
        learnerDataRepository = learnerDataRepository,
        tokenRegionRepository = tokenRegionRepository,
        translationRepository = translationRepository,
        settingsStore = settingsStore
    )
    val chatViewModel = ChatViewModel(chatRepository)

    application {
        val windowState = rememberWindowState(width = 450.dp, height = 900.dp)

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Manga Reader",
            onPreviewKeyEvent = { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    handleKeyDown(keyEvent, viewModel)
                } else false
            }
        ) {

            // Auth state
            var isLoggedIn by remember { mutableStateOf(authManager.isLoggedIn()) }

            // Catalog state
            var catalogComics by remember { mutableStateOf<List<CatalogComicUi>>(emptyList()) }
            var catalogCache by remember { mutableStateOf<List<CatalogComic>>(emptyList()) }
            var downloadStatus by remember { mutableStateOf<DownloadStatus>(DownloadStatus.Idle) }
            var downloadedComicIds by remember { mutableStateOf(downloadManager.getDownloadedIds()) }
            var activeComicId by remember { mutableStateOf(initialComicId) }
            var autoSyncEnabled by remember { mutableStateOf(settingsStore.getBoolean("auto_sync_enabled", false)) }

            // Observe download state
            LaunchedEffect(Unit) {
                downloadManager.state.collectLatest { state ->
                    downloadStatus = when (state) {
                        is DownloadState.Idle -> DownloadStatus.Idle
                        is DownloadState.Downloading -> DownloadStatus.Downloading(state.comicId, state.progress)
                        is DownloadState.Extracting -> DownloadStatus.Extracting(state.comicId)
                        is DownloadState.Done -> {
                            downloadedComicIds = downloadManager.getDownloadedIds()
                            catalogComics = catalogCache.map { comic ->
                                CatalogComicUi(
                                    id = comic.id, name = comic.name,
                                    sizeMb = comic.size_mb, chapters = comic.chapters,
                                    isDownloaded = downloadManager.isDownloaded(comic.id)
                                )
                            }
                            // Auto-select if first download
                            if (activeComicId == null) {
                                selectComic(state.comicId, settingsStore, downloadManager, viewModel)
                                activeComicId = state.comicId
                            }
                            DownloadStatus.Done(state.comicId)
                        }
                        is DownloadState.Error -> DownloadStatus.Error(state.comicId, state.message)
                    }
                }
            }

            // Auto-sync loop: runs sync every 60s when enabled
            LaunchedEffect(autoSyncEnabled) {
                if (!autoSyncEnabled) return@LaunchedEffect
                if (queueHost.isEmpty() || queuePort <= 0) {
                    println("[Desktop] Auto-sync enabled but no queue configured")
                    return@LaunchedEffect
                }
                println("[Desktop] Cloud sync started (every 10s)")
                while (true) {
                    kotlinx.coroutines.delay(10_000)
                    try {
                        val syncService = DesktopSyncService(
                            annotationRepository = annotationRepository,
                            authManager = authManager,
                            queueHost = queueHost,
                            queuePort = queuePort,
                            activeComicId = activeComicId ?: "_no_comic_"
                        )
                        val result = syncService.sync()
                        println("[Desktop] Auto-sync tick: ${result.message}")
                    } catch (e: Exception) {
                        println("[Desktop] Auto-sync tick failed: ${e.message}")
                    }
                }
            }

            App(
                viewModel = viewModel,
                chatViewModel = chatViewModel,
                isLoggedIn = isLoggedIn,
                onLogin = { userId ->
                    try {
                        val result = authApi.login(userId)
                        authManager.saveToken(result.token, result.expiresAt, userId)
                        isLoggedIn = true
                    } catch (_: AuthException) { }
                },
                onLogout = {
                    authManager.logout()
                    isLoggedIn = false
                },
                onExportJson = {
                    appScope.launch {
                        try {
                            val file = exportService.export()
                            exportService.openContainingFolder(file)
                        } catch (_: Exception) { }
                    }
                },
                onSyncCloud = {
                    if (queueHost.isNotEmpty() && queuePort > 0) {
                        appScope.launch {
                            val syncService = DesktopSyncService(
                                annotationRepository = annotationRepository,
                                authManager = authManager,
                                queueHost = queueHost,
                                queuePort = queuePort,
                                activeComicId = activeComicId ?: "_no_comic_"
                            )
                            val result = syncService.sync()
                            println("[Desktop] Sync result: ${result.message}")
                        }
                    } else {
                        println("[Desktop] Sync not configured (no queue_host/queue_port)")
                    }
                },
                autoSyncEnabled = autoSyncEnabled,
                onAutoSyncToggled = { enabled ->
                    autoSyncEnabled = enabled
                    settingsStore.putBoolean("auto_sync_enabled", enabled)
                    println("[Desktop] Auto-sync toggled: $enabled")
                },
                isDesktop = true,

                catalogComics = catalogComics,
                downloadStatus = downloadStatus,
                onRefreshCatalog = {
                    if (backendUrl.isNotEmpty()) {
                        appScope.launch {
                            downloadStatus = DownloadStatus.Idle
                            try {
                                val comics = DesktopComicCatalogApi.fetchCatalog(backendUrl)
                                catalogCache = comics
                                catalogComics = comics.map { comic ->
                                    CatalogComicUi(
                                        id = comic.id, name = comic.name,
                                        sizeMb = comic.size_mb, chapters = comic.chapters,
                                        isDownloaded = downloadManager.isDownloaded(comic.id)
                                    )
                                }
                            } catch (e: Exception) {
                                downloadStatus = DownloadStatus.Error("catalog", e.message ?: "Connection failed")
                            }
                        }
                    }
                },
                onDownloadComic = { comicId ->
                    val comic = catalogCache.find { it.id == comicId }
                    if (backendUrl.isNotEmpty() && comic != null) {
                        appScope.launch {
                            downloadManager.download(backendUrl, comic)
                        }
                    }
                },
                onRemoveComic = { comicId ->
                    appScope.launch {
                        val wasActive = comicId == activeComicId
                        val ok = downloadManager.remove(comicId)
                        if (ok) {
                            downloadedComicIds = downloadManager.getDownloadedIds()
                            catalogComics = catalogCache.map { comic ->
                                CatalogComicUi(
                                    id = comic.id, name = comic.name,
                                    sizeMb = comic.size_mb, chapters = comic.chapters,
                                    isDownloaded = downloadManager.isDownloaded(comic.id)
                                )
                            }
                            if (wasActive) {
                                val next = downloadedComicIds.firstOrNull()
                                if (next != null) {
                                    selectComic(next, settingsStore, downloadManager, viewModel)
                                    activeComicId = next
                                } else {
                                    activeComicId = null
                                    settingsStore.putString("selected_asset_id", null)
                                    viewModel.clearAsset()
                                }
                            }
                        }
                    }
                },
                downloadedComicIds = downloadedComicIds,
                activeComicId = activeComicId,
                onSelectActiveComic = { comicId ->
                    if (comicId != activeComicId) {
                        selectComic(comicId, settingsStore, downloadManager, viewModel)
                        activeComicId = comicId
                    }
                }
            )
        }
    }
}

private fun selectComic(
    comicId: String,
    settingsStore: DesktopSettingsStore,
    downloadManager: DesktopComicDownloadManager,
    viewModel: LauncherViewModel
) {
    settingsStore.putString("selected_asset_id", comicId)
    val comicDir = File(downloadManager.comicsDir, comicId)
    if (comicDir.exists()) {
        val dataSource = FileImageDataSource(comicDir)
        viewModel.switchAsset(dataSource, comicId)
    }
}

private fun parseComicDirArg(args: Array<String>): File? {
    val idx = args.indexOf("--comic-dir")
    if (idx >= 0 && idx + 1 < args.size) {
        val dir = File(args[idx + 1])
        if (dir.exists()) return dir
        System.err.println("Comic directory does not exist: ${dir.absolutePath}")
    }
    return null
}

private const val DEFAULT_BACKEND_URL = "http://PLACEHOLDER_BACKEND_HOST:8080"

private fun resolveBackendUrl(args: Array<String>): String {
    // 1. CLI override
    val idx = args.indexOf("--backend-url")
    if (idx >= 0 && idx + 1 < args.size) return args[idx + 1]

    // 2. server-config.yaml in working dir
    val configFile = File("server-config.yaml")
    if (configFile.exists()) {
        return parseYamlBackendUrl(configFile) ?: DEFAULT_BACKEND_URL
    }

    // 3. Try from classpath (bundled in jar/resources)
    val classPathStream = Thread.currentThread().contextClassLoader.getResourceAsStream("server-config.yaml")
    if (classPathStream != null) {
        val lines = BufferedReader(InputStreamReader(classPathStream)).readLines()
        classPathStream.close()
        return extractBackendUrl(lines) ?: DEFAULT_BACKEND_URL
    }

    // 4. Hardcoded fallback
    return DEFAULT_BACKEND_URL
}

private fun parseYamlBackendUrl(file: File): String? {
    return extractBackendUrl(file.readLines())
}

private fun extractBackendUrl(lines: List<String>): String? {
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("backend_url:")) {
            return trimmed.removePrefix("backend_url:").trim().removeSurrounding("\"")
        }
    }
    return null
}

private fun resolveQueueConfig(args: Array<String>): Pair<String, Int> {
    // 1. CLI override
    val hostIdx = args.indexOf("--queue-host")
    val portIdx = args.indexOf("--queue-port")
    if (hostIdx >= 0 && hostIdx + 1 < args.size && portIdx >= 0 && portIdx + 1 < args.size) {
        return args[hostIdx + 1] to (args[portIdx + 1].toIntOrNull() ?: 0)
    }

    // 2. server-config.yaml (check multiple paths since CWD varies)
    val configFiles = listOf(
        File("server-config.yaml"),
        File("desktopApp/server-config.yaml"),
        File("androidApp/server-config.yaml")
    )
    for (configFile in configFiles) {
        if (configFile.exists()) {
            val result = extractQueueConfig(configFile.readLines())
            if (result.first.isNotEmpty()) return result
        }
    }

    return "" to 0
}

private fun extractQueueConfig(lines: List<String>): Pair<String, Int> {
    var host = ""
    var port = 0
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("queue_host:")) {
            host = trimmed.removePrefix("queue_host:").trim().removeSurrounding("\"")
        }
        if (trimmed.startsWith("queue_port:")) {
            port = trimmed.removePrefix("queue_port:").trim().toIntOrNull() ?: 0
        }
    }
    return host to port
}

/** An action that a keyboard shortcut can trigger */
private sealed class KeyAction {
    data object PrevPage : KeyAction()
    data object NextPage : KeyAction()
    data object BubbleUp : KeyAction()
    data object BubbleDown : KeyAction()
    data object ExitZoom : KeyAction()
    data object CancelAnnotation : KeyAction()
    data object AnnotateCurrentBubble : KeyAction()
    data class Annotate(val label: String) : KeyAction()
}

/** Default key bindings. Edit this map to remap shortcuts. */
private val keyBindings: Map<Key, KeyAction> = mapOf(
    Key.DirectionLeft to KeyAction.PrevPage,
    Key.DirectionRight to KeyAction.NextPage,
    Key.DirectionUp to KeyAction.BubbleUp,
    Key.DirectionDown to KeyAction.BubbleDown,
    Key.Escape to KeyAction.ExitZoom,
    Key.Spacebar to KeyAction.AnnotateCurrentBubble,
    Key.One to KeyAction.Annotate("understood"),
    Key.Two to KeyAction.Annotate("partially"),
    Key.Three to KeyAction.Annotate("not_understood"),
    Key.Four to KeyAction.CancelAnnotation,
)

private fun handleKeyDown(event: KeyEvent, viewModel: LauncherViewModel): Boolean {
    val action = keyBindings[event.key] ?: return false
    return when (action) {
        KeyAction.PrevPage -> { viewModel.previousPage(); true }
        KeyAction.NextPage -> { viewModel.nextPage(); true }
        KeyAction.BubbleUp -> { navigateBubble(viewModel, -1); true }
        KeyAction.BubbleDown -> { navigateBubble(viewModel, +1); true }
        KeyAction.ExitZoom -> if (viewModel.isZoomed.value) { viewModel.exitZoom(); true } else false
        KeyAction.CancelAnnotation -> { viewModel.clearHighlightedBubble(); true }
        KeyAction.AnnotateCurrentBubble -> {
            if (!viewModel.annotationOnSpace.value) false
            else {
                viewModel.toggleAnnotationBarForHighlighted()
                true
            }
        }
        is KeyAction.Annotate -> { viewModel.annotateHighlightedBubble(action.label) }
    }
}

private fun navigateBubble(viewModel: LauncherViewModel, delta: Int) {
    val boxes = viewModel.currentConversationBoxes.value
    if (boxes.isEmpty()) return
    val currentIdx = viewModel.getHighlightedBubbleIndex()
    val nextIdx = if (currentIdx < 0) {
        // Nothing highlighted yet — start from first (down) or last (up)
        if (delta > 0) 0 else boxes.lastIndex
    } else {
        (currentIdx + delta).coerceIn(0, boxes.lastIndex)
    }
    viewModel.selectBubbleByIndex(nextIdx, fromArrow = true)
}
