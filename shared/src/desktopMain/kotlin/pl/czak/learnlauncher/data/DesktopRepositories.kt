package pl.czak.learnlauncher.data

import pl.czak.learnlauncher.data.model.RegionTranslation
import pl.czak.learnlauncher.data.repository.*
import pl.czak.learnlauncher.domain.model.ChatMessage
import pl.czak.learnlauncher.domain.model.ConversationBox
import pl.czak.learnlauncher.domain.model.RegionAnnotation
import pl.czak.learnlauncher.domain.session.CompletedSession

class InMemoryAnnotationRepository : AnnotationRepository {
    private val annotations = mutableListOf<RegionAnnotation>()
    private val syncedTimestamps = mutableSetOf<Long>()

    override suspend fun addAnnotation(annotation: RegionAnnotation) {
        annotations.add(annotation)
    }

    override suspend fun getAll(): List<RegionAnnotation> = annotations.toList()

    fun getUnsynced(): List<RegionAnnotation> =
        annotations.filter { it.timestamp !in syncedTimestamps }

    fun markSynced(timestamps: List<Long>) {
        syncedTimestamps.addAll(timestamps)
    }

    override suspend fun getForImage(imageId: String): List<RegionAnnotation> =
        annotations.filter { it.imageId == imageId }

    override suspend fun getTokenAnnotationsForBubble(imageId: String, bubbleIndex: Int): List<RegionAnnotation> =
        annotations.filter { it.imageId == imageId && it.parentBubbleIndex == bubbleIndex }
}

class InMemorySessionRepository : SessionRepository {
    private val sessions = mutableListOf<CompletedSession>()

    override suspend fun addSessions(completed: List<CompletedSession>) {
        sessions.addAll(completed)
    }

    override suspend fun getAll(): List<CompletedSession> = sessions.toList()
}

class InMemoryTokenRegionRepository : TokenRegionRepository {
    override fun getTokensForBubble(imageId: String, bubbleIndex: Int): List<ConversationBox> = emptyList()
    override fun hasTokens(imageId: String, bubbleIndex: Int): Boolean = false
}

class InMemoryTranslationRepository : TranslationRepository {
    private val translations = mutableListOf<RegionTranslation>()

    override suspend fun getTranslation(imageId: String, bubbleIndex: Int): RegionTranslation? =
        translations.find { it.imageId == imageId && it.bubbleIndex == bubbleIndex }
    override fun hasTranslation(imageId: String, bubbleIndex: Int): Boolean =
        translations.any { it.imageId == imageId && it.bubbleIndex == bubbleIndex }
    override suspend fun getTranslationsForImage(imageId: String): List<RegionTranslation> =
        translations.filter { it.imageId == imageId }
    override suspend fun getAll(): List<RegionTranslation> = translations.toList()
    override suspend fun saveTranslation(translation: RegionTranslation) { translations.add(translation) }
}

class InMemoryChatRepository : ChatRepository {
    private val messages = mutableListOf<ChatMessage>()

    override suspend fun getAll(): List<ChatMessage> = messages.toList()
    override suspend fun save(message: ChatMessage) { messages.add(message) }
}

class InMemoryLearnerDataRepository : LearnerDataRepository {
    override suspend fun logSessionEvent(eventType: String, chapterName: String?, pageId: String?, pageTitle: String?) {}
    override suspend fun logPageInteraction(interactionType: String, chapterName: String?, pageId: String?, normalizedX: Float?, normalizedY: Float?, hitResult: String?) {}
    override suspend fun logAppLaunch(packageName: String, currentChapter: String?, currentPageId: String?) {}
    override suspend fun logSettingsChange(setting: String, oldValue: String, newValue: String) {}
    override suspend fun getTotalUnsyncedCount(): Int = 0
}
