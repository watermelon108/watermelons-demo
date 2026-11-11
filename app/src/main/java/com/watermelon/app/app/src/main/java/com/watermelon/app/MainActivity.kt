package com.watermelon.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hidden WebView scraper.
 * WebView screen ke bahar (invisible) layout me lagta hai, taaki lazy-loaded images chalein.
 */
class WebScraper(private val activity: Activity) {

    private val main = Handler(Looper.getMainLooper())

    // Page se cards nikalne ka JavaScript. Result: {t, n, i, c:[{title,link,image}]}
    private val cardJs = """
        (function () {
            function abs(u) {
                try { return new URL(u, location.href).href; } catch (e) { return ''; }
            }
            function imgSrc(img) {
                if (!img) return '';
                var s = img.getAttribute('data-src') || img.getAttribute('data-lazy-src') ||
                        img.getAttribute('data-original') || '';
                if (!s) {
                    var ss = img.getAttribute('srcset') || '';
                    if (ss) s = ss.split(',')[0].trim().split(' ')[0];
                }
                if (!s) s = img.currentSrc || img.src || '';
                return abs(s);
            }
            function titleOf(a, img) {
                var t = a.getAttribute('title') || a.getAttribute('aria-label') ||
                        (img ? img.getAttribute('alt') : '') || '';
                if (!t) {
                    var h = a.querySelector('h1,h2,h3,h4,p,span');
                    if (h) t = h.innerText || '';
                }
                if (!t && a.parentElement) {
                    var hh = a.parentElement.querySelector('h1,h2,h3,h4');
                    if (hh) t = hh.innerText || '';
                }
                if (!t) t = a.innerText || '';
                return t.replace(/\s+/g, ' ').trim();
            }
            var out = [];
            var seen = {};
            var anchors = document.querySelectorAll('a[href]');
            for (var i = 0; i < anchors.length; i++) {
                var a = anchors[i];
                var href = abs(a.getAttribute('href'));
                if (!href || href.indexOf('http') !== 0 || seen[href]) continue;
                var p = a.parentElement;
                var gp = p ? p.parentElement : null;
                var img = a.querySelector('img') ||
                          (p ? p.querySelector('img') : null) ||
                          (gp ? gp.querySelector('img') : null);
                if (!img) continue;
                var title = titleOf(a, img);
                if (title.length < 2) continue;
                seen[href] = 1;
                out.push({ title: title.substring(0, 80), link: href, image: imgSrc(img) });
            }
            return JSON.stringify({
                t: document.title,
                n: anchors.length,
                i: document.images.length,
                c: out.slice(0, 60)
            });
        })()
    """.trimIndent()

    private val scrollJs = "window.scrollTo(0, document.body ? document.body.scrollHeight : 0);"

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
        val wv = WebView(activity)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.loadsImagesAutomatically = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

        // Screen ke bahar, lekin layout me: lazy-load ke liye zaroori
        val host = activity.findViewById<ViewGroup>(android.R.id.content)
        val lp = FrameLayout.LayoutParams(720, 1280)
        host.addView(wv, lp)
        wv.translationX = -10000f
        return wv
    }

    private fun release(wv: WebView) {
        try {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.destroy()
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Page ke cards load karta hai. Callback main thread par aata hai.
     * Page scroll karke har 1.5 second check karta hai, jab tak cards na mil jayein (max ~20 second).
     */
    fun loadCards(url: String, source: String, onResult: (List<Card>) -> Unit) {
        main.post {
            val wv = newWebView()
            var done = false
            var started = false
            var polls = 0

            fun finish(list: List<Card>) {
                if (done) return
                done = true
                release(wv)
                onResult(list)
            }

            fun poll() {
                if (done) return
                polls++
                wv.evaluateJavascript(scrollJs, null)
                wv.evaluateJavascript(cardJs) { raw ->
                    val list = parseCards(raw, source)
                    if (list.isNotEmpty() || polls >= 12) {
                        finish(list)
                    } else {
                        main.postDelayed({ poll() }, 1500)
                    }
                }
            }

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    if (!started) {
                        started = true
                        main.postDelayed({ poll() }, 1500)
                    }
                }
            }
            // Hard timeout
            main.postDelayed({ finish(emptyList()) }, 40000)
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
                release(wv)
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
            // evaluateJavascript ek JSON string deta hai, pehle unwrap karna padta hai
            val text = JSONArray("[$raw]").getString(0)
            val obj = JSONObject(text)
            val arr = obj.optJSONArray("c") ?: return emptyList()
            val list = mutableListOf<Card>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
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
