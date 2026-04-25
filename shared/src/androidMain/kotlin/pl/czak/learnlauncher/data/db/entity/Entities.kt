package pl.czak.learnlauncher.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── Comic ID sentinel ────────────────────────────────────────
// Used for rows that pre-date the comic_id migration (v1 → v2) and for
// events logged while no comic is currently selected. Analytical queries
// must treat this value as "unknown comic, ignore or flag as legacy".
const val NO_COMIC_SENTINEL = "_no_comic_"

@Entity(tableName = "session_events")
data class SessionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long? = null,
    val comicId: String = NO_COMIC_SENTINEL,
    val chapterName: String? = null,
    val pageId: String? = null,
    val pageTitle: String? = null,
    val synced: Boolean = false
)

@Entity(tableName = "annotation_records")
data class AnnotationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageId: String,
    val boxIndex: Int,
    val boxX: Float,
    val boxY: Float,
    val boxWidth: Float,
    val boxHeight: Float,
    val label: String,
    val timestamp: Long,
    val tapX: Float,
    val tapY: Float,
    val regionType: String = "BUBBLE",
    val parentBubbleIndex: Int? = null,
    val tokenIndex: Int? = null,
    val comicId: String = NO_COMIC_SENTINEL,
    val synced: Boolean = false
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val synced: Boolean = false
)

@Entity(tableName = "page_interactions")
data class PageInteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val interactionType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val comicId: String = NO_COMIC_SENTINEL,
    val chapterName: String? = null,
    val pageId: String? = null,
    val normalizedX: Float? = null,
    val normalizedY: Float? = null,
    val hitResult: String? = null,
    val synced: Boolean = false
)

@Entity(tableName = "app_launch_records")
data class AppLaunchRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val comicId: String = NO_COMIC_SENTINEL,
    val currentChapter: String? = null,
    val currentPageId: String? = null,
    val synced: Boolean = false
)

@Entity(tableName = "settings_changes")
data class SettingsChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setting: String,
    val oldValue: String,
    val newValue: String,
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

@Entity(tableName = "region_translations")
data class RegionTranslationEntity(
    @PrimaryKey val id: String,
    val imageId: String,
    val bubbleIndex: Int,
    val originalText: String,
    val meaningTranslation: String,
    val literalTranslation: String,
    val sourceLanguage: String = "ja",
    val targetLanguage: String = "en"
)

// ═══════════════════════════════════════════════════════════════
// Session hierarchy aggregate tables (Part 2)
// ═══════════════════════════════════════════════════════════════
// Explicit parent-child aggregates that replace the fragile
// "infer sessions from flat event logs" approach. Populated by
// SessionHierarchyTracker at lifecycle transitions. Synced to the
// worker alongside the 7 existing tables via UnifiedPayload v5.

/**
 * One row per app launch / close cycle on a device.
 * `endTs == null` means the session is either still live or died
 * without a clean onDestroy (worker applies close-out policy).
 */
@Entity(tableName = "app_sessions")
data class AppSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTs: Long,
    val endTs: Long? = null,
    val durationMs: Long? = null,
    val appVersion: String = "",
    val synced: Boolean = false
)

/**
 * One row per comic the user selected during an app session. A user who
 * switches between two comics during a single app session creates two
 * rows under the same `appSessionId`.
 */
@Entity(tableName = "comic_sessions")
data class ComicSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appSessionId: Long,
    val comicId: String,
    val startTs: Long,
    val endTs: Long? = null,
    val durationMs: Long? = null,
    val pagesRead: Int? = null,
    val synced: Boolean = false
)

/**
 * One row per chapter read within a comic_session. Re-entering a
 * previously-read chapter mid-session creates a new row.
 */
@Entity(tableName = "chapter_sessions")
data class ChapterSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comicSessionId: Long,
    val comicId: String,
    val chapterName: String,
    val startTs: Long,
    val endTs: Long? = null,
    val durationMs: Long? = null,
    val pagesVisited: Int? = null,
    val synced: Boolean = false
)

/**
 * One row per page visit within a chapter_session. `dwellMs` is
 * leaveTs - enterTs when the page is closed; NULL while still visible.
 */
@Entity(tableName = "page_sessions")
data class PageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterSessionId: Long,
    val comicId: String,
    val pageId: String,
    val enterTs: Long,
    val leaveTs: Long? = null,
    val dwellMs: Long? = null,
    val interactionsN: Int = 0,
    val synced: Boolean = false
)
