package pl.czak.learnlauncher.data.session

import pl.czak.learnlauncher.data.db.AppDatabase
import pl.czak.learnlauncher.data.db.entity.AppSessionEntity
import pl.czak.learnlauncher.data.db.entity.ChapterSessionEntity
import pl.czak.learnlauncher.data.db.entity.ComicSessionEntity
import pl.czak.learnlauncher.data.db.entity.PageSessionEntity

/**
 * Populates the explicit session hierarchy aggregate tables
 * (`app_sessions → comic_sessions → chapter_sessions → page_sessions`)
 * at app lifecycle and user-navigation callbacks.
 *
 * This is the producer side of Part 2 of the explicit-db-hierarchy
 * feature. It replaces the fragile "reconstruct sessions from a flat
 * event log at query time" approach with explicit parent-child rows
 * that have clear open/close semantics.
 *
 * Thread model: every method is `suspend` and assumes it's called on
 * a single coroutine scope (MainActivity's `lifecycleScope`). The
 * in-memory `currentXxxId` state is NOT thread-safe and must not be
 * driven from multiple concurrent callers.
 *
 * Close-out semantics on abnormal termination:
 *   - `onAppStart` first closes any still-open app_session row for
 *     this device (endTs = now()) as a best-effort recovery from a
 *     process kill that skipped `onAppStop`. The worker's objective-24
 *     close-out policy is the final authority on cleaning up orphans.
 *   - Within a normal lifecycle, each level's state is strictly
 *     last-writer-wins: opening a new comic_session closes the old
 *     one (and its chapter/page descendants).
 */
class SessionHierarchyTracker(
    private val db: AppDatabase,
    private val appVersion: String = "1.0-kmp",
) {
    // ── Current open-row ids ─────────────────────────────────
    private var currentAppSessionId: Long? = null
    private var currentComicSessionId: Long? = null
    private var currentChapterSessionId: Long? = null
    private var currentPageSessionId: Long? = null

    // ── Per-parent counters (materialized on close) ──────────
    private var pagesVisitedInChapter = 0
    private var pagesReadInComic = 0

    private fun now(): Long = System.currentTimeMillis()

    // ══════════════════════════════════════════════════════════
    // App lifecycle
    // ══════════════════════════════════════════════════════════

    /**
     * Called from `MainActivity.onCreate` (or `onStart` if you'd rather
     * count "foreground sessions"). Closes any stale open row first,
     * then inserts a fresh one and remembers its id.
     */
    suspend fun onAppStart() {
        // Recover from a previous process kill that skipped onAppStop.
        val stale = db.appSessionDao().getMostRecent()
        if (stale != null && stale.endTs == null) {
            db.appSessionDao().close(stale.id, now())
        }
        val row = AppSessionEntity(
            startTs = now(),
            appVersion = appVersion,
        )
        currentAppSessionId = db.appSessionDao().insert(row)
        currentComicSessionId = null
        currentChapterSessionId = null
        currentPageSessionId = null
        pagesVisitedInChapter = 0
        pagesReadInComic = 0
    }

    /**
     * Called from `MainActivity.onDestroy` (or `onStop` if you'd rather
     * count "foreground sessions"). Cleanly closes the current app
     * session and all live descendants.
     */
    suspend fun onAppStop() {
        closeCurrentPage()
        closeCurrentChapter()
        closeCurrentComic()
        currentAppSessionId?.let { id ->
            db.appSessionDao().close(id, now())
        }
        currentAppSessionId = null
    }

    // ══════════════════════════════════════════════════════════
    // Comic / chapter / page transitions
    // ══════════════════════════════════════════════════════════

    /**
     * Called whenever the user selects a new comic. Closes any current
     * comic_session (and its chapter/page descendants) and opens a new
     * one under the current app_session. If no app_session is open, this
     * is a no-op.
     */
    suspend fun onComicSelected(comicId: String) {
        closeCurrentComic()
        val parent = currentAppSessionId ?: return
        val row = ComicSessionEntity(
            appSessionId = parent,
            comicId = comicId,
            startTs = now(),
        )
        currentComicSessionId = db.comicSessionDao().insert(row)
        pagesReadInComic = 0
    }

    suspend fun onChapterEntered(comicId: String, chapterName: String) {
        closeCurrentChapter()
        val parent = currentComicSessionId ?: return
        val row = ChapterSessionEntity(
            comicSessionId = parent,
            comicId = comicId,
            chapterName = chapterName,
            startTs = now(),
        )
        currentChapterSessionId = db.chapterSessionDao().insert(row)
        pagesVisitedInChapter = 0
    }

    suspend fun onPageEntered(comicId: String, pageId: String) {
        closeCurrentPage()
        val parent = currentChapterSessionId ?: return
        val row = PageSessionEntity(
            chapterSessionId = parent,
            comicId = comicId,
            pageId = pageId,
            enterTs = now(),
        )
        currentPageSessionId = db.pageSessionDao().insert(row)
    }

    /**
     * Called from existing pointer / tap / swipe handlers. Increments
     * the current page_session's `interactionsN` counter. No-op if no
     * page is currently open.
     */
    suspend fun onPageInteraction() {
        val id = currentPageSessionId ?: return
        db.pageSessionDao().incrementInteractions(id)
    }

    // ══════════════════════════════════════════════════════════
    // Private close-out helpers
    // ══════════════════════════════════════════════════════════

    private suspend fun closeCurrentPage() {
        val id = currentPageSessionId ?: return
        db.pageSessionDao().close(id, now())
        currentPageSessionId = null
        pagesVisitedInChapter++
        pagesReadInComic++
    }

    private suspend fun closeCurrentChapter() {
        closeCurrentPage()
        val id = currentChapterSessionId ?: return
        db.chapterSessionDao().close(id, now(), pagesVisitedInChapter)
        currentChapterSessionId = null
        pagesVisitedInChapter = 0
    }

    private suspend fun closeCurrentComic() {
        closeCurrentChapter()
        val id = currentComicSessionId ?: return
        db.comicSessionDao().close(id, now(), pagesReadInComic)
        currentComicSessionId = null
        pagesReadInComic = 0
    }
}
