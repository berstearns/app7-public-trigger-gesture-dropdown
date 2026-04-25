package pl.czak.learnlauncher.android

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.czak.learnlauncher.App
import pl.czak.learnlauncher.data.*
import pl.czak.learnlauncher.data.auth.AuthManager
import pl.czak.learnlauncher.data.auth.LocalAuthApi
import pl.czak.learnlauncher.data.db.AppDatabase
import pl.czak.learnlauncher.data.download.CatalogComic
import pl.czak.learnlauncher.data.download.ComicCatalogApi
import pl.czak.learnlauncher.data.download.ComicDownloadManager
import pl.czak.learnlauncher.data.download.DownloadState
import pl.czak.learnlauncher.data.export.JsonExportService
import pl.czak.learnlauncher.data.source.AssetsImageDataSource
import pl.czak.learnlauncher.data.source.FileImageDataSource
import pl.czak.learnlauncher.data.sync.RemoteSyncApi
import pl.czak.learnlauncher.data.sync.SyncApi
import pl.czak.learnlauncher.data.sync.SyncService
import pl.czak.learnlauncher.data.sync.tcp.TcpQueueSyncApi
import pl.czak.learnlauncher.domain.navigation.ImageSequenceNavigator
import pl.czak.learnlauncher.domain.session.SessionTimeTracker
import pl.czak.learnlauncher.ui.AppInfo
import pl.czak.learnlauncher.ui.CatalogComicUi
import pl.czak.learnlauncher.ui.DownloadStatus
import pl.czak.learnlauncher.viewmodel.ChatViewModel
import pl.czak.learnlauncher.viewmodel.LauncherViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var viewModel: LauncherViewModel
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var learnerDataRepository: AndroidLearnerDataRepository
    private lateinit var settingsStore: AndroidSettingsStore
    private var lastKnownAssetId: String? = null

    // Comic download state
    private lateinit var downloadManager: ComicDownloadManager
    private val _catalogComics = mutableStateListOf<CatalogComicUi>()
    private val _downloadStatus = mutableStateOf<DownloadStatus>(DownloadStatus.Idle)
    private val _downloadedComicIds = mutableStateListOf<String>()
    private val _activeComicId = mutableStateOf<String?>(null)
    private var catalogCache: List<CatalogComic> = emptyList()

    // Google Sign-In
    private lateinit var googleSignInClient: GoogleSignInClient
    private val _googleEmail = mutableStateOf<String?>(null)

    // Auto-sync
    private var autoSyncJob: Job? = null
    private val autoSyncEnabled = mutableStateOf(false)

    // Session hierarchy tracker (Part 2)
    private lateinit var sessionHierarchy: pl.czak.learnlauncher.data.session.SessionHierarchyTracker

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            _googleEmail.value = account.email
            Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                learnerDataRepository.logSettingsChange("google_account", "signed_out", "signed_in:${account.email}")
            }
        } catch (e: ApiException) {
            Log.w("MainActivity", "Google sign-in failed: code=${e.statusCode}", e)
            Toast.makeText(this, "Sign-in failed (code ${e.statusCode})", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(
            "FeatureFolder",
            "BUILD_QUEUE_HOST=${BuildConfig.QUEUE_HOST} variant=app7-explicit-db-hierarchy_20260409_154552"
        )

        setupLockScreenDisplay()

        authManager = AuthManager(this)
        val db = AppDatabase.getInstance(this)
        settingsStore = AndroidSettingsStore(this)
        autoSyncEnabled.value = settingsStore.getBoolean("auto_sync_enabled", false)
        sessionHierarchy = pl.czak.learnlauncher.data.session.SessionHierarchyTracker(db)
        lifecycleScope.launch {
            sessionHierarchy.onAppStart()
            // If a comic is already selected at launch, open its comic_session
            val initialComicId = settingsStore.getString("selected_asset_id", null)
            if (initialComicId != null) {
                sessionHierarchy.onComicSelected(initialComicId)
            }
        }

        // Handle intent extras (lets tests toggle auto_sync without UI)
        if (intent?.getBooleanExtra("auto_sync", false) == true) {
            autoSyncEnabled.value = true
            settingsStore.putBoolean("auto_sync_enabled", true)
        }
        val imageDataSource = AssetsImageDataSource(this)
        val annotationRepository = AndroidAnnotationRepository(db, settingsStore)
        val sessionRepository = AndroidSessionRepository(db, settingsStore)
        learnerDataRepository = AndroidLearnerDataRepository(db, settingsStore)
        val tokenRegionRepository = AndroidTokenRegionRepository(this)
        val translationRepository = AndroidTranslationRepository(this, db)
        val chatRepository = AndroidChatRepository(db)
        val authApi = LocalAuthApi()

        viewModel = LauncherViewModel(
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

        chatViewModel = ChatViewModel(chatRepository)

        lastKnownAssetId = settingsStore.getString("selected_asset_id", null)

        // Google Sign-In setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        _googleEmail.value = GoogleSignIn.getLastSignedInAccount(this)?.email

        // Initialize download manager
        downloadManager = ComicDownloadManager(this)
        refreshDownloadedList()
        _activeComicId.value = settingsStore.getString("selected_asset_id", null)

        // Observe download state changes
        lifecycleScope.launch {
            downloadManager.state.collectLatest { state ->
                _downloadStatus.value = when (state) {
                    is DownloadState.Idle -> DownloadStatus.Idle
                    is DownloadState.Downloading -> DownloadStatus.Downloading(state.comicId, state.progress)
                    is DownloadState.Extracting -> DownloadStatus.Extracting(state.comicId)
                    is DownloadState.Done -> {
                        refreshDownloadedList()
                        refreshCatalogUi()
                        // Auto-select if first download
                        if (_activeComicId.value == null) {
                            selectActiveComic(state.comicId)
                        }
                        DownloadStatus.Done(state.comicId)
                    }
                    is DownloadState.Error -> DownloadStatus.Error(state.comicId, state.message)
                }
            }
        }

        // If user already has a selected comic, switch to it
        val savedAssetId = settingsStore.getString("selected_asset_id", null)
        if (savedAssetId != null) {
            val comicDir = File(getExternalFilesDir("comics"), savedAssetId)
            if (comicDir.exists() && (File(comicDir, "manifest.json").exists() || File(comicDir, "images.json").exists())) {
                val dataSource = FileImageDataSource(comicDir)
                viewModel.switchAsset(dataSource, savedAssetId)
            }
        }

        setContent {
            var isLoggedIn by remember { mutableStateOf(authManager.isLoggedIn()) }
            val catalogComics = _catalogComics.toList()
            val downloadStatus by _downloadStatus
            val downloadedComicIds = _downloadedComicIds.toList()
            val activeComicId by _activeComicId
            val googleEmail by _googleEmail
            val currentScreen by viewModel.currentScreen.collectAsState()

            // Back gesture/button on non-Main screens goes back to reading
            BackHandler(enabled = currentScreen != pl.czak.learnlauncher.viewmodel.Screen.Main) {
                viewModel.currentScreen.value = pl.czak.learnlauncher.viewmodel.Screen.Main
            }

            App(
                viewModel = viewModel,
                chatViewModel = chatViewModel,
                isLoggedIn = isLoggedIn,
                onLogin = { userId ->
                    lifecycleScope.launch {
                        try {
                            val result = authApi.login(userId)
                            authManager.saveToken(result.token, result.expiresAt, userId)
                            isLoggedIn = true
                        } catch (_: Exception) { }
                    }
                },
                onLogout = {
                    authManager.logout()
                    isLoggedIn = false
                },
                onLaunchApp = { packageName ->
                    val pm = packageManager
                    val intent = pm.getLaunchIntentForPackage(packageName)
                    intent?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                    }
                    lifecycleScope.launch {
                        learnerDataRepository.logAppLaunch(
                            packageName = packageName,
                            currentChapter = viewModel.currentImage.value?.chapter,
                            currentPageId = viewModel.currentImage.value?.id
                        )
                    }
                },
                onSyncCloud = {
                    // Prefer direct TCP to the simple-tcp-comm job queue
                    // when QUEUE_HOST is configured; fall back to the HTTP
                    // shim if only BACKEND_URL is set.
                    val queueHost = BuildConfig.QUEUE_HOST
                    val queuePort = BuildConfig.QUEUE_PORT
                    val backendUrl = BuildConfig.BACKEND_URL
                    val syncApi: SyncApi? = when {
                        queueHost.isNotEmpty() && queuePort > 0 ->
                            TcpQueueSyncApi(queueHost, queuePort)
                        backendUrl.isNotEmpty() ->
                            RemoteSyncApi(backendUrl)
                        else -> null
                    }
                    if (syncApi != null) {
                        lifecycleScope.launch {
                            val syncService = SyncService(learnerDataRepository, syncApi, authManager)
                            val result = syncService.sync()
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onExportJson = {
                    lifecycleScope.launch {
                        val exportService = JsonExportService(learnerDataRepository)
                        try {
                            val json = exportService.exportToJsonString()
                            val file = File(cacheDir, "mangareader-data-${System.currentTimeMillis()}.json")
                            file.writeText(json)
                            val uri = FileProvider.getUriForFile(
                                this@MainActivity,
                                "${packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Manga Reader Data Export")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Send export via..."))
                        } catch (_: Exception) { }
                    }
                },
                onLoadApps = {
                    loadInstalledApps()
                },
                onOpenHomeSettings = {
                    openHomeSettings()
                },
                catalogComics = catalogComics,
                downloadStatus = downloadStatus,
                onRefreshCatalog = {
                    val backendUrl = BuildConfig.BACKEND_URL
                    if (backendUrl.isNotEmpty()) {
                        lifecycleScope.launch {
                            _downloadStatus.value = DownloadStatus.Idle
                            try {
                                val comics = ComicCatalogApi.fetchCatalog(backendUrl)
                                catalogCache = comics
                                refreshCatalogUi()
                            } catch (e: Exception) {
                                _downloadStatus.value = DownloadStatus.Error("catalog", e.message ?: "Connection failed")
                            }
                        }
                    }
                },
                onDownloadComic = { comicId ->
                    val backendUrl = BuildConfig.BACKEND_URL
                    val comic = catalogCache.find { it.id == comicId }
                    if (backendUrl.isNotEmpty() && comic != null) {
                        lifecycleScope.launch {
                            downloadManager.download(backendUrl, comic)
                        }
                    }
                },
                onRemoveComic = { comicId ->
                    lifecycleScope.launch {
                        val wasActive = comicId == _activeComicId.value
                        val ok = downloadManager.remove(comicId)
                        if (ok) {
                            refreshDownloadedList()
                            refreshCatalogUi()
                            learnerDataRepository.logSettingsChange("removed_comic", "", comicId)
                            if (wasActive) {
                                val next = _downloadedComicIds.firstOrNull()
                                if (next != null) {
                                    selectActiveComic(next)
                                } else {
                                    _activeComicId.value = null
                                    settingsStore.putString("selected_asset_id", null)
                                    viewModel.clearAsset()
                                }
                            }
                            Toast.makeText(this@MainActivity, "Removed ${comicId.replace("-", " ")}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                downloadedComicIds = downloadedComicIds,
                activeComicId = activeComicId,
                onSelectActiveComic = { comicId ->
                    selectActiveComic(comicId)
                },
                googleAccountEmail = googleEmail,
                onGoogleSignIn = {
                    signInLauncher.launch(googleSignInClient.signInIntent)
                },
                onGoogleSignOut = {
                    googleSignInClient.signOut().addOnCompleteListener {
                        _googleEmail.value = null
                        Toast.makeText(this@MainActivity, "Signed out", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch {
                            learnerDataRepository.logSettingsChange("google_account", "signed_in", "signed_out")
                        }
                    }
                },
                autoSyncEnabled = autoSyncEnabled.value,
                onAutoSyncToggled = { enabled ->
                    settingsStore.putBoolean("auto_sync_enabled", enabled)
                    autoSyncEnabled.value = enabled
                    if (enabled) startAutoSyncLoop() else stopAutoSyncLoop()
                }
            )
        }
    }

    private fun startAutoSyncLoop() {
        autoSyncJob?.cancel()
        autoSyncJob = lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                runCatching {
                    Log.i("AutoSync", "triggering SyncService.sync()")
                    val host = BuildConfig.QUEUE_HOST
                    val port = BuildConfig.QUEUE_PORT
                    val api: SyncApi? = when {
                        host.isNotEmpty() && port > 0 -> TcpQueueSyncApi(host, port)
                        BuildConfig.BACKEND_URL.isNotEmpty() -> RemoteSyncApi(BuildConfig.BACKEND_URL)
                        else -> null
                    }
                    api?.let {
                        val res = SyncService(learnerDataRepository, it, authManager).sync()
                        Log.i("AutoSync", "result: ${res.message} total=${res.totalSynced}")
                    }
                }.onFailure { Log.w("AutoSync", "tick failed: ${it.message}") }
            }
        }
    }

    private fun stopAutoSyncLoop() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    override fun onStart() {
        super.onStart()
        if (autoSyncEnabled.value) startAutoSyncLoop()
    }

    override fun onStop() {
        stopAutoSyncLoop()
        super.onStop()
    }

    override fun onDestroy() {
        // Close the session hierarchy chain (app_session → comic → chapter → page).
        // Uses lifecycleScope.launchWhenCreated so the close-out SQL lands
        // before the process is reaped. Best-effort; worker-side close-out
        // policy (objective 24) is the final safety net.
        lifecycleScope.launch {
            runCatching { sessionHierarchy.onAppStop() }
                .onFailure { Log.w("SessionHierarchy", "onAppStop failed: ${it.message}") }
        }
        super.onDestroy()
    }

    private fun selectActiveComic(comicId: String) {
        val oldId = _activeComicId.value
        if (comicId != oldId) {
            _activeComicId.value = comicId
            settingsStore.putString("selected_asset_id", comicId)
            lastKnownAssetId = comicId
            lifecycleScope.launch {
                runCatching { sessionHierarchy.onComicSelected(comicId) }
                    .onFailure { Log.w("SessionHierarchy", "onComicSelected failed: ${it.message}") }
            }

            val comicDir = File(getExternalFilesDir("comics"), comicId)
            if (comicDir.exists()) {
                val dataSource = FileImageDataSource(comicDir)
                viewModel.switchAsset(dataSource, comicId)
            }

            lifecycleScope.launch {
                learnerDataRepository.logSettingsChange("selected_asset_id", oldId ?: "", comicId)
            }

            Toast.makeText(this, "Active comic: ${comicId.replace("-", " ")}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshDownloadedList() {
        val downloadPrefs = getSharedPreferences("downloaded_comics", MODE_PRIVATE)
        val downloaded = downloadPrefs.all.filter { it.value == true }.keys.sorted()
        _downloadedComicIds.clear()
        _downloadedComicIds.addAll(downloaded)
    }

    private fun refreshCatalogUi() {
        _catalogComics.clear()
        _catalogComics.addAll(catalogCache.map { comic ->
            CatalogComicUi(
                id = comic.id,
                name = comic.name,
                sizeMb = comic.sizeMb,
                chapters = comic.chapters,
                isDownloaded = downloadManager.isDownloaded(comic.id)
            )
        })
    }

    private fun setupLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(android.app.KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return resolveInfos
            .filter { it.activityInfo.packageName != packageName }
            .map { resolveInfo ->
                AppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(pm).toString()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun openHomeSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            // Check if comic selection changed
            val currentAssetId = settingsStore.getString("selected_asset_id", null)
            if (currentAssetId != null && currentAssetId != lastKnownAssetId) {
                lastKnownAssetId = currentAssetId
                val comicDir = File(getExternalFilesDir("comics"), currentAssetId)
                if (comicDir.exists() && (File(comicDir, "manifest.json").exists() || File(comicDir, "images.json").exists())) {
                    val dataSource = FileImageDataSource(comicDir)
                    viewModel.switchAsset(dataSource, currentAssetId)
                    return
                }
            }
            viewModel.reloadSettingsIfChanged()
            viewModel.onAppResumed()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            viewModel.onAppPaused()
        }
    }
}
