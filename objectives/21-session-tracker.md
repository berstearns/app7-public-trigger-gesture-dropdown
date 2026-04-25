# 21 — SessionHierarchyTracker Populates Aggregate Rows (Part 2)

**Owner:** implementer
**Depends on:** 20

## Success criterion

A new class `SessionHierarchyTracker` in `shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/` wraps the 4 new DAOs and exposes lifecycle-callback methods:

- `onAppStart()` → inserts `AppSessionEntity` with `startTs=now, endTs=null`, remembers the id
- `onAppStop()` → closes current page/chapter/comic/app sessions (sets `endTs`, computes `durationMs`, materializes `pagesRead`/`pagesVisited`)
- `onComicSelected(comicId: String)` → closes current comic session (if any), opens new one under current app session
- `onChapterEntered(chapterName: String)` → closes current chapter session, opens new one
- `onPageEntered(pageId: String)` → closes current page session, opens new one
- `onPageInteraction()` → increments `interactionsN` on current page session

`MainActivity` + `SessionTimeTracker` call these at the appropriate lifecycle hooks.

## How to verify

After a simulated reading session (start → comic select → chapter 1 → page 1 → page 2 → chapter 2 → app stop), the Room DB has:

```
app_sessions:      1 row  (startTs < endTs)
comic_sessions:    1 row  (linked to app_session_id)
chapter_sessions:  2 rows (both linked to comic_session_id)
page_sessions:     2 rows (one linked to each chapter_session_id, with non-null dwell_ms)
```

## Pass criteria

- All 4 session tables have the expected row counts after the simulated flow
- Every aggregate row has `endTs` set (no orphans after `onAppStop`)
- FK integrity: `SELECT * FROM page_sessions ps LEFT JOIN chapter_sessions cs ON ps.chapter_session_id=cs.id WHERE cs.id IS NULL` returns 0 rows

## Current status

- [ ] Not started
- [x] In progress
- [ ] Verified

**What's done:** class created, app/comic lifecycle hooks wired, crash recovery verified end-to-end.
**What's not done:** chapter/page lifecycle hooks are not yet wired into the image-viewer navigation code. `onPageInteraction` stub not yet called from the Compose tap/swipe handlers.

## Evidence

`shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/session/SessionHierarchyTracker.kt` implements the full API:

- `onAppStart()` — inserts `AppSessionEntity`; on entry first closes any stale previous row (endTs=NULL from a force-killed process), recovering from abnormal termination
- `onAppStop()` — cleanly closes the full hierarchy chain (page → chapter → comic → app)
- `onComicSelected(comicId)` — closes previous comic/chapter/page, opens new
- `onChapterEntered(comicId, chapterName)` — closes previous chapter/page, opens new
- `onPageEntered(comicId, pageId)` — closes previous page, opens new
- `onPageInteraction()` — increments `interactionsN` on the current page_session
- All counters (`pagesVisitedInChapter`, `pagesReadInComic`) are materialized into the closing row's aggregate fields

**Wired in MainActivity:**
- `onCreate` → `lifecycleScope.launch { sessionHierarchy.onAppStart(); onComicSelected(initialComicId) }`
- `onDestroy` → `lifecycleScope.launch { sessionHierarchy.onAppStop() }`
- `selectActiveComic(comicId)` → calls `sessionHierarchy.onComicSelected(comicId)`

**Runtime verification with crash recovery:**

1. First launch with `selected_asset_id='batch-01-hq-tokens'`:
   ```
   app_sessions:    1 | 1775748214348 | NULL          (live)
   comic_sessions:  1 | 1 | batch-01-hq-tokens | ... | NULL   (live)
   ```

2. Force-stop (SIGKILL) skips `onDestroy`'s coroutine. `endTs` stays NULL.

3. Relaunch with `selected_asset_id='one_piece'`:
   ```
   app_sessions:
     1 | 1775748214348 | 1775748245304       ← closed by crash-recovery in onAppStart
     2 | 1775748245412 | NULL                ← new, live
   comic_sessions:
     1 | 1 | batch-01-hq-tokens | ... | NULL ← orphan, worker close-out (obj 24) cleans up
     2 | 2 | one_piece          | ... | NULL ← new, live
   ```

Crash recovery on `app_sessions` works. The orphaned `comic_sessions(id=1)` reveals a limitation: the tracker's recovery path only closes the app_session row, not its descendants. The worker-side close-out policy (objective 24) is expected to handle those when it sees the parent's endTs set but the child's isn't.

## Remaining work (out of scope for this turn)

- Wire `onChapterEntered` into the existing chapter-navigation code in `LauncherViewModel` / `SessionTimeTracker`
- Wire `onPageEntered` into the image-viewer page-change handler
- Wire `onPageInteraction` into `detectTapGestures` in `MainScreen.kt:137-168` (alongside the existing swipe/longpress handlers)
- Extend the tracker's crash recovery to also close orphaned children when closing a stale parent
