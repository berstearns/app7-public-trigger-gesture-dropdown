# 30 — End-to-End Pipeline Verification Reference

**Owner:** anyone resuming this folder
**Depends on:** 08 (Part 1 gate), optionally 23 (Part 2 session sync)

## Purpose

This is a **reference card**, not a pass/fail objective. It documents every stage of the user-interaction data pipeline and the exact command to verify each stage. Use it when you need to diagnose where data stopped flowing, or to confirm the full pipeline after infrastructure changes (worker restart, DO reboot, APK reinstall, etc.).

## Pipeline stages

```
Stage 1          Stage 2              Stage 3           Stage 4
App logs      →  UnifiedPayload    →  DO Queue        →  Worker polls
(Room DB on      built + serialized   server holds        job from queue
 emulator)       by TcpQueueSyncApi   the job             every 2s

Stage 5          Stage 6              Stage 7 (future)
Worker saves  →  Round-trip        →  Offline collector
in own sqlite    comparator           merges worker DBs
DB               confirms match       into global archive
```

## Stage 1 — App logs user interactions to Room DB

**What:** User taps, page views, settings changes, etc. are written to Room entities with `synced=0` and `comicId` populated from `settingsStore.getString("selected_asset_id")`.

**Where:** Emulator Room DB at `databases/learner_data.db` inside the app's private storage.

**Check unsynced rows waiting to be sent:**

```bash
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db \
  "SELECT 'session_events='||COUNT(*) FROM session_events WHERE synced=0;
   SELECT 'page_interactions='||COUNT(*) FROM page_interactions WHERE synced=0;
   SELECT 'annotation_records='||COUNT(*) FROM annotation_records WHERE synced=0;
   SELECT 'app_launch_records='||COUNT(*) FROM app_launch_records WHERE synced=0;
   SELECT 'settings_changes='||COUNT(*) FROM settings_changes WHERE synced=0;
   SELECT 'chat_messages='||COUNT(*) FROM chat_messages WHERE synced=0;
   SELECT 'region_translations='||COUNT(*) FROM region_translations WHERE synced=0;"
```

**Check comicId is populated (not all `_no_comic_`):**

```bash
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db \
  "SELECT comicId, COUNT(*) FROM session_events GROUP BY comicId;"
```

**If Part 2 session hierarchy is active, also check:**

```bash
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db \
  "SELECT 'app_sessions='||COUNT(*) FROM app_sessions WHERE synced=0;
   SELECT 'comic_sessions='||COUNT(*) FROM comic_sessions WHERE synced=0;
   SELECT 'chapter_sessions='||COUNT(*) FROM chapter_sessions WHERE synced=0;
   SELECT 'page_sessions='||COUNT(*) FROM page_sessions WHERE synced=0;"
```

**Healthy:** At least some rows with `synced=0` (means data is being generated and waiting for sync).

**Broken:** Zero rows everywhere → app isn't logging. Check logcat for crashes in `SessionRepository` or `LearnerDataRepository`.

---

## Stage 2 — UnifiedPayload built and submitted to queue

**What:** `SyncService.sync()` calls `UnifiedPayloadBuilder.buildUnifiedPayload()` to collect all `synced=0` rows, wraps them in a `UnifiedPayload` (schema_version=5), and `TcpQueueSyncApi.upload()` sends them as a TCP job to the DO queue.

**Where:** adb logcat output from the app process.

**Check recent submissions:**

```bash
adb logcat -v time -d | grep -E 'submit ok id=|SyncService|auto.sync' | tail -20
```

**Check the exact job ID of the most recent submit:**

```bash
N=$(adb logcat -v time -d | grep 'submit ok id=' | tail -1 | grep -oE 'id=[0-9]+' | cut -d= -f2)
echo "Most recent job: $N"
```

**Healthy:** `submit ok id=NNNN` lines appearing periodically (every ~60s if auto-sync is on).

**Broken:** No `submit ok` lines → check for `connect failed` or `timeout` in logcat. The DO queue at `PLACEHOLDER_BACKEND_HOST:9999` may be down.

---

## Stage 3 — Job is visible on the DO queue

**What:** The job sits on the queue server waiting for a worker to poll it.

**Where:** Queue server, queried via `client.py`.

```bash
python3 /home/b/simple-tcp-comm/client.py ls
```

**Check a specific job's full payload:**

```bash
python3 /home/b/simple-tcp-comm/client.py status $N
```

**Healthy:** Job shows `status: pending` (waiting) or `status: done` (already picked up). The payload should contain `schema_version: 5` and populated `tables`.

