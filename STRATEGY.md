# Public Clone — Placeholder + Rescue Strategy

> Created: 2026-04-22 · revised to placeholder-everywhere approach
> Source (private, untouched): `../app7-annotation-trigger-gesture-dropdown_20260422_221713`
> This folder: `public-app7-annotation-trigger-gesture-dropdown_20260422_221713`

## Why this folder exists

The original app7 worktree stays **private**. This `public-` prefixed
sibling is a stripped-down copy intended to become a public OSS mirror.
To do that safely, every credential, server identifier, and bundled
content asset was replaced with a **placeholder** before any publication
step — no files were deleted. Dynamic values (those that actually need to
vary per deploy) are now sourced from env vars, documented in
`.env.template` at this folder's root.

The app **does not build or run as-is** without env vars set and real
keystores generated — that is intentional. See §Expected breakage and
§Rescue strategy below.

Rule enforced throughout: the original private folder is never touched.
All edits here are one-way; if this clone drifts out of sync with the
private tree, re-clone (with the exclusions in §Cloning below) and
re-apply the strip.

## Cloning (how this copy was produced)

```
rsync -a \
  --exclude='build/' \
  --exclude='.gradle/' \
  --exclude='.kotlin/' \
  --exclude='__pycache__/' \
  ../app7-annotation-trigger-gesture-dropdown_20260422_221713/ \
  ./
```

Source had no `.git/`, so no history purge was required.

## Placeholder map — files (no deletions)

Every file listed below **still exists at the same path**, holding
placeholder content. Real values come from env vars at build/run time.

| Path | Placeholder content | Source of real value |
|---|---|---|
| `androidApp/server-config.yaml` | `PLACEHOLDER_BACKEND_HOST` / `PLACEHOLDER_QUEUE_HOST` / `0` | env: `BACKEND_URL` / `QUEUE_HOST` / `QUEUE_PORT` |
| `desktopApp/server-config.yaml` | same | same |
| `desktopApp/src/main/resources/server-config.yaml` | same | same |
| `androidApp/debug-learnlauncher.keystore` | text stub with `keytool` instructions | env: `ANDROID_DEBUG_KEYSTORE_PATH` (+ generate a real JKS) |
| `androidApp/release-learnlauncher.keystore` | text stub with `keytool` instructions | env: `ANDROID_RELEASE_KEYSTORE_PATH` (+ generate a real JKS) |
| `androidApp/src/main/assets/token_regions.json` | `{ "tokenRegions": [] }` | demo-content pack (§Rescue) |
| `androidApp/src/main/assets/_dummy_token_regions.json` | `{ "tokenRegions": [] }` | demo-content pack |
| `androidApp/src/main/assets/_dummy_translations.json` | `{ "translations": [] }` | demo-content pack |

## Placeholder map — inline redactions in source/docs

Historical log artifacts in narrative docs — not dynamic runtime values,
kept as flat text placeholders:

| Token in repo | Category of original | Where |
|---|---|---|
| `REDACTED_IP` | Backend/queue server IP | `RESUME.md`, `objectives/*.md` |
| `REDACTED_HOST` | Dev workstation hostname | `RESUME.md`, `objectives/*.md` |
| `REDACTED_DROPLET` | Cloud VM instance id | `objectives/23-sync-session-rows-end-to-end.md` |
| `REDACTED_PID` | Local worker process id | `RESUME.md` |

Original values are not reproduced here — they live in the private
twin, and the substitution itself is done by a pre-publication strip
(see §Sync discipline). The tokens above are grepable markers: searching
the repo for `REDACTED_` will show every narrative reference that a
rescue pass needs to either rewrite or scrub further.

Config-file passwords were similarly replaced, but with **dynamic
placeholders** driven by env vars instead of static redactions:

| File | Dynamic placeholder | Reads from (precedence) |
|---|---|---|
| `androidApp/build.gradle.kts` → `signingConfigs.debug` | `"PLACEHOLDER"` | env: `ANDROID_DEBUG_{STORE,KEY}_PASSWORD` |
| `androidApp/build.gradle.kts` → `signingConfigs.release` | `"PLACEHOLDER"` | env: `ANDROID_RELEASE_{STORE,KEY}_PASSWORD` |
| `desktopApp/src/main/kotlin/.../Main.kt` → `DEFAULT_BACKEND_URL` | `http://PLACEHOLDER_BACKEND_HOST:8080` | env: `BACKEND_URL` |

## Env vars (canonical list)

Full documentation with examples: `./.env.template`.

| Env var | Consumed by | Notes |
|---|---|---|
| `BACKEND_URL` | `androidApp/build.gradle.kts` → `BuildConfig.BACKEND_URL`; `desktopApp/.../Main.kt` → `resolveBackendUrl()` | HTTP base URL incl. scheme + port |
| `QUEUE_HOST` | `androidApp/build.gradle.kts` → `BuildConfig.QUEUE_HOST`; `desktopApp/.../Main.kt` → `resolveQueueConfig()` | TCP host for job queue |
| `QUEUE_PORT` | same as QUEUE_HOST | Integer |
| `ANDROID_DEBUG_KEYSTORE_PATH` | `androidApp/build.gradle.kts` | Path to a real debug keystore |
| `ANDROID_DEBUG_STORE_PASSWORD` | same | |
| `ANDROID_DEBUG_KEY_ALIAS` | same | |
| `ANDROID_DEBUG_KEY_PASSWORD` | same | |
| `ANDROID_RELEASE_KEYSTORE_PATH` | same | Path to a real release keystore |
| `ANDROID_RELEASE_STORE_PASSWORD` | same | |
| `ANDROID_RELEASE_KEY_ALIAS` | same | |
| `ANDROID_RELEASE_KEY_PASSWORD` | same | |

