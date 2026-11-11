package com.watermelon.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var grid: RecyclerView
    private lateinit var adapter: CardAdapter
    private lateinit var banner: TextView
    private lateinit var status: TextView
    private lateinit var web: WebScraper
    private var updateApkUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = WebScraper(this)
        banner = findViewById(R.id.updateBanner)
        status = findViewById(R.id.statusText)
        grid = findViewById(R.id.resultsGrid)
        adapter = CardAdapter { card -> openDetail(card.link, card.title, card.image) }
        grid.layoutManager = GridLayoutManager(this, 3)
        grid.adapter = adapter

        findViewById<Button>(R.id.searchBtn).setOnClickListener { runSearch() }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { runSearch() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<Button>(R.id.btnLibrary).setOnClickListener { showLibrary() }
        findViewById<Button>(R.id.btnPlayer).setOnClickListener { askStreamUrl() }
        findViewById<Button>(R.id.btnDownload).setOnClickListener { askDownloadUrl() }
        findViewById<Button>(R.id.scraperChip).setOnClickListener { pickScraper() }
        banner.setOnClickListener { openUpdate() }

        handleIntent(intent)
        refreshChip()
        loadHome()
        checkUpdate(manual = false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "wm") addScraperFromLink(data.toString())
    }

    private fun refreshChip() {
        val active = ScraperStore.activeScrapers(this)
        val sel = ScraperStore.selectedName(this)
        val label = when {
            active.isEmpty() -> "Scraper: none"
            active.size == 1 -> "Scraper: ${active[0].name}"
            else -> "Scraper: ${sel ?: active[0].name} (${active.size} on)"
        }
        findViewById<Button>(R.id.scraperChip).text = label
    }

    /** Cards cache se (30 min) ya web se load karta hai. */
    private fun cachedCards(url: String, source: String, cb: (List<Card>) -> Unit) {
        val key = "cards:$url"
        val hit = Cache.get<List<Card>>(key)
        if (hit != null) {
            cb(hit)
            return
        }
        web.loadCards(url, source) { list ->
            if (list.isNotEmpty()) Cache.put(key, list)
            cb(list)
        }
    }

    // ---------- Home ----------

    private fun loadHome() {
        val active = ScraperStore.activeScrapers(this)
        if (active.isEmpty()) {
            adapter.setItems(emptyList())
            status.text = "Settings me scraper add karein"
            return
        }
        val selected = ScraperStore.selectedName(this)
        val target = active.firstOrNull { it.name == selected } ?: active[0]
        status.text = "Loading ${target.name}..."
        cachedCards(target.baseUrl, target.name) { list ->
            adapter.setItems(list)
            status.text = if (list.isEmpty()) "${target.name}: content nahi mila" else "${target.name}: ${list.size} items"
        }
    }

    // ---------- Search ----------

    private fun runSearch() {
        val query = findViewById<EditText>(R.id.searchInput).text.toString().trim()
        if (query.isEmpty()) return
        val active = ScraperStore.activeScrapers(this)
        if (active.isEmpty()) {
            Toast.makeText(this, "Settings me scraper add karein", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = ScraperStore.selectedName(this)
        val targets = if (active.size > 1 && selected != null) {
            active.filter { it.name == selected }
        } else {
            active
        }

        val collected = mutableListOf<Card>()
        var pending = targets.size
        adapter.setItems(emptyList())
        status.text = "Searching \"$query\"..."

        for (s in targets) {
            val url = s.baseUrl.trimEnd('/') + s.searchPath.replace("{q}", Uri.encode(query))
            cachedCards(url, s.name) { list ->
                collected.addAll(list)
                pending--
                adapter.setItems(collected.toList())
                status.text = when {
                    pending > 0 -> "Searching... ${collected.size} mile"
                    collected.isEmpty() -> "\"$query\": kuch nahi mila"
                    else -> "\"$query\": ${collected.size} results"
                }
            }
        }
    }

    // ---------- Detail / Library ----------

    private fun openDetail(link: String, title: String, image: String) {
        startActivity(
            Intent(this, DetailActivity::class.java)
                .putExtra(DetailActivity.EXTRA_LINK, link)
                .putExtra(DetailActivity.EXTRA_TITLE, title)
                .putExtra(DetailActivity.EXTRA_IMAGE, image)
        )
    }

    private fun showLibrary() {
        val tags = LibraryStore.TAGS.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Saved / Library")
            .setItems(tags) { _, which ->
                val items = LibraryStore.all(this).filter { it.tag == tags[which] }
                if (items.isEmpty()) {
                    Toast.makeText(this, "${tags[which]} me kuch nahi hai", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(tags[which])
                        .setItems(items.map { it.title }.toTypedArray()) { _, idx ->
                            openDetail(items[idx].url, items[idx].title, "")
                        }
                        .show()
                }
            }
            .show()
    }

    // ---------- Direct download / stream ----------

    private fun askDownloadUrl() {
        val input = EditText(this).apply { hint = "Direct video link (mp4/mkv/webm)" }
        AlertDialog.Builder(this)
            .setTitle("Download")
            .setView(input)
            .setPositiveButton("Download") { _, _ -> directDownload(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun directDownload(url: String) {
        if (url.isEmpty()) return
        if (url.contains(".m3u8", ignoreCase = true)) {
            Toast.makeText(this, "M3U8 (HLS) download abhi supported nahi hai", Toast.LENGTH_LONG).show()
            return
        }
        val fileName = Uri.parse(url).lastPathSegment ?: "watermelon_video.mp4"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(this, "Download shuru: $fileName", Toast.LENGTH_SHORT).show()
    }

    private fun openPlayer(url: String, sub: String?) {
        val i = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_URL, url)
        if (!sub.isNullOrEmpty()) i.putExtra(PlayerActivity.EXTRA_SUB, sub)
        startActivity(i)
    }

    private fun askStreamUrl() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val streamInput = EditText(this).apply { hint = "Stream link (m3u8 / mp4)" }
        val subInput = EditText(this).apply {
            hint = "Subtitle link (.srt/.vtt, optional)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        container.addView(streamInput)
        container.addView(subInput)
        AlertDialog.Builder(this)
            .setTitle("M3U8 / Network stream")
            .setView(container)
            .setPositiveButton("Play") { _, _ ->
                val sub = subInput.text.toString().trim()
                openPlayer(streamInput.text.toString().trim(), if (sub.isEmpty()) null else sub)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Scrapers / settings ----------

    private fun pickScraper() {
        val active = ScraperStore.activeScrapers(this)
        if (active.isEmpty()) {
            Toast.makeText(this, "Koi active scraper nahi hai", Toast.LENGTH_SHORT).show()
            return
        }
        val names = active.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Home scraper")
            .setItems(names) { _, which ->
                ScraperStore.setSelected(this, active[which].name)
                refreshChip()
                loadHome()
            }
            .show()
    }

    private fun showSettings() {
        val options = arrayOf("Add scraper (paste Melon link)", "Manage scrapers (on/off, delete)", "Check for update", "About")
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pasteMelonLink()
                    1 -> manageScrapers()
                    2 -> checkUpdate(manual = true)
                    3 -> Toast.makeText(this, "Watermelon v1.3.0", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun pasteMelonLink() {
        val input = EditText(this).apply {
            hint = "wm://add?cfg=..."
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Melon Codes link paste karein")
            .setView(input)
            .setPositiveButton("Add") { _, _ -> addScraperFromLink(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addScraperFromLink(link: String) {
        val s = ScraperStore.parseMelonLink(link)
        if (s == null) {
            Toast.makeText(this, "Link galat hai", Toast.LENGTH_SHORT).show()
            return
        }
        ScraperStore.add(this, s)
        if (ScraperStore.selectedName(this) == null) ScraperStore.setSelected(this, s.name)
        refreshChip()
        loadHome()
        Toast.makeText(this, "Scraper added: ${s.name}", Toast.LENGTH_SHORT).show()
    }

    private fun manageScrapers() {
        val list = ScraperStore.all(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "Koi scraper nahi", Toast.LENGTH_SHORT).show()
            return
        }
        val names = list.map { (if (it.enabled) "[ON] " else "[OFF] ") + it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Scrapers (tap to on/off)")
            .setItems(names) { _, which ->
                val s = list[which]
                s.enabled = !s.enabled
                ScraperStore.save(this, list)
                refreshChip()
                loadHome()
                manageScrapers()
            }
            .setNeutralButton("Delete last") { _, _ ->
                ScraperStore.remove(this, list.last().name)
                refreshChip()
                loadHome()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ---------- Update ----------

    private fun checkUpdate(manual: Boolean) {
        Thread {
            val text = Net.get(Net.UPDATE_URL)
            var newCode = 0
            var newName = ""
            var apk = ""
            if (text != null) {
                try {
                    val j = JSONObject(text)
                    newCode = j.getInt("versionCode")
                    newName = j.optString("versionName")
                    apk = j.optString("apkUrl")
                } catch (e: Exception) {
                    // bad json, ignore
                }
            }
            val current = packageManager.getPackageInfo(packageName, 0).versionCode
            runOnUiThread {
                if (text == null) {
                    if (manual) Toast.makeText(this, "Update check fail: internet dekhein", Toast.LENGTH_SHORT).show()
                } else if (newCode > current && apk.isNotEmpty()) {
                    updateApkUrl = apk
                    banner.text = "Naya version $newName available. Yahan tap karke update karein"
                    banner.visibility = View.VISIBLE
                    if (manual) openUpdate()
                } else {
                    banner.visibility = View.GONE
                    if (manual) Toast.makeText(this, "Aap latest version par hain", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun openUpdate() {
        val url = updateApkUrl ?: return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
