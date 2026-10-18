package com.oracyl.workstation

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** SAF text/JSON ingestion into Victor's private, provenance-preserving document port. */
class KnowledgeIngestor(private val context: Context, private val victor: VictorPythonRuntime) {
    fun ingest(uri: Uri, titleHint: String?): JSONObject {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(128 * 1024); var total = 0
            while (true) { val count = input.read(buffer); if (count < 0) break; total += count
                check(total <= 8_000_000) { "Knowledge import exceeds 8 MB" }; output.write(buffer, 0, count) }
            output.toByteArray()
        } ?: error("Unable to open knowledge source")
        val text = bytes.toString(Charsets.UTF_8)
        check(text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) { "Knowledge source must be UTF-8 text" }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val title = titleHint?.trim()?.take(80)?.ifBlank { null } ?: "Imported knowledge ${digest.take(12)}"
        // 15 KB keeps the JSON-escaped body below Victor's 100 KB hard limit
        // even for control-character-heavy UTF-8 input.
        val chunks = chunkUtf8(text, 15_000)
        val receipts = JSONArray()
        chunks.forEachIndexed { index, chunk ->
            val body = JSONObject().put("schema", "ORACYL_KNOWLEDGE_CHUNK_V1")
                .put("source_sha256", digest).put("source_uri", "user_selected_document")
                .put("part", index + 1).put("parts", chunks.size).put("text", chunk).toString()
            val request = JSONObject().put("version", "VICTOR_ARM64_RPC_V1").put("action", "document_create")
                .put("title", "$title (${index + 1}/${chunks.size})").put("body", body).put("mime", "application/json")
            val response = victor.dispatch(request)
            check(response.optBoolean("ok")) { response.optString("message", "Victor rejected knowledge chunk") }
            receipts.put(response.getJSONObject("result"))
        }
        return JSONObject().put("status", "KNOWLEDGE_STORED_AS_PRIVATE_DOCUMENTS_NOT_SEMANTIC_TRUTH")
            .put("source_sha256", digest).put("chunks", chunks.size).put("receipts", receipts)
    }

    private fun chunkUtf8(text: String, maxBytes: Int): List<String> {
        val result = mutableListOf<String>(); val current = StringBuilder(); var currentBytes = 0
        text.lineSequence().forEach { line ->
            val piece = if (current.isEmpty()) line else "\n$line"; val size = piece.toByteArray(Charsets.UTF_8).size
            if (size > maxBytes) {
                if (current.isNotEmpty()) { result += current.toString(); current.clear(); currentBytes = 0 }
                var offset = 0
                while (offset < line.length) {
                    var end = (offset + 20_000).coerceAtMost(line.length)
                    while (line.substring(offset, end).toByteArray(Charsets.UTF_8).size > maxBytes) end--
                    result += line.substring(offset, end); offset = end
                }
            } else {
                if (currentBytes + size > maxBytes) { result += current.toString(); current.clear(); currentBytes = 0 }
                if (current.isNotEmpty()) current.append('\n')
                current.append(line); currentBytes += size
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        check(result.isNotEmpty()) { "Knowledge source is empty" }
        return result
    }
}