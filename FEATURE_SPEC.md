# App7 Manga Reader — Complete Feature Specification

> **Feature folder:** `app7-annotations-bar-position-strategy_20260412_202111`
> **Forked from:** `app7-explicit-db-hierarchy-bug-fixes_20260412_191408`
> **New in this folder:** Annotation bar position strategy setting (bottom / closest / spread)
> **Also includes:** Token regions bug fix from the bug-fixes folder

---

## Screen Routing

| Screen | Entry | Exit |
|--------|-------|------|
| Login | App start (if not logged in) | Successful login -> Main |
| Main | Default after login | Long-press empty area -> Settings |
| Settings | Long-press on empty area in Main | "< Back" -> Main |
| Chat | "Open Chatbot" in Settings | "< Back" -> Main |
| Annotation Log | "View Annotation Log" in Settings | "< Back" -> Main |

---

## 1. Page Reading View (MainScreen)

### Layout: Column (top to bottom)

```
+------------------------------------------+
| [Page title]              [Chapter ▼]    |  <- Header row (FF1E1E1E)
|  ⏱ 45:23  📖 12:05  📄 0:32             |  <- Session timers (FF1A1A1A)
|                                          |
|                                          |
|          [Manga page image]              |  <- Image viewer (Black, weight=1f)
|     (yellow bubble overlays)             |
|     (annotation dots)                    |
|                                          |
|                                          |
| [< Ch]    ▼ swipe ▲    [Apps]  [Ch >]   |  <- Footer row (FF1E1E1E)
+------------------------------------------+
```

### Header Row

| Element | Details |
|---------|---------|
| Page title | `currentImage.title` after " - ", White, 14sp, weight=1f |
| Chapter dropdown | Current chapter name + " ▼", White, 14sp. Tap opens dropdown with all chapters. Selected chapter highlighted green (FF4CAF50) |

### Session Timers Row

Three timers displayed in a row (FF1A1A1A background, 8dp horizontal / 2dp vertical padding):

| Timer | Icon | Format | Color |
|-------|------|--------|-------|
| App elapsed | ⏱ | M:SS | FF888888 |
| Chapter elapsed | 📖 | M:SS | FF888888 |
| Page elapsed | 📄 | M:SS | FF888888 |

All 11sp. Shows "--:--" when null.

### Image Viewer

- ContentScale.Fit (maintains aspect ratio, letterboxed)
- `ImageFitRect` computed on layout to map normalized (0..1) coordinates to screen pixels
- `onImageSizeKnown` callback provides image dimensions + container dimensions

### Gestures on Image Viewer

#### Vertical Swipe (Normal mode only)

| Direction | Action | Threshold |
|-----------|--------|-----------|
| Swipe up | `nextPage()` | 80px accumulated |
| Swipe down | `previousPage()` | 80px accumulated |

Disabled when: zoomed, annotation bar visible, or apps overlay visible.

#### Double-Tap (Normal mode)

Requires "Double-tap annotation" setting enabled.

| Hit | Action |
|-----|--------|
| Hits a conversation bubble | Highlights bubble (yellow), shows annotation bar |
| Misses, right half (nx >= 0.5) | `nextPage()` + MarkUnderstoodAndAdvance |
| Misses, left half (nx < 0.5) | `previousPage()` + GoToPrevious |

#### Long-Press

| Hit | Action |
|-----|--------|
| Hits a conversation bubble | Enters **zoom mode** on that bubble |
| Misses all bubbles | Opens **Settings** screen |

#### Single Tap (Zoom mode only)

| Hit | Action |
|-----|--------|
| Hits a token region | Toggles token selection (cyan highlight) |
| Misses all tokens | Exits zoom mode |

#### Double-Tap (Zoom mode)

| Hit | Action |
|-----|--------|
| Hits a token region | Shows annotation bar for that token |
| Misses all tokens | Shows **translation overlay** |

### Annotation Overlay (Canvas layer)

Rendered when `showAnnotationDots` OR `conversationBoxes` non-empty OR `highlightedBubble` set.

#### Normal Mode Rendering

| Element | Fill | Stroke | Stroke Width |
|---------|------|--------|-------------|
| All conversation bubbles | 0x33FFEB3B (transparent yellow) | 0x66FFEB3B | 1.5f |
| Highlighted bubble (double-tapped) | 0x66FFEB3B | 0xCCFFEB3B | 3f |
| Annotation dots | Solid by label color | White outline | 1.5f |

Annotation dot colors by label:

| Label | Color |
|-------|-------|
| "understood" / "got it" | FF4CAF50 (green) |
| "partially" | FFFFC107 (amber) |
| "not_understood" / "didn't get it" | FFF44336 (red) |

