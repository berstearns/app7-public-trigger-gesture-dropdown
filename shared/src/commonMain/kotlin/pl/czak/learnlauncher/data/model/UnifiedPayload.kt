package pl.czak.learnlauncher.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unified payload schema v5 — single format for both sync (unsyncedOnly=true)
 * and export (unsyncedOnly=false). Covers 11 Room tables (7 event + catalog
 * + 4 session hierarchy aggregates).
 *
 * v4 → v5: add the four session hierarchy aggregate tables
 * (app_sessions, comic_sessions, chapter_sessions, page_sessions) so the
 * worker can persist explicit parent-child session rows instead of
 * reconstructing them from the flat event log at query time.
 *
 * v3 → v4: every event row carries a `comic_id` field so two comics
 * sharing a chapter name no longer collide. Legacy rows backfilled
 * with comic_id="_no_comic_".
 *
 * Worker accepts schema_version in (3, 4, 5); v3 and v4 payloads have
 * empty session hierarchy lists and are otherwise handled unchanged.
 */
@Serializable
data class UnifiedPayload(
    @SerialName("schema_version") val schemaVersion: Int = 5,
    @SerialName("export_timestamp") val exportTimestamp: Long,
    @SerialName("app_version") val appVersion: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("user_id") val userId: String? = null,
    val mode: String, // "sync" or "export"
    val tables: UnifiedTables
)

@Serializable
data class UnifiedTables(
    @SerialName("session_events") val sessionEvents: List<SessionEventRecord> = emptyList(),
    @SerialName("annotation_records") val annotationRecords: List<AnnotationRecord> = emptyList(),
    @SerialName("chat_messages") val chatMessages: List<ChatMessageRecord> = emptyList(),
    @SerialName("page_interactions") val pageInteractions: List<PageInteractionRecord> = emptyList(),
    @SerialName("app_launch_records") val appLaunchRecords: List<AppLaunchRecord> = emptyList(),
    @SerialName("settings_changes") val settingsChanges: List<SettingsChangeRecord> = emptyList(),
    @SerialName("region_translations") val regionTranslations: List<RegionTranslationRecord> = emptyList(),
    // v5: session hierarchy aggregates
    @SerialName("app_sessions") val appSessions: List<AppSessionRecord> = emptyList(),
    @SerialName("comic_sessions") val comicSessions: List<ComicSessionRecord> = emptyList(),
    @SerialName("chapter_sessions") val chapterSessions: List<ChapterSessionRecord> = emptyList(),
    @SerialName("page_sessions") val pageSessions: List<PageSessionRecord> = emptyList()
)

@Serializable
data class SessionEventRecord(
    @SerialName("local_id") val localId: Long,
    @SerialName("event_type") val eventType: String,
    val timestamp: Long,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("comic_id") val comicId: String = "_no_comic_",
    @SerialName("chapter_name") val chapterName: String? = null,
    @SerialName("page_id") val pageId: String? = null,
    @SerialName("page_title") val pageTitle: String? = null,
    val synced: Boolean = false
)

@Serializable
data class AnnotationRecord(
    @SerialName("local_id") val localId: Long,
    @SerialName("image_id") val imageId: String,
    @SerialName("box_index") val boxIndex: Int,
    @SerialName("box_x") val boxX: Float,
    @SerialName("box_y") val boxY: Float,
    @SerialName("box_width") val boxWidth: Float,
    @SerialName("box_height") val boxHeight: Float,
    val label: String,
    val timestamp: Long,
    @SerialName("tap_x") val tapX: Float,
    @SerialName("tap_y") val tapY: Float,
    @SerialName("region_type") val regionType: String = "BUBBLE",
    @SerialName("parent_bubble_index") val parentBubbleIndex: Int? = null,
    @SerialName("token_index") val tokenIndex: Int? = null,
    @SerialName("comic_id") val comicId: String = "_no_comic_",
    val synced: Boolean = false
)

@Serializable
data class ChatMessageRecord(
    @SerialName("local_id") val localId: Long,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val synced: Boolean = false
)

@Serializable
data class PageInteractionRecord(
    @SerialName("local_id") val localId: Long,
    @SerialName("interaction_type") val interactionType: String,
    val timestamp: Long,
    @SerialName("comic_id") val comicId: String = "_no_comic_",
    @SerialName("chapter_name") val chapterName: String? = null,
    @SerialName("page_id") val pageId: String? = null,
    @SerialName("normalized_x") val normalizedX: Float? = null,
    @SerialName("normalized_y") val normalizedY: Float? = null,
    @SerialName("hit_result") val hitResult: String? = null,
    val synced: Boolean = false
)

@Serializable
data class AppLaunchRecord(
    @SerialName("local_id") val localId: Long,
    @SerialName("package_name") val packageName: String,
    val timestamp: Long,
    @SerialName("comic_id") val comicId: String = "_no_comic_",
    @SerialName("current_chapter") val currentChapter: String? = null,
    @SerialName("current_page_id") val currentPageId: String? = null,
    val synced: Boolean = false
)

@Serializable
data class SettingsChangeRecord(
    @SerialName("local_id") val localId: Long,
    @SerialName("setting_key") val settingKey: String,
    @SerialName("old_value") val oldValue: String,
    @SerialName("new_value") val newValue: String,
    val timestamp: Long,
    val synced: Boolean = false
)

@Serializable
data class RegionTranslationRecord(
    val id: String,
    @SerialName("image_id") val imageId: String,
    @SerialName("bubble_index") val bubbleIndex: Int,
    @SerialName("original_text") val originalText: String,
    @SerialName("meaning_translation") val meaningTranslation: String,
    @SerialName("literal_translation") val literalTranslation: String,
    @SerialName("source_language") val sourceLanguage: String = "ja",
    @SerialName("target_language") val targetLanguage: String = "en"
)

// ═══════════════════════════════════════════════════════════════
// v5 session hierarchy record classes
// ═══════════════════════════════════════════════════════════════

@Serializable
data class AppSessionRecord(
    @SerialName("local_id") val localId: Long,
    @SerialName("start_ts") val startTs: Long,
    @SerialName("end_ts") val endTs: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("app_version") val appVersion: String = "",
    val synced: Boolean = false
)

@Serializable
data class ComicSessionRecord(
    @SerialName("local_id") val localId: Long,
    @SerialName("app_session_local_id") val appSessionLocalId: Long,
    @SerialName("comic_id") val comicId: String,
    @SerialName("start_ts") val startTs: Long,
    @SerialName("end_ts") val endTs: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("pages_read") val pagesRead: Int? = null,
    val synced: Boolean = false
)

@Serializable
data class ChapterSessionRecord(
    @SerialName("local_id") val localId: Long,
    @SerialName("comic_session_local_id") val comicSessionLocalId: Long,
    @SerialName("comic_id") val comicId: String,
    @SerialName("chapter_name") val chapterName: String,
    @SerialName("start_ts") val startTs: Long,
    @SerialName("end_ts") val endTs: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("pages_visited") val pagesVisited: Int? = null,
    val synced: Boolean = false
)

@Serializable
data class PageSessionRecord(
    @SerialName("local_id") val localId: Long,
    @SerialName("chapter_session_local_id") val chapterSessionLocalId: Long,
    @SerialName("comic_id") val comicId: String,
    @SerialName("page_id") val pageId: String,
    @SerialName("enter_ts") val enterTs: Long,
    @SerialName("leave_ts") val leaveTs: Long? = null,
    @SerialName("dwell_ms") val dwellMs: Long? = null,
    @SerialName("interactions_n") val interactionsN: Int = 0,
    val synced: Boolean = false
)
