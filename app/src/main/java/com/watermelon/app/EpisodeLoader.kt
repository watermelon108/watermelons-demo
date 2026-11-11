package com.watermelon.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray

data class EpLink(val label: String, val url: String)

data class Episode(
    val season: String,
    val number: Int,
    val title: String,
    val links: List<EpLink>
)

/**
 * Page se seasons, episodes aur har episode ke links nikalta hai.
 * Generic heuristics par chalta hai: "Season N", "Episode N" text aur uske neeche ke links.
 */
class EpisodeLoader(private val activity: Activity) {

    private val main = Handler(Looper.getMainLooper())

    private val js = """
        (function () {
            var seasonRe = /season\s*(\d{1,2})/i;
            var epRe = /(?:episode|ep)\.?\s*:?\s*(\d{1,4})/i;
            var qRe = /\b(2160|1440|1080|720|480|360)\s*p\b/i;
            var season = 'Season 1';
            var eps = [];
            var map = {};
            var cur = null;

            function getEp(num, title) {
                var key = season + '|' + num;
                if (!map[key]) {
                    map[key] = { season: season, number: num, title: title, links: [] };
                    eps.push(map[key]);
                }
                return map[key];
            }

            var els = document.querySelectorAll('h1,h2,h3,h4,h5,h6,p,span,strong,b,a');
            for (var i = 0; i < els.length; i++) {
                var el = els[i];
                if (el.tagName !== 'A' && el.children.length > 0) continue;
                var text = (el.innerText || '').replace(/\s+/g, ' ').trim();
                if (!text || text.length > 200) continue;

                if (el.tagName !== 'A') {
                    var sm = seasonRe.exec(text);
                    if (sm && text.length < 40) { season = 'Season ' + sm[1]; cur = null; continue; }
                    var em = epRe.exec(text);
                    if (em && text.length < 80) { cur = getEp(parseInt(em[1], 10), text); }
                    continue;
                }

                var href = el.href;
                if (!href || href.indexOf('http') !== 0) continue;
                var em2 = epRe.exec(text);
                if (em2) { cur = getEp(parseInt(em2[1], 10), text); }
                if (!cur) continue;
                var q = qRe.exec(text);
                var label = q ? (q[1] + 'p') : (text || 'Link');
                var dup = false;
                for (var k = 0; k < cur.links.length; k++) {
                    if (cur.links[k].url === href) dup = true;
                }
                if (!dup) cur.links.push({ label: label, url: href });
            }
            return JSON.stringify(eps.slice(0, 500));
        })()
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    fun load(url: String, cb: (List<Episode>) -> Unit) {
        main.post {
            val wv = WebView(activity)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            val host = activity.findViewById<ViewGroup>(android.R.id.content)
            host.addView(wv, FrameLayout.LayoutParams(720, 1280))
            wv.translationX = -10000f

            var done = false
            var polls = 0

            fun finish(list: List<Episode>) {
                if (done) return
                done = true
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.destroy()
                cb(list)
            }

            fun poll() {
                if (done) return
                polls++
                wv.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);", null)
                wv.evaluateJavascript(js) { raw ->
                    val list = parse(raw)
                    if (list.isNotEmpty() || polls >= 12) {
                        finish(list)
                    } else {
                        main.postDelayed({ poll() }, 1500)
                    }
                }
            }

            wv.webViewClient = object : WebViewClient() {
                private var started = false
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    if (!started) {
                        started = true
                        main.postDelayed({ poll() }, 1500)
                    }
                }
            }
            main.postDelayed({ finish(emptyList()) }, 40000)
            wv.loadUrl(url)
        }
    }

    private fun parse(raw: String?): List<Episode> {
        if (raw == null || raw == "null") return emptyList()
        return try {
            val text = JSONArray("[$raw]").getString(0)
            val arr = JSONArray(text)
            val out = mutableListOf<Episode>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val la = o.optJSONArray("links") ?: JSONArray()
                val links = mutableListOf<EpLink>()
                for (j in 0 until la.length()) {
                    val l = la.getJSONObject(j)
                    links.add(EpLink(l.optString("label"), l.optString("url")))
                }
                if (links.isEmpty()) continue
                out.add(
                    Episode(
                        o.optString("season"),
                        o.optInt("number"),
                        o.optString("title"),
                        links
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
