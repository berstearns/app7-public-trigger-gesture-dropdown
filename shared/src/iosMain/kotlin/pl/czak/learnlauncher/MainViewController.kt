package pl.czak.learnlauncher

import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeUIViewController
import pl.czak.learnlauncher.data.source.ImageDataSource
import pl.czak.learnlauncher.domain.navigation.ImageSequenceNavigator
import pl.czak.learnlauncher.domain.session.SessionTimeTracker
import pl.czak.learnlauncher.viewmodel.ChatViewModel
import pl.czak.learnlauncher.viewmodel.LauncherViewModel

fun MainViewController(
    imageDataSource: ImageDataSource,
    annotationRepository: pl.czak.learnlauncher.data.repository.AnnotationRepository,
    sessionRepository: pl.czak.learnlauncher.data.repository.SessionRepository,
    learnerDataRepository: pl.czak.learnlauncher.data.repository.LearnerDataRepository,
    tokenRegionRepository: pl.czak.learnlauncher.data.repository.TokenRegionRepository,
    translationRepository: pl.czak.learnlauncher.data.repository.TranslationRepository,
    chatRepository: pl.czak.learnlauncher.data.repository.ChatRepository,
    settingsStore: pl.czak.learnlauncher.viewmodel.SettingsStore
) = ComposeUIViewController {
    val viewModel = remember {
        LauncherViewModel(
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
    }

    val chatViewModel = remember { ChatViewModel(chatRepository) }

    var isLoggedIn by remember { mutableStateOf(true) } // iOS: skip login for now

    App(
        viewModel = viewModel,
        chatViewModel = chatViewModel,
        isLoggedIn = isLoggedIn,
        onLogin = { isLoggedIn = true },
        onLogout = { isLoggedIn = false }
    )
}
