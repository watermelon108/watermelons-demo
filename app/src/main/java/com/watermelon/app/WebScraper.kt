package com.watermelon.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hidden WebView based scraper. Site ko browser ki tarah load karta hai,
 * JavaScript chalne deta hai, phir cards ya video links nikalta hai.
 */
class WebScraper(private val ctx: Context) {

    private val main = Handler(Looper.getMainLooper())

    private val cardJs = """
        (function () {
            var out = [];
            var seen = {};
            var anchors = document.querySelectorAll('a[href]');
            for (var i = 0; i < anchors.length; i++) {
                var a = anchors[i];
                var img = a.querySelector('img');
                if (!img) continue;
                var href = a.href;
                if (!href || seen[href]) continue;
                seen[href] = 1;
                var title = (a.getAttribute('title') || img.getAttribute('alt') || a.innerText || '').trim();
                if (!title) continue;
                var src = img.getAttribute('data-src') || img.src || '';
                out.push({ title: title.substring(0, 80), link: href, image: src });
            }
            return JSON.stringify(out.slice(0, 60));
        })()
    """.trimIndent()

    private val playJs = """
        (function () {
            var v = document.querySelector('video');
            if (v) { try { v.play(); } catch (e) {} }
            var b = document.querySelector('[class*="play"], [id*="play"]');
            if (b) { try { b.click(); } catch (e) {} }
        })()
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView {
        val wv = WebView(ctx)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
        return wv
    }

    /** Page ke cards (poster + title + link) load karta hai. Callback main thread par aata hai. */
    fun loadCards(url: String, source: String, onResult: (List<Card>) -> Unit) {
        main.post {
            val wv = newWebView()
            var done = false
            fun finish(list: List<Card>) {
                if (done) return
                done = true
                wv.stopLoading()
                wv.destroy()
                onResult(list)
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    // JS ko content render karne ke liye thoda time do
                    main.postDelayed({
                        view?.evaluateJavascript(cardJs) { raw -> finish(parseCards(raw, source)) }
                    }, 3000)
                }
            }
            main.postDelayed({ finish(emptyList()) }, 25000)
            wv.loadUrl(url)
        }
    }

    /** Page khol kar pehla video/stream link pakadta hai. Callback main thread par aata hai. */
    fun sniffMedia(pageUrl: String, onResult: (String?) -> Unit) {
        main.post {
            val wv = newWebView()
            var done = false
            fun finish(u: String?) {
                if (done) return
                done = true
                wv.stopLoading()
                wv.destroy()
                onResult(u)
            }
            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val u = request?.url?.toString()
                    if (u != null && isMedia(u)) {
                        main.post { finish(u) }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, u: String?) {
                    // Play button dabane ki koshish, taaki video request shuru ho
                    main.postDelayed({ view?.evaluateJavascript(playJs, null) }, 2000)
                }
            }
            main.postDelayed({ finish(null) }, 30000)
            wv.loadUrl(pageUrl)
        }
    }

    private fun isMedia(u: String): Boolean {
        val l = u.lowercase()
        return l.contains(".m3u8") || l.contains(".mp4") || l.contains(".mkv") || l.contains(".webm")
    }

    private fun parseCards(raw: String?, source: String): List<Card> {
        if (raw == null || raw == "null") return emptyList()
        return try {
            // evaluateJavascript ek JSON string deta hai, use unwrap karna padta hai
            val text = JSONArray("[$raw]").getString(0)
            val arr = JSONArray(text)
            val list = mutableListOf<Card>()
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.getJSONObject(i)
                list.add(
                    Card(
                        o.optString("title"),
                        o.optString("link"),
                        o.optString("image"),
                        source
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
