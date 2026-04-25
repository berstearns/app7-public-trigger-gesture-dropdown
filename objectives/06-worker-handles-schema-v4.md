# 06 — Worker Handler Accepts schema_v4 and Populates `comic_id`

**Owner:** implementer
**Depends on:** 05

## Success criterion

The cloned worker's `_ingest_unified_payload` handler accepts a UnifiedPayload with `schema_version == 4`, reads `comic_id` from each row, populates the new `comics` catalog table before insertion, and writes the `comic_id` column in every INSERT. Handler also maintains backward compatibility with `schema_version == 3` by treating missing `comic_id` as `'_no_comic_'`.

## How to verify

```bash
# 1. Confirm the clone handler reads comic_id
grep -n "comic_id\|schema_version" /home/b/simple-tcp-comm/workers/app7-explicit-db-hierarchy_20260409_154552/worker.py

# 2. _upsert_catalog should be updated for composite keys + comics table
grep -A 10 "def _upsert_catalog" /home/b/simple-tcp-comm/workers/app7-explicit-db-hierarchy_20260409_154552/worker.py | head -30

# 3. Submit a real payload (after Part 1 producer-side changes are live) and check the result JSON
python3 /home/b/simple-tcp-comm/client.py status <N>
# Must show: result.accepted=True, result.counts populated, no "error" key

# 4. Inspect worker DB after ingest
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db "
SELECT comic_id, COUNT(*) FROM session_events GROUP BY comic_id;
SELECT comic_id, chapter_name FROM chapters;
SELECT comic_id FROM comics;
"
```

## Expected output

- Handler code at line ~358 checks `schema_version in (3, 4)`
- `_upsert_catalog` iterates events with `(comic_id, chapter_name, page_id)` tuples
- Post-ingest worker DB shows rows in `comics` and composite `chapters`/`pages`
- All 4 event tables have `comic_id` populated (either a real comic_id or `'_no_comic_'`)

## Pass criteria

- Submit from Android with `schema_version=4` → worker acks with `accepted=True`
- Submit from legacy producer (hypothetical, with `schema_version=3`) → worker still acks with `accepted=True` and backfills `comic_id='_no_comic_'`
- `comics` table has at least one row after ingest
- No FK constraint violations in the worker pane output

## Fail criteria

- Handler rejects with `"unsupported schema_version"` when receiving v4
- FK constraint violation on INSERT → composite key migration incomplete
- `comics` table remains empty even after events with real `comic_id`

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 16:07 local time.

**Handler changes** at `/home/b/simple-tcp-comm/workers/app7-explicit-db-hierarchy_20260409_154552/worker.py`:

- Line ~362: `schema_version not in (3, 4)` (accepts both; v3 rows backfill `comic_id='_no_comic_'`)
- `_upsert_catalog` rewritten to iterate `(comic_id, chapter_name)` and `(comic_id, page_id)` tuples, populate `comics` table first, then composite-key `chapters`/`pages`
- All 4 `_ingest_*` functions for event tables (session_events, annotation_records, page_interactions, app_launch_records) now pass `r.get("comic_id") or NO_COMIC_SENTINEL` into the INSERT

**Jobs 2631 and 2632 both acked successfully:**

```
Job 2631: accepted=True, batch_id=2, counts={'session_events': 14, 'page_interactions': 4, ...}
Job 2632: accepted=True, batch_id=3, counts={'session_events': 49, 'page_interactions': 10, 'settings_changes': 1, ...}
```

No `"error"` key in either result. No FK constraint violations in worker tmux pane output. Post-ingest inspection:

```
SELECT * FROM comics;
batch-01-hq-tokens|
one_piece|
naruto|
_no_comic_|          ← legacy rows from pre-migration backfill
```

Three real comics plus the `_no_comic_` sentinel — all correctly populated via the composite-key catalog upsert.
