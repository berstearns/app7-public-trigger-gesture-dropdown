package pl.czak.learnlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import pl.czak.learnlauncher.ui.AnnotationLogScreen
import pl.czak.learnlauncher.ui.AppInfo
import pl.czak.learnlauncher.ui.CatalogComicUi
import pl.czak.learnlauncher.ui.ChatScreen
import pl.czak.learnlauncher.ui.DownloadStatus
import pl.czak.learnlauncher.ui.FlagsScreen
import pl.czak.learnlauncher.ui.LoginScreen
import pl.czak.learnlauncher.ui.MainScreen
import pl.czak.learnlauncher.ui.SettingsScreen
import pl.czak.learnlauncher.ui.theme.App7Theme
import pl.czak.learnlauncher.viewmodel.ChatViewModel
import pl.czak.learnlauncher.viewmodel.LauncherViewModel
import pl.czak.learnlauncher.viewmodel.Screen

@Composable
fun App(
    viewModel: LauncherViewModel,
    chatViewModel: ChatViewModel,
    isLoggedIn: Boolean,
    onLogin: (String) -> Unit,
    onLogout: () -> Unit,
    onLaunchApp: ((String) -> Unit)? = null,
    onSyncCloud: (() -> Unit)? = null,
    onExportJson: (() -> Unit)? = null,
    onLoadApps: (() -> List<AppInfo>)? = null,
    onOpenHomeSettings: (() -> Unit)? = null,
    catalogComics: List<CatalogComicUi> = emptyList(),
    downloadStatus: DownloadStatus = DownloadStatus.Idle,
    onRefreshCatalog: (() -> Unit)? = null,
    onDownloadComic: ((String) -> Unit)? = null,
    onRemoveComic: ((String) -> Unit)? = null,
    downloadedComicIds: List<String> = emptyList(),
    activeComicId: String? = null,
    onSelectActiveComic: ((String) -> Unit)? = null,
    googleAccountEmail: String? = null,
    onGoogleSignIn: (() -> Unit)? = null,
    onGoogleSignOut: (() -> Unit)? = null,
    autoSyncEnabled: Boolean = false,
    onAutoSyncToggled: ((Boolean) -> Unit)? = null,
    isDesktop: Boolean = false
) {
    App7Theme {
        val screen by viewModel.currentScreen.collectAsState()

        if (!isLoggedIn) {
            LoginScreen(
                onLogin = onLogin
            )
        } else {
            when (screen) {
                is Screen.Main -> MainScreen(
                    viewModel = viewModel,
                    onLaunchApp = onLaunchApp,
                    onLoadApps = onLoadApps,
                    onOpenHomeSettings = onOpenHomeSettings
                )
                is Screen.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.currentScreen.value = Screen.Main },
                    onLogout = onLogout,
                    onOpenChat = { viewModel.currentScreen.value = Screen.Chat },
                    onOpenAnnotationLog = { viewModel.currentScreen.value = Screen.AnnotationLog },
                    onOpenFlags = { viewModel.currentScreen.value = Screen.Flags },
                    onSyncCloud = onSyncCloud,
                    onExportJson = onExportJson,
                    onOpenHomeSettings = onOpenHomeSettings,
                    catalogComics = catalogComics,
                    downloadStatus = downloadStatus,
                    onRefreshCatalog = onRefreshCatalog,
                    onDownloadComic = onDownloadComic,
                    onRemoveComic = onRemoveComic,
                    downloadedComicIds = downloadedComicIds,
                    activeComicId = activeComicId,
                    onSelectActiveComic = onSelectActiveComic,
                    googleAccountEmail = googleAccountEmail,
                    onGoogleSignIn = onGoogleSignIn,
                    onGoogleSignOut = onGoogleSignOut
                )
                is Screen.Flags -> FlagsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.currentScreen.value = Screen.Settings },
                    autoSyncEnabled = autoSyncEnabled,
                    onAutoSyncToggled = onAutoSyncToggled,
                    isDesktop = isDesktop
                )
                is Screen.Chat -> ChatScreen(
                    viewModel = chatViewModel,
                    onBack = { viewModel.currentScreen.value = Screen.Main }
                )
                is Screen.AnnotationLog -> AnnotationLogScreen(
                    annotationRepository = viewModel.annotationRepository,
                    onBack = { viewModel.currentScreen.value = Screen.Main },
                    onNavigateToImage = { imageId ->
                        viewModel.goToImageId(imageId)
                        viewModel.currentScreen.value = Screen.Main
                    }
                )
                is Screen.Login -> {
                    LoginScreen(onLogin = onLogin)
                }
            }
        }
    }
}