Precedence at every read site:
**CLI flag > env var > `server-config.yaml` fallback > hard-coded PLACEHOLDER**

## Expected breakage (before rescue)

Breakage is deliberate — a broken build is preferable to a leaky one.

1. **Android signing fails** — `debug-learnlauncher.keystore` and
   `release-learnlauncher.keystore` are text stubs, not real JKS files.
   Gradle will fail at signing for both debug and release until a real
   keystore is generated (via `keytool`, see `.env.template`).
2. **BuildConfig values are placeholders** — if env vars aren't set,
   `BACKEND_URL` / `QUEUE_HOST` / `QUEUE_PORT` get baked as
   `PLACEHOLDER_BACKEND_HOST` / `PLACEHOLDER_QUEUE_HOST` / `0`. The
   build succeeds; any networked feature (sync upload, queue polling,
   worker registration) fails at runtime.
3. **Desktop backend URL is a placeholder** — `DEFAULT_BACKEND_URL` in
   `desktopApp/.../Main.kt` is `http://PLACEHOLDER_BACKEND_HOST:8080`.
   Desktop app launches fine; HTTP calls fail DNS.
4. **Content-dependent code paths no-op or crash** — `token_regions.json`
   and the two dummy fixtures are `{ ... [] }`. The
   `annotate-tokens-in-region-zoom-mode` feature and the translations
   layer will render nothing. Needs a demo-content pack.
5. **Historical docs still readable** — `RESUME.md` and `objectives/*.md`
   narrate past runs with `REDACTED_IP:9999` etc.; the story reads fine
   but any contributor following them literally will need to substitute
   their own values.

## Rescue strategy (deferred)

Goal: make this public tree buildable and runnable for a cold-clone user
without leaking anything stripped above.

1. **Secrets layer.**
   - `.env.template` exists. Consumers copy to `.env`, source it, or
     pass through their CI secrets manager.
   - Gradle signing configs already env-driven.
   - Bash helper (TBD): `scripts/load-env.sh` that sources `.env` before
     invoking `./gradlew`, so `ANDROID_DEBUG_STORE_PASSWORD` etc. reach
     the JVM.
   - For release signing in OSS forks: strip the `release` signingConfig
     entirely or gate it behind `if (System.getenv("ANDROID_RELEASE_KEYSTORE_PATH") != null)`.
2. **Server endpoints.** Done — `BACKEND_URL` / `QUEUE_HOST` / `QUEUE_PORT`
   are read in both Android (Gradle → BuildConfig) and Desktop (Main.kt).
3. **Bundled content.** TBD — design a demo-content pack: a handful of
   CC-licensed or synthetically generated page images + a tiny
   `token_regions.json` with fabricated coordinates, enough to exercise
   the UI. Keep under ~100 KB. Ship under `androidApp/src/main/assets/demo/`.
   Loader fallback: if `token_regions.json` is the empty placeholder, load
   `demo/token_regions.json` and show a "demo content" banner.
4. **Objectives / docs.** Audit each file under `objectives/` — some
   reference private infrastructure (DO droplet, tmux session names)
   that a public reader has no context for. Either (a) trim those
   sections, or (b) rewrite as generic "your queue server" prose.
5. **Smoke test before publication.**
   - From a clean checkout:
     ```
     cp .env.template .env && "$EDITOR" .env       # fill real values
     keytool -genkeypair -v -keystore debug-learnlauncher.keystore ...   # see .env.template
     set -a && . ./.env && set +a
     ./gradlew :androidApp:assembleDebug           # must succeed
     ./gradlew :desktopApp:run                     # must launch
     ```
   - Residual-leak check must return nothing. Keep a gitignored private
     list of literal patterns (the values that were stripped out — do
     not commit this list) and grep against it:
     ```
     grep -rn -f ../.strip-patterns .          # ../.strip-patterns is gitignored
     ```
6. **Sync discipline going forward.**
   - After each resync from the private tree: re-apply the placeholder
     swap (config files + inline redactions + keystore stubs) before
     touching anything else. Consider scripting once the set is stable:
     `./scripts/strip-public.sh`.

## Quick verification commands

```
# 1) no real secrets or IPs remain — uses a gitignored pattern file outside this tree
grep -rn -f ../.strip-patterns .

# 2) placeholder files all present
ls androidApp/{debug,release}-learnlauncher.keystore
ls {androidApp,desktopApp,desktopApp/src/main/resources}/server-config.yaml
ls androidApp/src/main/assets/

# 3) env var coverage
grep -nE 'System\.getenv\(' androidApp/build.gradle.kts desktopApp/src/main/kotlin/**/Main.kt

# 4) .env itself is gitignored (should print ".env")
grep -x '.env' .gitignore
```
