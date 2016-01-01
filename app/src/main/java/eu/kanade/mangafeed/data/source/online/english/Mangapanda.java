package eu.kanade.mangafeed.data.source.online.english;

import android.content.Context;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.source.SourceManager;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.data.source.model.MangasPage;

public class Mangapanda extends Source {
    String NAME = "MangaPanda (EN)";
    String BASE_URL = "http://www.mangapanda.com";
    String POP_MANGA_URL = BASE_URL + "/popular";
    String SEARCH_BASE_URL = BASE_URL + "/search/";

    public Mangapanda(Context context) {
        super(context);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getId() {
        return SourceManager.MANGAPANDA;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public boolean isLoginRequired() {
        return false;
    }

    @Override
    protected String getInitialPopularMangasUrl() {
        return POP_MANGA_URL;
    }

    @Override
    protected String getInitialSearchUrl(String query) {
        return SEARCH_BASE_URL + "?w=" + query;
    }

    @Override
    protected List<Manga> parsePopularMangasFromHtml(Document parsedHtml) {
        List<Manga> mangaList = new ArrayList<>();

        Elements mangaHtmlBlocks = parsedHtml.select("div.mangaresultitem");
        for (Element currentHtmlBlock : mangaHtmlBlocks) {
            Manga currentManga = constructMangaFromHtmlBlock(currentHtmlBlock);
            mangaList.add(currentManga);
        }

        return mangaList;
    }


    private Manga constructMangaFromHtmlBlock(Element htmlBlock) {
        Manga output = new Manga();
        Element title = htmlBlock.select("div.manga_name > div > h3 > a").first();
        output.title = title.text();
        output.url = title.attr("href");

        output.source = getId();

        Element thumb_block = htmlBlock.select("div.imgsearchresults").first();
        Pattern css_bkgd = Pattern.compile("background-image:url\\('(.*?)'\\)");
        Matcher thumb = css_bkgd.matcher(thumb_block.attr("style"));
        if (thumb.find()) {
            output.thumbnail_url = thumb.group(1);
            Log.i("Found thumb for %s", output.title);
        }

        Element genre = htmlBlock.select("div.manga_genre").first();
        output.genre = genre.text();

        Element author = htmlBlock.select("div.author_name").first();
        output.author = author.text().replace("(Store & Art)", "");


        return output;
    }

    @Override
    protected String parseNextPopularMangasUrl(Document parsedHtml, MangasPage page) {
        return getBaseUrl() + parsedHtml.select("div#sp > a:containsOwn(>)").first().attr("href");
    }

    @Override
    protected List<Manga> parseSearchFromHtml(Document parsedHtml) {
        List<Manga> mangaList = new ArrayList<>();

        Elements mangaHtmlBlocks = parsedHtml.select("div.mangaresultitem");
        for (Element currentHtmlBlock : mangaHtmlBlocks) {
            Manga currentManga = constructMangaFromHtmlBlock(currentHtmlBlock);
            mangaList.add(currentManga);
        }

        return mangaList;
    }

    @Override
    protected String parseNextSearchUrl(Document parsedHtml, MangasPage page, String query) {
        return getBaseUrl() + parsedHtml.select("div#sp > a:containsOwn(>)").first().attr("href");
    }

    @Override
    protected Manga parseHtmlToManga(String mangaUrl, String unparsedHtml) {
        Manga manga = new Manga();
        manga.url = mangaUrl;

        Document doc = Jsoup.parse(unparsedHtml);

        Element summary = doc.select("div#readmangasum > p").first();
        if (summary != null)
            manga.description = summary.text();

        Element title = doc.select("h2.aname").first();
        if (title != null)
            manga.title = title.text();

        Element author = doc.select("div#mangaproperties > table > tbody > tr:eq(4) > td:eq(1)").first();
        if (author != null) {
            manga.author = author.text();
            final String IS_BOTH = " (Story & Art)";
            if (manga.author.contains(IS_BOTH)) {
                manga.author = manga.artist = manga.author.replace(IS_BOTH, "");
            }
        }

        if (manga.artist == null) {
            Element artist = doc.select("div#mangaproperties > table > tbody > tr:eq(5) > td:eq(1)").first();
            if (artist != null) {
                manga.artist = artist.text();
            }
        }

        Element img = doc.select("#mangaimg > img").first();
        if (img != null)
            manga.thumbnail_url = img.attr("src");

        return manga;

    }

    @Override
    protected List<Chapter> parseHtmlToChapters(String unparsedHtml) {
        List<Chapter> chapters = new ArrayList<>();
        Elements chapter_blocks = Jsoup.parse(unparsedHtml)
                .select("table#listing tr:not(.table_head)");

        for (Element chapter_block : chapter_blocks) {
            chapters.add(constructChapterFromHtmlBlock(chapter_block));
        }
        return chapters;
    }

    private Chapter constructChapterFromHtmlBlock(Element htmlBlock) {
        Chapter out = new Chapter();
        out.url = htmlBlock.select("a").first().attr("href");
        out.name = htmlBlock.child(0).childNode(4).outerHtml().substring(" : ".length());

        String chap_num = out.url.substring(out.url.lastIndexOf("/") + 1);
        if (chap_num.length() > 0)
            out.chapter_number = Integer.parseInt(chap_num);
        else
            out.chapter_number = 1;

        try {
            Date date = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH).parse(
                    htmlBlock.child(1).text()
            );

            out.date_upload = date.getTime();
        } catch (ParseException e) {
            // Do Nothing.
        }

        out.date_fetch = new Date().getTime();
        return out;

    }

    @Override
    protected List<String> parseHtmlToPageUrls(String unparsedHtml) {
        Elements chap_drop_options = Jsoup.parse(unparsedHtml).select("select#pageMenu").first().children();
        List<String> out = new ArrayList<>();

        for (Element option : chap_drop_options) {
            out.add(getBaseUrl() + option.attr("value"));
        }
        return out;
    }

    @Override
    protected String parseHtmlToImageUrl(String unparsedHtml) {
        return Jsoup.parse(unparsedHtml).select("div#imgholder > a > img").attr("src");
    }
}
