package pl.czak.learnlauncher.data.sync.tcp

/**
 * Wire constants for the simple-tcp-comm job queue protocol.
 *
 * Protocol (from `/home/b/simple-tcp-comm/server.py`):
 *
 *   Framing:   [ 4 bytes big-endian length ][ JSON bytes ]
 *   Each RPC:  client writes one frame, reads one frame, then may close
 *              or send another frame on the same connection.
 *
 *   Submit request:
 *     { "op": "submit", "payload": { ...job payload... } }
 *   Submit reply:
 *     { "ok": true, "id": <Long> }    OR    { "ok": false, "err": "<string>" }
 *
 * This client uses one-shot connections: open, submit, read reply, close.
 * That matches how `client.py` is written and keeps things simple.
 */
object QueueProtocol {
    const val OP_SUBMIT = "submit"
    const val OP_STATUS = "status"

    /** Task name handled by `workers/app7/worker.py` in simple-tcp-comm. */
    const val TASK_INGEST_UNIFIED_PAYLOAD = "ingest_unified_payload"

    /** Target SQLite DB name on the worker host (`dbs/app7/...`). */
    const val DB_APP7 = "app7"

    /**
     * Server-side payload cap from `server.py:MAX_PAYLOAD` (1 MB).
     * We leave headroom for the "submit" envelope, auth token, etc.
     */
    const val MAX_WIRE_PAYLOAD_BYTES: Int = 1 * 1024 * 1024

    /**
     * Safe ceiling we try not to exceed when building a single ingest
     * job's JSON body. Chunks larger than this get split before submit.
     */
    const val SAFE_CHUNK_BYTES: Int = 768 * 1024

    /** TCP connect timeout, in ms. Short — phone is on Wi-Fi or LTE. */
    const val CONNECT_TIMEOUT_MS: Int = 5_000

    /** Socket read timeout, in ms. Submit is non-blocking on the server. */
    const val READ_TIMEOUT_MS: Int = 15_000
}
