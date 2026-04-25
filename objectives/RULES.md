# Objectives Rules

Mandatory rules that apply to ALL objectives and ALL work in this project.

## Process Execution

1. **All server processes must run inside named tmux sessions.** Never run `.py` servers or long-lived processes as background jobs (`nohup ... &`), detached processes, or bare terminal commands. Every process must be in a strictly named tmux session with a strictly named tmux pane so it can be:
   - Inspected: `tmux capture-pane -t <session>:<window> -p`
   - Reattached: `tmux attach -t <session>`
   - Reproduced: the exact tmux session/window/pane name is documented

2. **Naming convention for tmux sessions on DO droplet:**
   - `comic-server` — Flask comic catalog/download server (port 8080)
   - `queue-server` — TCP job queue server (port 9999)
   - `worker-<name>` — any worker polling the queue

3. **Naming convention for local tmux sessions:**
   - `app7-e2e` — end-to-end pipeline (collector + verify + inspect)
   - `app7-build` — gradle build
   - `app7-deploy` — APK deploy
   - `app7-local-worker` — local worker polling the queue

4. **After droplet reboot:** Both `comic-server` and `queue-server` tmux sessions must be recreated. There are no systemd services — everything runs in tmux.

## Feature Parity

5. **Android and Desktop must have the same features.** Every feature implemented in `shared/commonMain` is automatically shared. Platform-specific wiring (callbacks in `MainActivity.kt` for Android, `Main.kt` for Desktop) must be kept in sync. If a callback exists in Android's `App()` call, it must also exist in Desktop's `App()` call.

6. **Desktop sync must use the same TCP queue** as Android. Config is read from `server-config.yaml` (checked in `./` and `androidApp/` paths). Queue host/port must be parsed and wired to a real `DesktopSyncService`, not a stub.
