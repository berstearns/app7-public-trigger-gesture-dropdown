# 20 — Room Entities for Session Hierarchy (Part 2)

**Owner:** implementer
**Depends on:** 08 (Part 1 shipped and verified first)

## Success criterion

Four new Room `@Entity` classes exist in `shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/db/entity/`:

- `AppSessionEntity` — `id`, `startTs`, `endTs?`, `durationMs?`, `userId`, `deviceId`, `appVersion`, `synced`
- `ComicSessionEntity` — `id`, `appSessionId→`, `comicId`, `startTs`, `endTs?`, `durationMs?`, `pagesRead?`, `synced`
- `ChapterSessionEntity` — `id`, `comicSessionId→`, `comicId`, `chapterName`, `startTs`, `endTs?`, `durationMs?`, `pagesVisited?`, `synced`
- `PageSessionEntity` — `id`, `chapterSessionId→`, `comicId`, `pageId`, `enterTs`, `leaveTs?`, `dwellMs?`, `interactionsN`, `synced`

Matching DAOs (`AppSessionDao`, `ComicSessionDao`, etc.) with the usual `insert`, `getUnsynced`, `markSynced` methods.

`AppDatabase` version is bumped and a new `Migration` object adds these 4 tables.

## How to verify

```bash
cd /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552
grep -rn "AppSessionEntity\|ComicSessionEntity\|ChapterSessionEntity\|PageSessionEntity" shared/src/androidMain/kotlin/
./gradlew :shared:compileAndroidMain 2>&1 | tail -20
```

## Pass criteria

- All 4 entities present
- All 4 DAOs present
- `@Database` entities list updated
- `BUILD SUCCESSFUL`

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 16:23 local time.

**Entities added** to `shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/db/entity/Entities.kt`:

```kotlin
@Entity(tableName = "app_sessions")       data class AppSessionEntity(...)
@Entity(tableName = "comic_sessions")     data class ComicSessionEntity(...)
@Entity(tableName = "chapter_sessions")   data class ChapterSessionEntity(...)
@Entity(tableName = "page_sessions")      data class PageSessionEntity(...)
```

**DAOs added** to `shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/db/dao/Daos.kt`:

- `AppSessionDao` — `insert`, `close(id, endTs)`, `getUnsynced`, `markSynced`, `getMostRecent`
- `ComicSessionDao` — same pattern + `close(id, endTs, pagesRead)`
- `ChapterSessionDao` — same + `close(id, endTs, pagesVisited)`
- `PageSessionDao` — same + `close(id, leaveTs)` + `incrementInteractions`

**AppDatabase.kt** version bumped `2 → 3`, new `MIGRATION_2_3` adds all 4 tables via `CREATE TABLE IF NOT EXISTS`. Room's schema verification passes (no `IllegalStateException` in logcat).

`./gradlew :shared:compileAndroidMain :androidApp:compileDebugKotlin` → `BUILD SUCCESSFUL in 5s`.

**Runtime verification:** after installing the new APK and launching on the emulator, `adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db '.tables'` shows all 4 new tables present alongside the existing ones.
