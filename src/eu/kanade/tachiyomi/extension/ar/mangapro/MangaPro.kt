package eu.kanade.tachiyomi.extension.ar.mangapro

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaPro : ParsedHttpSource() {

    override val name = "MangaPro (ProManga)"
    override val baseUrl = "https://promanga.net"
    override val lang = "ar"
    override val supportsLatest = true

    /* =================== Browsing =================== */

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaSelector(): String =
        "a[href*='/series/']"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val href = element.attr("abs:href")
        if (!href.contains("/series/")) throw Exception("skip")
        if (href.contains("/chapter-")) throw Exception("skip-chapter")
        setUrlWithoutDomain(href)
        title = element.text().ifBlank { element.attr("title").ifBlank { href.substringAfterLast("/") } }
        thumbnail_url = element.selectFirst("img[src]")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector(): String =
        "a[href*='page='], nav a:contains(التالي), .pagination a:contains(التالي)"

    /* =================== Latest =================== */

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    /* =================== Search =================== */

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        popularMangaRequest(page)

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun searchMangaParse(document: Document): MangasPage {
        val all = document.select(searchMangaSelector())
            .mapNotNull {
                runCatching { searchMangaFromElement(it) }.getOrNull()
            }
            .distinctBy { it.url }
        val hasNext = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(all, hasNext)
    }

    /* =================== Details =================== */

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1, h2:matchesOwn(^\\s*[^\\n]+)")
            ?.text()?.trim().orEmpty()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("img[src*='/uploads/'], .series img[src]")?.absUrl("src")
        author = document.select("div:matchesOwn(الكاتب|المؤلف) + *").firstOrNull()?.text()
        artist = document.select("div:matchesOwn(الرسام) + *").firstOrNull()?.text()
        genre = document.select("a[href*='/genres/']").joinToString { it.text() }
        status = when {
            document.text().contains(Regex("مكتمل|منتهي")) -> SManga.COMPLETED
            document.text().contains(Regex("مستمر")) -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = document.selectFirst("meta[name=description]")?.attr("content")
            ?: document.selectFirst("p, .description")?.text()
    }

    /* =================== Chapters =================== */

    override fun chapterListSelector(): String = "a[href*='/chapter-']"

    private val dateFormats = arrayOf(
        SimpleDateFormat("d MMM yyyy", Locale("ar")),
        SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
    )

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val href = element.attr("abs:href")
        setUrlWithoutDomain(href)
        val text = element.text().ifBlank { href.substringAfterLast("/") }
        name = text.trim()
        val dateText = element.parent()?.ownText()?.trim().orEmpty()
        date_upload = parseDate(dateText)
    }

    private fun parseDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        for (f in dateFormats) {
            runCatching { return f.parse(raw)?.time ?: 0L }.getOrNull()
        }
        return 0L
    }

    /* =================== Pages =================== */

    override fun pageListParse(document: Document): List<Page> {
        val imgs = document.select("img[src*='uploads'], main img[src], .reader img[src]")
        return imgs.mapIndexed { i, el ->
            Page(i, document.location(), el.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document): String =
        document.selectFirst("img")!!.absUrl("src")

    /* =================== Requests =================== */

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
}
