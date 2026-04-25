package pl.czak.learnlauncher.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pl.czak.learnlauncher.data.db.entity.*

@Dao
interface SessionEventDao {
    @Insert
    suspend fun insertAll(events: List<SessionEventEntity>)

    @Query("SELECT * FROM session_events ORDER BY timestamp ASC")
    suspend fun getAll(): List<SessionEventEntity>

    @Query("SELECT * FROM session_events WHERE synced = 0")
    suspend fun getUnsynced(): List<SessionEventEntity>

    @Query("UPDATE session_events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

@Dao
interface AnnotationRecordDao {
    @Insert
    suspend fun insert(record: AnnotationRecordEntity)

    @Query("SELECT * FROM annotation_records ORDER BY timestamp DESC")
    suspend fun getAll(): List<AnnotationRecordEntity>

    @Query("SELECT * FROM annotation_records WHERE imageId = :imageId ORDER BY timestamp DESC")
    suspend fun getForImage(imageId: String): List<AnnotationRecordEntity>

    @Query("SELECT * FROM annotation_records WHERE imageId = :imageId AND regionType = 'TOKEN' AND parentBubbleIndex = :bubbleIndex ORDER BY timestamp DESC")
    suspend fun getTokenAnnotationsForBubble(imageId: String, bubbleIndex: Int): List<AnnotationRecordEntity>

    @Query("SELECT DISTINCT label FROM annotation_records")
    suspend fun getDistinctLabels(): List<String>

    @Query("SELECT * FROM annotation_records WHERE synced = 0")
    suspend fun getUnsynced(): List<AnnotationRecordEntity>

    @Query("UPDATE annotation_records SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE synced = 0")
    suspend fun getUnsynced(): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

@Dao
interface PageInteractionDao {
    @Insert
    suspend fun insert(interaction: PageInteractionEntity)

    @Query("SELECT COUNT(*) FROM page_interactions WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT * FROM page_interactions WHERE synced = 0")
    suspend fun getUnsynced(): List<PageInteractionEntity>

    @Query("UPDATE page_interactions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM page_interactions ORDER BY timestamp ASC")
    suspend fun getAll(): List<PageInteractionEntity>
}

@Dao
interface AppLaunchRecordDao {
    @Insert
    suspend fun insert(record: AppLaunchRecordEntity)

    @Query("SELECT COUNT(*) FROM app_launch_records WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT * FROM app_launch_records WHERE synced = 0")
    suspend fun getUnsynced(): List<AppLaunchRecordEntity>

    @Query("UPDATE app_launch_records SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM app_launch_records ORDER BY timestamp ASC")
    suspend fun getAll(): List<AppLaunchRecordEntity>
}

@Dao
interface SettingsChangeDao {
    @Insert
    suspend fun insert(change: SettingsChangeEntity)

    @Query("SELECT COUNT(*) FROM settings_changes WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT * FROM settings_changes WHERE synced = 0")
    suspend fun getUnsynced(): List<SettingsChangeEntity>

    @Query("UPDATE settings_changes SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM settings_changes ORDER BY timestamp ASC")
    suspend fun getAll(): List<SettingsChangeEntity>
}

@Dao
interface RegionTranslationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(translation: RegionTranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(translations: List<RegionTranslationEntity>)

    @Query("SELECT * FROM region_translations WHERE imageId = :imageId AND bubbleIndex = :bubbleIndex")
    suspend fun getByImageAndBubble(imageId: String, bubbleIndex: Int): RegionTranslationEntity?

    @Query("SELECT * FROM region_translations WHERE imageId = :imageId")
    suspend fun getForImage(imageId: String): List<RegionTranslationEntity>

    @Query("SELECT * FROM region_translations")
    suspend fun getAll(): List<RegionTranslationEntity>
}

// ═══════════════════════════════════════════════════════════════
// Session hierarchy DAOs (Part 2)
// ═══════════════════════════════════════════════════════════════

@Dao
interface AppSessionDao {
    @Insert
    suspend fun insert(row: AppSessionEntity): Long

    @Query("UPDATE app_sessions SET endTs = :endTs, durationMs = :endTs - startTs WHERE id = :id")
    suspend fun close(id: Long, endTs: Long)

    @Query("SELECT * FROM app_sessions WHERE synced = 0 AND endTs IS NOT NULL")
    suspend fun getUnsynced(): List<AppSessionEntity>

    @Query("UPDATE app_sessions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM app_sessions ORDER BY startTs DESC LIMIT 1")
    suspend fun getMostRecent(): AppSessionEntity?
}

@Dao
interface ComicSessionDao {
    @Insert
    suspend fun insert(row: ComicSessionEntity): Long

    @Query("UPDATE comic_sessions SET endTs = :endTs, durationMs = :endTs - startTs, pagesRead = :pagesRead WHERE id = :id")
    suspend fun close(id: Long, endTs: Long, pagesRead: Int)

    @Query("SELECT * FROM comic_sessions WHERE synced = 0 AND endTs IS NOT NULL")
    suspend fun getUnsynced(): List<ComicSessionEntity>

    @Query("UPDATE comic_sessions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

@Dao
interface ChapterSessionDao {
    @Insert
    suspend fun insert(row: ChapterSessionEntity): Long

    @Query("UPDATE chapter_sessions SET endTs = :endTs, durationMs = :endTs - startTs, pagesVisited = :pagesVisited WHERE id = :id")
    suspend fun close(id: Long, endTs: Long, pagesVisited: Int)

    @Query("SELECT * FROM chapter_sessions WHERE synced = 0 AND endTs IS NOT NULL")
    suspend fun getUnsynced(): List<ChapterSessionEntity>

    @Query("UPDATE chapter_sessions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

@Dao
interface PageSessionDao {
    @Insert
    suspend fun insert(row: PageSessionEntity): Long

    @Query("UPDATE page_sessions SET leaveTs = :leaveTs, dwellMs = :leaveTs - enterTs WHERE id = :id")
    suspend fun close(id: Long, leaveTs: Long)

    @Query("UPDATE page_sessions SET interactionsN = interactionsN + 1 WHERE id = :id")
    suspend fun incrementInteractions(id: Long)

    @Query("SELECT * FROM page_sessions WHERE synced = 0 AND leaveTs IS NOT NULL")
    suspend fun getUnsynced(): List<PageSessionEntity>

    @Query("UPDATE page_sessions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}
