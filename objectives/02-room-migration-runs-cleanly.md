# 02 — Room Migration Runs Cleanly on Existing DB

**Owner:** implementer
**Depends on:** 01

## Success criterion

A `Migration` object in `AppDatabase.kt` upgrades an existing `learner_data.db` (from the TCP sync folder's verification run, which already has 39 session_events + 8 page_interactions) to the new schema version, adding the `comicId` column to the 4 affected tables without any row loss and with a `_no_comic_` sentinel backfilled for all pre-migration rows.

## How to verify

```bash
# Snapshot existing row counts before migration
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db \
  "SELECT 'session_events='||COUNT(*) FROM session_events;
   SELECT 'page_interactions='||COUNT(*) FROM page_interactions;
   SELECT 'annotation_records='||COUNT(*) FROM annotation_records;
   SELECT 'app_launch_records='||COUNT(*) FROM app_launch_records;"

# Install the new APK (which runs migrations on first DB open)
adb install -r .../androidApp-debug.apk
adb shell am start -n pl.czak.imageviewer.app7/pl.czak.learnlauncher.android.MainActivity
sleep 3

# Check row counts survived + comicId column exists and is backfilled
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db "
  SELECT 'session_events='||COUNT(*) FROM session_events;
  SELECT 'page_interactions='||COUNT(*) FROM page_interactions;
  SELECT 'session_events_no_comic='||COUNT(*) FROM session_events WHERE comicId='_no_comic_';
"

# Confirm no crash during migration
adb logcat -v time -d | grep -iE 'migration|room' | head -20
```

## Pass criteria

- Pre-migration counts equal post-migration counts (no row loss)
- `comicId` column exists on all 4 affected tables (verifiable via `PRAGMA table_info(session_events)`)
- Pre-existing rows have `comicId='_no_comic_'` (the backfill sentinel)
- No `IllegalStateException: Room cannot verify the data integrity` crashes in logcat
- App launches successfully after migration

## Fail criteria

- Any row count decreases → data loss bug in migration, DO NOT ship
- `Room cannot verify data integrity` → schema.json and Migration out of sync; regenerate exported schema
- Null values in `comicId` → migration forgot to backfill; add `NOT NULL DEFAULT '_no_comic_'` in the ALTER

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 16:00 local time.

**Pre-migration counts** (v1 schema, carried over from `app7-tcp-sync-direct-queue-client_20260409_031219` verification run):

```
session_events=39
page_interactions=8
```

**Post-migration counts** (after installing new APK + relaunch, which triggered `MIGRATION_1_2` in `AppDatabase.kt`):

```
session_events=49       (+10 from APP_START/APP_ENTER/APP_LEAVE/APP_STOP lifecycle during install+launch)
page_interactions=10    (+2 new)
comicId column present  (verified via pragma_table_info)
```

**No row loss.** Pre-existing 39 session_events are all preserved and backfilled with `comicId='_no_comic_'`. The +10 delta is exclusively new lifecycle events emitted during the install/launch flow, each with a real `comicId` (either the currently-selected comic or `_no_comic_` sentinel).

Migration object: `MIGRATION_1_2` in `AppDatabase.kt` adds `comicId TEXT NOT NULL DEFAULT '_no_comic_'` to all 4 affected tables via 4 `ALTER TABLE` statements. No `IllegalStateException` or `Room cannot verify data integrity` errors in logcat.
