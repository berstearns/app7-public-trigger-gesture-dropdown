#!/usr/bin/env python3
"""
verify-sync-roundtrip.py — Row-for-row verifier for the app7 TCP sync feature.

What it does
------------
1. Pulls the Room database (`learner_data.db`) off the running Android emulator
   using `adb exec-out run-as <package> cat databases/learner_data.db` and
   writes it to /tmp/emulator-app7.db.
2. Opens the local-worker sqlite DB (default:
   /home/b/simple-tcp-comm-local-state/app7-tcp-sync-verify.db).
3. Determines the expected device_id by reading the most recent
   ingest_batches.device_id from the worker DB (source of truth — that is
   what the producer sent as `Build.MODEL`).
4. For each of the 7 syncable tables, compares:
     - Room-side vs worker-side row count (worker filtered by device_id).
     - The local_id sets (missing / extra on either side).
     - A small set of content columns per row in the intersection.
6. Prints an aligned report and exits 0 on all-match, 1 on any mismatch.

Assumptions
-----------
- The Room entities use Kotlin field names verbatim as SQLite column names
  (no @ColumnInfo), so the Room side uses camelCase (e.g. `eventType`,
  `pageId`, `chapterName`, `oldValue`). The worker side uses snake_case
  (e.g. `event_type`, `page_id`, `chapter_name`, `old_value`).
- In Room there is no `device_id`, `user_id`, or `local_id` column: the
  Room row's primary key `id` IS the `local_id` on the worker side.
- `region_translations` is keyed by the textual `id` field (no local_id).
- The worker's `ingest_batches` table exists even if empty.

Usage
-----
    python3 verify-sync-roundtrip.py
    python3 verify-sync-roundtrip.py --verbose
    python3 verify-sync-roundtrip.py --worker-db /tmp/other.db

Pre-conditions (script exits non-zero with an explanation if missing):
    * `adb devices` must list at least one device.
    * The app package must be installed and debuggable on that device.
    * At least one successful sync tick must have landed in the worker
      (i.e. ingest_batches has at least one row).
"""

from __future__ import annotations

import argparse
import datetime as _dt
import os
import shutil
import sqlite3
import subprocess
import sys
from typing import Dict, List, Sequence, Tuple

DEFAULT_WORKER_DB = "/home/b/simple-tcp-comm-local-state/app7-tcp-sync-verify.db"
DEFAULT_PACKAGE = "pl.czak.imageviewer.app7"
ROOM_DB_NAME = "learner_data.db"
EMULATOR_DB_LOCAL = "/tmp/emulator-app7.db"

# ----------------------------------------------------------------------------
# Table spec: (room_table, worker_table, join_key_room, join_key_worker,
#             content_columns [list of (room_col, worker_col)])
#
# For the six event tables we join on local_id (worker) = id (room).
# For region_translations we join on id (identical on both sides).
# Content columns are a small sanity-check subset — enough to catch corruption.
# ----------------------------------------------------------------------------
TABLES: List[Dict] = [
    {
        "room_table": "session_events",
        "worker_table": "session_events",
        "room_key": "id",
        "worker_key": "local_id",
        "content": [
            ("eventType", "event_type"),
            ("timestamp", "timestamp"),
            ("durationMs", "duration_ms"),
            ("chapterName", "chapter_name"),
            ("pageId", "page_id"),
            ("pageTitle", "page_title"),
        ],
    },
    {
        "room_table": "annotation_records",
        "worker_table": "annotation_records",
        "room_key": "id",
        "worker_key": "local_id",
        "content": [
            ("imageId", "image_id"),
            ("boxIndex", "box_index"),
            ("label", "label"),
            ("timestamp", "timestamp"),
            ("regionType", "region_type"),
        ],
    },
    {
        "room_table": "chat_messages",
        "worker_table": "chat_messages",
        "room_key": "id",
        "worker_key": "local_id",
        "content": [
            ("sender", "sender"),
            ("text", "text"),
            ("timestamp", "timestamp"),
        ],
    },
    {
        "room_table": "page_interactions",
        "worker_table": "page_interactions",
        "room_key": "id",
        "worker_key": "local_id",
        "content": [
            ("interactionType", "interaction_type"),
            ("timestamp", "timestamp"),
            ("chapterName", "chapter_name"),
            ("pageId", "page_id"),
            ("hitResult", "hit_result"),
        ],
    },
    {
        "room_table": "app_launch_records",
        "worker_table": "app_launch_records",
        "room_key": "id",
        "worker_key": "local_id",
        "content": [
            ("packageName", "package_name"),
            ("timestamp", "timestamp"),
            ("currentChapter", "current_chapter"),
            ("currentPageId", "current_page_id"),
        ],
    },
    {
        "room_table": "settings_changes",
        "worker_table": "settings_changes",
        "room_key": "id",
        "worker_key": "local_id",
        "content": [
            # Note: Room side stores the field as `setting`; worker as `setting_key`.
            ("setting", "setting_key"),
            ("oldValue", "old_value"),
            ("newValue", "new_value"),
            ("timestamp", "timestamp"),
        ],
    },
    {
        "room_table": "region_translations",
        "worker_table": "region_translations",
        "room_key": "id",
        "worker_key": "id",
        "content": [
            ("imageId", "image_id"),
            ("bubbleIndex", "bubble_index"),
            ("originalText", "original_text"),
            ("meaningTranslation", "meaning_translation"),
            ("literalTranslation", "literal_translation"),
            ("sourceLanguage", "source_language"),
            ("targetLanguage", "target_language"),
        ],
    },
]


