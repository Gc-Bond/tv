package com.gc.wxjswtv;

import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 西瓜影视网站数据解析器。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 19:20:00</p>
 *
 * <p>说明：集中处理 RSS、首页、分类页、详情页和播放页 HTML 解析，让 Activity 只负责页面编排和控件交互。
 * 解析规则仍保持原有正则策略，避免一次拆分同时引入行为变化。</p>
 */
class WxjswDataParser {
    private static final String TAG = "WxjswTvShell";
    private static final String CATEGORY_MOVIE_URL = "https://www.wxjsw.com/vodtype/dianying.html";
    private static final String CATEGORY_TV_URL = "https://www.wxjsw.com/vodtype/dianshiju.html";
    private static final String CATEGORY_VARIETY_URL = "https://www.wxjsw.com/vodtype/zongyi.html";
    private static final String CATEGORY_ANIME_URL = "https://www.wxjsw.com/vodtype/dongman.html";

    private WxjswDataParser() {
    }

    static boolean isWxjswUrl(String url) {
        return url != null && (url.startsWith("https://wxjsw.com/") || url.startsWith("https://www.wxjsw.com/"));
    }

    static boolean isMainCategoryUrl(String url) {
        return url != null
                && (url.contains("/vodtype/dianying")
                || url.contains("/vodtype/dianshiju")
                || url.contains("/vodtype/zongyi")
                || url.contains("/vodtype/dongman")
                || url.contains("/vodshow/dianying")
                || url.contains("/vodshow/dianshiju")
                || url.contains("/vodshow/zongyi")
                || url.contains("/vodshow/dongman")
                || url.contains("/vodsearch/"));
    }

