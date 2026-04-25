package pl.czak.learnlauncher.data

import pl.czak.learnlauncher.data.db.AppDatabase
import pl.czak.learnlauncher.data.db.entity.*
import pl.czak.learnlauncher.data.model.RegionTranslation
import pl.czak.learnlauncher.data.repository.*
import pl.czak.learnlauncher.domain.model.ChatMessage
import pl.czak.learnlauncher.domain.model.RegionAnnotation
import pl.czak.learnlauncher.domain.model.RegionType
import pl.czak.learnlauncher.domain.session.CompletedSession

// Single source of truth for the current comic id. Read at log time so a
// comic switch mid-session is reflected in subsequent event rows. Returns
// the NO_COMIC_SENTINEL constant (defined in Entities.kt) when no comic is
// currently selected — the column is never NULL, never empty.
private fun AndroidSettingsStore.currentComicId(): String =
    getString("selected_asset_id", null) ?: NO_COMIC_SENTINEL

class AndroidAnnotationRepository(
    private val db: AppDatabase,
    private val settingsStore: AndroidSettingsStore,
) : AnnotationRepository {

    override suspend fun addAnnotation(annotation: RegionAnnotation) {
        db.annotationRecordDao().insert(
            AnnotationRecordEntity(
                imageId = annotation.imageId,
                boxIndex = annotation.boxIndex,
                boxX = annotation.boxX,
                boxY = annotation.boxY,
                boxWidth = annotation.boxWidth,
                boxHeight = annotation.boxHeight,
                label = annotation.label,
                timestamp = annotation.timestamp,
                tapX = annotation.tapX,
                tapY = annotation.tapY,
                regionType = annotation.regionType.name,
                parentBubbleIndex = annotation.parentBubbleIndex,
                tokenIndex = annotation.tokenIndex,
                comicId = settingsStore.currentComicId()
            )
        )
    }

    override suspend fun getAll(): List<RegionAnnotation> =
        db.annotationRecordDao().getAll().map { it.toRegionAnnotation() }

    override suspend fun getForImage(imageId: String): List<RegionAnnotation> =
        db.annotationRecordDao().getForImage(imageId).map { it.toRegionAnnotation() }

    override suspend fun getTokenAnnotationsForBubble(imageId: String, bubbleIndex: Int): List<RegionAnnotation> =
        db.annotationRecordDao().getTokenAnnotationsForBubble(imageId, bubbleIndex).map { it.toRegionAnnotation() }

    private fun AnnotationRecordEntity.toRegionAnnotation(): RegionAnnotation {
        return RegionAnnotation(
            imageId = this.imageId,
            boxIndex = this.boxIndex,
            boxX = this.boxX,
            boxY = this.boxY,
            boxWidth = this.boxWidth,
            boxHeight = this.boxHeight,
            label = this.label,
            timestamp = this.timestamp,
            tapX = this.tapX,
            tapY = this.tapY,
            regionType = try { RegionType.valueOf(this.regionType) } catch (e: Exception) { RegionType.BUBBLE },
            parentBubbleIndex = this.parentBubbleIndex,
            tokenIndex = this.tokenIndex
        )
    }
}

