# 05 — Worker Schema Has `comics` Table + Composite-Keyed Catalog

**Owner:** implementer
**Depends on:** 04

## Success criterion

A new worker clone at `/home/b/simple-tcp-comm/workers/app7-explicit-db-hierarchy_20260409_154552/` and schema at `/home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql` implement the upgraded worker-side schema:

1. **NEW** `comics` table — `(comic_id TEXT PRIMARY KEY, display_name TEXT, added_at INTEGER)`
2. **CHANGED** `chapters` — composite primary key `(comic_id, chapter_name)`, FK to `comics(comic_id)`
3. **CHANGED** `pages` — composite primary key `(comic_id, page_id)`, FK to both `comics(comic_id)` and `chapters(comic_id, chapter_name)`
4. **CHANGED** 4 event tables (session_events, page_interactions, annotation_records, app_launch_records) — each gains a `comic_id TEXT NOT NULL DEFAULT '_no_comic_'` column and their FK to chapters/pages updates to the composite shape
5. **UNCHANGED** 3 tables — `chat_messages`, `settings_changes`, `region_translations` stay exactly as they are in `app7-tcp-sync-direct-queue-client_20260409_031219/`

When the worker starts, `_ensure_schema` creates a fresh DB at `/home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db` with all these tables.

## How to verify

```bash
# 1. Schema file has the new tables and keys
grep -nE 'CREATE TABLE comics|PRIMARY KEY \(comic_id|comic_id TEXT NOT NULL' \
  /home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql

# 2. Worker clone exists with correct adaptations
ls -la /home/b/simple-tcp-comm/workers/app7-explicit-db-hierarchy_20260409_154552/

# 3. Start the worker in a new tmux session and confirm fresh DB creation
tmux kill-session -t app7-hierarchy-worker 2>/dev/null
tmux new-session -d -s app7-hierarchy-worker -c /home/b/simple-tcp-comm
# Create a .env.app7-hierarchy with QUEUE_DBS pointing at /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db
tmux send-keys -t app7-hierarchy-worker \
  "set -a; source .env.app7-hierarchy; set +a; python3 workers/app7-explicit-db-hierarchy_20260409_154552/worker.py" C-m
sleep 4

# 4. Confirm DB has the expected schema
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db '.schema comics'
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db '.schema chapters'
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db '.schema pages'
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db "PRAGMA table_info(session_events);" | grep comic_id
```

## Expected output

```sql
CREATE TABLE comics ( comic_id TEXT PRIMARY KEY, ... );
CREATE TABLE chapters ( comic_id TEXT NOT NULL, chapter_name TEXT NOT NULL, PRIMARY KEY (comic_id, chapter_name), ... );
CREATE TABLE pages ( comic_id TEXT NOT NULL, page_id TEXT NOT NULL, ..., PRIMARY KEY (comic_id, page_id), ... );
```

And `PRAGMA table_info(session_events)` output contains a row for the `comic_id` column.

## Pass criteria

- Schema file contains all 5 listed changes
- Worker starts without errors (tmux pane shows `polling PLACEHOLDER_BACKEND_HOST:9999 every 2s`)
- Fresh DB is created with correct tables
- `comic_id` column present in all 4 event tables

## Fail criteria

- Schema file missing any of the 5 changes
- Worker crashes on startup (schema SQL error, FK syntax error, etc.)
- `comic_id` missing from any event table

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 16:00 local time.

**Schema file** at `/home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql` contains:

```sql
CREATE TABLE comics (
    comic_id     TEXT PRIMARY KEY,
    display_name TEXT,
    added_at     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE chapters (
    comic_id     TEXT NOT NULL REFERENCES comics(comic_id),
    chapter_name TEXT NOT NULL,
    PRIMARY KEY (comic_id, chapter_name)
);

CREATE TABLE pages (
    comic_id TEXT NOT NULL, page_id TEXT NOT NULL,
    chapter_name TEXT, page_title TEXT,
    PRIMARY KEY (comic_id, page_id),
    FOREIGN KEY (comic_id, chapter_name) REFERENCES chapters(comic_id, chapter_name)
);
```

All 4 event tables have `comic_id TEXT NOT NULL DEFAULT '_no_comic_'` + an index on `(comic_id, chapter_name)` where applicable.

**Fresh worker DB created on startup** at `/home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db`. Tables present:

```
annotation_records  chat_messages   page_interactions    session_events
app_launch_records  comics          pages                settings_changes
chapters            images          ingest_batches       region_translations
```

Worker tmux pane (`app7-hierarchy-worker`) shows:

```
app7: schema applied from /home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql
app7-worker 'bernardo-pc-app7-hierarchy-verify-app7-hierarchy-verify' v6bdff4e polling PLACEHOLDER_BACKEND_HOST:9999 every 2s
```

`PRAGMA table_info(session_events)` shows `comic_id|TEXT|1|'_no_comic_'|0` — NOT NULL with the sentinel default.
