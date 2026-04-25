# 00a — Correct App Is Launched on the Emulator (Precondition)

**Owner:** implementer
**Depends on:** — (precondition to everything else)

## Why this exists (reused from TCP sync folder)

The monorepo at `/home/b/p/minimal-android-apps/` holds multiple feature-folder variants that share the `pl.czak.imageviewer.app7` package. When the emulator boots, the app visible on screen may have been built from a completely different feature folder. This objective catches that class of mistake before anything else runs — with SHA comparison and foreground-activity dump, not hope.

## Success criterion

After `adb install -r` + explicit `am start -n pl.czak.imageviewer.app7/pl.czak.learnlauncher.android.MainActivity`, all three of the following are true:

1. **Foreground resumed activity is ours**: `dumpsys activity activities` top resumed activity is `pl.czak.imageviewer.app7/pl.czak.learnlauncher.android.MainActivity`.
2. **Installed APK SHA matches freshly-built**: `sha256sum` of the APK currently installed on device equals `sha256sum` of the APK at `androidApp/build/outputs/apk/debug/androidApp-debug.apk` in THIS feature folder (`app7-explicit-db-hierarchy_20260409_154552`).
3. **Feature-folder provenance marker present**: logcat contains a startup line from `MainActivity.onCreate` reading `variant=app7-explicit-db-hierarchy_20260409_154552`. (The `Log.i("FeatureFolder", ...)` line inherited from the TCP sync folder will be updated to the new variant name as part of objective 01.)

## How to verify

```bash
# 1. Foreground resumed activity
adb shell dumpsys activity activities 2>/dev/null | grep -E 'topResumedActivity|ResumedActivity' | head -5

# 2. APK SHA consistency
INSTALLED=$(adb shell pm path pl.czak.imageviewer.app7 | head -1 | sed 's/^package://' | tr -d '\r')
adb pull "$INSTALLED" /tmp/installed-app7.apk 2>&1
sha256sum /tmp/installed-app7.apk
sha256sum /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552/androidApp/build/outputs/apk/debug/androidApp-debug.apk

# 3. Feature-folder provenance in logcat
adb logcat -v time -d | grep -E 'FeatureFolder.*explicit-db-hierarchy' | head -3
```

## Pass criteria

- Foreground activity match
- Both sha256 values equal
- Logcat contains `variant=app7-explicit-db-hierarchy_20260409_154552`

## Fail criteria (any of these)

- `dumpsys` shows another package (e.g. `pl.czak.freedomclone.app11`) → retry `force-stop` + `am start` once, then escalate
- SHA mismatch → reinstall
- Logcat says `variant=app7-tcp-sync-direct-queue-client_20260409_031219` → wrong feature folder was built and installed; rebuild this one

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 16:00 local time.

Logcat (provenance line from MainActivity.onCreate):
```
04-09 16:00:41.687 I/FeatureFolder(11188): BUILD_QUEUE_HOST=PLACEHOLDER_BACKEND_HOST variant=app7-explicit-db-hierarchy_20260409_154552
```

Package is foreground-resumed (confirmed via `dumpsys activity activities` at later steps). Fresh APK was installed from this feature folder at 16:00; the logcat variant string confirms it was not a stale install from the previous `app7-tcp-sync-direct-queue-client_20260409_031219` folder.
