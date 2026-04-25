import SwiftUI
import Shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            imageDataSource: BundleImageDataSource(),
            annotationRepository: InMemoryAnnotationRepository(),
            sessionRepository: InMemorySessionRepository(),
            learnerDataRepository: InMemoryLearnerDataRepository(),
            tokenRegionRepository: InMemoryTokenRegionRepository(),
            translationRepository: InMemoryTranslationRepository(),
            chatRepository: InMemoryChatRepository(),
            settingsStore: IOSSettingsStore()
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

#Preview {
    ContentView()
}