**Broken:** Job shows `status: error` → read the `result.error` field. Or no jobs at all → Stage 2 failed.

---

## Stage 4 — Worker polls and picks up the job

**What:** The worker process (running in tmux) polls the queue every 2s, claims the job, and runs `_ingest_unified_payload`.

**Where:** tmux session `app7-hierarchy-worker`.

**Attach to the worker pane to watch it live:**

```bash
tmux attach -t app7-hierarchy-worker
# Ctrl-B D to detach without killing
```

**Check if worker is registered and alive:**

```bash
python3 /home/b/simple-tcp-comm/client.py workers | grep hierarchy-verify
```

**Check if a specific job was acked by the worker:**

```bash
python3 /home/b/simple-tcp-comm/client.py status $N
# Look for: status=done, result.accepted=True
```

**Healthy:** Worker tmux pane shows periodic `poll → 0 jobs` (idle) or `poll → 1 job → ingesting → accepted` lines. `client.py status` shows `accepted: True` with row counts.

**Broken:** Worker not running → restart it (see below). Worker running but job stuck as `pending` → worker may be registered under a different name than the job targets. Check `WORKER_NAME` in `.env.app7-hierarchy`.

**Restart the worker:**

```bash
tmux kill-session -t app7-hierarchy-worker 2>/dev/null
tmux new-session -d -s app7-hierarchy-worker -c /home/b/simple-tcp-comm
tmux send-keys -t app7-hierarchy-worker \
  "set -a; source .env.app7-hierarchy; set +a; \
   python3 workers/app7-explicit-db-hierarchy_20260409_154552/worker.py" C-m
```

---

## Stage 5 — Worker saves rows in its own sqlite DB

**What:** `_ingest_unified_payload` inserts rows into the worker's local sqlite DB. `_upsert_catalog` populates `comics`, `chapters`, `pages` from the ingested data. All event tables get `comic_id` on every row.

**Where:** Worker DB at `/home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db`.

**Row counts across all tables:**

```bash
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db <<'SQL'
.mode column
.headers on
SELECT 'comics'              AS tbl, COUNT(*) AS n FROM comics           UNION ALL
SELECT 'chapters',                   COUNT(*)      FROM chapters         UNION ALL
SELECT 'pages',                      COUNT(*)      FROM pages            UNION ALL
SELECT 'session_events',             COUNT(*)      FROM session_events   UNION ALL
SELECT 'page_interactions',          COUNT(*)      FROM page_interactions UNION ALL
SELECT 'annotation_records',         COUNT(*)      FROM annotation_records UNION ALL
SELECT 'chat_messages',              COUNT(*)      FROM chat_messages    UNION ALL
SELECT 'app_launch_records',         COUNT(*)      FROM app_launch_records UNION ALL
SELECT 'settings_changes',           COUNT(*)      FROM settings_changes UNION ALL
SELECT 'region_translations',        COUNT(*)      FROM region_translations UNION ALL
SELECT 'ingest_batches',             COUNT(*)      FROM ingest_batches;
SQL
```

**Check collision-freedom (the core Part 1 invariant):**

```bash
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db \
  "SELECT comic_id, chapter_name FROM chapters ORDER BY comic_id;"
```

**If Part 2 session tables are active:**

```bash
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db <<'SQL'
SELECT 'app_sessions='||COUNT(*)     FROM app_sessions;
SELECT 'comic_sessions='||COUNT(*)   FROM comic_sessions;
SELECT 'chapter_sessions='||COUNT(*) FROM chapter_sessions;
SELECT 'page_sessions='||COUNT(*)    FROM page_sessions;
SQL
```

**Healthy:** Row counts match what the app sent. `ingest_batches` count increments with each sync. Multiple `comic_id` values in `chapters` if user interacted with multiple comics.

