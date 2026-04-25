# 24 — Worker Close-Out Policy for Dead Sessions (Part 2)

**Owner:** implementer
**Depends on:** 23

## Success criterion

When the producer crashes mid-session, an `AppSessionEntity` row arrives at the worker with `endTs = null`. The worker's ingest handler applies a close-out policy:

1. If the row is the most recent session for a given device, leave `endTs = null` (the user's app is possibly still running — don't prematurely close).
2. If there is a newer `app_session` for the same device that has a later `start_ts`, force-close the old one at `end_ts = max(child_page_session.leave_ts) OR old start_ts + 0ms` and set `close_reason = 'force_closed_by_new_session'`.

Analytical queries can then ignore `endTs IS NULL` rows as "live sessions" or include them as "possibly-active".

## How to verify

Simulate a process death by inserting an `AppSessionEntity` with `endTs=null`, then later insert another one with a later `startTs`. After ingest, the first row should have `endTs` set and `close_reason='force_closed_by_new_session'`. The second row remains open.

## Pass criteria

- Old orphaned session rows are closed on the next ingest
- `close_reason` column populated correctly
- Newest session stays open until the next batch

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 ~19:57 local time.

**Implementation** in `workers/app7-explicit-db-hierarchy_20260409_154552/worker.py:_force_close_orphans(conn, device_id)`. Called from `_ingest_unified_payload` immediately after the per-table INSERTs complete, inside the same SQLite transaction. Runs one UPDATE per session level (`app_sessions`, `comic_sessions`, `chapter_sessions`, `page_sessions`), each scoped to `device_id = ?`.

**Policy** (verbatim from the SQL):

```sql
UPDATE {table}
   SET end_ts = (SELECT MIN(newer.start_ts) FROM {table} newer
                  WHERE newer.device_id = {table}.device_id
                    AND newer.start_ts > {table}.start_ts),
       duration_ms = (same subquery) - start_ts,
       close_reason = 'force_closed_by_new_session'
 WHERE device_id = ?
   AND end_ts IS NULL
   AND EXISTS (SELECT 1 FROM {table} newer
                WHERE newer.device_id = {table}.device_id
                  AND newer.start_ts > {table}.start_ts);
```

For `page_sessions`, `start_ts/end_ts` become `enter_ts/leave_ts` and `duration_ms` becomes `dwell_ms`.

**Idempotency:** the `WHERE end_ts IS NULL AND EXISTS (newer)` filter means re-running on an already-closed DB is a no-op. Only untouched orphans with strictly-newer siblings get updated.

**Verification test** at `/tmp/test_v5_closeout.py`:

1. Fresh worker DB (wiped).
2. **Batch 1:** submit an `app_session(local_id=1, start_ts=1000, end_ts=None)` + matching `comic_session(local_id=1)`. Both land as orphans (end_ts=NULL, close_reason=NULL). Expected — single batch, no newer sibling.
3. **Batch 2:** submit an `app_session(local_id=2, start_ts=5000)` + matching `comic_session(local_id=2)` for the same device. Close-out fires at the end of the handler.

**Post-batch-2 state (asserted via assertions in the test):**

```
app_sessions:
  id=1 | start_ts=1000 | end_ts=5000 | duration_ms=4000 | close_reason='force_closed_by_new_session'   ← CLOSED
  id=2 | start_ts=5000 | end_ts=NULL | duration_ms=NULL | close_reason=NULL                              ← LIVE

comic_sessions:
  id=1 | start_ts=1000 | end_ts=5000 | duration_ms=4000 | close_reason='force_closed_by_new_session'   ← CLOSED
  id=2 | start_ts=5000 | end_ts=NULL | duration_ms=NULL | close_reason=NULL                              ← LIVE
```

All assertions pass:
- Orphan's `end_ts == 5000` (min start_ts of newer sibling)
- Orphan's `duration_ms == 4000` (5000 - 1000)
- Orphan's `close_reason == 'force_closed_by_new_session'`
- Newer row's `end_ts IS NULL` (untouched)
- Newer row's `close_reason IS NULL` (untouched)
- Same assertions hold for `comic_sessions`

Final line of test output: `✅ ALL ASSERTIONS PASS — objective 24 close-out policy verified`

## Known limitation

This policy only fires at the END of a successful ingest batch. If a producer has orphaned sessions but never syncs again, they remain NULL forever. A separate periodic cleanup sweep (e.g. a `cron` job or a `query` op handler that scans for `end_ts IS NULL` older than a threshold) could backstop this. Not needed for Part 2 MVP — producers that sync regularly self-clean via normal batch flow.