Dots: 8f radius, positioned above or below bubble center (smart placement avoids image edge).

#### Zoom Mode Rendering

| Element | Fill | Stroke | Stroke Width |
|---------|------|--------|-------------|
| Token region (unselected) | none | 0x8000BCD4 (semi-transparent cyan) | 1.5f |
| Token region (selected) | 0x6600BCD4 | 0xFF00BCD4 (opaque cyan) | 3f |
| Token indicator dot (unselected) | cyan | — | radius 3f |
| Token indicator dot (selected) | cyan | — | radius 5f |
| Token annotation dot | label color | — | radius 6f |

Indicator dots positioned 6f above token top edge. Only shown if token has text.

### Annotation Bar

Triggered by double-tap on bubble (normal mode) or double-tap on token (zoom mode).

Three positioning strategies (configured in Settings):

#### Strategy: "bottom" (default)

```
+------------------------------------------+
|  [Got it] [Partial] [Didn't get it] [X]  |  <- Alignment.BottomCenter
+------------------------------------------+
```

Full-width row, FF333333 background, 8dp padding, SpaceEvenly arrangement.

#### Strategy: "closest"

Bar goes to **top** if bubble center Y < 0.5, **bottom** if >= 0.5. Same AnnotationBarRow component.

#### Strategy: "spread"

Buttons positioned around the bubble using pixel coordinates from `imageFitRect`:

```
           [Got it]           <- above bubble (green)
  [?]    [ BUBBLE ]    [X]   <- left=partial(amber), right=cancel(gray)
        [Didn't get it]       <- below bubble (red)
```

| Button | Label | Color | Position |
|--------|-------|-------|----------|
| Got it | "understood" | FF4CAF50 | centerX - 40dp, topY - 48dp |
| Didn't get it | "not_understood" | FFF44336 | centerX - 50dp, bottomY + 8dp |
| ? (Partial) | "partially" | FFFFC107 | leftX - 88dp, midY |
| X (Cancel) | — | Gray | rightX + 8dp, midY |

All buttons 12sp font. Falls back to bottom row if bubble box is null.

### Annotation Bar Buttons (all strategies)

| Button | Label saved | Container color | Text |
|--------|-------------|-----------------|------|
| "Got it" | "understood" | FF4CAF50 | White |
| "Partial" | "partially" | FFFFC107 | Black |
| "Didn't get it" | "not_understood" | FFF44336 | White |
| "Cancel" | (none, dismisses) | TextButton | Gray |

On submit: saves RegionAnnotation, clears highlight, closes bar.

### Footer Row

| Element | Condition | Action |
|---------|-----------|--------|
| "< Ch" | canGoPreviousChapter | `previousChapter()` |
| Swipe hint | Always | Shows available directions. Triple-tap (3 taps in 500ms) opens home settings |
| "Apps" | onLaunchApp provided | Opens apps overlay |
| "Ch >" | canGoNextChapter | `nextChapter()` |

All text 12sp. Buttons White, Apps button green (FF4CAF50).

---

## 2. Bubble Zoom View

Entered via long-press on a conversation bubble. Image is cropped to the bubble bounding box.

### Layout (overlaid on image viewer)

```
+------------------------------------------+
|                              [Exit Zoom] |  <- TopEnd, White TextButton
|                                          |
|     [Cropped bubble image]               |
|     (cyan token region outlines)         |
|     (token indicator dots)               |
|                                          |
|  +------------------------------------+  |
|  | Extracted bubble text (18sp, bold)  |  |  <- White surface, 16dp padding
|  +------------------------------------+  |
|  | Selected token text (22sp, bold)    |  |  <- Cyan surface (FF00BCD4), conditional
|  +------------------------------------+  |
+------------------------------------------+
```

### Image Display in Zoom

- Bitmap cropped to bubble region: `Bitmap.createBitmap(fullBitmap, x, y, w, h)`
- Coordinates from `zoomedBubble.{x, y, width, height}` (normalized -> pixel)
- ContentScale.Fit applied to cropped bitmap
- New `imageFitRect` computed for the cropped image

### Token Region Loading (3-step fallback)

1. **Embedded OCR tokens** — from `conversations_ocr.json` bubble's `tokens` field, converted page-normalized -> bubble-relative via `ocrTokenRegions()`
2. **Comic-level token_regions.json** — loaded by `FileImageDataSource.getTokensForBubble()` from the comic directory (bubble-relative coordinates)
3. **Bundled token_regions.json** — loaded by `AndroidTokenRegionRepository` from app assets (fallback)

### Zoom Interactions

