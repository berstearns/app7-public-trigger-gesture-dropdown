# Resume State — `app7-explicit-db-hierarchy_20260409_154552`

> **Stopped 2026-04-09 ~16:27 local time.** Part 1 shipped; Part 2 foundations shipped; chapter/page wiring + sync + close-out + analytics deferred.
>
> This document captures exactly what's still running, what's been stopped, and the concrete steps to resume.

---

## What's still running (preserved)

| Resource | Where | State | Why kept |
|---|---|---|---|
| **Android emulator** (`Pixel_API_34`) | pid `1687196`, `emulator-5554` via `adb` | booted, app installed, auth/prefs populated | Has real test data (73 session_events, 16 page_interactions, 4 comics in worker DB), preserved state for hierarchy verification. Kill with `adb emu kill` if you want a clean slate. |
| **Local worker** (app7-hierarchy-verify) | tmux `app7-hierarchy-worker`, pid `2790990` | polling DO queue every 2s | Actively polling — if you trigger auto-sync from the emulator, it picks up and ingests. Registered on DO as `bernardo-pc-app7-hierarchy-verify-app7-hierarchy-verify`. Kill with `tmux kill-session -t app7-hierarchy-worker`. |
| **DO queue server** | `PLACEHOLDER_BACKEND_HOST:9999` | task-agnostic, unchanged | Not controlled locally; always on. |
| **gradle/kotlin daemons** | `pid 377364` + helpers | idle | Will auto-shutdown after 2h idle. Force-stop with `./gradlew --stop` in the feature folder. |

## What was stopped

| Resource | State before | Why stopped |
|---|---|---|
| tmux `app7-build` | idle at shell prompt from TCP sync folder's build | stale, safe to kill |
| tmux `app7-deploy` | idle at shell prompt from TCP sync folder's deploy | stale, safe to kill (commit already pushed) |
| tmux `app7-hier-build` | idle at shell prompt from hierarchy folder's build | build succeeded, APK already installed |
| tmux `app7-hier-deploy` | idle at shell prompt from hierarchy folder's deploy | commit `7173ee8` already pushed to GitHub |
| hung `adb exec-out run-as ... sqlite3` (pid 2582000) | stuck waiting for stdin since earlier heredoc mishap | leftover zombie |
| tmux `app7-local-worker` | polling (old TCP sync verify worker) | killed earlier to prevent polling race with the hierarchy worker |

## Durable state on disk (nothing to resume, just reference)

| Path | What |
|---|---|
| `/home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552/` | Feature folder, Part 1 shipped, Part 2 foundations in `objectives/20-22`. APK at `androidApp/build/outputs/apk/debug/androidApp-debug.apk` matches SHA-checked install. |
| `/home/b/simple-tcp-comm/workers/app7-explicit-db-hierarchy_20260409_154552/worker.py` | Pushed to GitHub at commit `7173ee8`. |
| `/home/b/simple-tcp-comm/dbs/app7-explicit-db-hierarchy_20260409_154552/head_schema/schema.sql` | Pushed at commit `7173ee8`. 11 core tables + 4 session aggregate tables (Part 2 objective 22). |
| `/home/b/simple-tcp-comm/.env.app7-hierarchy` | Local worker env (not committed) — `QUEUE_DBS`, `WORKER_NAME`, etc. |
| `/home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db` | Live worker DB. Currently populated with the Part 1 collision test data. |
| `/home/b/simple-tcp-comm-local-state/app7-tcp-sync-verify.db` | Previous TCP sync folder's worker DB (from earlier verification). Still exists. |
| `/home/b/.claude/plans/iridescent-petting-muffin.md` | Plan file from the original TCP sync folder's plan mode session. |

## Objectives state (verbatim)

From `objectives/00-main-goal.md`:

### Part 1 — collision fix (SHIPPED, all green)

- [x] 00a — Correct app launched on the emulator
- [x] 01 — Room entities have `comicId` field
- [x] 02 — Room Migration runs cleanly on an existing DB
- [x] 03 — Event logging call sites populate `comicId`
- [x] 04 — UnifiedPayload serializes `@SerialName("comic_id")`, schema_version=4
- [x] 05 — Worker schema has `comics` table + composite keys
- [x] 06 — Worker handler accepts schema_v4 and populates `comic_id`
- [x] 07 — Comparator reports `OVERALL: PASS (7/7 tables match)`
- [x] 08 — Multi-comic collision smoke test (PRIMARY GATE)

### Part 2 — session hierarchy (foundations shipped; work remaining)

