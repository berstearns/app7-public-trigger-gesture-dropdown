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
        println("[QueueTcpClient] submit ok id=$id")
        id
    }

    private fun rpc(request: JsonObject): JsonObject {
        Socket().use { sock ->
            sock.connect(InetSocketAddress(host, port), connectTimeoutMs)
            sock.soTimeout = readTimeoutMs
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            val bytes = request.toString().toByteArray(Charsets.UTF_8)
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

class QueueSubmitException(message: String) : RuntimeException(message)
