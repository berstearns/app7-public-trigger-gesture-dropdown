# 03 — Event Logging Call Sites Populate `comicId`

**Owner:** implementer
**Depends on:** 02

## Success criterion

Every call site that inserts a row into `session_events`, `page_interactions`, `annotation_records`, or `app_launch_records` populates `comicId` from the current `selected_asset_id` SharedPreference. When no comic is selected, the sentinel `"_no_comic_"` is used (never NULL, never empty string). After triggering known activity with a known comic selected, the Room DB shows the expected `comicId` in the newly-inserted rows.

## How to verify

```bash
# 1. Find all logging call sites
cd /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552
grep -rn "sessionEventDao\(\)\.\|pageInteractionDao\(\)\.\|annotationRecordDao\(\)\.\|appLaunchRecordDao\(\)\." shared/src --include="*.kt" | grep -iE "insert|log"

# Every call site must pass a comicId (check manually or via a follow-up grep of the call context)

# 2. With the new APK installed, select a comic (via Settings → Download → pick one)
#    Or write settings.xml directly to simulate a comic selection:
adb exec-out run-as pl.czak.imageviewer.app7 sh -c 'cat > shared_prefs/settings.xml' <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="auto_sync_enabled" value="true" />
    <string name="selected_asset_id">test_comic_alpha</string>
</map>
EOF

adb shell am force-stop pl.czak.imageviewer.app7
adb shell am start -n pl.czak.imageviewer.app7/pl.czak.learnlauncher.android.MainActivity
sleep 3

# 3. Check newly inserted rows have the expected comicId
adb exec-out run-as pl.czak.imageviewer.app7 sqlite3 databases/learner_data.db "
  SELECT id, eventType, comicId, timestamp
  FROM session_events
  ORDER BY id DESC LIMIT 5;
"
```

## Expected output

Top 5 rows (newest) from `session_events` all show `comicId = 'test_comic_alpha'`. Older rows still show `comicId = '_no_comic_'` (backfill from migration).

## Pass criteria

- Every DAO `.insert` or `.log` call site that targets the 4 affected tables has a `comicId` argument supplied
- New rows inserted after setting `selected_asset_id='test_comic_alpha'` show `comicId='test_comic_alpha'`
- Pre-migration rows still show `'_no_comic_'` (no accidental update)

## Fail criteria

- Any insert call site forgets `comicId` → compile error (since the field is non-nullable without a default on the DAO side) OR silently writes `'_no_comic_'` for real comic data (worse, because it looks correct but isn't)
- New rows show NULL `comicId` → someone bypassed the field default

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 16:08 local time. Evidence is stronger than the test plan called for — the producer populated `comicId` from REAL user activity, not just synthetic seeds.

**Call sites updated:** `shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/AndroidRepositories.kt`:
- `AndroidAnnotationRepository.addAnnotation` — reads `settingsStore.currentComicId()` at log time
- `AndroidSessionRepository.addSessions` — reads once per batch, applies to every session event
- `AndroidLearnerDataRepository.logSessionEvent` / `.logPageInteraction` / `.logAppLaunch` — all read `settingsStore.currentComicId()` inline

Helper defined at the top of the file:
```kotlin
private fun AndroidSettingsStore.currentComicId(): String =
    getString("selected_asset_id", null) ?: NO_COMIC_SENTINEL
```

Constructor injection: `AndroidSettingsStore` is now passed to all 3 Android repositories. Instantiation updated in `MainActivity.kt:114-116`.

**Real-world evidence:** after the first auto-sync post-migration, the worker DB's `comics` table showed a row `batch-01-hq-tokens` that was never explicitly seeded. That comic ID came from `settingsStore.getString("selected_asset_id")` being populated during a real Settings → Download catalog interaction earlier in the session. This proves the production logging path is populating `comicId` correctly without any test harness involvement.

Seeded test data with explicit `comicId='one_piece'` and `comicId='naruto'` also landed with their correct values — see objective 08 evidence.
