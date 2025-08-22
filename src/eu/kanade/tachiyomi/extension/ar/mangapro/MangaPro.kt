package eu.kanade.tachiyomi.extension.ar.mangapro

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaPro : ParsedHttpSource() {

    override val name = "Manga Pro"
    override val baseUrl = "https://promanga.net"
    override val lang = "ar"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.series-card"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.selectFirst("h3.series-title")?.text() ?: ""
            setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
            thumbnail_url = element.selectFirst("img")?.attr("src")
        }
    }

    override fun popularMangaNextPageSelector() = "a.next"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/series?sort=latest&page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/?s=$query&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.selectFirst("h1.entry-title")?.text() ?: ""
            author = document.select("span.author").text()
            genre = document.select("div.genres a").joinToString { it.text() }
            description = document.selectFirst("div.description")?.text()
            status = SManga.UNKNOWN
            thumbnail_url = document.selectFirst("div.thumb img")?.attr("src")
        }
    }

    override fun chapterListSelector() = "ul.chapter-list li a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.text()
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-area img").mapIndexed { index, element ->
            Page(index, "", element.attr("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = document.location()
}