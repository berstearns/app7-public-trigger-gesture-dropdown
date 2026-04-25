# 01 — Room Entities Carry `comicId`

**Owner:** implementer
**Depends on:** 00a

## Success criterion

Every Room `@Entity` class that currently references a chapter or page has a new non-null `comicId: String` field with a safe default. The four affected entities are:

- `SessionEventEntity`
- `PageInteractionEntity`
- `AnnotationRecordEntity` (via `imageId`)
- `AppLaunchRecordEntity` (via `currentChapter` / `currentPageId`)

`SettingsChangeEntity`, `ChatMessageEntity`, and `RegionTranslationEntity` do NOT get a `comicId` — they are either global settings, global chat, or already keyed by `imageId` + `bubbleIndex` (which carries comic provenance transitively once pages are composite-keyed).

## How to verify

```bash
# 1. Confirm all four entities have a comicId field
grep -n "comicId" /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552/shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/db/entity/Entities.kt

# 2. Confirm the code compiles
cd /home/b/p/minimal-android-apps/app7-explicit-db-hierarchy_20260409_154552
./gradlew :shared:compileAndroidMain 2>&1 | tail -20
```

## Expected output

Four or more lines from the first grep showing `val comicId: String` inside the four entity classes. The compile ends with `BUILD SUCCESSFUL`.

## Pass criteria

- Four entities each have `val comicId: String` (non-nullable, with a default or required in the constructor)
- `:shared:compileAndroidMain` → `BUILD SUCCESSFUL`
- No warnings about missing `@ColumnInfo` — Room auto-generates the column name from the field

## Fail criteria

- Compile fails because existing code creates entity instances without the new required field (fix by adding default `= "_no_comic_"` to the field)
- Any entity that should have `comicId` doesn't (only SettingsChange/ChatMessage/RegionTranslation are exempt)

## Current status

- [ ] Not started
- [ ] In progress
- [x] Verified

## Evidence

Verified 2026-04-09 15:50 local time.

All 4 affected entities in `shared/src/androidMain/kotlin/pl/czak/learnlauncher/data/db/entity/Entities.kt` have `val comicId: String = NO_COMIC_SENTINEL`:

- `SessionEventEntity` — line ~14
- `AnnotationRecordEntity` — line ~36
- `PageInteractionEntity` — line ~55
- `AppLaunchRecordEntity` — line ~68

Plus the `NO_COMIC_SENTINEL = "_no_comic_"` constant at the top of the file.

`SettingsChangeEntity`, `ChatMessageEntity`, `RegionTranslationEntity` intentionally do NOT have `comicId` — they're either global or already keyed by `imageId`.

Compile: `./gradlew :shared:compileAndroidMain :androidApp:compileDebugKotlin` → `BUILD SUCCESSFUL in 20s`. The 3 pre-existing warnings in MainScreen/AnnotationOverlay/LauncherViewModel are unchanged and unrelated.
