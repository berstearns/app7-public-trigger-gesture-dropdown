package pl.czak.learnlauncher.data.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.czak.learnlauncher.data.model.*
import pl.czak.learnlauncher.data.repository.AnnotationRepository
import pl.czak.learnlauncher.data.repository.SessionRepository
import pl.czak.learnlauncher.data.repository.ChatRepository
import pl.czak.learnlauncher.data.repository.TranslationRepository
import java.io.File

class DesktopJsonExportService(
    private val annotationRepository: AnnotationRepository,
    private val sessionRepository: SessionRepository,
    private val chatRepository: ChatRepository,
    private val translationRepository: TranslationRepository
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun export(): File {
        val annotations = annotationRepository.getAll()
        val sessions = sessionRepository.getAll()
        val chatMessages = chatRepository.getAll()
        val translations = translationRepository.getAll()

        val payload = UnifiedPayload(
            exportTimestamp = System.currentTimeMillis(),
            appVersion = "1.0-kmp-desktop",
            deviceId = "desktop-${System.getProperty("os.name")}",
            mode = "export",
            tables = UnifiedTables(
                sessionEvents = sessions.mapIndexed { index, session ->
                    SessionEventRecord(
                        localId = index.toLong(),
                        eventType = session.sessionType,
                        timestamp = session.startedAt,
                        durationMs = session.durationMs,
                        chapterName = session.chapterName,
                        pageId = session.pageId,
                        pageTitle = session.pageTitle
                    )
                },
                annotationRecords = annotations.mapIndexed { index, ann ->
                    AnnotationRecord(
                        localId = index.toLong(),
                        imageId = ann.imageId,
                        boxIndex = ann.boxIndex,
                        boxX = ann.boxX,
                        boxY = ann.boxY,
                        boxWidth = ann.boxWidth,
                        boxHeight = ann.boxHeight,
                        label = ann.label,
                        timestamp = ann.timestamp,
                        tapX = ann.tapX,
                        tapY = ann.tapY,
                        regionType = ann.regionType.name,
                        parentBubbleIndex = ann.parentBubbleIndex,
                        tokenIndex = ann.tokenIndex
                    )
                },
                chatMessages = chatMessages.mapIndexed { index, msg ->
                    ChatMessageRecord(
                        localId = index.toLong(),
                        sender = msg.sender,
                        text = msg.text,
                        timestamp = msg.timestamp
                    )
                },
                regionTranslations = translations.map { t ->
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
                }
            )
        )

        val exportJson = json.encodeToString(payload)
        val timestamp = System.currentTimeMillis()
        val file = File(System.getProperty("user.home"), "manga-reader-export-$timestamp.json")
        file.writeText(exportJson)
        return file
    }

    fun openContainingFolder(file: File) {
        try {
            val parentDir = file.parentFile ?: return
            ProcessBuilder("xdg-open", parentDir.absolutePath).start()
        } catch (_: Exception) { }
    }
}
