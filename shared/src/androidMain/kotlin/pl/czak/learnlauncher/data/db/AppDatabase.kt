package pl.czak.learnlauncher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import pl.czak.learnlauncher.data.db.dao.*
import pl.czak.learnlauncher.data.db.entity.*

@Database(
    entities = [
        SessionEventEntity::class,
        AnnotationRecordEntity::class,
        ChatMessageEntity::class,
        PageInteractionEntity::class,
        AppLaunchRecordEntity::class,
        SettingsChangeEntity::class,
        RegionTranslationEntity::class,
        // Part 2: explicit session hierarchy aggregate tables
        AppSessionEntity::class,
        ComicSessionEntity::class,
        ChapterSessionEntity::class,
        PageSessionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionEventDao(): SessionEventDao
    abstract fun annotationRecordDao(): AnnotationRecordDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun pageInteractionDao(): PageInteractionDao
    abstract fun appLaunchRecordDao(): AppLaunchRecordDao
    abstract fun settingsChangeDao(): SettingsChangeDao
    abstract fun regionTranslationDao(): RegionTranslationDao
    // Part 2
    abstract fun appSessionDao(): AppSessionDao
    abstract fun comicSessionDao(): ComicSessionDao
    abstract fun chapterSessionDao(): ChapterSessionDao
    abstract fun pageSessionDao(): PageSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration 1 → 2: add `comicId` column to the 4 event tables that
         * reference chapters/pages. Backfills pre-existing rows with the
         * `_no_comic_` sentinel so analytics queries can distinguish legacy
         * rows from rows logged after the fix shipped.
         *
         * Note: annotation_records uses the same sentinel; the imageId already
         * transitively identifies a comic, but the column is added for
         * symmetry with the worker-side schema and for simpler joins.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE session_events ADD COLUMN comicId TEXT NOT NULL DEFAULT '_no_comic_'"
                )
                db.execSQL(
                    "ALTER TABLE page_interactions ADD COLUMN comicId TEXT NOT NULL DEFAULT '_no_comic_'"
                )
                db.execSQL(
                    "ALTER TABLE annotation_records ADD COLUMN comicId TEXT NOT NULL DEFAULT '_no_comic_'"
                )
                db.execSQL(
                    "ALTER TABLE app_launch_records ADD COLUMN comicId TEXT NOT NULL DEFAULT '_no_comic_'"
                )
            }
        }

        /**
         * Migration 2 → 3: add the four session hierarchy aggregate tables.
         * No data to migrate — they start empty. Room verifies column
         * definitions against the @Entity classes, so these must match
         * exactly (types, nullability, defaults).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startTs INTEGER NOT NULL,
                        endTs INTEGER,
                        durationMs INTEGER,
                        appVersion TEXT NOT NULL DEFAULT '',
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS comic_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        appSessionId INTEGER NOT NULL,
                        comicId TEXT NOT NULL,
                        startTs INTEGER NOT NULL,
                        endTs INTEGER,
                        durationMs INTEGER,
                        pagesRead INTEGER,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chapter_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        comicSessionId INTEGER NOT NULL,
                        comicId TEXT NOT NULL,
                        chapterName TEXT NOT NULL,
                        startTs INTEGER NOT NULL,
                        endTs INTEGER,
                        durationMs INTEGER,
                        pagesVisited INTEGER,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS page_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chapterSessionId INTEGER NOT NULL,
                        comicId TEXT NOT NULL,
                        pageId TEXT NOT NULL,
                        enterTs INTEGER NOT NULL,
                        leaveTs INTEGER,
                        dwellMs INTEGER,
                        interactionsN INTEGER NOT NULL DEFAULT 0,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "learner_data.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
