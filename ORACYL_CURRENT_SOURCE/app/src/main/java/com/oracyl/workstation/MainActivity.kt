package com.oracyl.workstation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import java.io.ByteArrayInputStream

/** Trusted Oracyl UI host. Untrusted browser pages belong in a separate GeckoView surface. */
class MainActivity : Activity(), HostBridge.UiActions {
    private lateinit var web: WebView
    private lateinit var bridge: HostBridge
    private lateinit var browser: BrowserPane
    private var pendingFileAction: Pair<String, String>? = null

    companion object { private const val FILE_REQUEST = 4401 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = OracylDatabase(this)
        val states = VictorStateManager(this)
        val models = ModelRegistry(this)
        val llama = LlamaServerController(this, models)
        val victor = VictorPythonRuntime(this, states)
        val knowledge = KnowledgeIngestor(this, victor)
        val coordinator = OracylTurnCoordinator(llama, victor, database)
        val tasks = TaskScheduler(this, database)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.domStorageEnabled = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        WebView.setWebContentsDebuggingEnabled(false)
        val loader = WebViewAssetLoader.Builder().setDomain("appassets.androidplatform.net")
            .addPathHandler("/oracyl/", AssetsPathHandler(this)).build()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                request.url.host != "appassets.androidplatform.net"
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net" ||
                    uri.path !in setOf("/oracyl/index.html", "/oracyl/oracyl_native.js")) {
                    return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                return loader.shouldInterceptRequest(uri)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                web.evaluateJavascript("var s=document.createElement('script');s.src='/oracyl/oracyl_native.js';document.body.appendChild(s)", null)
            }
        }
        web.webChromeClient = WebChromeClient()
        val root = FrameLayout(this)
        root.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        browser = BrowserPane(this, root, database)
        bridge = HostBridge(web, coordinator, database, models, llama, states, victor, knowledge, tasks, this)
        web.addJavascriptInterface(bridge, "OracylHost")
        setContentView(root)
        web.loadUrl("https://appassets.androidplatform.net/oracyl/index.html")
    }

    override fun requestFile(kind: String, callbackId: String) = runOnUiThread {
        if (pendingFileAction != null) {
            bridge.completeFileAction(kind, callbackId, null); return@runOnUiThread
        }
        pendingFileAction = kind to callbackId
        val intent = when (kind) {
            "model_import" -> Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/octet-stream")
            "victor_import" -> Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip")
            "victor_export" -> Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, "Oracyl-Victor-State.zip")
            "knowledge_import" -> Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/markdown", "application/json"))
            else -> error("Unknown file action")
        }.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, FILE_REQUEST)
    }

    override fun openBrowser(url: String, tabId: String?, callbackId: String) = runOnUiThread {
        runCatching { browser.open(url, tabId) }
            .onSuccess { bridge.completeBrowserOpen(callbackId, it) }
            .onFailure { bridge.completeBrowserOpen(callbackId, null, it) }
    }

    @Deprecated("Activity result API retained to keep this source dependency-minimal")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_REQUEST) return
        val pending = pendingFileAction ?: return
        pendingFileAction = null
        bridge.completeFileAction(pending.first, pending.second, if (resultCode == RESULT_OK) data?.data else null)
    }

    override fun onBackPressed() {
        if (browser.isOpen()) browser.close() else if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
    override fun onDestroy() {
        web.removeJavascriptInterface("OracylHost"); bridge.close(); web.destroy(); super.onDestroy()
    }
}