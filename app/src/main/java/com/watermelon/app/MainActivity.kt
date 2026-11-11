package com.watermelon.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var results: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val resultUrls = mutableListOf<String>()
    private val libraryTags = listOf("Watching", "Completed", "On Hold", "Dropped", "Plan to Watch", "Favorites")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        results = findViewById(R.id.resultsList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        results.adapter = adapter

        findViewById<Button>(R.id.searchBtn).setOnClickListener { runSearch() }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { runSearch() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<Button>(R.id.btnLibrary).setOnClickListener { showLibrary() }
        findViewById<Button>(R.id.btnPlayer).setOnClickListener { askStreamUrl() }
        findViewById<Button>(R.id.btnDownload).setOnClickListener {
            Toast.makeText(this, "Downloader: coming in next update", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.scraperChip).setOnClickListener { pickScraper() }

        results.setOnItemClickListener { _, _, pos, _ ->
            val url = resultUrls.getOrNull(pos) ?: return@setOnItemClickListener
            startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, url))
        }

        handleIntent(intent)
        refreshChip()
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

        resultUrls.clear()
        adapter.clear()
        for (s in targets) {
            val url = s.baseUrl.trimEnd('/') + s.searchPath.replace("{q}", Uri.encode(query))
            resultUrls.add(url)
            adapter.add("$query\n[${s.name}]")
        }
        adapter.notifyDataSetChanged()
    }

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
                    2 -> checkUpdate()
                    3 -> Toast.makeText(this, "Watermelon v1.0.0", Toast.LENGTH_SHORT).show()
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
                manageScrapers()
            }
            .setNeutralButton("Delete last") { _, _ ->
                ScraperStore.remove(this, list.last().name)
                refreshChip()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun checkUpdate() {
        // Placeholder: point this at your update JSON (e.g. GitHub releases API) later.
        Toast.makeText(this, "Aap latest version par hain (v1.0.0)", Toast.LENGTH_LONG).show()
    }

    private fun showLibrary() {
        AlertDialog.Builder(this)
            .setTitle("Saved / Library")
            .setItems(libraryTags.toTypedArray()) { _, which ->
                Toast.makeText(this, "${libraryTags[which]} list: next update me save hogi", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun askStreamUrl() {
        val input = EditText(this).apply { hint = "https://...m3u8 ya video link" }
        AlertDialog.Builder(this)
            .setTitle("M3U8 / Network stream")
            .setView(input)
            .setPositiveButton("Play") { _, _ ->
                startActivity(
                    Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_URL, input.text.toString().trim())
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
