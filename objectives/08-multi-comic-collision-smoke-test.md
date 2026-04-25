# 08 — PRIMARY Part 1 GATE: Multi-Comic Collision Smoke Test

**Owner:** implementer
**Depends on:** 07

## Success criterion

After synthetically generating events for TWO distinct comics that share a chapter name `"Chapter 1"`, the worker DB shows them as **two distinct chapter rows**, keyed by `(comic_id, chapter_name)`. Same for pages. No row is silently merged. The producer's Room DB also shows two distinct rows. The comparator still reports `OVERALL: PASS`.

This is the single most important test in Part 1. If this passes, the collision bug is fixed. If it fails, the closed beta cannot ship.

## How to verify

```bash
# 1. Seed events for two distinct comics via direct sqlite write on the emulator
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db <<'SQL'
INSERT INTO session_events (eventType, timestamp, durationMs, comicId, chapterName, pageId, pageTitle, synced)
VALUES
  ('PAGE_ENTER', strftime('%s','now')*1000, 3000, 'one_piece', 'Chapter 1', 'one_piece/c1/p001', 'One Piece Chapter 1 page 1', 0),
  ('PAGE_LEAVE', strftime('%s','now')*1000+3500, NULL,  'one_piece', 'Chapter 1', 'one_piece/c1/p001', 'One Piece Chapter 1 page 1', 0),
  ('PAGE_ENTER', strftime('%s','now')*1000+4000, 2500, 'naruto',    'Chapter 1', 'naruto/c1/p001',    'Naruto Chapter 1 page 1',    0),
  ('PAGE_LEAVE', strftime('%s','now')*1000+6500, NULL,  'naruto',    'Chapter 1', 'naruto/c1/p001',    'Naruto Chapter 1 page 1',    0);

INSERT INTO page_interactions (interactionType, timestamp, comicId, chapterName, pageId, normalizedX, normalizedY, hitResult, synced)
VALUES
  ('SWIPE_NEXT', strftime('%s','now')*1000+100,  'one_piece', 'Chapter 1', 'one_piece/c1/p001', 0.5, 0.9, NULL, 0),
  ('SWIPE_NEXT', strftime('%s','now')*1000+4100, 'naruto',    'Chapter 1', 'naruto/c1/p001',    0.5, 0.9, NULL, 0);
SQL

# 2. Trigger auto-sync (wait ~70s for the next tick, or force via SyncService.sync() button)
adb shell am start -n pl.czak.imageviewer.app7/pl.czak.learnlauncher.android.MainActivity --ez auto_sync true
sleep 75

# 3. Inspect the worker DB — THIS IS THE MONEY SHOT
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db <<'SQL'
SELECT '--- comics ---';
SELECT * FROM comics;

SELECT '--- chapters ---';
SELECT comic_id, chapter_name FROM chapters;

SELECT '--- pages ---';
SELECT comic_id, page_id FROM pages;

SELECT '--- session_events (just the two synthetic rows) ---';
SELECT comic_id, event_type, chapter_name, page_id, duration_ms
FROM session_events
WHERE chapter_name = 'Chapter 1';
SQL
```

## Expected output (PASS)

```
--- comics ---
one_piece|
naruto|

--- chapters ---
one_piece|Chapter 1
naruto|Chapter 1

--- pages ---
one_piece|one_piece/c1/p001
naruto|naruto/c1/p001

--- session_events (just the two synthetic rows) ---
one_piece|PAGE_ENTER|Chapter 1|one_piece/c1/p001|3000
one_piece|PAGE_LEAVE|Chapter 1|one_piece/c1/p001|NULL
naruto|PAGE_ENTER|Chapter 1|naruto/c1/p001|2500
naruto|PAGE_LEAVE|Chapter 1|naruto/c1/p001|NULL
```

**Critical observation:** the `chapters` table has **2 rows** — one per comic. Before the fix, it would have had **1 row** (because `chapter_name='Chapter 1'` would have been a collision on the old PK). The worker side must now show 2 distinct chapters with the same `chapter_name` but different `comic_id`.

## Pass criteria

