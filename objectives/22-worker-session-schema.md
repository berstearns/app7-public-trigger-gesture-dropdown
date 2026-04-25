# 22 — Worker Schema Gains 4 Session Aggregate Tables (Part 2)

**Owner:** implementer
**Depends on:** 21

## Success criterion

The worker schema at `/home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql` is extended with four new tables matching the producer-side entities, with proper composite FK to `comics` where relevant:

```sql
CREATE TABLE app_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    local_id INTEGER NOT NULL,
    ...
    UNIQUE(device_id, local_id)
);

CREATE TABLE comic_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    local_id INTEGER NOT NULL,
    app_session_local_id INTEGER NOT NULL,   -- the producer-side id, to resolve after ingest
    comic_id TEXT NOT NULL REFERENCES comics(comic_id),
    ...
    UNIQUE(device_id, local_id)
);

CREATE TABLE chapter_sessions (...);
CREATE TABLE page_sessions (...);
```

Worker's `_ensure_schema` recreates the DB if any of these tables are missing.

## Pass criteria

- All 4 new tables present via `.schema`
- FK to `comics(comic_id)` enforced
- Worker starts with `schema applied from ...` log line and no errors

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 16:25 local time.

**Schema extension** at `/home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql` appends 4 new tables with composite FK:

- `app_sessions` — `(device_id, local_id)` unique, indexed on `(device_id, start_ts)`
- `comic_sessions` — parent pointer via `app_session_local_id`, FK to `comics(comic_id)`, `(device_id, local_id)` unique
- `chapter_sessions` — parent pointer via `comic_session_local_id`, composite FK to `chapters(comic_id, chapter_name)`, `(device_id, local_id)` unique
- `page_sessions` — parent pointer via `chapter_session_local_id`, `(device_id, local_id)` unique

Each table has a `close_reason TEXT` column for the worker's future close-out policy (objective 24) to record reasons like `'force_closed_by_new_session'`.

**Parent pointer design note:** the `*_local_id` columns store the producer-side Room PK of the parent, NOT the worker-side AUTOINCREMENT id. The worker can resolve to the worker-side id via a `(device_id, local_id)` join when needed. This decouples parent-child linkage from insertion order, which is important because the producer and worker have independent AUTOINCREMENT sequences.

**Syntax validation:** `sqlite3 :memory: < schema.sql` → `SCHEMA OK`, no errors.

**Runtime verification** after restarting the worker with the updated schema:

```
$ sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db \
    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('app_sessions','comic_sessions','chapter_sessions','page_sessions') ORDER BY name;"
app_sessions
chapter_sessions
comic_sessions
page_sessions
```

All 4 tables present in the fresh worker DB. Worker startup log confirms:

```
  app7: schema applied from /home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql
app7-worker 'bernardo-pc-app7-hierarchy-verify-app7-hierarchy-verify' v<sha> polling PLACEHOLDER_BACKEND_HOST:9999 every 2s
```

Worker is currently polling the DO queue with the extended schema. Once the producer starts including session hierarchy rows in its `UnifiedPayload` (objective 23), the worker will route them into these tables.