    static List<VideoItem> parseRss(String xml) throws Exception {
        List<VideoItem> items = new ArrayList<VideoItem>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        VideoItem current = null;
        String currentTag = null;
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                currentTag = parser.getName();
                if ("item".equals(currentTag)) {
                    current = new VideoItem();
                }
            } else if (eventType == XmlPullParser.TEXT && current != null && currentTag != null) {
                String text = parser.getText();
                if ("title".equals(currentTag)) {
                    current.title = text;
                } else if ("link".equals(currentTag)) {
                    current.link = text;
                } else if ("description".equals(currentTag)) {
                    current.description = cleanText(text);
                } else if ("pubDate".equals(currentTag)) {
                    current.pubDate = text;
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                String endTag = parser.getName();
                if ("item".equals(endTag) && current != null) {
                    if (!TextUtils.isEmpty(current.title) && !TextUtils.isEmpty(current.link)) {
                        items.add(current);
                    }
                    current = null;
                    if (items.size() >= 80) {
                        break;
                    }
                }
                currentTag = null;
            }
            eventType = parser.next();
        }
        Log.i(TAG, "======>>>>>>【RSS 解析完成，count=" + items.size() + "】<<<<<<======");
        return items;
    }

    static List<VideoCategory> parseHomeCategories(String html) {
        List<VideoCategory> categories = new ArrayList<VideoCategory>();
        if (TextUtils.isEmpty(html)) {
            return categories;
        }
        Pattern titlePattern = Pattern.compile("<h2[^>]*class=[\"'][^\"']*title[^\"']*[\"'][^>]*>(.*?)</h2>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = titlePattern.matcher(html);
        List<String> names = new ArrayList<String>();
        List<Integer> starts = new ArrayList<Integer>();
        while (matcher.find()) {
            String name = parseHomeCategoryName(matcher.group(1));
            if (!TextUtils.isEmpty(name)) {
                names.add(name);
                starts.add(matcher.end());
            }
        }
        for (int i = 0; i < names.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : html.length();
            if (start >= end) {
                continue;
            }
            VideoCategory category = new VideoCategory();
            category.name = names.get(i);
            category.url = getMainCategoryUrl(category.name);
            category.items = parseHomeItems(html.substring(start, end), category.name);
            if (!category.items.isEmpty()) {
                categories.add(category);
            }
        }
        Log.i(TAG, "======>>>>>>【首页 HTML 分类解析完成，categoryCount=" + categories.size() + "】<<<<<<======");
        return categories;
    }

    static VideoCategory parseCategoryPage(String url, String html, String pendingCategoryName, String pendingSearchKeyword) {
        VideoCategory category = new VideoCategory();
        category.name = TextUtils.isEmpty(pendingCategoryName) ? parseCategoryNameFromUrl(url) : pendingCategoryName;
        category.searchResult = url != null && url.contains("/vodsearch/");
        if (category.searchResult && !TextUtils.isEmpty(pendingSearchKeyword)) {
            category.name = "搜索：" + pendingSearchKeyword;
        }
        category.url = url;
        category.items = parseHomeItems(html, category.name);
        category.pageInfo = parseCategoryPageInfo(html);
        parseCategoryPagination(html, category);
        category.filters = parseCategoryFilters(html);
        Log.i(TAG, "======>>>>>>【网站分类页解析完成，category=" + category.name
                + "，count=" + category.items.size()
                + "，filterCount=" + category.filters.size()
                + "，pageInfo=" + category.pageInfo
                + "，currentPage=" + category.currentPage
                + "，totalPage=" + category.totalPage + "】<<<<<<======");
        return category;
    }

    static List<EpisodeItem> parseEpisodes(String html) {
        List<EpisodeItem> episodes = new ArrayList<EpisodeItem>();
        if (TextUtils.isEmpty(html)) {
            return episodes;
        }
        Pattern pattern = Pattern.compile("<a[^>]+href=[\"']([^\"']*/vodplay/[^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String url = normalizeWxjswUrl(matcher.group(1));
            String name = cleanHtmlText(matcher.group(2));
            name = normalizeEpisodeName(name, url);
            if (TextUtils.isEmpty(url) || TextUtils.isEmpty(name) || hasEpisodeUrl(episodes, url)) {
                continue;
            }
            EpisodeItem item = new EpisodeItem();
            item.name = name;
            item.url = url;
            episodes.add(item);
        }
        Log.i(TAG, "======>>>>>>【详情 HTML 集数解析完成，count=" + episodes.size() + "】<<<<<<======");
        return episodes;
    }

    /**
     * 解析网站详情页里的影片信息。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:46:06</p>
     *
     * <p>说明：详情页顶部区域包含封面、年份、地区、类型、状态、主演和导演，底部“剧情介绍”区域包含更完整的简介。
     * 这里优先取详情页结构化内容，只有简介为空时才回退到 meta description，避免电视端继续展示“详情数据来自”这类占位文案。</p>
     *
     * @param html 网站详情页 HTML。
     * @return 解析出的详情信息；HTML 为空时返回字段为空的对象。
     */
    static VideoDetailInfo parseDetailInfo(String html) {
        VideoDetailInfo detailInfo = new VideoDetailInfo();
        if (TextUtils.isEmpty(html)) {
            return detailInfo;
        }
        detailInfo.title = parseFirstCleanText(html, "<h2[^>]*class=[\"'][^\"']*title[^\"']*[\"'][^>]*>(.*?)</h2>");
        detailInfo.posterUrl = parseDetailPosterUrl(html);
        detailInfo.year = parseDetailInlineValue(html, "年份");
        detailInfo.area = parseDetailInlineValue(html, "地区");
        detailInfo.type = parseDetailInlineValue(html, "类型");
        detailInfo.status = parseDetailStatus(html);
        detailInfo.updateDate = parseDetailUpdateDate(html);
        detailInfo.actors = parseDetailListValue(html, "主演");
        detailInfo.director = parseDetailListValue(html, "导演");
        detailInfo.description = parseDetailDescription(html);
        Log.i(TAG, "======>>>>>>【详情信息解析完成，title=" + detailInfo.title
                + "，poster=" + detailInfo.posterUrl
                + "，year=" + detailInfo.year
                + "，area=" + detailInfo.area
                + "，type=" + detailInfo.type
                + "，status=" + detailInfo.status + "】<<<<<<======");
        return detailInfo;
    }

    static String parseVideoUrl(String html) {
        if (TextUtils.isEmpty(html)) {
            return "";
        }
        Pattern playerPattern = Pattern.compile("player_aaaa\\s*=\\s*\\{(.*?)\\}</script>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher playerMatcher = playerPattern.matcher(html);
        String playerConfig = playerMatcher.find() ? playerMatcher.group(1) : html;
        Pattern urlPattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher urlMatcher = urlPattern.matcher(playerConfig);
        if (!urlMatcher.find()) {
            return "";
        }
        return unescapeJsonUrl(urlMatcher.group(1));
    }

    static List<VideoCategory> mergeHomeCategoryMetadata(List<VideoItem> rssItems, List<VideoCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return new ArrayList<VideoCategory>();
        }
        if (rssItems == null || rssItems.isEmpty()) {
            return categories;
        }
        for (VideoCategory category : categories) {
            if (category == null || category.items == null) {
                continue;
            }
            for (VideoItem homeItem : category.items) {
                VideoItem rssItem = findVideoByLink(rssItems, homeItem.link);
                if (rssItem == null) {
                    continue;
                }
                homeItem.pubDate = rssItem.pubDate;
                homeItem.description = rssItem.description;
            }
        }
        return categories;
    }

    private static String getMainCategoryUrl(String categoryName) {
        if ("电影".equals(categoryName)) {
            return CATEGORY_MOVIE_URL;
        }
        if ("电视剧".equals(categoryName)) {
            return CATEGORY_TV_URL;
        }
        if ("综艺".equals(categoryName)) {
            return CATEGORY_VARIETY_URL;
        }
        if ("动漫".equals(categoryName)) {
            return CATEGORY_ANIME_URL;
        }
        return "";
    }

    private static String parseCategoryNameFromUrl(String url) {
        if (url == null) {
            return "分类";
        }
        if (url.contains("dianying")) {
            return "电影";
        }
        if (url.contains("dianshiju")) {
            return "电视剧";
        }
        if (url.contains("zongyi")) {
            return "综艺";
        }
        if (url.contains("dongman")) {
            return "动漫";
        }
        if (url.contains("/vodsearch/")) {
            return "搜索结果";
        }
        return "分类";
    }

    private static List<FilterGroup> parseCategoryFilters(String html) {
        List<FilterGroup> groups = new ArrayList<FilterGroup>();
        if (TextUtils.isEmpty(html)) {
            return groups;
        }
        Pattern listPattern = Pattern.compile("<ul[^>]*class=[\"'][^\"']*screen_list[^\"']*[\"'][^>]*>(.*?)</ul>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher listMatcher = listPattern.matcher(html);
        while (listMatcher.find()) {
            String block = listMatcher.group(1);
            String groupName = parseFilterGroupName(block);
            if (TextUtils.isEmpty(groupName) && isCategorySortFilterBlock(block)) {
                groupName = "排序";
            }
            if (TextUtils.isEmpty(groupName)) {
                continue;
            }
            FilterGroup group = new FilterGroup();
            group.name = groupName;
            group.options = parseFilterOptions(block);
            if (!group.options.isEmpty()) {
                groups.add(group);
            }
            if (groups.size() >= 6) {
                break;
            }
        }
        Log.i(TAG, "======>>>>>>【分类筛选项解析完成，groupCount=" + groups.size() + "】<<<<<<======");
        return groups;
    }

    private static boolean isCategorySortFilterBlock(String html) {
        String text = cleanHtmlText(html);
        return text.contains("按最新") && text.contains("按最热") && text.contains("按评分");
    }

    private static String parseFilterGroupName(String html) {
        Matcher matcher = Pattern.compile("<span[^>]*class=[\"'][^\"']*text_muted[^\"']*[\"'][^>]*>(.*?)</span>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html == null ? "" : html);
        if (matcher.find()) {
            return cleanHtmlText(matcher.group(1));
        }
        return "";
    }

    private static List<FilterOption> parseFilterOptions(String html) {
        List<FilterOption> options = new ArrayList<FilterOption>();
        Pattern optionPattern = Pattern.compile("<li([^>]*)>\\s*<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>\\s*</li>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = optionPattern.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String name = cleanHtmlText(matcher.group(3));
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            FilterOption option = new FilterOption();
            option.name = name;
            option.url = normalizeWxjswUrl(matcher.group(2));
            option.selected = matcher.group(1) != null && matcher.group(1).contains("hl");
            options.add(option);
        }
        return options;
    }

    private static String parseCategoryPageInfo(String html) {
        Matcher matcher = Pattern.compile("<div[^>]*class=[\"'][^\"']*page_tips[^\"']*[\"'][^>]*>(.*?)</div>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html == null ? "" : html);
        if (matcher.find()) {
            return cleanHtmlText(matcher.group(1));
        }
        return "";
    }

    private static void parseCategoryPagination(String html, VideoCategory category) {
        if (category == null) {
            return;
        }
        Matcher pageInfoMatcher = Pattern.compile("第\\s*(\\d+)\\s*页.*?共有\\s*(\\d+)\\s*页",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(category.pageInfo == null ? "" : category.pageInfo);
        if (pageInfoMatcher.find()) {
            category.currentPage = parsePositiveInt(pageInfoMatcher.group(1));
            category.totalPage = parsePositiveInt(pageInfoMatcher.group(2));
        }
        Matcher pageBlockMatcher = Pattern.compile("<ul[^>]*class=[\"'][^\"']*page[^\"']*[\"'][^>]*>(.*?)</ul>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html == null ? "" : html);
        if (!pageBlockMatcher.find()) {
            return;
        }
        String pageBlock = pageBlockMatcher.group(1);
        Matcher linkMatcher = Pattern.compile("<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(pageBlock);
        while (linkMatcher.find()) {
            String linkText = cleanHtmlText(linkMatcher.group(2));
            String linkUrl = normalizeWxjswUrl(linkMatcher.group(1));
            if ("首页".equals(linkText)) {
                category.firstPageUrl = linkUrl;
            } else if ("上一页".equals(linkText)) {
                category.previousPageUrl = linkUrl;
            } else if ("下一页".equals(linkText)) {
                category.nextPageUrl = linkUrl;
            } else if ("尾页".equals(linkText)) {
                category.lastPageUrl = linkUrl;
            }
        }
        if (category.currentPage <= 1) {
            category.previousPageUrl = "";
        }
        if (category.totalPage > 0 && category.currentPage >= category.totalPage) {
            category.nextPageUrl = "";
        }
    }

    private static int parsePositiveInt(String text) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String parseHomeCategoryName(String html) {
        if (html == null) {
            return "";
        }
        return cleanHtmlText(html.replaceAll("(?is)<i[^>]*>.*?</i>", ""));
    }

    private static List<VideoItem> parseHomeItems(String html, String categoryName) {
        List<VideoItem> items = new ArrayList<VideoItem>();
        if (TextUtils.isEmpty(html)) {
            return items;
        }
        Pattern pattern = Pattern.compile("<a[^>]*class=[\"'][^\"']*vodlist_thumb[^\"']*[\"'][^>]*href=[\"']([^\"']+)[\"'][^>]*title=[\"']([^\"']+)[\"'][^>]*data-original=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find() && items.size() < 80) {
            VideoItem item = new VideoItem();
            item.link = normalizeWxjswUrl(matcher.group(1));
            item.title = cleanHtmlText(matcher.group(2));
            item.posterUrl = normalizeImageUrl(matcher.group(3));
            item.episodeText = parseHomeEpisodeText(matcher.group(4));
            item.categoryName = categoryName;
            if (!TextUtils.isEmpty(item.link) && !TextUtils.isEmpty(item.title) && !hasVideoLink(items, item.link)) {
                items.add(item);
            }
        }
        Log.i(TAG, "======>>>>>>【首页 HTML 海报数据解析完成，category=" + categoryName + "，count=" + items.size() + "】<<<<<<======");
        return items;
    }

    private static String parseHomeEpisodeText(String html) {
        Matcher matcher = Pattern.compile("<span[^>]*class=[\"'][^\"']*pic_text[^\"']*[\"'][^>]*>(.*?)</span>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html == null ? "" : html);
        if (matcher.find()) {
            return cleanHtmlText(matcher.group(1));
        }
        return "";
    }

    private static String parseDetailPosterUrl(String html) {
        Matcher matcher = Pattern.compile("<a[^>]*class=[\"'][^\"']*vodlist_thumb[^\"']*[\"'][^>]*data-original=[\"']([^\"']+)[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (matcher.find()) {
            return normalizeImageUrl(matcher.group(1));
        }
        matcher = Pattern.compile("background-image\\s*:\\s*url\\(([^)]+)\\)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (matcher.find()) {
            return normalizeImageUrl(matcher.group(1).replace("\"", "").replace("'", "").trim());
        }
        return "";
    }

    private static String parseDetailInlineValue(String html, String label) {
        Matcher matcher = Pattern.compile("<span[^>]*>\\s*" + Pattern.quote(label) + "：\\s*</span>(.*?)(?:<span[^>]*class=[\"'][^\"']*split_line|</li>)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (!matcher.find()) {
            return "";
        }
        return normalizeUnknownValue(cleanHtmlText(matcher.group(1)));
    }

    private static String parseDetailStatus(String html) {
        String status = parseFirstCleanText(html, "<span[^>]*>\\s*状态：\\s*</span>\\s*<span[^>]*class=[\"'][^\"']*data_style[^\"']*[\"'][^>]*>(.*?)</span>");
        return normalizeUnknownValue(status);
    }

    private static String parseDetailUpdateDate(String html) {
        String date = parseFirstCleanText(html, "<span[^>]*>\\s*状态：\\s*</span>.*?<em>(.*?)</em>");
        return normalizeUnknownValue(date);
    }

    private static String parseDetailListValue(String html, String label) {
        Matcher matcher = Pattern.compile("<li[^>]*class=[\"'][^\"']*data[^\"']*[\"'][^>]*>\\s*<span[^>]*>\\s*" + Pattern.quote(label) + "：\\s*</span>(.*?)</li>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (!matcher.find()) {
            return "";
        }
        return normalizeUnknownValue(cleanHtmlText(matcher.group(1)));
    }

    private static String parseDetailDescription(String html) {
        String description = parseFirstCleanText(html, "<div[^>]*class=[\"'][^\"']*content_desc[^\"']*full_text[^\"']*[\"'][^>]*>\\s*<span>(.*?)</span>");
        if (TextUtils.isEmpty(description)) {
            description = parseFirstCleanText(html, "<div[^>]*class=[\"'][^\"']*content_desc[^\"']*context[^\"']*[\"'][^>]*>\\s*<span>(.*?)</span>");
        }
        if (TextUtils.isEmpty(description)) {
            description = parseFirstCleanText(html, "<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"'][^>]*>");
            description = description.replaceFirst("^.*?剧情:", "");
        }
        return normalizeUnknownValue(description);
    }

    private static String parseFirstCleanText(String html, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html == null ? "" : html);
        if (!matcher.find()) {
            return "";
        }
        return cleanHtmlText(matcher.group(1));
    }

    private static String normalizeUnknownValue(String value) {
        String text = cleanText(value);
        if (TextUtils.isEmpty(text) || "未知".equals(text) || "暂无".equals(text)) {
            return "";
        }
        return text;
    }

    private static boolean hasVideoLink(List<VideoItem> items, String link) {
        return findVideoByLink(items, link) != null;
    }

    private static VideoItem findVideoByLink(List<VideoItem> items, String link) {
        if (items == null || TextUtils.isEmpty(link)) {
            return null;
        }
        for (VideoItem item : items) {
            if (item != null && link.equals(item.link)) {
                return item;
            }
        }
        return null;
    }

    private static boolean hasEpisodeUrl(List<EpisodeItem> episodes, String url) {
        for (EpisodeItem episode : episodes) {
            if (url.equals(episode.url)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeEpisodeName(String name, String url) {
        if (TextUtils.isEmpty(name) || !name.contains("立即播放")) {
            return name;
        }
        Pattern pattern = Pattern.compile("/vodplay/\\d+-\\d+-(\\d+)\\.html");
        Matcher matcher = pattern.matcher(url);
        if (!matcher.find()) {
            return name;
        }
        int number = Integer.parseInt(matcher.group(1));
        return number < 10 ? "第0" + number + "集" : "第" + number + "集";
    }

    private static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String cleanHtmlText(String html) {
        if (html == null) {
            return "";
        }
        return Html.fromHtml(html.replace('\n', ' ').replace('\r', ' ')).toString().replaceAll("\\s+", " ").trim();
    }

    private static String normalizeWxjswUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("/")) {
            return "https://www.wxjsw.com" + url;
        }
        return "https://www.wxjsw.com/" + url;
    }

    private static String normalizeImageUrl(String url) {
        return normalizeWxjswUrl(url);
    }

    private static String unescapeJsonUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&").trim();
    }
}