- [x] 20 — Room entities + DAOs + Migration 2→3 for 4 session tables
- [-] 21 — `SessionHierarchyTracker` class + app/comic wiring + crash recovery verified; **chapter/page hooks not yet wired**
- [x] 22 — Worker schema has the 4 session aggregate tables with FK to `comics`/`chapters`
- [ ] 23 — Producer syncs session rows end-to-end via existing `TcpQueueSyncApi`
- [ ] 24 — Worker close-out policy for orphaned sessions
- [ ] 25 — Analytical queries run as simple joins (PRIMARY PART 2 GATE)

---

## To resume — concrete commands

### 1. Reattach to the live worker (what it's doing right now)

```bash
tmux attach -t app7-hierarchy-worker
# Ctrl-B D to detach without killing
```

### 2. Run the comparator again (sanity check the saved state is still green)

```bash
cd /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552
python3 scripts/verify-sync-roundtrip.py \
  --worker-db /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db
```

Expected: `OVERALL: PASS (7/7 tables match)` — unchanged from when we stopped.

### 3. Inspect the current worker DB state

```bash
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db <<'SQL'
.mode column
.headers on
SELECT 'comics'      AS t, COUNT(*) AS n FROM comics UNION ALL
SELECT 'chapters',        COUNT(*) FROM chapters UNION ALL
SELECT 'session_events',  COUNT(*) FROM session_events UNION ALL
SELECT 'page_interactions', COUNT(*) FROM page_interactions UNION ALL
SELECT 'app_sessions',    COUNT(*) FROM app_sessions UNION ALL
SELECT 'comic_sessions',  COUNT(*) FROM comic_sessions UNION ALL
SELECT 'chapter_sessions', COUNT(*) FROM chapter_sessions UNION ALL
SELECT 'page_sessions',   COUNT(*) FROM page_sessions UNION ALL
SELECT 'ingest_batches',  COUNT(*) FROM ingest_batches;
SQL
```

The 4 Part-2 session tables will be empty (producer hasn't synced them — that's objective 23).

### 4. Inspect the emulator's Room DB (Part 2 producer state)

```bash
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db <<'SQL'
SELECT 'app_sessions='||COUNT(*) FROM app_sessions;
SELECT 'comic_sessions='||COUNT(*) FROM comic_sessions;
SELECT * FROM app_sessions ORDER BY id;
SELECT * FROM comic_sessions ORDER BY id;
SQL
```

Should show at least 2 app_sessions (id=1 closed by crash-recovery, id=2 still open) and 2 comic_sessions (one orphaned, one live). These are waiting for objective 23 to sync them.

### 5. If you want to restart the emulator fresh

```bash
# Kill it
adb emu kill

# Reboot later
nohup /home/b/Android/Sdk/emulator/emulator -avd Pixel_API_34 \
  -no-snapshot-save -no-boot-anim > /tmp/emulator-boot.log 2>&1 &
disown
```

### 6. If you want to restart the worker fresh

```bash
tmux kill-session -t app7-hierarchy-worker
tmux new-session -d -s app7-hierarchy-worker -c /home/b/simple-tcp-comm
tmux send-keys -t app7-hierarchy-worker \
  "set -a; source .env.app7-hierarchy; set +a; \
   python3 workers/app7-explicit-db-hierarchy_20260409_154552/worker.py" C-m
```

### 7. If you want a completely clean worker DB

```bash
tmux kill-session -t app7-hierarchy-worker
rm -f /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db*
# Then restart per step 6
# Worker auto-creates the schema from the pinned head_schema/schema.sql
```

---

## Next work items (in dependency order)

### ⏳ Objective 21 finish — chapter/page lifecycle wiring

**Files to edit:**
- `shared/src/commonMain/kotlin/pl/czak/learnlauncher/viewmodel/LauncherViewModel.kt` — find the chapter-change detection code, add a call to `sessionHierarchy.onChapterEntered(comicId, chapterName)`
- `shared/src/commonMain/kotlin/pl/czak/learnlauncher/ui/MainScreen.kt` around line 137-168 — inside `detectTapGestures`, add `sessionHierarchy.onPageInteraction()` to the existing `onTap`/`onDoubleTap`/`onLongPress` blocks
- Wherever the image-viewer page changes (probably `ImageSequenceNavigator` or a Compose `HorizontalPager` state change) — add `sessionHierarchy.onPageEntered(comicId, pageId)`

