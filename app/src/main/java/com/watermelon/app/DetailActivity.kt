package com.watermelon.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class DetailActivity : AppCompatActivity() {

    private lateinit var web: WebScraper
    private lateinit var loader: EpisodeLoader
    private lateinit var list: ListView
    private lateinit var epAdapter: ArrayAdapter<String>
    private lateinit var seasonBtn: Button
    private lateinit var statusText: TextView
    private lateinit var posterImg: ImageView
    private lateinit var titleText: TextView
    private lateinit var infoText: TextView
    private lateinit var genresText: TextView
    private lateinit var synopsisText: TextView

    private var allEps: List<Episode> = emptyList()
    private var seasons: List<String> = emptyList()
    private var selectedSeason: String? = null
    private var shownEps: List<Episode> = emptyList()
    private var trailerId: String? = null
    private lateinit var pageLink: String
    private lateinit var pageTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        pageLink = intent.getStringExtra(EXTRA_LINK) ?: run {
            finish()
            return
        }
        pageTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        web = WebScraper(this)
        loader = EpisodeLoader(this)

        list = findViewById(R.id.episodeList)
        val header = layoutInflater.inflate(R.layout.detail_header, list, false)
        list.addHeaderView(header, null, false)

        posterImg = header.findViewById(R.id.posterImg)
        titleText = header.findViewById(R.id.titleText)
        infoText = header.findViewById(R.id.infoText)
        genresText = header.findViewById(R.id.genresText)
        synopsisText = header.findViewById(R.id.synopsisText)
        statusText = header.findViewById(R.id.statusText)
        seasonBtn = header.findViewById(R.id.seasonBtn)

        titleText.text = pageTitle
        val image = intent.getStringExtra(EXTRA_IMAGE)
        if (!image.isNullOrEmpty()) Glide.with(this).load(image).centerCrop().into(posterImg)

        header.findViewById<Button>(R.id.playBtn).setOnClickListener { playLink(pageLink) }
        header.findViewById<Button>(R.id.downloadBtn).setOnClickListener { downloadPage(pageLink) }
        header.findViewById<Button>(R.id.saveBtn).setOnClickListener { pickTag() }
        header.findViewById<Button>(R.id.trailerBtn).setOnClickListener {
            val id = trailerId
            if (id == null) {
                Toast.makeText(this, "Trailer nahi mila", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")))
            }
        }
        seasonBtn.setOnClickListener { pickSeason() }

        epAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        list.adapter = epAdapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val idx = pos - list.headerViewsCount
            val ep = shownEps.getOrNull(idx) ?: return@setOnItemClickListener
            showLinks(ep)
        }

        loadMeta()
        loadEpisodes()
    }

    // ---------- Metadata ----------

    private fun loadMeta() {
        Metadata.fetchAsync(Metadata.cleanTitle(pageTitle)) { meta ->
            if (meta == null) return@fetchAsync
            trailerId = meta.trailerId
            titleText.text = meta.title
            infoText.text = "${meta.format} | ${meta.year} | ${meta.episodes} episodes | Rating ${meta.score}"
            genresText.text = meta.genres.joinToString(" | ")
            synopsisText.text = meta.synopsis
            if (meta.poster.isNotEmpty()) {
                Glide.with(this).load(meta.poster).centerCrop().into(posterImg)
            }
        }
    }

    // ---------- Episodes / seasons ----------

    private fun loadEpisodes() {
        val key = "eps2:$pageLink"
        val hit = Cache.get<List<Episode>>(key)
        if (hit != null) {
            showEpisodes(hit)
            return
        }
        statusText.text = "Episodes load ho rahe hain..."
        loader.load(pageLink) { found ->
            if (found.isNotEmpty()) Cache.put(key, found)
            showEpisodes(found)
        }
    }

    private fun showEpisodes(found: List<Episode>) {
        allEps = found
        seasons = found.map { it.season }.distinct()
        if (selectedSeason == null || selectedSeason !in seasons) {
            selectedSeason = seasons.firstOrNull()
        }
        refreshEpisodes()
    }

    private fun refreshEpisodes() {
        shownEps = allEps.filter { it.season == selectedSeason }.sortedBy { it.number }
        epAdapter.clear()
        epAdapter.addAll(shownEps.map { "E${it.number}  ${it.title}  (${it.links.size} links)" })
        epAdapter.notifyDataSetChanged()
        seasonBtn.text = "Season: ${selectedSeason ?: "-"}"
        statusText.text = if (allEps.isEmpty()) {
            "Episodes nahi mile. Play button dabayein"
        } else {
            "${shownEps.size} episodes"
        }
    }

    private fun pickSeason() {
        if (seasons.isEmpty()) {
            Toast.makeText(this, "Koi season nahi mila", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Season chunein")
            .setItems(seasons.toTypedArray()) { _, which ->
                selectedSeason = seasons[which]
                refreshEpisodes()
            }
            .show()
    }

    /** Ek episode ke sabhi links dikhata hai (quality ya server). */
    private fun showLinks(ep: Episode) {
        val labels = ep.links.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("E${ep.number}: quality / server chunein")
            .setItems(labels) { _, which -> playLink(ep.links[which].url) }
            .show()
    }

    // ---------- Play / download ----------

    private fun isMedia(u: String): Boolean {
        val l = u.lowercase()
        return l.contains(".m3u8") || l.contains(".mp4") || l.contains(".mkv") || l.contains(".webm")
    }

    private fun openPlayer(url: String) {
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, url))
    }

    private fun playLink(link: String) {
        if (isMedia(link)) {
            openPlayer(link)
            return
        }
        Toast.makeText(this, "Video dhoondh raha hoon...", Toast.LENGTH_SHORT).show()
        web.sniffMedia(link) { url ->
            if (url == null) {
                Toast.makeText(this, "Video link nahi mila, browser me kholte hain", Toast.LENGTH_LONG).show()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            } else {
                openPlayer(url)
            }
        }
    }

    private fun downloadPage(link: String) {
        Toast.makeText(this, "Video dhoondh raha hoon...", Toast.LENGTH_SHORT).show()
        web.sniffMedia(link) { url ->
            if (url == null) {
                Toast.makeText(this, "Video link nahi mila", Toast.LENGTH_SHORT).show()
                return@sniffMedia
            }
            if (url.contains(".m3u8", ignoreCase = true)) {
                Toast.makeText(this, "M3U8 (HLS) download abhi supported nahi hai", Toast.LENGTH_LONG).show()
                return@sniffMedia
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
    }

    private fun pickTag() {
        val tags = LibraryStore.TAGS.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tag chunein")
            .setItems(tags) { _, which ->
                LibraryStore.put(this, LibItem(pageLink, pageTitle, tags[which]))
                Toast.makeText(this, "Saved: ${tags[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    companion object {
        const val EXTRA_LINK = "link"
        const val EXTRA_TITLE = "title"
        const val EXTRA_IMAGE = "image"
    }
}
