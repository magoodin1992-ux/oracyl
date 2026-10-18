package com.oracyl.workstation

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject

/** Executes the protected Victor MobileBridge in one in-process Python session. */
class VictorPythonRuntime(
    private val context: Context,
    private val states: VictorStateManager,
) {
    private val lock = Any()

    fun semanticTurn(envelopeJson: String): JSONObject = synchronized(lock) {
        val active = states.ensureInitialInstalled()
        if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
        val module = Python.getInstance().getModule("oracyl_semantic_gateway")
        val response = module.callAttr(
            "dispatch_semantic_turn",
            active.runtimeDir.absolutePath,
            active.sourceDir.absolutePath,
            envelopeJson,
        ).toString()
        JSONObject(response)
    }

    fun dispatch(request: JSONObject): JSONObject = synchronized(lock) {
        val active = states.ensureInitialInstalled()
        if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
        val module = Python.getInstance().getModule("oracyl_semantic_gateway")
        JSONObject(module.callAttr("dispatch_victor_rpc", active.runtimeDir.absolutePath,
            active.sourceDir.absolutePath, request.toString()).toString())
    }

    fun status(): JSONObject = try {
        val active = states.active()
        JSONObject().put("available", true).put("version_id", active.versionId)
    } catch (_: Exception) {
        JSONObject().put("available", false).put("reason", "Victor is not installed")
    }
}