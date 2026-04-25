package pl.czak.learnlauncher.domain.session

import pl.czak.learnlauncher.currentTimeMillis

data class CompletedSession(
    val sessionType: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val chapterName: String? = null,
    val pageId: String? = null,
    val pageTitle: String? = null
)

data class SessionElapsed(
    val appMs: Long?,
    val chapterMs: Long?,
    val pageMs: Long?
)

class SessionTimeTracker {

    private var appStartTime: Long? = null
    private var chapterStartTime: Long? = null
    private var chapterName: String? = null
    private var pageStartTime: Long? = null
    private var pageId: String? = null
    private var pageTitle: String? = null

    fun startAppSession() {
        appStartTime = currentTimeMillis()
    }

    fun endAppSession(): List<CompletedSession> {
        val now = currentTimeMillis()
        val results = mutableListOf<CompletedSession>()
        endPageInternal(now)?.let { results.add(it) }
        endChapterInternal(now)?.let { results.add(it) }
        val appStart = appStartTime
        if (appStart != null) {
            results.add(
                CompletedSession(
                    sessionType = "APP",
                    startedAt = appStart,
                    endedAt = now,
                    durationMs = now - appStart
                )
            )
        }
        appStartTime = null
        return results
    }

    fun startChapterSession(name: String): List<CompletedSession> {
        val now = currentTimeMillis()
        val results = mutableListOf<CompletedSession>()
        endPageInternal(now)?.let { results.add(it) }
        endChapterInternal(now)?.let { results.add(it) }
        chapterStartTime = now
        chapterName = name
        return results
    }

    fun startPageSession(id: String, title: String): CompletedSession? {
        val now = currentTimeMillis()
        val ended = endPageInternal(now)
        pageStartTime = now
        pageId = id
        pageTitle = title
        return ended
    }

    private fun endPageInternal(now: Long): CompletedSession? {
        val start = pageStartTime ?: return null
        val result = CompletedSession(
            sessionType = "PAGE",
            startedAt = start,
            endedAt = now,
            durationMs = now - start,
            chapterName = chapterName,
            pageId = pageId,
            pageTitle = pageTitle
        )
        pageStartTime = null
        pageId = null
        pageTitle = null
        return result
    }

    private fun endChapterInternal(now: Long): CompletedSession? {
        val start = chapterStartTime ?: return null
        val result = CompletedSession(
            sessionType = "CHAPTER",
            startedAt = start,
            endedAt = now,
            durationMs = now - start,
            chapterName = chapterName
        )
        chapterStartTime = null
        chapterName = null
        return result
    }

    fun getElapsedMs(): SessionElapsed {
        val now = currentTimeMillis()
        return SessionElapsed(
            appMs = appStartTime?.let { now - it },
            chapterMs = chapterStartTime?.let { now - it },
            pageMs = pageStartTime?.let { now - it }
        )
    }
}