**Tricky part:** `sessionHierarchy` is currently a `MainActivity` field, and `LauncherViewModel`/`MainScreen` don't have a reference to it. Either: (a) inject it into `LauncherViewModel` and expose it, (b) make it a singleton / DI-backed object, (c) plumb it through the `App(...)` composable as another callback parameter. Option (c) matches the existing callback-driven pattern but adds verbosity; option (a) is cleaner long-term.

### ⏳ Objective 23 — UnifiedPayload v5 end-to-end session sync

See `objectives/23-sync-session-rows-end-to-end.md` for the full spec. Summary:

1. Add 4 `@Serializable` record classes (`AppSessionRecord`, `ComicSessionRecord`, `ChapterSessionRecord`, `PageSessionRecord`) to `UnifiedPayload.kt`
2. Add 4 new lists to `UnifiedTables` with `@SerialName` mappings
3. Bump `schemaVersion = 5` in `UnifiedPayload.kt`
4. Extend `UnifiedPayloadBuilder` with 4 more `.map { ... }` blocks pulling from the new DAOs
5. Extend `SyncService.sync()` to call `markSynced()` on the 4 new DAOs
6. Extend worker handler to accept `schema_version in (3, 4, 5)` and add 4 new `_ingest_<session_type>` functions wired into `_ingest_unified_payload`
7. Extend `scripts/verify-sync-roundtrip.py` TABLES spec with 4 new entries
8. End-to-end test: force-stop/relaunch → trigger sync → verify session rows land in worker DB → run comparator → should report 11/11 tables match

### ⏳ Objective 24 — Worker close-out policy

After 23 lands, add logic in `_ingest_unified_payload` that scans the 4 session tables for `end_ts IS NULL` rows that have a newer sibling and force-closes them. Schema already has `close_reason TEXT` columns ready. See `objectives/24-worker-close-out-dead-sessions.md`.

### ⏳ Objective 25 — Run analytical benchmark queries

After 23 + 24 land, run the 4 queries from `objectives/25-analytical-queries-cheap.md` against the worker DB, confirm each executes in <10ms, and capture the results as evidence.

---

## Git state (nothing uncommitted to worry about in simple-tcp-comm)

**Pushed to `origin/main` in `/home/b/simple-tcp-comm`:**
- `6bdff4e Add timestamped app7 worker+schema clone for sync verification` (TCP sync folder)
- `7173ee8 Add timestamped app7 hierarchy worker+schema clone for collision fix` (this folder)

**Pre-existing WIP in `simple-tcp-comm` — untouched, still dirty:**
- `M dbs/app7/head_schema/schema.sql`
- `?? workers/app7/`
- `?? docs/constraints.md`

**Monorepo `minimal-android-apps` git state:** has many uncommitted feature folders (this one and ~60 others are all `??` untracked). Nothing in this folder has been committed to the monorepo — it exists only on disk. Entire feature folder is ready to commit if desired.

---

## Known issues still open (from work done this turn)

1. **DO pull soft-blocker** — `deploy-queue update` hangs on ssh password prompt. You'll need to manually pull on the droplet to bring the new worker clone to DO. Run `cd ~/p/all-my-tiny-projects/do-automation/job-queue && ./deploy-queue update` from an interactive terminal.

2. **Part 2 orphan comic_session** — objective 21's crash-recovery path closes the `app_session` parent but leaves child `comic_sessions` / `chapter_sessions` / `page_sessions` open. Worker close-out policy (objective 24) will handle this when it lands, but the tracker could also do it defensively.

3. **Comparator doesn't check `comic_id`** — extension noted in objective 07 evidence. Low effort; defer until Part 2.

4. **Objective 21 coroutine race on force-kill** — `onAppStop` runs in `lifecycleScope.launch` from `onDestroy`, which doesn't await. On a hard process kill, the close-out SQL doesn't land. Recovered next run via `onAppStart` but a rare race window exists. Acceptable for now.

---

## Quick rehydration checklist

If you come back cold, run these in order:

```bash
# 1. Sanity: processes/tmux
tmux list-sessions | grep app7
adb devices

# 2. Sanity: worker polling
python3 /home/b/simple-tcp-comm/client.py workers | grep hierarchy-verify

# 3. Sanity: round-trip still green
cd /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552
python3 scripts/verify-sync-roundtrip.py --worker-db /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db

# 4. Sanity: collision fix still holding
sqlite3 /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db \
  "SELECT comic_id, chapter_name FROM chapters ORDER BY comic_id;"
# Expect: one_piece|Chapter 1 AND naruto|Chapter 1 as distinct rows

# 5. Sanity: main-goal checklist
grep "\[x\]" objectives/00-main-goal.md
```

All 5 of those should look identical to what was reported at the end of the previous session.
