# Watermelon (v1.0.0 starter)

## Open and run
1. Android Studio me "Open" karke ye folder select karein.
2. Gradle sync hone dein, phir Run dabayein (Android 7.0+).
3. Launcher icon: Android Studio me `res > drawable > watermelon_icon.jpg` ko
   "Image Asset" se mipmap icons me convert karein (Right click res > New > Image Asset).

## Melon Codes link format
Melon Codes app is format me link banayega:
    wm://add?cfg=<base64url JSON>
JSON: {"name":"SiteName","baseUrl":"https://site.com","searchPath":"/search?q={q}"}
Watermelon me Settings > Add scraper me ye link paste karein.

## Included now
- Media3 (ExoPlayer) player: normal files, network streams, M3U8/HLS
- Audio language and subtitle track selection (player settings icon)
- Multi-scraper add, on/off, delete; home scraper selection; results show scraper name
- Bottom control row: Download, Saved, M3U, Search, Settings

## Pending (next updates)
- Real downloader (high quality) and saving library data (Watching/Completed/etc.)
- Actual page scraping/parsing (currently builds search URLs and opens them)
- Subtitle file loading (.srt/.vtt) and FFmpeg extension for extra codecs
- Online update check (point checkUpdate() to your update JSON)

## Legal note
Copyrighted content ki legal zimmedari aap ki hai. Sirf wahi sources use karein jinke liye aapke paas rights hain.