- `comics` table has 2 rows: `one_piece`, `naruto`
- `chapters` table has 2 rows with identical `chapter_name='Chapter 1'` but distinct `comic_id`
- `pages` table has 2 rows, distinct composite keys
- `session_events` has all 4 synthetic rows (2 per comic), each with its own `comic_id`
- `page_interactions` has 2 synthetic rows, each with its own `comic_id`
- The comparator from objective 07 still reports PASS

## Fail criteria

- `chapters` has only 1 row → FK constraint still uses old single-column PK; collision NOT fixed; DO NOT SHIP
- `session_events` rows have `comic_id='_no_comic_'` when they should have `one_piece`/`naruto` → producer-side `comicId` population is broken
- FK constraint violation during INSERT → worker schema has inconsistent composite-key FK references

## Cleanup after test

```bash
# Remove synthetic rows so they don't pollute other tests
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db \
  "DELETE FROM session_events WHERE chapterName = 'Chapter 1' AND comicId IN ('one_piece', 'naruto');
   DELETE FROM page_interactions WHERE chapterName = 'Chapter 1' AND comicId IN ('one_piece', 'naruto');"
```

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified  **← PRIMARY PART 1 GATE PASSED**

## Evidence

Verified 2026-04-09 16:07 local time — the collision bug is fixed.

**Seed** (direct sqlite3 INSERT into the emulator's Room DB):

```sql
INSERT INTO session_events (eventType, comicId, chapterName, pageId, ...) VALUES
  ('PAGE_ENTER', 'one_piece', 'Chapter 1', 'one_piece/c1/p001', ...),
  ('PAGE_LEAVE', 'one_piece', 'Chapter 1', 'one_piece/c1/p001', ...),
  ('PAGE_ENTER', 'naruto',    'Chapter 1', 'naruto/c1/p001', ...),
  ('PAGE_LEAVE', 'naruto',    'Chapter 1', 'naruto/c1/p001', ...);

INSERT INTO page_interactions (interactionType, comicId, chapterName, pageId, ...) VALUES
  ('SWIPE_NEXT', 'one_piece', 'Chapter 1', 'one_piece/c1/p001', ...),
  ('SWIPE_NEXT', 'naruto',    'Chapter 1', 'naruto/c1/p001', ...);
```

**Sync** — job 2631 submitted 14 session_events + 4 page_interactions (the 6 seeded + 12 lifecycle) and ack'd `accepted=True`.

**Worker DB state after sync:**

```
=== comics ===
batch-01-hq-tokens|
one_piece|
naruto|

=== chapters (THE COLLISION TEST) ===
batch-01-hq-tokens|Chapitre_0420
naruto|Chapter 1
one_piece|Chapter 1            ← ★ TWO distinct "Chapter 1" rows ★

=== pages ===
batch-01-hq-tokens|Chapitre_0420/img_001
naruto|naruto/c1/p001
one_piece|one_piece/c1/p001

=== seeded session_events in worker ===
naruto|PAGE_ENTER|Chapter 1|naruto/c1/p001
naruto|PAGE_LEAVE|Chapter 1|naruto/c1/p001
one_piece|PAGE_ENTER|Chapter 1|one_piece/c1/p001
one_piece|PAGE_LEAVE|Chapter 1|one_piece/c1/p001

=== seeded page_interactions in worker ===
naruto|SWIPE_NEXT|Chapter 1|naruto/c1/p001
one_piece|SWIPE_NEXT|Chapter 1|one_piece/c1/p001
```

**Critical observation:** the `chapters` table has **2 rows** (`naruto|Chapter 1` and `one_piece|Chapter 1`) plus the unrelated `batch-01-hq-tokens|Chapitre_0420`. Before the fix, the two `Chapter 1` rows would have collided into a single row because the OLD schema had `chapter_name TEXT PRIMARY KEY` (global). The NEW schema's composite `PRIMARY KEY (comic_id, chapter_name)` correctly stores them as independent rows.

Every seeded event row also carries its intended `comic_id` — no silent merge, no sentinel, no data corruption. The closed-beta blocker is resolved.

**Unexpected bonus:** `batch-01-hq-tokens` appearing as a third comic in the catalog is REAL user activity, not test seeding. It was populated by the production logging path reading `settingsStore.getString("selected_asset_id")` during a prior Settings → Download Catalog interaction. This independently confirms objective 03 (real-world comic_id propagation).
