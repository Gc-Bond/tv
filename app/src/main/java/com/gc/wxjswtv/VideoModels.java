package com.gc.wxjswtv;

import android.widget.ImageView;

import java.util.List;

/**
 * 西瓜影院电视端基础数据模型。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:45:00</p>
 *
 * <p>说明：这些模型只承载解析结果和布局请求，不直接访问 Activity、WebView 或播放器。
 * 从 MainActivity 拆出后，页面渲染、分类解析和海报加载可以共享同一批轻量对象，避免主界面文件继续膨胀。</p>
 */
class VideoItem {
    String title;
    String link;
    String description;
    String pubDate;
    String posterUrl;
    String episodeText;
    String categoryName;
}

/**
 * 网站详情页解析出的影片详情信息。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 16:46:06</p>
 *
 * <p>说明：原网站详情页同时提供封面、年份、地区、类型、状态、主演、导演和剧情介绍；
 * 电视端右侧详情面板直接消费这个模型，避免继续展示 RSS 占位文案。</p>
 */
class VideoDetailInfo {
    String title;
    String posterUrl;
    String year;
    String area;
    String type;
    String status;
    String updateDate;
    String actors;
    String director;
    String description;
}

/**
 * 首页或分类页的视频分组。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:45:00</p>
 */
class VideoCategory {
    String name;
    String url;
    String pageInfo;
    String firstPageUrl;
    String previousPageUrl;
    String nextPageUrl;
    String lastPageUrl;
    boolean searchResult;
    int currentPage;
    int totalPage;
    List<FilterGroup> filters;
    List<VideoItem> items;
}

/**
 * 分类页筛选分组。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:45:00</p>
 */
class FilterGroup {
    String name;
    List<FilterOption> options;
}

/**
 * 分类页筛选选项。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:45:00</p>
 */
class FilterOption {
    String name;
    String url;
    boolean selected;
}

/**
 * 首页卡片布局尺寸。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:45:00</p>
 */
class HomeCardMetrics {
    int columnCount;
    int cardWidth;
    int cardHeight;
    int posterDecodeWidth;
    int posterDecodeHeight;
    int marginHorizontal;
    int marginVertical;
    int overlayHeight;
}

/**
 * 右侧详情页选集网格布局尺寸。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 16:35:54</p>
 *
 * <p>说明：详情页已嵌入首页右侧内容区，选集按钮不能继续使用固定宽度；
 * 这里统一承载按真实内容宽度计算出的列数、按钮尺寸和间距，保证不同盒子分辨率下都能自适应。</p>
 */
class DetailEpisodeMetrics {
    int columnCount;
    int buttonWidth;
    int buttonHeight;
    int margin;
}

/**
 * 首页海报控件登记信息。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:45:00</p>
 */
class PosterTarget {
    ImageView imageView;
    String url;
    int targetWidth;
    int targetHeight;
}

/**
 * 首页海报加载请求。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:45:00</p>
 */
class PosterRequest {
    String url;
    ImageView imageView;
    int targetWidth;
    int targetHeight;

    PosterRequest(String url, ImageView imageView, int targetWidth, int targetHeight) {
        this.url = url;
        this.imageView = imageView;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
    }
}

/**
 * 网站详情页解析出的播放集数。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:45:00</p>
 */
class EpisodeItem {
    String name;
    String url;
}