| Gesture | Action |
|---------|--------|
| Tap on token | Toggle selection (cyan highlight + text panel below) |
| Tap outside tokens | Exit zoom |
| Double-tap on token | Show annotation bar for that token |
| Double-tap outside tokens | Show translation overlay |
| "Exit Zoom" button | Exit zoom |

### Text Panels (Bottom, conditional)

**Bubble text panel** (always shown if bubble has text):
- Surface: White background, 16dp horizontal margin, 4dp bottom margin
- Text: `zoomedBubble.text`, Black, 18sp, Bold, 16dp padding

**Selected token panel** (shown when a token is selected):
- Surface: FF00BCD4 (cyan), 16dp horizontal margin, 4dp bottom margin
- Text: `selectedToken.text`, White, 22sp, Bold, 12dp padding

### Translation Overlay

Triggered by double-tap on empty area in zoom mode.

```
+------------------------------------------+
|         (0xCC000000 tinted bg)           |
|                                          |
|   +----------------------------------+   |
|   | Extracted Text            (cyan) |   |
|   | "HEY!!! LYSOP!!!"   (White,20sp)|   |
|   |                                  |   |
|   | Meaning                 (green)  |   |
|   | "Hey!!! Usopp!!!" (green, 16sp)  |   |
|   |                                  |   |
|   | Literal                  (gray)  |   |
|   | "Hey!!! Lysop!!!" (gray, 14sp)   |   |
|   |                                  |   |
|   |        Tap to dismiss            |   |
|   +----------------------------------+   |
|                                          |
+------------------------------------------+
```

- Modal: RoundedCornerShape(12dp), FF2D2D2D, 85% screen width
- Tap anywhere dismisses
- Shows "No translation yet" if fields blank
- Shows "No text detected" if translation is null
- Pre-fills originalText from OCR text if no saved translation exists

---

## 3. Settings View

### Layout

Scrollable Column, FF121212 background, 16dp padding.

### Sections (top to bottom)

#### Header
| Element | Details |
|---------|---------|
| "< Back" button | FF4CAF50, navigates to Main |
| "Settings" title | White, 20sp, centered |

#### Active Comic Selector (conditional: if downloaded comics exist)

- Section header: "Active Comic" (FF4CAF50, 16sp)
- OutlinedButton dropdown, full width
- Shows formatted comic name (hyphens -> spaces, capitalized)
- Selected comic highlighted green in dropdown
- On select: calls `onSelectActiveComic(comicId)`, switches `FileImageDataSource`

#### Comic Downloads (conditional: if catalog refresh available)

- Section header: "Comic Downloads"
- Status text: Idle / Downloading X% / Extracting / Done / Error
- Progress bar: LinearProgressIndicator (FF4CAF50 on FF333333)
- "Refresh Catalog" button (outlined, green)
- Comic list: name, size, chapters, Download/Downloaded button

#### Launcher (conditional: if home settings available)

- Section header: "Launcher"
- "Set as Default Launcher" button
- "Change Default Launcher" button

#### Annotation

- Section header: "Annotation"

| Setting | Type | Key | Default |
|---------|------|-----|---------|
| Double-tap annotation | Switch | `double_tap_annotation` | true |
| Show annotation dots | Switch | `show_annotation_dots` | true |
| Annotation bar position | Dropdown | `annotation_bar_position` | "bottom" |

Dropdown options:
1. "Bottom (fixed)" -> `"bottom"`
2. "Closest to bubble (top/bottom)" -> `"closest"`
3. "Spread around bubble" -> `"spread"`

Switch colors: thumb FF4CAF50, track FF388E3C.

#### Actions

| Button | Callback | Condition |
|--------|----------|-----------|
| Open Chatbot | `onOpenChat` | Always |
| View Annotation Log | `onOpenAnnotationLog` | Always |
| Export / Email JSON | `onExportJson` | If callback provided |
| Sync to Cloud | `onSyncCloud` | If callback provided |
| Auto-sync every 60s | `onAutoSyncToggled` (Switch) | If callback provided |

Sync priority: TCP queue (QUEUE_HOST:QUEUE_PORT) > HTTP shim (BACKEND_URL).

#### Hidden Apps (conditional: if any apps hidden)

- Shows count: "{n} apps hidden"
- "Reset Hidden Apps" button

#### Account

- Google sign-in status display
- "Sign in with Google" / "Sign Out of Google" button
- "Logout" button (red, FFF44336, full width, always visible)

All settings changes are persisted to `SettingsStore` (SharedPreferences on Android) and logged to `learnerDataRepository`.

---

## 4. Apps List Overlay

Full-screen overlay (0xE6000000 semi-transparent black) on top of Main screen.

### Layout

