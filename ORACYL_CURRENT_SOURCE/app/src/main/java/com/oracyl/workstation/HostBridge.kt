package com.oracyl.workstation

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.util.concurrent.Executors

/** Narrow asynchronous host API. Arbitrary web content never receives this object. */
class HostBridge(
    private val web: WebView,
    private val coordinator: OracylTurnCoordinator,
    private val database: OracylDatabase,
    private val models: ModelRegistry,
    private val llama: LlamaServerController,
    private val victorStates: VictorStateManager,
    private val victor: VictorPythonRuntime,
    private val knowledge: KnowledgeIngestor,
    private val tasks: TaskScheduler,
    private val uiActions: UiActions,
) : AutoCloseable {
    interface UiActions {
        fun requestFile(kind: String, callbackId: String)
        fun openBrowser(url: String, tabId: String?, callbackId: String)
    }
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "oracyl-host") }

    @JavascriptInterface fun dispatch(json: String, callbackId: String) {
        if (callbackId.length !in 8..100) return
        val request = try { JSONObject(json) } catch (_: Exception) {
            deliver(callbackId, error("Malformed request")); return
        }
        when (request.optString("action")) {
            "model_import", "victor_import", "victor_export", "knowledge_import" -> uiActions.requestFile(request.getString("action"), callbackId)
            "browser_open" -> uiActions.openBrowser(request.optString("url", "https://www.google.com"),
                request.optString("tab_id", "").ifBlank { null }, callbackId)
            "cancel" -> { llama.cancel(); deliver(callbackId, JSONObject().put("ok", true).put("status", "CANCEL_REQUESTED")) }
            else -> executor.execute { deliver(callbackId, runCatching { execute(request) }.getOrElse { error(it.message ?: it.javaClass.simpleName) }) }
        }
    }

    fun completeFileAction(kind: String, callbackId: String, uri: Uri?) {
        if (uri == null) { deliver(callbackId, error("File selection cancelled")); return }
        executor.execute {
            val response = runCatching {
                when (kind) {
                    "model_import" -> {
                        val model = models.import(uri, "Imported model")
                        JSONObject().put("ok", true).put("status", "MODEL_IMPORTED_NOT_ACTIVE")
                            .put("model_id", model.id).put("sha256", model.sha256).put("size", model.size)
                    }
                    "victor_import" -> {
                        val state = victorStates.importFrom(uri)
                        JSONObject().put("ok", true).put("status", "VICTOR_IMPORTED_AND_ACTIVATED").put("version_id", state.versionId)
                    }
                    "victor_export" -> JSONObject().put("ok", true).put("result", victorStates.exportTo(uri))
                    "knowledge_import" -> JSONObject().put("ok", true).put("result", knowledge.ingest(uri, null))
                    else -> error("Unknown file action")
                }
            }.getOrElse { error(it.message ?: it.javaClass.simpleName) }
            deliver(callbackId, response)
        }
    }

    fun completeBrowserOpen(callbackId: String, tabId: String?, failure: Throwable? = null) {
        deliver(callbackId, if (failure == null) JSONObject().put("ok", true).put("status", "BROWSER_OPEN")
            .put("tab_id", tabId) else error(failure.message ?: "Browser failed to open"))
    }

    private fun execute(request: JSONObject): JSONObject = when (request.optString("action")) {
        "status" -> JSONObject().put("ok", true).put("mode", "precompile-source")
            .put("qwen", llama.status()).put("victor", runCatching { victorStates.ensureInitialInstalled(); JSONObject().put("available", true).put("version_id", victorStates.active().versionId) }
                .getOrElse { JSONObject().put("available", false).put("reason", it.message) })
            .put("browser", JSONObject().put("available", true).put("engine", "GeckoView"))
        "send" -> coordinator.send(request.optString("chat_id", "").ifBlank { null }, request.getString("text"))
        "chat_new" -> JSONObject().put("ok", true).put("chat_id", database.ensureChat(null))
        "chat_list" -> JSONObject().put("ok", true).put("chats", database.chats())
        "chat_history" -> JSONObject().put("ok", true).put("messages", database.messages(request.getString("chat_id")))
        "draft_save" -> { database.saveDraft(request.getString("chat_id"), request.optString("text")); JSONObject().put("ok", true) }
        "draft_load" -> JSONObject().put("ok", true).put("text", database.draft(request.getString("chat_id")))
        "model_list" -> JSONObject().put("ok", true).put("models", models.list().fold(org.json.JSONArray()) { array, model ->
            array.put(JSONObject().put("id", model.id).put("name", model.name).put("sha256", model.sha256).put("size", model.size))
        }).put("active", models.active()?.id ?: JSONObject.NULL)
        "model_switch" -> JSONObject().put("ok", true).put("result", llama.switchModel(request.getString("model_id")))
        "victor_versions" -> JSONObject().put("ok", true).put("active", victorStates.active().versionId)
            .put("versions", victorStates.listVersions())
        "victor_rollback" -> JSONObject().put("ok", true).put("status", "VICTOR_ROLLED_BACK")
            .put("version_id", victorStates.rollback().versionId)
        "research_pending", "research_status" -> limitedVictor(request, setOf("action"))
        "research_transport_request", "research_teacher_review" -> limitedVictor(request, setOf("action", "ticket"))
        "research_transport_result" -> limitedVictor(request, setOf("action", "packet"))
        "research_answer" -> limitedVictor(request, setOf("action", "ticket", "answer", "facts"))
        "tabs_list" -> JSONObject().put("ok", true).put("tabs", database.tabs())
        "task_create" -> JSONObject().put("ok", true).put("task", tasks.create(request.getString("title"), request.getJSONObject("spec")))
        "task_list" -> JSONObject().put("ok", true).put("tasks", database.tasks())
        "task_pause" -> JSONObject().put("ok", true).put("task", tasks.pause(request.getString("id")))
        "task_resume" -> JSONObject().put("ok", true).put("task", tasks.resume(request.getString("id")))
        "task_cancel" -> JSONObject().put("ok", true).put("task", tasks.cancel(request.getString("id")))
        "operator_question_create" -> JSONObject().put("ok", true).put("question", database.createOperatorQuestion(request.getString("question"), request.optJSONObject("context") ?: JSONObject()))
        "operator_question_list" -> JSONObject().put("ok", true).put("questions", database.operatorQuestions())
        "operator_question_answer" -> JSONObject().put("ok", true).put("question", database.answerOperatorQuestion(request.getString("id"), request.getString("answer")))
        else -> error("Unsupported host action")
    }

    private fun limitedVictor(request: JSONObject, allowed: Set<String>): JSONObject {
        check(request.keys().asSequence().toSet() == allowed) { "Victor request schema mismatch" }
        val rpc = JSONObject(request.toString()).put("version", "VICTOR_ARM64_RPC_V1")
        val response = victor.dispatch(rpc)
        return if (response.optBoolean("ok")) JSONObject().put("ok", true).put("result", response.get("result"))
            else error(response.optString("message", response.optString("error", "Victor request failed")))
    }

    private fun error(message: String) = JSONObject().put("ok", false).put("error", message.take(300))
    private fun deliver(callbackId: String, response: JSONObject) {
        val script = "window.OracylNativeResult(${JSONObject.quote(callbackId)},${JSONObject.quote(response.toString())})"
        web.post { web.evaluateJavascript(script, null) }
    }
    override fun close() { llama.cancel(); executor.shutdownNow() }
}