def _die(msg: str, code: int = 2) -> None:
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(code)


def pull_emulator_db(package: str) -> str:
    """Pull the Room DB off the emulator. Exits non-zero on any failure."""
    if shutil.which("adb") is None:
        _die("adb is not on PATH — install Android platform tools.")

    try:
        devs = subprocess.run(
            ["adb", "devices"], capture_output=True, text=True, check=True
        ).stdout
    except subprocess.CalledProcessError as e:
        _die(f"`adb devices` failed: {e.stderr or e}")

    # First line is the header; any subsequent non-empty line with "device" is good.
    device_lines = [
        ln for ln in devs.splitlines()[1:] if ln.strip() and "device" in ln.split()
    ]
    if not device_lines:
        _die(
            "precondition failed: no adb devices found — start the emulator "
            "and install the APK first."
        )

    # Verify the package is installed.
    pm_check = subprocess.run(
        ["adb", "shell", "pm", "list", "packages", package],
        capture_output=True,
        text=True,
    )
    if package not in pm_check.stdout:
        _die(
            f"precondition failed: package {package} is not installed on the "
            "emulator — build and deploy the APK first."
        )

    # Pull the DB file AND its WAL/SHM companions via run-as. Room runs in
    # WAL mode, so a bare `cat learner_data.db` would miss any writes still
    # sitting in learner_data.db-wal. sqlite3 will replay the WAL automatically
    # when the three files sit next to each other.
    for suffix in ("", "-wal", "-shm"):
        target = f"{EMULATOR_DB_LOCAL}{suffix}"
        if os.path.exists(target):
            os.remove(target)
        with open(target, "wb") as fh:
            proc = subprocess.run(
                [
                    "adb", "exec-out", "run-as", package,
                    "cat", f"databases/{ROOM_DB_NAME}{suffix}",
                ],
                stdout=fh,
                stderr=subprocess.PIPE,
            )
        # The main file is required; the -wal and -shm companions may not
        # exist if Room isn't in WAL mode or has already checkpointed. Only
        # fail hard on the main file.
        if suffix == "" and (proc.returncode != 0 or os.path.getsize(target) == 0):
            _die(
                "failed to pull Room DB from emulator. stderr: "
                f"{proc.stderr.decode(errors='replace').strip()}"
            )
        if suffix != "" and os.path.getsize(target) == 0:
            os.remove(target)  # drop empty companion file
    return EMULATOR_DB_LOCAL


def _row_map(
    conn: sqlite3.Connection,
    table: str,
    key_col: str,
    value_cols: Sequence[str],
    where_clause: str = "",
    params: Tuple = (),
) -> Dict:
    """Return {key -> tuple(values)} for a table, quoting column identifiers."""
    qcols = ", ".join(f'"{c}"' for c in value_cols)
    qkey = f'"{key_col}"'
    sql = f'SELECT {qkey}, {qcols} FROM "{table}"'
    if where_clause:
        sql += f" WHERE {where_clause}"
    cur = conn.execute(sql, params)
    return {row[0]: tuple(row[1:]) for row in cur.fetchall()}


