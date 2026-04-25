# deploy/ — APK build with tcpc-targeted config

This subfolder is the single entry point for building APKs that point at a
specific tcpc deployed instance. Mirrors the tcpc/tcpux deploy pattern
(env-driven config, gitignored secrets, `.env.example` template).

## Usage

```bash
cp deploy/.env.example deploy/.env && chmod 600 deploy/.env
$EDITOR deploy/.env                     # fill BACKEND_URL, QUEUE_HOST, QUEUE_PORT, signing
./deploy/build-apk.sh debug              # or `release` or `both`
```

Output APKs land in `$APK_OUT_DIR` (default: `./build/apks/`).

## Pulling tcpc connection params from rclone

```bash
rclone copy <rclone-deploy-path>/client.env /tmp/tcpc-client.env
# Then in deploy/.env, set:
#   QUEUE_HOST = TCPC_HOST from client.env
#   QUEUE_PORT = TCPC_PORT from client.env  (e.g. 51521)
#   TCPC_ARCHIVE_PORT = TCPC_ARCHIVE_PORT (e.g. 51537)
#   TCPC_ADMIN_TOKEN  = TCPC_ADMIN_TOKEN
```

## What flows where

```
  deploy/.env               (real values, mode 600, gitignored)
       │
       ▼  build-apk.sh
       │
       ├─► envsubst → androidApp/server-config.yaml
       ├─► envsubst → desktopApp/server-config.yaml
       ├─► envsubst → desktopApp/src/main/resources/server-config.yaml
       │
       └─► export BACKEND_URL/QUEUE_HOST/QUEUE_PORT/ANDROID_*_KEYSTORE_PATH/...
              │
              ▼  ./gradlew :androidApp:assembleRelease
                  reads env vars (per env-aware build.gradle.kts)
                  bakes BuildConfig.{BACKEND_URL, QUEUE_HOST, QUEUE_PORT}
                  signs with deploy/release-learnlauncher.keystore
                  outputs APK
```

## Today's caveat

`SyncService.kt` still HTTP-POSTs to `BACKEND_URL/sync/upload`. The
tcpc archive uses a binary protocol (header/verdict/receipt — see
[tcpux-tcp-comm/archive_protocol.py](https://github.com/berstearns/tcpux-tcp-comm/blob/main/archive_protocol.py)).
Wiring the Kotlin client to that protocol is **option b** of the
public-clone work — separate commit. Until it lands, the APK can build
with tcpc-style config in `.env` but actually targets BACKEND_URL via HTTP.
