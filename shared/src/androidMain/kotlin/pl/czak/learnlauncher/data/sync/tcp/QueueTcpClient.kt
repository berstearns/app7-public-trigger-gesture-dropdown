package pl.czak.learnlauncher.data.sync.tcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Zero-dep TCP client for the simple-tcp-comm job queue.
 *
 * Mirrors `client.py:rpc()` exactly:
 *   sock = socket.connect(host, port)
 *   sock.sendall(struct.pack("!I", len(json)) + json)
 *   len = struct.unpack("!I", sock.recv(4))
 *   reply = sock.recv(len)
 *
 * Every call opens a fresh socket. Matches the Python client's style and
 * keeps the server's per-connection sqlite handle path simple.
 *
 * This client is deliberately tiny — it only knows how to submit an
 * ingest job and (optionally) poll status. Higher-level concerns
 * (chunking, auth token injection, retries) live in [TcpQueueSyncApi].
 */
class QueueTcpClient(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = QueueProtocol.CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = QueueProtocol.READ_TIMEOUT_MS,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Submit an `ingest_unified_payload` job containing the given
     * already-serialized UnifiedPayload JSON object.
     *
     * @param unifiedPayloadJson serialized UnifiedPayload as a [JsonElement]
     *   (caller owns serialization so we don't couple this class to the
     *   UnifiedPayload DTO shape).
     * @param bearerToken optional auth token. If non-null, it is placed on
     *   the submit envelope as `"auth"`. The server is expected to grow a
     *   guard that validates this — see FEATURE_SPEC.md "Auth" section.
     * @return server-assigned job id from `jobs.db`.
     * @throws QueueSubmitException if the server replied with `ok: false`
     *   or we got an unexpected reply shape.
     */
    suspend fun submitIngest(
        unifiedPayloadJson: JsonElement,
        bearerToken: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val envelope = buildJsonObject {
            put("op", QueueProtocol.OP_SUBMIT)
            if (bearerToken != null) put("auth", bearerToken)
            put("payload", buildJsonObject {
                put("task", QueueProtocol.TASK_INGEST_UNIFIED_PAYLOAD)
                put("db", QueueProtocol.DB_APP7)
                put("unified_payload", unifiedPayloadJson)
            })
        }
        val reply = rpc(envelope)
        val ok = reply["ok"]?.jsonPrimitive?.boolean == true
        if (!ok) {
            val err = reply["err"]?.jsonPrimitive?.content ?: "unknown error"
            throw QueueSubmitException(err)
        }
        val id = reply["id"]?.jsonPrimitive?.long
            ?: throw QueueSubmitException("server reply missing 'id'")
        android.util.Log.i("QueueTcpClient", "submit ok id=$id")
        id
    }

    /**
     * Low-level framed RPC: write one length-prefixed JSON frame, read one
     * length-prefixed JSON frame back, return the parsed reply.
     *
     * Direct translation of `client.py:rpc()`.
     */
    private fun rpc(request: JsonObject): JsonObject {
        Socket().use { sock ->
            sock.connect(InetSocketAddress(host, port), connectTimeoutMs)
            sock.soTimeout = readTimeoutMs
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            val bytes = request.toString().toByteArray(Charsets.UTF_8)
            // !I in struct.pack = 4-byte big-endian unsigned int. Java
            // DataOutputStream.writeInt is already big-endian.
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()

            val len = try {
                inp.readInt()
            } catch (e: EOFException) {
                throw QueueSubmitException("server closed connection before reply")
            }
            if (len < 0 || len > QueueProtocol.MAX_WIRE_PAYLOAD_BYTES) {
                throw QueueSubmitException("reply length out of range: $len")
            }
            val buf = ByteArray(len)
            inp.readFully(buf)
            val replyText = String(buf, Charsets.UTF_8)
            return json.parseToJsonElement(replyText).jsonObject
        }
    }
}

/**
 * Thrown when the queue server rejects a submit, closes the connection
 * mid-RPC, or sends a malformed reply. Callers translate this into a
 * [pl.czak.learnlauncher.data.sync.SyncResponse] with `accepted = false`.
 */
class QueueSubmitException(message: String) : RuntimeException(message)
