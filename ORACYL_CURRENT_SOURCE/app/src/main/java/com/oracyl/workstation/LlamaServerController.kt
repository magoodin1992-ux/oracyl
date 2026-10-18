package com.oracyl.workstation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Localhost-only llama.cpp server lifecycle with cancellation and rollback. */
class LlamaServerController(private val context: Context, private val models: ModelRegistry) : AutoCloseable {
    private val lock = Any()
    private var process: Process? = null
    private var port: Int? = null
    private var runningModel: ModelRegistry.Model? = null
    @Volatile private var activeConnection: HttpURLConnection? = null
    private val cancelled = AtomicBoolean(false)

    fun status(): JSONObject = synchronized(lock) {
        val binary = binaryFile()
        JSONObject().put("binary_present", binary.isFile).put("running", process?.isAlive == true)
            .put("model_id", runningModel?.id ?: JSONObject.NULL)
            .put("compatibility", if (process?.isAlive == true) "MODEL_LOADED_BY_LLAMA_CPP" else "UNVERIFIED_UNTIL_DEVICE_LOAD")
    }

    fun switchModel(id: String): JSONObject = synchronized(lock) {
        val prior = runningModel
        stopLocked()
        val selected = models.activate(id)
        try {
            startLocked(selected)
            JSONObject().put("status", "MODEL_ACTIVE").put("model_id", selected.id)
        } catch (failure: Throwable) {
            stopLocked()
            if (prior != null) {
                try { models.activate(prior.id); startLocked(prior) } catch (_: Throwable) { stopLocked() }
            }
            throw IllegalStateException("Model switch failed; prior model rollback attempted: ${failure.message}", failure)
        }
    }

    fun ensureRunning(): ModelRegistry.Model = synchronized(lock) {
        if (process?.isAlive == true && runningModel != null) return runningModel!!
        val selected = models.active() ?: error("No local GGUF model is active")
        startLocked(selected); selected
    }

    fun complete(messages: JSONArray, temperature: Double = 0.2, maxTokens: Int = 768): String {
        require(temperature in 0.0..2.0 && maxTokens in 1..4096)
        ensureRunning(); cancelled.set(false)
        val localPort = synchronized(lock) { port ?: error("llama.cpp port is unavailable") }
        val body = JSONObject().put("messages", messages).put("temperature", temperature)
            .put("max_tokens", maxTokens).put("stream", false)
        val connection = URL("http://127.0.0.1:$localPort/v1/chat/completions").openConnection() as HttpURLConnection
        activeConnection = connection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 10_000; connection.readTimeout = 300_000
            connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (cancelled.get()) throw InterruptedException("Generation cancelled")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val payload = stream.bufferedReader().use { it.readText() }
            check(code in 200..299) { "llama.cpp HTTP $code: ${payload.take(240)}" }
            val json = JSONObject(payload)
            return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } finally {
            activeConnection = null; connection.disconnect()
        }
    }

    fun cancel() {
        cancelled.set(true)
        activeConnection?.disconnect()
    }

    private fun startLocked(model: ModelRegistry.Model) {
        val binary = binaryFile()
        check(binary.isFile) { "llama.cpp runtime was not packaged" }
        check(model.file.isFile) { "Active GGUF model is missing" }
        val selectedPort = ServerSocket(0, 1).use { it.localPort }
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val builder = ProcessBuilder(binary.absolutePath, "--model", model.file.absolutePath,
            "--host", "127.0.0.1", "--port", selectedPort.toString(), "--ctx-size", "8192",
            "--threads", threads.toString(), "--jinja", "--no-webui")
        builder.directory(File(context.filesDir, "llama").apply { mkdirs() })
        builder.environment()["LD_LIBRARY_PATH"] = nativeDir
        builder.redirectErrorStream(true)
        val candidate = builder.start()
        Thread({ candidate.inputStream.bufferedReader().useLines { lines -> lines.forEach { android.util.Log.i("OracylLlama", it.take(1000)) } } }, "oracyl-llama-log").apply { isDaemon = true }.start()
        val deadline = System.nanoTime() + 90_000_000_000L
        var ready = false; var last = "not ready"
        while (System.nanoTime() < deadline && candidate.isAlive) {
            try {
                val conn = URL("http://127.0.0.1:$selectedPort/health").openConnection() as HttpURLConnection
                conn.connectTimeout = 500; conn.readTimeout = 500
                val code = conn.responseCode; last = "HTTP $code"; conn.disconnect()
                if (code == 200) { ready = true; break }
            } catch (failure: Exception) { last = failure.javaClass.simpleName }
            Thread.sleep(150)
        }
        if (!ready) { candidate.destroy(); candidate.waitFor(); error("llama.cpp did not become ready ($last)") }
        process = candidate; port = selectedPort; runningModel = model
    }

    private fun stopLocked() {
        cancel(); process?.let { running ->
            running.destroy()
            if (!running.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) running.destroyForcibly()
        }
        process = null; port = null; runningModel = null
    }

    private fun binaryFile() = File(context.applicationInfo.nativeLibraryDir, "liboracyl_llama_server.so")
    override fun close() = synchronized(lock) { stopLocked() }
}