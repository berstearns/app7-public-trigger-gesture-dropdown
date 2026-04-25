# 04 — UnifiedPayload Serializes `comic_id`, schema_version Bumped to 4

**Owner:** implementer
**Depends on:** 03

## Success criterion

The KMP `UnifiedPayload` DTO (`shared/src/commonMain/kotlin/pl/czak/learnlauncher/data/model/UnifiedPayload.kt`) has:

1. `schemaVersion = 4` as the top-level constant
2. `@SerialName("comic_id") val comicId: String` added to the 4 affected record classes:
   - `SessionEventRecord`
   - `PageInteractionRecord`
   - `AnnotationRecord` (may use it transitively via imageId — check if needed)
   - `AppLaunchRecord`
3. `UnifiedPayloadBuilder.buildUnifiedPayload()` maps the Room entity's `comicId` field into the new DTO field

When `TcpQueueSyncApi.upload()` serializes a payload, the `comic_id` field appears in the JSON.

## How to verify

```bash
# 1. Grep the DTO for the new field
grep -n "comic_id\|comicId\|schemaVersion\|schema_version" \
  /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552/shared/src/commonMain/kotlin/pl/czak/learnlauncher/data/model/UnifiedPayload.kt

# 2. Confirm UnifiedPayloadBuilder maps it
grep -n "comicId\|comic_id" \
  /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552/shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/export/UnifiedPayloadBuilder.kt

# 3. Force a sync and capture the actual JSON submitted — the server stores the full payload,
# so we can fetch it back via client.py status <N>
adb shell am start -n pl.czak.imageviewer.app7/pl.czak.learnlauncher.android.MainActivity --ez auto_sync true
# Wait ~70s for an auto-sync tick
sleep 75
# Grab the most recent submit id from logcat
N=$(adb logcat -v time -d | grep 'submit ok id=' | tail -1 | grep -oE 'id=[0-9]+' | cut -d= -f2)
python3 /home/b/simple-tcp-comm/client.py status "$N" \
  | python3 -c "import sys,ast; d=ast.literal_eval(sys.stdin.read());
print('schema_version=',d['payload']['unified_payload']['schema_version']);
print('first session_events row:',d['payload']['unified_payload']['tables']['session_events'][0] if d['payload']['unified_payload']['tables']['session_events'] else 'EMPTY')"
```

## Expected output

```
schema_version= 4
first session_events row: {'local_id': 1, 'event_type': 'APP_START', 'timestamp': ..., 'comic_id': 'test_comic_alpha', ...}
```

## Pass criteria

- `schemaVersion = 4` in the DTO
- Every newly-sent row in the captured payload has a `comic_id` field
- Pre-migration rows (with `comicId='_no_comic_'`) show `comic_id: '_no_comic_'` in the payload

## Fail criteria

- `schemaVersion` is still 3 → bump missed
- Any sent row lacks `comic_id` → `@SerialName` wasn't added to one of the record classes, or `UnifiedPayloadBuilder` forgot the mapping
- Field name is `comicId` (camelCase) instead of `comic_id` (snake_case) → `@SerialName("comic_id")` annotation is missing

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 16:07 local time.

**DTO changes** in `shared/src/commonMain/kotlin/pl/czak/learnlauncher/data/model/UnifiedPayload.kt`:
- `UnifiedPayload.schemaVersion` default changed from `3` to `4`
- `@SerialName("comic_id") val comicId: String = "_no_comic_"` added to: `SessionEventRecord`, `AnnotationRecord`, `PageInteractionRecord`, `AppLaunchRecord`
- Top-of-file docstring updated to note v4 semantics

**UnifiedPayloadBuilder** updated at `shared/src/androidMain/.../data/export/UnifiedPayloadBuilder.kt` — all 4 record-mapping lambdas now pass `comicId = <entity>.comicId` to the DTO constructor.

**Server-confirmed schema_version and comic_id field** — job 2631's payload stored on the DO server:

```
status: done
accepted: True
batch_id: 2
counts: {'session_events': 14, 'annotation_records': 0, ..., 'page_interactions': 4, ...}
```

And `client.py status 2631` returned a payload whose `unified_payload.schema_version = 4` with every row in `session_events` and `page_interactions` carrying a `comic_id` field. The worker handler accepted without error.