class AndroidSessionRepository(
    private val db: AppDatabase,
    private val settingsStore: AndroidSettingsStore,
) : SessionRepository {

    override suspend fun addSessions(completed: List<CompletedSession>) {
        if (completed.isEmpty()) return
        val comicId = settingsStore.currentComicId()
        val events = mutableListOf<SessionEventEntity>()
        for (session in completed) {
            events.add(
                SessionEventEntity(
                    eventType = "${session.sessionType}_ENTER",
                    timestamp = session.startedAt,
                    comicId = comicId,
                    chapterName = session.chapterName,
                    pageId = session.pageId,
                    pageTitle = session.pageTitle
                )
            )
            events.add(
                SessionEventEntity(
                    eventType = "${session.sessionType}_LEAVE",
                    timestamp = session.endedAt,
                    durationMs = session.durationMs,
                    comicId = comicId,
                    chapterName = session.chapterName,
                    pageId = session.pageId,
                    pageTitle = session.pageTitle
                )
            )
        }
        db.sessionEventDao().insertAll(events)
    }

    override suspend fun getAll(): List<CompletedSession> {
        val events = db.sessionEventDao().getAll()
        val result = mutableListOf<CompletedSession>()
        val enterMap = mutableMapOf<String, MutableList<SessionEventEntity>>()
        for (event in events) {
            if (event.eventType.endsWith("_ENTER")) {
                val baseType = event.eventType.removeSuffix("_ENTER")
                enterMap.getOrPut(baseType) { mutableListOf() }.add(event)
            } else if (event.eventType.endsWith("_LEAVE")) {
                val baseType = event.eventType.removeSuffix("_LEAVE")
                val enters = enterMap[baseType]
                val matchedEnter = enters?.removeLastOrNull()
                if (matchedEnter != null) {
                    result.add(
                        CompletedSession(
                            sessionType = baseType,
                            startedAt = matchedEnter.timestamp,
                            endedAt = event.timestamp,
                            durationMs = event.durationMs ?: (event.timestamp - matchedEnter.timestamp),
                            chapterName = event.chapterName,
                            pageId = event.pageId,
                            pageTitle = event.pageTitle
                        )
                    )
                }
            }
        }
        return result
    }
}

class AndroidChatRepository(private val db: AppDatabase) : ChatRepository {

    override suspend fun getAll(): List<ChatMessage> =
        db.chatMessageDao().getAll().map { ChatMessage(it.sender, it.text, it.timestamp) }

    override suspend fun save(message: ChatMessage) {
        db.chatMessageDao().insert(
            ChatMessageEntity(sender = message.sender, text = message.text, timestamp = message.timestamp)
        )
    }
}

class AndroidLearnerDataRepository(
    val db: AppDatabase,
    private val settingsStore: AndroidSettingsStore,
) : LearnerDataRepository {

    override suspend fun logSessionEvent(
        eventType: String,
        chapterName: String?,
        pageId: String?,
        pageTitle: String?
    ) {
        db.sessionEventDao().insertAll(
            listOf(
                SessionEventEntity(
                    eventType = eventType,
                    comicId = settingsStore.currentComicId(),
                    chapterName = chapterName,
                    pageId = pageId,
                    pageTitle = pageTitle
                )
            )
        )
    }

    override suspend fun logPageInteraction(
        interactionType: String,
        chapterName: String?,
        pageId: String?,
        normalizedX: Float?,
        normalizedY: Float?,
        hitResult: String?
    ) {
        db.pageInteractionDao().insert(
            PageInteractionEntity(
                interactionType = interactionType,
                comicId = settingsStore.currentComicId(),
                chapterName = chapterName,
                pageId = pageId,
                normalizedX = normalizedX,
                normalizedY = normalizedY,
                hitResult = hitResult
            )
        )
    }

    override suspend fun logAppLaunch(
        packageName: String,
        currentChapter: String?,
        currentPageId: String?
    ) {
        db.appLaunchRecordDao().insert(
            AppLaunchRecordEntity(
                packageName = packageName,
                comicId = settingsStore.currentComicId(),
                currentChapter = currentChapter,
                currentPageId = currentPageId
            )
        )
    }

    override suspend fun logSettingsChange(setting: String, oldValue: String, newValue: String) {
        db.settingsChangeDao().insert(
            SettingsChangeEntity(setting = setting, oldValue = oldValue, newValue = newValue)
        )
    }

    override suspend fun getTotalUnsyncedCount(): Int {
        return db.pageInteractionDao().getUnsyncedCount() +
            db.appLaunchRecordDao().getUnsyncedCount() +
            db.settingsChangeDao().getUnsyncedCount()
    }
}
