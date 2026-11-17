package com.pj.signals

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("pj", Context.MODE_PRIVATE)

// ntfy topic names: letters, digits, - and _, max 64. Anything else is ignored (input from the web page).
private val TOPIC = Regex("^[A-Za-z0-9_-]{1,64}$")

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "PJApp")
        setContentView(web)
        if (saved == null) web.loadUrl(BuildConfig.SITE_URL) else web.restoreState(saved)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        SignalService.start(this)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        web.saveState(out)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    inner class Bridge {
        @JavascriptInterface
        fun setTopic(code: String) {
            val t = code.trim()
            if (t.isNotEmpty() && !TOPIC.matches(t)) return   // "" = Leave: the service finds no topic and stops
            if (t == getTopic()) return
            prefs(this@MainActivity).edit().putString("topic", t).remove("lastId").apply()
            runOnUiThread { SignalService.start(this@MainActivity) }
        }

        @JavascriptInterface
        fun getTopic(): String = prefs(this@MainActivity).getString("topic", "") ?: ""
    }
}