def compare_table(
    spec: Dict,
    room_conn: sqlite3.Connection,
    worker_conn: sqlite3.Connection,
    device_id: str,
    verbose: bool,
) -> Tuple[str, int, int, int, int, int, bool]:
    """Return (name, room_count, worker_count, missing, extra, mismatched, ok)."""
    room_table = spec["room_table"]
    worker_table = spec["worker_table"]
    room_key = spec["room_key"]
    worker_key = spec["worker_key"]
    room_content = [c[0] for c in spec["content"]]
    worker_content = [c[1] for c in spec["content"]]

    # region_translations has no device_id filter on the Room side and the
    # worker side does carry a device_id column but the natural key is unique
    # globally, so filtering by device_id is still the safe choice when data
    # from multiple devices accumulates.
    room_rows = _row_map(room_conn, room_table, room_key, room_content)
    worker_rows = _row_map(
        worker_conn,
        worker_table,
        worker_key,
        worker_content,
        where_clause='"device_id" = ?',
        params=(device_id,),
    )

    room_keys = set(room_rows.keys())
    worker_keys = set(worker_rows.keys())
    missing = room_keys - worker_keys  # in Room but not yet ingested by worker
    extra = worker_keys - room_keys  # in worker but no longer in Room

    mismatched_keys: List = []
    for k in room_keys & worker_keys:
        if room_rows[k] != worker_rows[k]:
            mismatched_keys.append(k)

    ok = not missing and not extra and not mismatched_keys

    if verbose and (missing or extra or mismatched_keys):
        print(f"  [verbose] {room_table}:")
        for k in sorted(missing, key=lambda x: (str(type(x)), x)):
            print(f"    MISSING in worker: key={k!r}  room_row={room_rows[k]!r}")
        for k in sorted(extra, key=lambda x: (str(type(x)), x)):
            print(f"    EXTRA in worker:   key={k!r}  worker_row={worker_rows[k]!r}")
        for k in sorted(mismatched_keys, key=lambda x: (str(type(x)), x)):
            print(
                f"    MISMATCH key={k!r}\n"
                f"      room  ={room_rows[k]!r}\n"
                f"      worker={worker_rows[k]!r}"
            )

    return (
        room_table,
        len(room_rows),
        len(worker_rows),
        len(missing),
        len(extra),
        len(mismatched_keys),
        ok,
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    ap.add_argument("--worker-db", default=DEFAULT_WORKER_DB, help="worker sqlite path")
    ap.add_argument("--package", default=DEFAULT_PACKAGE, help="Android package name")
    ap.add_argument(
        "--verbose",
        action="store_true",
        help="print per-row diffs for any mismatched table",
    )
    args = ap.parse_args()

    if not os.path.exists(args.worker_db):
        _die(f"worker DB does not exist: {args.worker_db}")

    emulator_db = pull_emulator_db(args.package)
    pulled_at = _dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    room_conn = sqlite3.connect(f"file:{emulator_db}?mode=ro", uri=True)
    worker_conn = sqlite3.connect(f"file:{args.worker_db}?mode=ro", uri=True)
    try:
        cur = worker_conn.execute(
            "SELECT device_id FROM ingest_batches "
            "ORDER BY ingested_at DESC LIMIT 1"
        )
        row = cur.fetchone()
        if row is None:
            _die(
                "no ingest batches yet — wait for at least one successful "
                "sync tick before running this script."
            )
        device_id = row[0]

        print(f"Device ID: {device_id}")
        print(f"Emulator DB: {emulator_db} (pulled {pulled_at})")
        print(f"Worker DB:   {args.worker_db}")
        print()

        results = [
            compare_table(spec, room_conn, worker_conn, device_id, args.verbose)
            for spec in TABLES
        ]
    finally:
        room_conn.close()
        worker_conn.close()

    # Aligned report.
    name_w = max(len(r[0]) for r in results) + 1
    all_ok = True
    for name, room_n, worker_n, missing, extra, mismatched, ok in results:
        all_ok = all_ok and ok
        badge = "\u2705" if ok else "\u274c"  # green check / red cross
        print(
            f"{(name + ':').ljust(name_w)}  "
            f"emulator={str(room_n).ljust(4)} "
            f"worker={str(worker_n).ljust(4)} "
            f"missing={str(missing).ljust(3)} "
            f"extra={str(extra).ljust(3)} "
            f"content_mismatch={str(mismatched).ljust(3)} "
            f"{badge}"
        )

    print("------")
    passed = sum(1 for r in results if r[-1])
    total = len(results)
    verdict = "PASS" if all_ok else "FAIL"
    print(f"OVERALL: {verdict} ({passed}/{total} tables match)")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
