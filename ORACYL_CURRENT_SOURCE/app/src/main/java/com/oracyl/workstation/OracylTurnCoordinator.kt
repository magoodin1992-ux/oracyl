package com.oracyl.workstation

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Qwen interprets/verbalizes; Victor alone supplies cognition and warranted result. */
class OracylTurnCoordinator(
    private val llama: LlamaServerController,
    private val victor: VictorPythonRuntime,
    private val database: OracylDatabase,
) {
    fun send(chatIdHint: String?, text: String): JSONObject {
        val clean = text.trim(); require(clean.isNotEmpty() && clean.length <= 4096)
        val chatId = database.ensureChat(chatIdHint, clean)
        database.appendMessage(chatId, "operator", clean, "ACCEPTED")
        val turnId = "turn-${UUID.randomUUID()}"
        val interpretation = interpret(chatId, turnId, clean)
        val semanticJson = interpretation.toString()
        val victorEnvelope = victor.semanticTurn(semanticJson)
        if (!victorEnvelope.optBoolean("ok")) {
            val message = "Victor rejected the semantic turn: " + victorEnvelope.optString("message", victorEnvelope.optString("error", "unknown error"))
            database.appendMessage(chatId, "system", message, "ERROR", semanticJson, victorEnvelope.toString())
            return JSONObject().put("ok", false).put("chat_id", chatId).put("error", message)
        }
        val result = victorEnvelope.getJSONObject("result")
        if (result.optString("status") == "INTERPRETATION_REJECTED") {
            val question = result.getString("clarification_question")
            database.appendMessage(chatId, "victor", question, "CLARIFICATION_REQUIRED", semanticJson, result.toString())
            return JSONObject().put("ok", true).put("chat_id", chatId).put("status", "CLARIFICATION_REQUIRED")
                .put("text", question).put("turn_id", turnId)
        }
        check(result.optString("status") == "VICTOR_RESULT") { "Unexpected Victor result status" }
        val native = result.getJSONObject("victor")
        val answer = verbalizeGrounded(native)
        database.appendMessage(chatId, "victor", answer, "COMPLETED", semanticJson, result.toString())
        return JSONObject().put("ok", true).put("chat_id", chatId).put("turn_id", turnId)
            .put("status", native.optString("status", "VICTOR_RESULT")).put("text", answer)
            .put("research_requested", hasResearchRequest(native))
    }

    private fun interpret(chatId: String, turnId: String, text: String): JSONObject {
        val prompt = """
            Convert the operator utterance into JSON only. Do not answer it. Schema:
            {"speech_act":"QUERY|ASSERTION|COMMAND|CORRECTION|ACKNOWLEDGEMENT","clauses":[{"clause_id":"c1","predicate":"snake_case","arguments":[{"role":"semantic_role","entity_id":"stable:type:name","surface":"exact phrase"}],"polarity":"AFFIRMED|NEGATED|UNKNOWN","tense":"PAST|PRESENT|FUTURE|TIMELESS|UNKNOWN","modality":"ACTUAL|POSSIBLE|NECESSARY|REQUESTED|UNKNOWN","confidence":0.0,"epistemic_status":"OPERATOR_STATEMENT_UNVERIFIED"}],"references":[{"mention":"text","entity_id":"stable:type:name or null","status":"RESOLVED|UNRESOLVED|AMBIGUOUS"}],"effective_time":"text or empty","ambiguity":{"requires_clarification":false,"alternatives":[],"question":""}}
            Preserve negation, uncertainty, role direction, tense, and unresolved references. Confidence below 0.70 when one interpretation is not adequately supported. No extra keys.
        """.trimIndent()
        val context = database.recentMessages(chatId, 10)
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", prompt))
            .put(JSONObject().put("role", "user").put("content", JSONObject().put("recent_context", context).put("utterance", text).toString()))
        val raw = llama.complete(messages, 0.0, 900).trim()
        check(raw.startsWith("{") && raw.endsWith("}")) { "Qwen semantic output was not a JSON object" }
        val qwen = JSONObject(raw)
        val required = setOf("speech_act", "clauses", "references", "effective_time", "ambiguity")
        check(qwen.keys().asSequence().toSet() == required) { "Qwen semantic output schema mismatch" }
        val now = Instant.now()
        return JSONObject().put("schema", "ORACYL_SEMANTIC_TURN_V1").put("turn_id", turnId)
            .put("thread_id", chatId).put("surface_text", text).put("speech_act", qwen.getString("speech_act"))
            .put("clauses", qwen.getJSONArray("clauses")).put("references", qwen.getJSONArray("references"))
            .put("temporal_context", JSONObject().put("utterance_time", now.toString())
                .put("effective_time", qwen.getString("effective_time")).put("timezone", ZoneId.systemDefault().id))
            .put("provenance", JSONObject().put("source_kind", "operator_via_local_qwen")
                .put("source_id", "chat:$chatId").put("captured_at", now.toString()))
            .put("ambiguity", qwen.getJSONObject("ambiguity"))
    }

    private fun verbalizeGrounded(native: JSONObject): String {
        val direct = native.optString("text", "").trim()
        if (direct.isNotEmpty()) return direct
        val source = native.toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content",
            "Express only the supplied Victor result. Preserve uncertainty and unknowns. Add no facts, names, numbers, actions, or success claims. Return JSON only: {\"source_sha256\":\"exact supplied digest\",\"text\":\"faithful wording\"}."))
            .put(JSONObject().put("role", "user").put("content", JSONObject().put("source_sha256", digest).put("victor_result", native).toString()))
        val raw = llama.complete(messages, 0.0, 600).trim()
        val candidate = runCatching { JSONObject(raw) }.getOrNull()
        if (candidate != null && candidate.optString("source_sha256") == digest) {
            val text = candidate.optString("text", "").trim()
            if (text.isNotEmpty() && grounded(text, source)) return text
        }
        return "Victor returned ${native.optString("status", "a structured result")}. Open the result details to inspect the exact native output."
    }

    private fun grounded(text: String, source: String): Boolean {
        val token = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]*")
        val allowed = token.findAll(source.lowercase()).map { it.value }.toMutableSet()
        allowed += setOf("a","an","the","and","or","but","if","then","is","are","was","were","be","to","of","in","on","for","with","from","that","this","it","i","my","your","not","no","unknown","according","victor","cannot","can","could","may","might","because")
        return token.findAll(text.lowercase()).all { it.value in allowed }
    }

    private fun hasResearchRequest(native: JSONObject): Boolean = native.has("research_request") || native.has("research_handoff") ||
        native.optString("status").contains("INQUIRY")
}