**Broken:** Zero rows → Stage 4 failed (worker didn't ingest). Rows present but `comic_id='_no_comic_'` on new data → Stage 1 broken (comicId not populated in Room).

---

## Stage 6 — Round-trip comparator confirms emulator == worker

**What:** `scripts/verify-sync-roundtrip.py` pulls the Room DB from the emulator, compares row-for-row against the worker DB. Reports per-table: `missing`, `extra`, `content_mismatch`.

**Where:** Run from this feature folder.

```bash
cd /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy-bug-fixes_20260410_161800
python3 scripts/verify-sync-roundtrip.py \
  --worker-db /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db
```

**Healthy:** `OVERALL: PASS (7/7 tables match)` (or `11/11` after Part 2 session tables are wired). Exit code 0.

**Broken:** `FAIL` on specific tables → check `missing` (rows in emulator but not worker = sync didn't send them) vs `content_mismatch` (rows differ = serialization bug). Run with `--verbose` for row-level diff.

**Known limitation:** The comparator does NOT yet check `comic_id` in row tuples. A producer/worker disagreement on which comic a row belongs to would not be caught here. Objective 08's direct SELECTs cover this semantically for Part 1.

---

## Stage 7 — Offline collector consolidates into global archive DB (NOT YET IMPLEMENTED)

**What:** `collector.py` queries each worker's DB via the queue (submit `query` jobs targeted at specific workers), merges results into a single `archive.db` with `source_user` and `source_worker` metadata.

**Where:** Design doc at `/home/b/simple-tcp-comm/docs/implementation_plan/03-offline-collector.md`.

**Dependencies before this can be built:**
- Strategy 01 (user-worker affinity) — needed to know which users live on which workers
- Strategy 05 (per-user database files) — optional but improves isolation

**Planned verification (once built):**

```bash
ARCHIVE_DB=/tmp/test-archive.db python3 /home/b/simple-tcp-comm/collector.py collect
sqlite3 /tmp/test-archive.db "SELECT source_user, source_worker, COUNT(*) FROM session_events GROUP BY 1,2;"
sqlite3 /tmp/test-archive.db "SELECT * FROM collection_log ORDER BY collected_at DESC LIMIT 20;"
```

---

## Quick full-pipeline smoke test (stages 1-6 in one shot)

Run these sequentially after installing a fresh APK or restarting infrastructure:

```bash
# 0. Confirm emulator + worker are alive
adb devices | grep emulator
tmux has-session -t app7-hierarchy-worker && echo "worker alive" || echo "WORKER DOWN"

# 1. Check unsynced rows exist in Room
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db \
  "SELECT COUNT(*) AS unsynced FROM session_events WHERE synced=0;"

# 2. Force a sync (launch app with auto_sync flag)
adb shell am start -n pl.czak.imageviewer.app7/pl.czak.learnlauncher.android.MainActivity --ez auto_sync true
echo "Waiting 75s for auto-sync tick..."
sleep 75

# 3. Grab the latest job ID
N=$(adb logcat -v time -d | grep 'submit ok id=' | tail -1 | grep -oE 'id=[0-9]+' | cut -d= -f2)
echo "Job ID: $N"

# 4. Check worker acked it
python3 /home/b/simple-tcp-comm/client.py status "$N" 2>/dev/null | head -5

# 5. Check worker DB row counts
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db \
  "SELECT 'session_events='||COUNT(*) FROM session_events;
   SELECT 'page_interactions='||COUNT(*) FROM page_interactions;"

# 6. Run the comparator
cd /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy-bug-fixes_20260410_161800
python3 scripts/verify-sync-roundtrip.py \
  --worker-db /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db
echo "exit=$?"
```

---

## Key file and process reference

| Component | Location |
|-----------|----------|
| Android app package | `pl.czak.imageviewer.app7` |
| Room DB (emulator) | `databases/learner_data.db` inside app private storage |
| UnifiedPayload DTO | `shared/src/commonMain/kotlin/pl/czak/learnlauncher/data/model/UnifiedPayload.kt` |
| UnifiedPayloadBuilder | `shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/export/UnifiedPayloadBuilder.kt` |
| SyncService | `shared/src/commonMain/kotlin/pl/czak/learnlauncher/data/sync/SyncService.kt` |
| TcpQueueSyncApi | `shared/src/commonMain/kotlin/pl/czak/learnlauncher/data/sync/TcpQueueSyncApi.kt` |
| DO queue server | `PLACEHOLDER_BACKEND_HOST:9999` (task-agnostic, always on) |
| client.py | `/home/b/simple-tcp-comm/client.py` |
| Worker source | `/home/b/simple-tcp-comm/workers/app7-explicit-db-hierarchy_20260409_154552/worker.py` |
| Worker env | `/home/b/simple-tcp-comm/.env.app7-hierarchy` |
| Worker DB | `/home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db` |
| Worker tmux session | `app7-hierarchy-worker` |
| Worker schema | `/home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql` |
| Round-trip comparator | `scripts/verify-sync-roundtrip.py` (in this feature folder) |
| Collector design doc | `/home/b/simple-tcp-comm/docs/implementation_plan/03-offline-collector.md` |

## Current status

- [ ] Not started
- [x] Reference card — not a pass/fail objective