```
+------------------------------------------+
| Installed Apps        [Continue Reading]  |  <- Header (White 18sp / Green)
|                                          |
| [Filter apps...                       ]  |  <- OutlinedTextField
|                                          |
| App Name 1                        open   |  <- LazyColumn items
| ─────────────────────────────────────    |
| App Name 2                        open   |
| ─────────────────────────────────────    |
| App Name 3                        open   |
| ...                                      |
+------------------------------------------+
```

### Elements

| Element | Details |
|---------|---------|
| Title | "Installed Apps", White, 18sp |
| Continue Reading | TextButton, FF4CAF50, hides overlay |
| Filter input | OutlinedTextField, placeholder "Filter apps..." (Gray), single-line, focused border FF4CAF50, unfocused FF555555, White text |
| App item | Row: app label (White 16sp, weight=1f) + "open" (FF4CAF50 12sp) |
| Divider | HorizontalDivider(FF333333) between items |
| Item tap | Launches app via `onLaunchApp(packageName)`, hides overlay, logs to learnerDataRepository |

### Filtering

- Real-time filtering on each keystroke via `viewModel.filterApps(query)`
- Filters by lowercase label contains
- Hidden apps excluded (managed via Settings)

---

## 5. Chat Screen

### Layout

```
+------------------------------------------+
| [< Back]        Chatbot                  |  <- Header (FF1E1E1E)
|                                          |
|                    [User message]  (grn) |  <- LazyColumn
| [Bot reply]  (gray)                      |
|                    [User message]  (grn) |
| [Bot reply]  (gray)                      |
|                                          |
| [Type a message...           ] [Send]    |  <- Input (FF1E1E1E)
+------------------------------------------+
```

| Element | Details |
|---------|---------|
| User bubble | FF2E7D32 (dark green), right-aligned, max 280dp, White 15sp |
| Bot bubble | FF424242 (dark gray), left-aligned, max 280dp, White 15sp |
| Shape | RoundedCornerShape(12dp) |
| Input | OutlinedTextField, White text, single-line |
| Send button | FF4CAF50, "Send" |
| Bot behavior | Static reply: "I'm a simple bot. I don't understand anything yet, but I'm here to listen!" |
| Auto-scroll | Scrolls to latest message on new message |
| Persistence | All messages saved to chatRepository |

---

## 6. Annotation Log Screen

### Layout

```
+------------------------------------------+
| [< Back]      Annotation Log             |  <- Header (FF1E1E1E)
| [All] [understood] [partially] [not...]  |  <- Filter chips
|                                          |
| [green] Chapitre_0420/img_003   Box #2   |  <- Annotation items
| [red]   Chapitre_0420/img_005   Box #0   |
| [amber] Chapitre_0421/img_001   Box #1   |
| ...                                      |
+------------------------------------------+
```

| Element | Details |
|---------|---------|
| Filter chips | "All" + one per unique label. Selected chip uses label color |
| Item layout | Surface(FF1E1E1E, rounded 8dp): label badge (colored, 12sp) + imageId + "Box #N" |
| Item tap | Navigates to that image in Main screen |
| Sort | Newest first (by timestamp descending) |
| Empty state | "No annotations" centered in gray |

---

## Data Flow Summary

### Annotation Lifecycle

1. User double-taps bubble/token
2. `DoubleTapAction` created with coordinates + box data
3. Annotation bar appears (position based on setting)
4. User taps label button
5. `RegionAnnotation` saved to `AnnotationRepository` (Room DB)
6. Annotation dot appears on overlay
7. Logged to `learnerDataRepository`

### Session Tracking Lifecycle

1. App resume -> `onAppResumed()` starts app session
2. Page navigation -> `onPageLanded()` starts/ends page + chapter sessions
3. Chapter change detected -> closes chapter session, opens new one
4. App pause -> `onAppPaused()` closes all sessions, saves to `SessionRepository`
5. Timer updates every 1000ms via coroutine

### Comic Data Loading

1. App starts with `AssetsImageDataSource` (bundled, likely empty)
2. Checks saved `selected_asset_id` in SettingsStore
3. If found: switches to `FileImageDataSource(comicDir)` via `switchAsset()`
4. `FileImageDataSource` loads: `manifest.json` -> per-chapter `images.json` -> `conversations_ocr.json` (preferred) or `conversations.json`
5. Token regions: embedded OCR tokens -> comic-level `token_regions.json` -> bundled assets fallback

### Sync Flow

Priority: TCP queue (`QUEUE_HOST:QUEUE_PORT`) > HTTP shim (`BACKEND_URL`).
Configured via `androidApp/server-config.yaml`.
Auto-sync: optional 60s interval toggle in Settings.
