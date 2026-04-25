# 00 — Main Goal: Explicit DB Hierarchy Ships

**Owner:** Master checklist (no single agent)
**Depends on:** 00a and all of 01-08 for Part 1. 20-25 optional for Part 2.

## Success criterion

The app stores and syncs each event with a `comic_id` that disambiguates chapters and pages across comics, and the worker's local sqlite stores it such that two comics with a `"Chapter 1"` NEVER collide. This is verifiable by a smoke test that seeds events for two distinct comics and shows they land as two distinct chapter rows downstream.

Optionally (Part 2), the schema also captures explicit `app_session → comic_session → chapter_session → page_session` aggregate rows populated by the producer at lifecycle transitions, so analytical queries run as simple joins instead of window-function scans over a flat event log.

## How to verify

This is a composite objective — it is "Verified" only when **all Part 1** sub-objectives below are checked off. Part 2 is a bonus and is tracked separately.

### Part 1 — collision fix (required)

- [x] 00a — Correct app launched on the emulator (SHA + foreground, reused pattern)
- [x] 01 — Room entities have `comicId` field
- [x] 02 — Room Migration runs cleanly on an existing DB
- [x] 03 — Event logging call sites populate `comicId` from `settingsStore.getString("selected_asset_id")`
- [x] 04 — UnifiedPayload serializes `@SerialName("comic_id")`, schema_version bumped to 4
- [x] 05 — Worker schema has `comics` table + composite-keyed `chapters`/`pages`
- [x] 06 — Worker handler accepts schema_v4 and populates `comic_id` on every INSERT
- [x] 07 — Round-trip comparator still reports `OVERALL: PASS (7/7 tables match)` with the new column
- [x] 08 — Multi-comic collision smoke test — two chapters with same name but different `comic_id` land as two distinct worker rows (PRIMARY Part 1 GATE)

### Part 2 — session hierarchy (5/6 verified; chapter/page UI hooks deferred)

- [x] 20 — Room entities + DAOs + Migration 2→3 for the 4 session aggregate tables
- [-] 21 — `SessionHierarchyTracker` class + `onAppStart`/`onAppStop`/`onComicSelected` wired and crash-recovery verified; chapter/page lifecycle hooks not yet integrated with the image-viewer navigation
- [x] 22 — Worker schema has the 4 session aggregate tables with FK to `comics`/`chapters`
- [x] 23 — Producer Kotlin v5 DTOs + builder + sync + worker handler; **handler-level verified** via direct Python call (full TCP E2E via DO deferred — DO droplet unreachable)
- [x] 24 — Worker close-out policy — `_force_close_orphans(conn, device_id)` runs per batch; verified with a 2-batch orphan-and-close test
- [x] 25 — Analytical queries run as simple joins — all 4 benchmark queries run in **6ms total** (well under 10ms budget), none touch `session_events` (PRIMARY PART 2 GATE PASSED)

## Expected output

For Part 1 to be green: objective 08's smoke test prints the following or equivalent from the worker DB:

```
chapters:
  comic_id='one_piece'       chapter_name='Chapter 1'
  comic_id='naruto'          chapter_name='Chapter 1'

(2 rows — confirming no collision)
```

And the comparator at `scripts/verify-sync-roundtrip.py` still exits 0 with `OVERALL: PASS (7/7 tables match)`.

For Part 2 to be green: a sample analytical query runs and returns a meaningful aggregate without touching `session_events` at all:

```sql
SELECT c.comic_id, c.display_name, COUNT(DISTINCT cs.id) AS reading_sessions,
       SUM(cs.duration_ms) / 60000 AS total_minutes_read,
       SUM(cs.pages_read) AS total_pages
FROM comic_sessions cs JOIN comics c USING (comic_id)
WHERE cs.device_id = ? AND cs.start_ts > ?
GROUP BY c.comic_id;
```

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified  **(Part 1)**

## Evidence

**Part 1 verified 2026-04-09 16:10 local time.** All 9 sub-objectives (00a + 01-08) show `[x] Verified` with evidence.

**The collision bug is fixed.** The key artifact is objective 08's `chapters` table dump, showing two distinct rows for `chapter_name='Chapter 1'` disambiguated by `comic_id`:

```
naruto     | Chapter 1
one_piece  | Chapter 1
```

Plus a third real comic `batch-01-hq-tokens | Chapitre_0420` that was populated by the production logging path (not by test seeding) — proving the producer-side `settingsStore.getString("selected_asset_id") → comicId` path works end-to-end without any test harness involvement.

**Full round-trip still green.** Comparator at `scripts/verify-sync-roundtrip.py` reports `OVERALL: PASS (7/7 tables match)` against the new worker DB `/home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db`.

**Infrastructure.** Worker running in tmux session `app7-hierarchy-worker`. DO queue server unchanged (task-agnostic). New worker clone + schema committed to `/home/b/simple-tcp-comm/workers/app7-explicit-db-hierarchy_20260409_154552/` and `/home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/` respectively — these are NEW local files, not yet committed to git (would require a fresh `app7-deploy` run analogous to the TCP sync folder's deploy step).

**Part 2 not started.** All 20-25 objectives still `[ ] Not started`. Part 2 is explicit session hierarchy (app_sessions → comic_sessions → chapter_sessions → page_sessions) and can ship independently after closed beta ships.
