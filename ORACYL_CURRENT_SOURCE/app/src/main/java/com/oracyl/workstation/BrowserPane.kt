package com.oracyl.workstation

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Untrusted pages run only in GeckoView and receive no Oracyl JavaScript interface. */
class BrowserPane(private val activity: Activity, private val root: FrameLayout, private val database: OracylDatabase) {
    private val container = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(5, 6, 7)); visibility = View.GONE
    }
    private val address = EditText(activity).apply {
        setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setSingleLine(true); hint = "https://"
        setBackgroundColor(Color.rgb(28, 34, 43))
    }
    private val gecko = GeckoView(activity)
    private var session: GeckoSession? = null
    private var tabId: String? = null
    private var lastUrl = "about:blank"
    private var lastState: String? = null
    var onClosed: (() -> Unit)? = null

    init {
        val toolbar = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun button(label: String, action: () -> Unit) = Button(activity).apply {
            text = label; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(28, 34, 43)); setOnClickListener { action() }
        }
        toolbar.addView(button("‹") { session?.goBack() }, LinearLayout.LayoutParams(64, ViewGroup.LayoutParams.WRAP_CONTENT))
        toolbar.addView(button("›") { session?.goForward() }, LinearLayout.LayoutParams(64, ViewGroup.LayoutParams.WRAP_CONTENT))
        toolbar.addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(button("Go") { load(address.text.toString()) })
        toolbar.addView(button("✓") { tabId?.let { database.setTabSaved(it, true) } })
        toolbar.addView(button("×") { close() })
        container.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(gecko, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun open(url: String, existingTabId: String? = null): String {
        closeSession()
        val safe = safeUrl(url)
        val id = existingTabId ?: database.createTab(safe)
        val record = database.tab(id) ?: error("Browser tab record is missing")
        tabId = id; lastUrl = record.getString("url"); address.setText(lastUrl)
        val next = GeckoSession()
        next.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                lastUrl = url; address.setText(url); database.updateTab(id, url, null, lastState)
            }
            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                lastState = sessionState.toString(); database.updateTab(id, lastUrl, null, lastState)
            }
        }
        next.open((activity.application as OracylApplication).geckoRuntime)
        val stored = record.opt("session_state")
        if (stored != null && stored != org.json.JSONObject.NULL) {
            runCatching { next.restoreState(GeckoSession.SessionState.fromString(stored.toString())) }
                .onFailure { next.load(GeckoSession.Loader().uri(safe)) }
        } else next.load(GeckoSession.Loader().uri(safe))
        gecko.setSession(next); session = next; container.visibility = View.VISIBLE
        return id
    }

    fun isOpen() = container.visibility == View.VISIBLE
    fun close() { closeSession(); container.visibility = View.GONE; onClosed?.invoke() }
    private fun closeSession() {
        session?.let { current -> current.setActive(false); current.flushSessionState(); gecko.releaseSession(); current.close() }
        session = null
    }
    private fun load(raw: String) { session?.load(GeckoSession.Loader().uri(safeUrl(raw))) }
    private fun safeUrl(raw: String): String {
        val value = raw.trim(); val uri = android.net.Uri.parse(value)
        require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()) { "Only absolute HTTP(S) URLs are allowed" }
        return uri.toString()
    }
}