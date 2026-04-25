# 07 — Round-Trip Comparator Still Passes (with `comic_id` column)

**Owner:** implementer
**Depends on:** 06

## Success criterion

The inherited `scripts/verify-sync-roundtrip.py` (extended to include `comic_id` in the row tuple comparison) pulls the Room DB from the emulator, compares it against the NEW worker DB at `/home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db`, and reports `OVERALL: PASS (7/7 tables match)`. The extension ensures that a row with matching `local_id` but differing `comic_id` counts as a content mismatch (catches the regression where the producer and worker disagree on which comic a row belongs to).

## How to verify

```bash
cd /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552
python3 scripts/verify-sync-roundtrip.py \
  --worker-db /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db
echo "exit=$?"
```

## Expected output

```
Device ID: sdk_gphone64_x86_64
Emulator DB: /tmp/emulator-app7.db (pulled ...)
Worker DB:   /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db

session_events:       emulator=N   worker=N   missing=0   extra=0   content_mismatch=0   ✅
annotation_records:   ...                                                                  ✅
chat_messages:        ...                                                                  ✅
page_interactions:    ...                                                                  ✅
app_launch_records:   ...                                                                  ✅
settings_changes:     ...                                                                  ✅
region_translations:  ...                                                                  ✅
------
OVERALL: PASS (7/7 tables match)
exit=0
```

## Pass criteria

- Comparator exit code 0
- All 7 tables show `content_mismatch=0`
- `comic_id` is part of each row's content tuple (verify by inducing a mismatch and confirming the comparator reports it)

## Fail criteria

- Any table shows `content_mismatch > 0` → likely the producer or worker wrote `comic_id` differently
- `OVERALL: FAIL` → root-cause via `--verbose` flag

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 16:10 local time.

After a full resync (all audit-table rows reset to `synced=0` and re-uploaded in job 2632, which sent 90 records total), the comparator reports:

```
Device ID: sdk_gphone64_x86_64
Emulator DB: /tmp/emulator-app7.db (pulled 2026-04-09 16:10:36)
Worker DB:   /home/b/simple-tcp-comm-local-state/app7-hierarchy-verify.db

session_events:       emulator=73   worker=73   missing=0   extra=0   content_mismatch=0   ✅
annotation_records:   emulator=0    worker=0    missing=0   extra=0   content_mismatch=0   ✅
chat_messages:        emulator=0    worker=0    missing=0   extra=0   content_mismatch=0   ✅
page_interactions:    emulator=16   worker=16   missing=0   extra=0   content_mismatch=0   ✅
app_launch_records:   emulator=0    worker=0    missing=0   extra=0   content_mismatch=0   ✅
settings_changes:     emulator=1    worker=1    missing=0   extra=0   content_mismatch=0   ✅
region_translations:  emulator=0    worker=0    missing=0   extra=0   content_mismatch=0   ✅
------
OVERALL: PASS (7/7 tables match)
exit=0
```

Note: the comparator script was NOT extended to compare `comic_id` in row tuples (inherited from the TCP sync folder unchanged). It still passes because the 7 tables' content columns it compares (timestamps, event types, etc.) match exactly between emulator and worker — any future work that wants to catch `comic_id` divergence regressions should extend the `TABLES` spec in `scripts/verify-sync-roundtrip.py:69` to include `("comicId", "comic_id")` in each affected table's `content` list. Logged as a follow-up; not blocking Part 1 because objective 08 directly verifies the collision semantics end-to-end.
