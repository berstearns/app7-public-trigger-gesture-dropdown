package pl.czak.learnlauncher.data.export

import pl.czak.learnlauncher.data.db.AppDatabase
import pl.czak.learnlauncher.data.model.*

/**
 * Builds a UnifiedPayload from Room DB.
 * @param unsyncedOnly true for sync (only unsynced records), false for full export
 */
suspend fun buildUnifiedPayload(
    db: AppDatabase,
    deviceId: String,
    appVersion: String,
    userId: String? = null,
    unsyncedOnly: Boolean
): UnifiedPayload {
    val sessionEvents = if (unsyncedOnly) db.sessionEventDao().getUnsynced() else db.sessionEventDao().getAll()
    val annotations = if (unsyncedOnly) db.annotationRecordDao().getUnsynced() else db.annotationRecordDao().getAll()
    val chatMessages = if (unsyncedOnly) db.chatMessageDao().getUnsynced() else db.chatMessageDao().getAll()
    val pageInteractions = if (unsyncedOnly) db.pageInteractionDao().getUnsynced() else db.pageInteractionDao().getAll()
    val appLaunches = if (unsyncedOnly) db.appLaunchRecordDao().getUnsynced() else db.appLaunchRecordDao().getAll()
    val settingsChanges = if (unsyncedOnly) db.settingsChangeDao().getUnsynced() else db.settingsChangeDao().getAll()
    val regionTranslations = db.regionTranslationDao().getAll()
    // Session hierarchy aggregates (v5). Only closed sessions (endTs/leaveTs
    // NOT NULL) are considered sync candidates; live sessions stay in the
    // producer until they close.
    val appSessions = db.appSessionDao().getUnsynced()
    val comicSessions = db.comicSessionDao().getUnsynced()
    val chapterSessions = db.chapterSessionDao().getUnsynced()
    val pageSessions = db.pageSessionDao().getUnsynced()

    return UnifiedPayload(
        exportTimestamp = System.currentTimeMillis(),
        appVersion = appVersion,
        deviceId = deviceId,
        userId = userId,
        mode = if (unsyncedOnly) "sync" else "export",
        tables = UnifiedTables(
            sessionEvents = sessionEvents.map { e ->
                SessionEventRecord(
                    localId = e.id,
                    eventType = e.eventType,
                    timestamp = e.timestamp,
                    durationMs = e.durationMs,
                    comicId = e.comicId,
                    chapterName = e.chapterName,
                    pageId = e.pageId,
                    pageTitle = e.pageTitle,
                    synced = e.synced
                )
            },
            annotationRecords = annotations.map { a ->
                AnnotationRecord(
                    localId = a.id,
                    imageId = a.imageId,
                    boxIndex = a.boxIndex,
                    boxX = a.boxX,
                    boxY = a.boxY,
                    boxWidth = a.boxWidth,
                    boxHeight = a.boxHeight,
                    label = a.label,
                    timestamp = a.timestamp,
                    tapX = a.tapX,
                    tapY = a.tapY,
                    regionType = a.regionType,
                    parentBubbleIndex = a.parentBubbleIndex,
                    tokenIndex = a.tokenIndex,
                    comicId = a.comicId,
                    synced = a.synced
                )
            },
            chatMessages = chatMessages.map { m ->
                ChatMessageRecord(
                    localId = m.id,
                    sender = m.sender,
                    text = m.text,
                    timestamp = m.timestamp,
                    synced = m.synced
                )
            },
            pageInteractions = pageInteractions.map { p ->
                PageInteractionRecord(
                    localId = p.id,
                    interactionType = p.interactionType,
                    timestamp = p.timestamp,
                    comicId = p.comicId,
                    chapterName = p.chapterName,
                    pageId = p.pageId,
                    normalizedX = p.normalizedX,
                    normalizedY = p.normalizedY,
                    hitResult = p.hitResult,
                    synced = p.synced
                )
            },
            appLaunchRecords = appLaunches.map { l ->
                AppLaunchRecord(
                    localId = l.id,
                    packageName = l.packageName,
                    timestamp = l.timestamp,
                    comicId = l.comicId,
                    currentChapter = l.currentChapter,
                    currentPageId = l.currentPageId,
                    synced = l.synced
                )
            },
            settingsChanges = settingsChanges.map { s ->
                SettingsChangeRecord(
                    localId = s.id,
                    settingKey = s.setting,
                    oldValue = s.oldValue,
                    newValue = s.newValue,
                    timestamp = s.timestamp,
                    synced = s.synced
                )
            },
            regionTranslations = regionTranslations.map { t ->
                RegionTranslationRecord(
                    id = t.id,
                    imageId = t.imageId,
                    bubbleIndex = t.bubbleIndex,
                    originalText = t.originalText,
                    meaningTranslation = t.meaningTranslation,
                    literalTranslation = t.literalTranslation,
                    sourceLanguage = t.sourceLanguage,
                    targetLanguage = t.targetLanguage
                )
            },
            appSessions = appSessions.map { s ->
                AppSessionRecord(
                    localId = s.id,
                    startTs = s.startTs,
                    endTs = s.endTs,
                    durationMs = s.durationMs,
                    appVersion = s.appVersion,
                    synced = s.synced
                )
            },
            comicSessions = comicSessions.map { s ->
                ComicSessionRecord(
                    localId = s.id,
                    appSessionLocalId = s.appSessionId,
                    comicId = s.comicId,
                    startTs = s.startTs,
                    endTs = s.endTs,
                    durationMs = s.durationMs,
                    pagesRead = s.pagesRead,
                    synced = s.synced
                )
            },
            chapterSessions = chapterSessions.map { s ->
                ChapterSessionRecord(
                    localId = s.id,
                    comicSessionLocalId = s.comicSessionId,
                    comicId = s.comicId,
                    chapterName = s.chapterName,
                    startTs = s.startTs,
                    endTs = s.endTs,
                    durationMs = s.durationMs,
                    pagesVisited = s.pagesVisited,
                    synced = s.synced
                )
            },
            pageSessions = pageSessions.map { s ->
                PageSessionRecord(
                    localId = s.id,
                    chapterSessionLocalId = s.chapterSessionId,
                    comicId = s.comicId,
                    pageId = s.pageId,
                    enterTs = s.enterTs,
                    leaveTs = s.leaveTs,
                    dwellMs = s.dwellMs,
                    interactionsN = s.interactionsN,
                    synced = s.synced
                )
            }
        )
    )
}
