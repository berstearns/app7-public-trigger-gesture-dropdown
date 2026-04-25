package pl.czak.learnlauncher.data.repository

import pl.czak.learnlauncher.data.model.ImageItem
import pl.czak.learnlauncher.data.model.RegionTranslation
import pl.czak.learnlauncher.domain.model.ChatMessage
import pl.czak.learnlauncher.domain.model.ConversationBox
import pl.czak.learnlauncher.domain.model.RegionAnnotation
import pl.czak.learnlauncher.domain.session.CompletedSession

interface AnnotationRepository {
    suspend fun addAnnotation(annotation: RegionAnnotation)
    suspend fun getAll(): List<RegionAnnotation>
    suspend fun getForImage(imageId: String): List<RegionAnnotation>
    suspend fun getTokenAnnotationsForBubble(imageId: String, bubbleIndex: Int): List<RegionAnnotation>
}

interface SessionRepository {
    suspend fun addSessions(completed: List<CompletedSession>)
    suspend fun getAll(): List<CompletedSession>
}

interface TokenRegionRepository {
    fun getTokensForBubble(imageId: String, bubbleIndex: Int): List<ConversationBox>
    fun hasTokens(imageId: String, bubbleIndex: Int): Boolean
}

interface TranslationRepository {
    suspend fun getTranslation(imageId: String, bubbleIndex: Int): RegionTranslation?
    fun hasTranslation(imageId: String, bubbleIndex: Int): Boolean
    suspend fun getTranslationsForImage(imageId: String): List<RegionTranslation>
    suspend fun getAll(): List<RegionTranslation>
    suspend fun saveTranslation(translation: RegionTranslation)
}

interface ChatRepository {
    suspend fun getAll(): List<ChatMessage>
    suspend fun save(message: ChatMessage)
}

interface LearnerDataRepository {
    suspend fun logSessionEvent(
        eventType: String,
        chapterName: String? = null,
        pageId: String? = null,
        pageTitle: String? = null
    )
    suspend fun logPageInteraction(
        interactionType: String,
        chapterName: String? = null,
        pageId: String? = null,
        normalizedX: Float? = null,
        normalizedY: Float? = null,
        hitResult: String? = null
    )
    suspend fun logAppLaunch(
        packageName: String,
        currentChapter: String? = null,
        currentPageId: String? = null
    )
    suspend fun logSettingsChange(setting: String, oldValue: String, newValue: String)
    suspend fun getTotalUnsyncedCount(): Int
}
