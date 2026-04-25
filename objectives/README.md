# Objectives — Explicit DB Hierarchy (comic_id + session tables)

This folder is the success checklist for the `app7-explicit-db-hierarchy_20260409_154552` feature folder. It is forked from `app7-tcp-sync-direct-queue-client_20260409_031219/` and inherits its working TCP sync + auto-sync + comparator + local worker infrastructure. **Everything the TCP sync folder verified is assumed still true here**; this folder's objectives focus only on the *new* work.

## What's being verified

Two stacked outcomes, tracked separately so Part 1 can ship independently:

### Part 1 — URGENT: `comic_id` collision fix (must-ship-before-beta)

Every syncable row that references a chapter/page must also carry a `comic_id`, composite-keyed so that two comics with "Chapter 1" never collide in either the producer's Room DB or the worker's sqlite DB.

### Part 2 — Session hierarchy (nice-to-have, not urgent)

Explicit `app_sessions → comic_sessions → chapter_sessions → page_sessions` aggregate tables, populated by a producer-side tracker and with FK integrity enforced. Covered by a separate set of objectives (20-series) so Part 1 can pass without Part 2.

See `/home/b/p/minimal-android-apps/app7-ideas-for-later/URGENT-explicit-db-hierarchy/` for the original problem statement and the full migration plan this work is executing against.

## Ordering

```
00  ── main goal (composite, covers BOTH parts)
│
00a ── correct app launched (precondition — SHA + foreground check, reused from TCP sync folder)
│
Part 1 — comic_id collision fix
├── 01 (Room entity has comicId field)
│      │
│      └── 02 (Room Migration runs cleanly on existing DB)
│             │
│             └── 03 (event logging call sites populate comicId from settingsStore)
│                    │
│                    └── 04 (UnifiedPayload schema_version=4 + @SerialName comic_id)
│                           │
│                           └── 05 (worker schema has comics table + composite-keyed chapters/pages)
│                                  │
│                                  └── 06 (worker handler accepts schema_v4, populates comic_id on insert)
│                                         │
│                                         └── 07 (round-trip still passes — comparator 7/7 match with new column)
│                                                │
│                                                └── 08 (multi-comic collision test — PRIMARY Part 1 gate)
│
Part 2 — session hierarchy (optional to ship)
├── 20 (Room entities for AppSession/ComicSession/ChapterSession/PageSession)
│      │
│      └── 21 (SessionHierarchyTracker producer-side logic)
│             │
│             └── 22 (worker schema has 4 new session tables with FK)
│                    │
│                    └── 23 (producer emits and syncs session rows)
│                           │
│                           └── 24 (worker close-out policy for dead sessions)
│                                  │
│                                  └── 25 (analytical queries run cheaply on aggregate tables — PRIMARY Part 2 gate)
│
Cross-cutting reference (not a pass/fail objective)
└── 30 (E2E pipeline verification — exact commands to check each stage from Room DB → queue → worker → comparator → collector)
```

## Ownership

This work is sequential enough that one agent (or one person) owns all of it end-to-end. No multi-agent split.

| Part | Objectives | Rough scope |
|---|---|---|
| Precondition | 00a | 5 minutes — already working from TCP sync folder |
| Part 1 (urgent) | 01 through 08 | 1-2 focused days |
| Part 2 (defer OK) | 20 through 25 | 3-5 focused days |

## Part 1 pass criteria (ship-before-beta gate)

Every Part 1 objective (01-08) shows `[x] Verified` with evidence. The PRIMARY gate is **objective 08** — a multi-comic smoke test proving that events with the same `chapter_name` but different `comic_id` land as two distinct rows in every FK-targeted table.

## How to use this folder

Same convention as the TCP sync folder:

1. Each objective file has a `## Current status` checklist — flip `[ ] Not started` → `[ ] In progress` → `[x] Verified` as work progresses.
2. Fill in the `## Evidence` section with the actual command output when verifying.
3. Objective `00-main-goal.md` is a composite checklist mirroring 01-08 and 20-25.
4. `grep -l "\[x\] Verified" *.md` for a quick pass-count.

## Reused infrastructure (inherited from TCP sync folder, no rework)

- Working APK build pipeline (`./gradlew :androidApp:assembleDebug`)
- Auto-sync loop in `MainActivity.onStart` → works unchanged
- `TcpQueueSyncApi` / `QueueTcpClient` — works unchanged (but `UnifiedPayload` format changes to v4)
- `scripts/verify-sync-roundtrip.py` — **needs extension** to compare `comic_id` columns; extension is part of objective 07
- Local worker in tmux session `app7-local-worker` — **needs restart with new schema**; will be handled during objective 06
- DO queue server at `PLACEHOLDER_BACKEND_HOST:9999` — untouched, task-agnostic as ever

## Not inherited (intentionally replaced for this folder)

- `objectives/` — fresh set, focused on hierarchy/collision work
- `FEATURE_SPEC.md` — will be updated to describe this folder's scope
- (Part 2) Room entity set — will gain 4 new session aggregate entities
- (Part 1 only) Worker schema.sql — will add `comics` table and composite keys; lives at `/home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql` (new clone) so the TCP sync worker's DB stays untouched

## Reference

- Parent feature folder: `../app7-tcp-sync-direct-queue-client_20260409_031219/`
- Original problem statement: `../app7-ideas-for-later/URGENT-explicit-db-hierarchy/`
- Part 1 migration plan (what this folder is executing): `../app7-ideas-for-later/URGENT-explicit-db-hierarchy/02-proposed-schema-and-migration.md` sections "Part 1 — URGENT comic_id Fix"
- Part 2 migration plan: same file, section "Part 2 — Full Session Hierarchy"
