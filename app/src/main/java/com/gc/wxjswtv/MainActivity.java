package com.gc.wxjswtv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 西瓜影院电视轻量入口。
 *
 * <p>用途：在 Android TV / 电视盒子上用原生电视列表展示网站 RSS 数据，遥控器焦点由原生控件负责；
 * 点击影片后进入原生详情和原生播放器，网站页面只作为隐藏数据源。</p>
 *
 * <p>输入：固定读取 {@code https://wxjsw.com/rss.xml}，并从网站 HTML 中提取封面、集数和播放地址等数据。
 * 输出：原生网格卡片列表、原生详情页和原生 VideoView 播放页。
 * 约束：RSS 只提供标题、详情链接、简介和更新时间，不保证封面；播放兼容性取决于系统播放器对 HLS 的支持。
 * 副作用：会访问外部站点、启用隐藏数据采集 WebView 的 JavaScript，并对 wxjsw.com 做 HTTPS 数据读取放行。</p>
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 13:12:00</p>
 */
public class MainActivity extends Activity {
    private static final String TAG = "WxjswTvShell";
    private static final String RSS_URL = "https://wxjsw.com/rss.xml";
    private static final String HOME_URL = "https://www.wxjsw.com/";
    private static final String HOME_NAV_NAME = "首页";
    private static final int HOME_COLUMN_COUNT = 5;
    private static final int HOME_SIDE_NAV_WIDTH_DP = 190;

    private FrameLayout rootView;
    private LinearLayout homeView;
    private FrameLayout homeSidePanel;
    private FrameLayout homeContentPanel;
    private LinearLayout detailView;
    private FrameLayout detailInlinePlayerHost;
    private ScrollView homeScrollView;
    private GridLayout homeCategoryNav;
    private LinearLayout homeCategoryContainer;
    private GridLayout episodeGrid;
    private ImageView detailPosterView;
    private TextView detailTitleView;
    private TextView detailInfoView;
    private TextView detailStaffView;
    private TextView detailDescriptionView;
    private TextView detailEpisodeHintView;
    private View detailPreviewFocusBorder;
    private View lastInlineEpisodeFocusView;
    private TextView statusView;
    private TextView floatingSearchButton;
    private WebView rssWebView;
    private boolean showingDetailPage;
    private VideoItem currentItem;
    private List<VideoItem> currentHomeItems = new ArrayList<VideoItem>();
    private List<VideoCategory> currentHomeCategories = new ArrayList<VideoCategory>();
    private CategoryNavigationState categoryNavigationState = new CategoryNavigationState();
    private AudioManager audioManager;
    private NativePlayerController playerController;
    private FilterPanelController filterPanelController;
    private CategoryLoadingController categoryLoadingController;
    private HomePosterLoader posterLoader;
    private List<PosterTarget> homePosterTargets = new ArrayList<PosterTarget>();
    private String pendingCategoryContentFocusFilterName;

    /**
     * 创建 Activity、原生首页、原生详情和原生播放器。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     *
     * @param savedInstanceState 系统保存状态，本应用每次启动重新读取 RSS，避免老盒子恢复复杂 WebView 状态导致卡顿。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(Color.rgb(17, 18, 24));
        setContentView(rootView);
        enterImmersiveFullScreen();
        posterLoader = new HomePosterLoader(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        buildHomeView();
        buildDetailView();
        buildPlayerView();
        buildFilterPanelView();
        buildRssWebView();
        buildStatusView();
        buildCategoryLoadingView();
        loadRssList();
    }

    /**
     * 进入电视端沉浸式全屏。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 15:18:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 15:18:00
     * 修改说明：首页、详情页和播放页都使用原生全屏布局，同时隐藏系统状态栏和导航栏，避免用户误以为仍在网页容器中显示。</p>
     */
    private void enterImmersiveFullScreen() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags = flags | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        rootView.setSystemUiVisibility(flags);
        Log.i(TAG, "======>>>>>>【进入原生沉浸式全屏】<<<<<<======");
    }

    /**
     * 窗口重新获得焦点时恢复沉浸式全屏。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 15:18:00</p>
     *
     * @param hasFocus 窗口是否获得焦点。
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && rootView != null) {
            enterImmersiveFullScreen();
        }
    }

    /**
     * 创建隐藏数据采集 WebView。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:15:30</p>
     *
     * <p>说明：部分电视系统的 Java HTTPS 栈无法和 wxjsw.com 完成 TLS 握手，但系统 WebView 可以通过
     * {@link WebViewClient#onReceivedSslError(WebView, SslErrorHandler, SslError)} 放行目标站点证书后加载页面。
     * 因此前台仍完全原生，只用这个 1dp 隐藏 WebView 抓取 RSS、首页、详情和播放页 HTML 数据。</p>
     */
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void buildRssWebView() {
        rssWebView = new WebView(this);
        rssWebView.setVisibility(View.INVISIBLE);
        WebSettings settings = rssWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setLoadsImagesAutomatically(false);
        settings.setBlockNetworkImage(true);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        rssWebView.addJavascriptInterface(new WxjswBridge(this, new WxjswBridge.Callback() {
            @Override
            public void onRss(String xml) {
                handleRssXml(xml);
            }

            @Override
            public void onHome(String html) {
                handleHomeHtml(html);
            }

            @Override
            public void onCategory(String url, String html) {
                handleCategoryHtml(url, html);
            }

            @Override
            public void onDetail(String url, String html) {
                handleDetailHtml(url, html);
            }

            @Override
            public void onPlayPage(String url, String html) {
                handlePlayPageHtml(url, html);
            }
        }), "AndroidRss");
        rootView.addView(rssWebView, new FrameLayout.LayoutParams(dp(1), dp(1), Gravity.LEFT | Gravity.TOP));
        rssWebView.setWebViewClient(new WebViewClient() {
            /**
             * 处理电视系统加载 wxjsw.com 数据时的 HTTPS 证书问题。
             *
             * <p>作者：gc
             * 创建时间：2026-05-23 13:15:30</p>
             */
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                String url = error == null ? "" : error.getUrl();
                if (WxjswDataParser.isWxjswUrl(url)) {
                    Log.w(TAG, "======>>>>>>【隐藏数据 WebView HTTPS 读取放行，url=" + url + "】<<<<<<======");
                    handler.proceed();
                    return;
                }
                handler.cancel();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "======>>>>>>【隐藏数据 WebView 加载完成，url=" + url + "】<<<<<<======");
                if (url != null && url.contains("/voddetail/")) {
                    view.loadUrl("javascript:window.AndroidRss.onDetail(location.href, document.documentElement.outerHTML);");
                } else if (url != null && url.contains("/vodplay/")) {
                    view.loadUrl("javascript:window.AndroidRss.onPlayPage(location.href, document.documentElement.outerHTML);");
                } else if (WxjswDataParser.isMainCategoryUrl(url)) {
                    view.loadUrl("javascript:window.AndroidRss.onCategory(location.href, document.documentElement.outerHTML);");
                } else if (url != null && (url.equals(HOME_URL) || url.equals("https://www.wxjsw.com"))) {
                    view.loadUrl("javascript:window.AndroidRss.onHome(document.documentElement.outerHTML);");
                } else {
                    view.loadUrl("javascript:window.AndroidRss.onRss(document.documentElement.outerHTML);");
                }
            }
        });
    }

    /**
     * 创建原生电视首页。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     *
     * <p>说明：首页只保留最简单的标题、状态和影片卡片，减少老盒子渲染压力。卡片使用 Android 原生焦点，
     * 不再依赖网页 CSS，因此遥控器焦点会比 WebView 内部稳定。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:01:00
     * 修改说明：首页由单一影片网格调整为分类分区容器，后续渲染时每个分类拥有独立标题和网格，
     * 避免电影、电视剧、综艺、动漫混在一个长列表里影响电视端浏览效率。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:21:26
     * 修改说明：增加顶部分类导航，参考原网站首页入口提供电影、电视剧、综艺、动漫四个按钮，
     * 用户可以直接进入对应分类内容，不必在长首页里连续向下翻找。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:30:50
     * 修改说明：顶部导航增加“首页”按钮，应用刚进入时默认停留在首页聚合内容，分类页可通过首页按钮返回。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:48:00
     * 修改说明：导航从顶部横排改为左侧竖排，右侧专注展示影片内容，减少顶部空间占用。</p>
     */
    private void buildHomeView() {
        homeView = new LinearLayout(this);
        homeView.setOrientation(LinearLayout.HORIZONTAL);
        homeView.setPadding(dp(24), dp(14), dp(24), dp(14));
        homeView.setBackgroundColor(Color.rgb(17, 18, 24));
        rootView.addView(homeView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        homeCategoryNav = new GridLayout(this);
        homeCategoryNav.setColumnCount(1);
        homeCategoryNav.setPadding(0, dp(8), dp(12), dp(8));
        homeSidePanel = new FrameLayout(this);
        homeView.addView(homeSidePanel, new LinearLayout.LayoutParams(
                dp(HOME_SIDE_NAV_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        homeSidePanel.addView(homeCategoryNav, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        homeContentPanel = new FrameLayout(this);
        homeView.addView(homeContentPanel, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1));

        homeScrollView = new ScrollView(this);
        homeScrollView.setFillViewport(true);
        homeScrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            /**
             * 首页滚动时自动加载可视区域附近海报。
             *
             * <p>作者：gc
             * 创建时间：2026-05-23 14:15:34</p>
             *
             * <p>说明：遥控器上下移动会带动 ScrollView 滚动，如果只靠焦点触发图片加载，
             * 新进入屏幕的卡片必须逐个获得焦点才出图；这里在滚动位置变化时扫描可视区域，
             * 让当前屏幕和上下缓冲区内的海报自动进入加载队列。</p>
             */
            @Override
            public void onScrollChanged() {
                loadVisibleHomePosters();
            }
        });
        homeCategoryContainer = new LinearLayout(this);
        homeCategoryContainer.setOrientation(LinearLayout.VERTICAL);
        homeCategoryContainer.setPadding(0, dp(10), 0, dp(56));
        homeScrollView.addView(homeCategoryContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        homeContentPanel.addView(homeScrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /**
     * 创建原生详情页。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:08:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:08:00
     * 修改说明：详情页不再展示网站原页面，改为标题、简介和集数按钮，避免遥控器焦点落入网页不可控区域。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:35:54
     * 修改说明：详情页已内嵌到首页右侧内容区，原全屏详情尺寸会显得过大；
     * 收紧文字字号、高度和内边距，选集网格在渲染时再按右侧实际宽度自适应计算。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:41:15
     * 修改说明：详情根容器不再接收焦点，改由可见的集数提示行承接加载期焦点；
     * 避免遥控器左右切换时焦点落到没有高亮样式的容器上，看起来像光标消失。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:46:06
     * 修改说明：详情头部改为封面加文字信息的横向布局，展示网站详情页解析出的年份、地区、类型、状态、主演和导演；
     * 同时移除“详情数据来自”和“正在读取影片简介”这类占位文案。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:51:14
     * 修改说明：详情页增加局部播放器容器，选择集数后先在右侧区域内播放，不再直接切换到全屏播放页。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 17:01:28
     * 修改说明：局部播放区改为详情头部左侧大预览区域，播放前显示封面，播放后同一区域播放视频；
     * 预览区域支持遥控器选中，确认键进入全屏播放。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 17:04:50
     * 修改说明：预览区保持 16:9 视频比例，但竖版封面在其中按海报比例居中显示，避免强行裁切导致封面难看。</p>
     */
    private void buildDetailView() {
        detailView = new LinearLayout(this);
        detailView.setOrientation(LinearLayout.VERTICAL);
        detailView.setPadding(dp(28), dp(18), dp(28), dp(18));
        detailView.setBackgroundColor(Color.rgb(17, 18, 24));
        detailView.setFocusable(false);
        detailView.setFocusableInTouchMode(false);
        detailView.setVisibility(View.GONE);
        homeContentPanel.addView(detailView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout detailHeaderView = new LinearLayout(this);
        detailHeaderView.setOrientation(LinearLayout.HORIZONTAL);
        detailHeaderView.setGravity(Gravity.TOP);
        detailView.addView(detailHeaderView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(244)));

        detailInlinePlayerHost = new FrameLayout(this);
        detailInlinePlayerHost.setBackground(makePreviewBackground(false));
        detailInlinePlayerHost.setFocusable(true);
        detailInlinePlayerHost.setFocusableInTouchMode(true);
        detailInlinePlayerHost.setClickable(true);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(420), dp(236));
        previewParams.setMargins(0, 0, dp(20), 0);
        detailHeaderView.addView(detailInlinePlayerHost, previewParams);

        detailPosterView = new ImageView(this);
        detailPosterView.setBackgroundColor(Color.rgb(25, 28, 36));
        detailPosterView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        detailInlinePlayerHost.addView(detailPosterView, new FrameLayout.LayoutParams(
                dp(158),
                dp(222),
                Gravity.CENTER));
        detailPreviewFocusBorder = new View(this);
        detailPreviewFocusBorder.setBackground(makePreviewFocusBorder(false));
        detailInlinePlayerHost.addView(detailPreviewFocusBorder, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        detailInlinePlayerHost.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                view.setBackground(makePreviewBackground(hasFocus));
                detailPreviewFocusBorder.setBackground(makePreviewFocusBorder(hasFocus));
                bringDetailPreviewFocusBorderToFront();
            }
        });
        detailInlinePlayerHost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enterFullscreenFromInlinePreview();
            }
        });

        LinearLayout detailTextColumn = new LinearLayout(this);
        detailTextColumn.setOrientation(LinearLayout.VERTICAL);
        detailHeaderView.addView(detailTextColumn, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1));

        detailTitleView = new TextView(this);
        detailTitleView.setTextColor(Color.WHITE);
        detailTitleView.setTextSize(24);
        detailTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        detailTitleView.setSingleLine(true);
        detailTitleView.setEllipsize(TextUtils.TruncateAt.END);
        detailTextColumn.addView(detailTitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)));

        detailInfoView = new TextView(this);
        detailInfoView.setTextColor(Color.rgb(255, 170, 88));
        detailInfoView.setTextSize(15);
        detailInfoView.setMaxLines(2);
        detailInfoView.setEllipsize(TextUtils.TruncateAt.END);
        detailTextColumn.addView(detailInfoView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)));

        detailStaffView = new TextView(this);
        detailStaffView.setTextColor(Color.rgb(214, 218, 226));
        detailStaffView.setTextSize(14);
        detailStaffView.setMaxLines(3);
        detailStaffView.setEllipsize(TextUtils.TruncateAt.END);
        detailTextColumn.addView(detailStaffView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(72)));

        detailDescriptionView = new TextView(this);
        detailDescriptionView.setTextColor(Color.rgb(214, 218, 226));
        detailDescriptionView.setTextSize(15);
        detailDescriptionView.setMaxLines(4);
        detailDescriptionView.setEllipsize(TextUtils.TruncateAt.END);
        detailTextColumn.addView(detailDescriptionView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(86)));

        detailEpisodeHintView = new TextView(this);
        detailEpisodeHintView.setText("正在读取集数...");
        detailEpisodeHintView.setTextColor(Color.rgb(198, 203, 213));
        detailEpisodeHintView.setTextSize(15);
        detailEpisodeHintView.setGravity(Gravity.CENTER_VERTICAL);
        detailEpisodeHintView.setPadding(dp(12), 0, dp(12), 0);
        detailEpisodeHintView.setFocusable(true);
        detailEpisodeHintView.setFocusableInTouchMode(true);
        detailEpisodeHintView.setBackground(makeCardBackground(false));
        detailEpisodeHintView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                view.setBackground(makeCardBackground(hasFocus));
            }
        });
        detailView.addView(detailEpisodeHintView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)));

        ScrollView episodeScroll = new ScrollView(this);
        episodeScroll.setFillViewport(true);
        episodeGrid = new GridLayout(this);
        episodeGrid.setColumnCount(1);
        episodeGrid.setPadding(0, dp(6), 0, dp(36));
        episodeScroll.addView(episodeGrid, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        detailView.addView(episodeScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1));
    }

    /**
     * 创建原生播放页。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:08:00</p>
     *
     * <p>说明：前台只放系统 VideoView 和少量状态文字，播放页 HTML 在隐藏 WebView 中解析出 m3u8 地址后才交给系统播放器。
     * 这样可以绕开网站播放页复杂 DOM，让遥控器确认键只负责暂停/继续。</p>
     */
    private void buildPlayerView() {
        playerController = new NativePlayerController(this, rootView, audioManager, new NativePlayerController.Callback() {
            @Override
            public void onPlayerEpisodeSelected(EpisodeItem episode) {
                openNativePlayer(episode);
            }
        });
        playerController.build();
    }

    /**
     * 创建分类筛选弹层。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:41:49</p>
     *
     * <p>说明：遥控器不适合在很长的横向筛选条里连续右移，尤其是字母筛选；
     * 弹层用 5 列网格承载全部选项，让用户通过上下左右完成选择，返回键关闭弹层。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 15:16:30
     * 修改说明：弹层按屏幕安全区域设置固定尺寸，选项区域独立滚动，避免长筛选项在电视屏幕上被裁切。</p>
     */
    private void buildFilterPanelView() {
        filterPanelController = new FilterPanelController(this, rootView, new FilterPanelController.Callback() {
            @Override
            public void onFilterOptionSelected(FilterGroup filterGroup, FilterOption option) {
                String filterGroupName = filterGroup == null ? "" : filterGroup.name;
                loadCategoryPageUrl(option.url, "筛选：" + option.name, filterGroupName);
            }
        });
        filterPanelController.build();
    }

    /**
     * 创建顶部状态提示。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     */
    private void buildStatusView() {
        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(20);
        statusView.setPadding(dp(24), dp(14), dp(24), dp(14));
        statusView.setBackgroundColor(Color.rgb(255, 106, 0));
        statusView.setVisibility(View.GONE);
        rootView.addView(statusView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));
    }

    /**
     * 创建分类加载遮罩。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:45:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 20:45:00
     * 修改说明：分类页 WebView 请求加载期间显示遮罩并禁止继续操作，避免用户连续切换分类导致多个异步回调交错覆盖页面。</p>
     */
    private void buildCategoryLoadingView() {
        categoryLoadingController = new CategoryLoadingController(this, rootView);
        categoryLoadingController.build();
    }

    /**
     * 加载 RSS 影片列表。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     */
    private void loadRssList() {
        if (!isNetworkConnected()) {
            showStatus("未检测到网络，请连接网络后重启应用");
            return;
        }
        showStatus("正在加载最新影片...");
        rssWebView.loadUrl(RSS_URL);
    }

    /**
     * 按分类渲染首页影片区块。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:01:00</p>
     *
     * <p>说明：电视端纵向浏览更适合“分类标题 + 多列网格”的节奏，用户可以先扫分类再进入内容；
     * 首页只登记海报控件，实际图片由可视区域扫描按需加载，避免离屏图片抢占首屏资源。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:07:18
     * 修改说明：首页卡片不再使用固定 dp 尺寸，改为按屏幕实际像素宽度计算 5 列卡片尺寸，
     * 解决电视盒子 density 较高时一屏只能看到 3 个剧集的问题。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:15:34
     * 修改说明：移除固定数量预加载，改为首页渲染完成后立即扫描可视区域图片，后续滚动时继续自动加载新进入屏幕的海报。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 17:43:00
     * 修改说明：内容刷新后不再强制让第一张影片获得焦点，避免用户点击左侧导航后焦点跳进海报列表。</p>
     *
     * @param categories 已按网站首页分块解析出的影片分类列表。
     */
    private void renderCategorySections(List<VideoCategory> categories) {
        homeCategoryContainer.removeAllViews();
        homePosterTargets.clear();
        if (categories == null || categories.isEmpty()) {
            showStatus("未读取到分类数据，请稍后重试");
            return;
        }
        hideStatus();
        HomeCardMetrics cardMetrics = createHomeCardMetrics();
        for (VideoCategory category : categories) {
            if (category == null || category.items == null || category.items.isEmpty()) {
                continue;
            }
            TextView titleView = createCategoryTitleText(category);
            homeCategoryContainer.addView(titleView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44)));

            renderCategoryFilters(category);

            GridLayout sectionGrid = new GridLayout(this);
            sectionGrid.setColumnCount(cardMetrics.columnCount);
            sectionGrid.setPadding(0, dp(4), 0, dp(22));
            homeCategoryContainer.addView(sectionGrid, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            renderSectionItems(category.items, sectionGrid, cardMetrics);
            renderCategoryBottomControls(category);
        }
        if (homeCategoryContainer.getChildCount() == 0) {
            showStatus("分类数据为空，请稍后重试");
        }
        homeCategoryContainer.post(new Runnable() {
            @Override
            public void run() {
                loadVisibleHomePosters();
            }
        });
        Log.i(TAG, "======>>>>>>【首页分类渲染完成，categoryCount=" + categories.size()
                + "，columnCount=" + cardMetrics.columnCount
                + "，cardWidth=" + cardMetrics.cardWidth
                + "，cardHeight=" + cardMetrics.cardHeight + "】<<<<<<======");
    }

    /**
     * 渲染首页分类导航和内容区。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:21:26</p>
     *
     * <p>说明：分类列表只以网站首页解析出的四大分区为准，顶部按钮负责切换当前内容集合；
     * 不再把分类按钮做成普通卡片，否则遥控器用户仍然需要在内容列表里寻找入口。</p>
     *
     * @param categories 网站首页解析出的分类。
     */
    private void renderHomeCategories(List<VideoCategory> categories) {
        currentHomeCategories = categories == null ? new ArrayList<VideoCategory>() : categories;
        if (categoryNavigationState.isLoadingCategoryPage()) {
            Log.i(TAG, "======>>>>>>【分类页加载中，忽略迟到的首页渲染，pendingCategory=" + categoryNavigationState.getPendingCategoryName() + "】<<<<<<======");
            return;
        }
        categoryNavigationState.showHome();
        renderHomeCategoryNav();
        renderCategorySections(currentHomeCategories);
        requestFirstHomeCategoryNavFocus();
    }

    /**
     * 渲染分类页筛选项。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:33:21</p>
     *
     * <p>说明：分类页顶部只展示筛选入口，例如“类型：全部”；完整选项放到弹层网格中选择。
     * 这样字母、地区等长列表不会变成遥控器难操作的横向链条。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:41:49
     * 修改说明：废弃横向滚动筛选条，改为筛选入口按钮 + 弹层网格，字母选择也复用同一套 TV 交互。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 15:28:20
     * 修改说明：筛选入口统一渲染到同一行，减少分类页顶部占用高度，让遥控器先横向选择筛选分组再打开弹层。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 17:27:00
     * 修改说明：筛选入口宽度按右侧内容区计算，扣除左侧导航栏，避免类型、地区、年份等按钮横向显示不全。</p>
     *
     * @param category 当前渲染的分类。
     */
    private void renderCategoryFilters(VideoCategory category) {
        if (category == null || category.searchResult || category.filters == null || category.filters.isEmpty()) {
            return;
        }
        List<FilterGroup> visibleGroups = new ArrayList<FilterGroup>();
        for (int i = 0; i < category.filters.size(); i++) {
            FilterGroup filterGroup = category.filters.get(i);
            if (filterGroup == null || filterGroup.options == null || filterGroup.options.isEmpty()) {
                continue;
            }
            visibleGroups.add(filterGroup);
        }
        if (visibleGroups.isEmpty()) {
            return;
        }
        GridLayout filterRow = new GridLayout(this);
        filterRow.setColumnCount(visibleGroups.size());
        filterRow.setPadding(0, dp(2), 0, dp(8));
        int totalMarginWidth = dp(12) * visibleGroups.size();
        int contentWidth = getHomeContentWidthPx();
        int buttonWidth = (contentWidth - totalMarginWidth) / visibleGroups.size();
        for (int i = 0; i < visibleGroups.size(); i++) {
            FilterGroup filterGroup = visibleGroups.get(i);
            TextView entryButton = createFilterGroupButton(filterGroup);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = buttonWidth;
            params.height = dp(46);
            params.setMargins(dp(6), dp(3), dp(6), dp(3));
            filterRow.addView(entryButton, params);
        }
        homeCategoryContainer.addView(filterRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /**
     * 创建筛选分组入口按钮。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:41:49</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 15:28:20
     * 修改说明：入口按钮改为适配同一行布局，文本保持“分组：当前值”，避免分类页顶部重复占用多行。</p>
     *
     * @param filterGroup 筛选分组。
     * @return 可聚焦入口按钮。
     */
    private TextView createFilterGroupButton(final FilterGroup filterGroup) {
        final TextView button = new TextView(this);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setText(filterGroup.name + "：" + getSelectedFilterOptionName(filterGroup));
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setBackground(makeFilterOptionBackground(false, false));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                button.setBackground(makeFilterOptionBackground(false, hasFocus));
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                filterPanelController.show(filterGroup);
            }
        });
        return button;
    }

    /**
     * 获取筛选分组当前选中项。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:41:49</p>
     *
     * @param filterGroup 筛选分组。
     * @return 当前选中项名称。
     */
    private String getSelectedFilterOptionName(FilterGroup filterGroup) {
        if (filterGroup == null || filterGroup.options == null) {
            return "全部";
        }
        for (FilterOption option : filterGroup.options) {
            if (option != null && option.selected) {
                return option.name;
            }
        }
        return "全部";
    }

    /**
     * 生成筛选项按钮背景。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:33:21</p>
     */
    private GradientDrawable makeFilterOptionBackground(boolean selected, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(6));
        drawable.setColor(selected || focused ? Color.rgb(255, 106, 0) : Color.rgb(35, 38, 48));
        drawable.setStroke(focused ? dp(3) : dp(1), focused ? Color.WHITE : Color.rgb(72, 76, 90));
        return drawable;
    }

    /**
     * 渲染顶部导航按钮。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:21:26</p>
     *
     * <p>说明：原网站首页顶部入口就是电影、电视剧、综艺、动漫；这里保留同样的信息架构，
     * 但用原生 TV 按钮实现焦点、点击和选中态。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:30:50
     * 修改说明：导航首位增加首页按钮，首页状态用空 categoryNavigationState.getActiveHomeNavCategoryName() 表示，不再让用户误以为刚进入就是电影分类。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:33:00
     * 修改说明：把全局剧名搜索入口放进顶部导航最后一格，取消额外悬浮按钮，遥控器横向即可访问搜索。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:48:00
     * 修改说明：导航改为左侧单列按钮，遥控器上下切换首页、分类和搜索。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 17:22:00
     * 修改说明：左侧导航按钮高度和文字缩小，降低导航栏视觉重量，避免抢占内容区注意力。</p>
     */
    private void renderHomeCategoryNav() {
        homeCategoryNav.removeAllViews();
        int buttonMargin = dp(6);
        int buttonCount = 6;
        homeCategoryNav.setColumnCount(1);
        int buttonWidth = dp(HOME_SIDE_NAV_WIDTH_DP) - dp(12) - buttonMargin * 2;
        TextView homeButton = createHomeCategoryNavButton(HOME_NAV_NAME);
        GridLayout.LayoutParams homeParams = new GridLayout.LayoutParams();
        homeParams.width = buttonWidth;
        homeParams.height = dp(48);
        homeParams.setMargins(buttonMargin, dp(4), buttonMargin, dp(4));
        homeButton.setLayoutParams(homeParams);
        homeCategoryNav.addView(homeButton);

        int count = 1;
        for (int i = 0; i < currentHomeCategories.size() && count < buttonCount - 1; i++) {
            VideoCategory category = currentHomeCategories.get(i);
            if (category == null || TextUtils.isEmpty(category.name)) {
                continue;
            }
            TextView button = createHomeCategoryNavButton(category.name);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = buttonWidth;
            params.height = dp(48);
            params.setMargins(buttonMargin, dp(4), buttonMargin, dp(4));
            button.setLayoutParams(params);
            homeCategoryNav.addView(button);
            count++;
        }
        floatingSearchButton = createSearchTitleButton();
        floatingSearchButton.setText("搜索");
        GridLayout.LayoutParams searchParams = new GridLayout.LayoutParams();
        searchParams.width = buttonWidth;
        searchParams.height = dp(48);
        searchParams.setMargins(buttonMargin, dp(4), buttonMargin, dp(4));
        floatingSearchButton.setLayoutParams(searchParams);
        homeCategoryNav.addView(floatingSearchButton);
    }

    /**
     * 让首页顶部第一个分类按钮获得初始焦点。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:30:00</p>
     *
     * <p>说明：顶部分类按钮是现在的主要入口，首页启动后焦点应先停在分类导航；
     * 否则用户按确认会直接打开第一张影片卡片，误以为分类按钮不可用。</p>
     */
    private void requestFirstHomeCategoryNavFocus() {
        if (homeCategoryNav == null || homeCategoryNav.getChildCount() == 0) {
            return;
        }
        homeCategoryNav.post(new Runnable() {
            @Override
            public void run() {
                if (homeCategoryNav.getChildCount() > 0) {
                    homeCategoryNav.getChildAt(0).requestFocus();
                }
            }
        });
    }

    /**
     * 让当前选中的左侧导航按钮获得焦点。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:43:00</p>
     *
     * <p>说明：分类页加载和内容重绘期间会重建导航按钮；重建后立即按当前 categoryNavigationState.getActiveHomeNavCategoryName() 找回焦点，
     * 避免系统把焦点落到“首页”或第一张影片卡片。</p>
     */
    private void requestSelectedHomeCategoryNavFocus() {
        if (homeCategoryNav == null || homeCategoryNav.getChildCount() == 0) {
            return;
        }
        final String activeCategoryName = categoryNavigationState.getActiveHomeNavCategoryName();
        final String targetName = TextUtils.isEmpty(activeCategoryName) ? HOME_NAV_NAME : activeCategoryName;
        homeCategoryNav.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < homeCategoryNav.getChildCount(); i++) {
                    View child = homeCategoryNav.getChildAt(i);
                    if (child instanceof TextView && targetName.equals(((TextView) child).getText().toString())) {
                        child.requestFocus();
                        return;
                    }
                }
            }
        });
    }

    /**
     * 让分类内容区第一个可操作控件获得焦点。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 18:27:00</p>
     *
     * <p>说明：用户在小分类、排序或分页里操作时，结果页仍属于右侧内容流；
     * 加载完成后焦点应回到筛选入口或影片卡片，而不是回左侧主导航。
     * 这样可以避免“首页”按钮短暂获得焦点造成的闪动感，也符合连续筛选的遥控器操作预期。</p>
     */
    private void requestFirstCategoryContentFocus() {
        requestCategoryContentFocus("", false);
    }

    /**
     * 让分类内容区第一个可操作控件获得焦点，并可在焦点稳定后结束分类加载态。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 19:02:00</p>
     *
     * <p>说明：小分类结果页渲染完成后，加载态需要延迟到右侧控件拿到焦点后再关闭。
     * 这样即使系统在中间帧临时触发左侧首页按钮焦点，也不会显示首页高亮。</p>
     *
     * @param finishCategoryLoadAfterFocus 是否在焦点请求完成后关闭分类加载态。
     */
    private void requestFirstCategoryContentFocus(final boolean finishCategoryLoadAfterFocus) {
        requestCategoryContentFocus("", finishCategoryLoadAfterFocus);
    }

    /**
     * 让分类内容区指定筛选入口优先获得焦点。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 21:03:00</p>
     *
     * <p>说明：小分类筛选后，结果页顶部会重新渲染“类型/地区/年份/排序”等入口。
     * 如果仍然简单寻找第一个可聚焦控件，焦点会固定落到“类型”，造成用户刚选了“年份”却回到“类型”的错觉。
     * 因此这里先按筛选分组名称匹配按钮文案前缀，找不到时才回退到第一个可聚焦控件。</p>
     *
     * @param preferredFilterName 希望恢复焦点的筛选分组名称，例如“年份”；为空时使用默认第一个可聚焦控件。
     * @param finishCategoryLoadAfterFocus 是否在焦点请求完成后关闭分类加载态。
     */
    private void requestCategoryContentFocus(final String preferredFilterName, final boolean finishCategoryLoadAfterFocus) {
        if (homeCategoryContainer == null || homeCategoryContainer.getChildCount() == 0) {
            if (finishCategoryLoadAfterFocus) {
                categoryNavigationState.finishCategoryLoad();
            }
            return;
        }
        homeCategoryContainer.post(new Runnable() {
            @Override
            public void run() {
                View focusableView = findPreferredFilterButton(homeCategoryContainer, preferredFilterName);
                if (focusableView == null) {
                    focusableView = findFirstFocusableDescendant(homeCategoryContainer);
                }
                if (focusableView != null) {
                    focusableView.requestFocus();
                }
                if (finishCategoryLoadAfterFocus) {
                    categoryNavigationState.finishCategoryLoad();
                    refreshHomeCategoryNavStates();
                }
            }
        });
    }

    /**
     * 按筛选分组名称查找入口按钮。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 21:03:00</p>
     *
     * @param view 当前查找起点。
     * @param filterName 筛选分组名，例如“年份”。
     * @return 匹配“年份：xxx”这类文案的可聚焦按钮；找不到时返回 null。
     */
    private View findPreferredFilterButton(View view, String filterName) {
        if (view == null || TextUtils.isEmpty(filterName)) {
            return null;
        }
        if (view instanceof TextView && view.isFocusable()) {
            String text = ((TextView) view).getText().toString();
            if (text.startsWith(filterName + "：")) {
                return view;
            }
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View result = findPreferredFilterButton(group.getChildAt(i), filterName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * 从指定容器中查找第一个可聚焦子控件。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 18:27:00</p>
     *
     * <p>说明：分类页顶部可能先渲染筛选入口，也可能在搜索结果中直接渲染影片网格；
     * 递归查找可聚焦子控件可以同时覆盖两种结构，不需要为每个页面类型写一套焦点规则。</p>
     *
     * @param view 当前查找起点。
     * @return 第一个可聚焦控件；没有找到时返回 null。
     */
    private View findFirstFocusableDescendant(View view) {
        if (view == null) {
            return null;
        }
        if (view.isFocusable()) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View result = findFirstFocusableDescendant(group.getChildAt(i));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * 刷新左侧导航按钮选中态。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:53:00</p>
     *
     * <p>说明：点击分类时不再重建导航栏，只更新现有按钮背景，避免 GridLayout 重建瞬间首页按钮闪一下。</p>
     */
    private void refreshHomeCategoryNavStates() {
        if (homeCategoryNav == null) {
            return;
        }
        for (int i = 0; i < homeCategoryNav.getChildCount(); i++) {
            View child = homeCategoryNav.getChildAt(i);
            if (child instanceof TextView) {
                TextView button = (TextView) child;
                String name = button.getText().toString();
                button.setBackground(makeHomeCategoryNavButtonBackground(name, button.hasFocus()));
            }
        }
    }

    /**
     * 创建顶部分类按钮。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:21:26</p>
     *
     * @param categoryName 分类名称。
     * @return 可聚焦、可点击的分类按钮。
     */
    private TextView createHomeCategoryNavButton(final String categoryName) {
        final TextView button = new TextView(this);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setGravity(Gravity.CENTER);
        button.setText(categoryName);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setBackground(makeHomeCategoryNavButtonBackground(categoryName, false));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                button.setBackground(makeHomeCategoryNavButtonBackground(categoryName, hasFocus));
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (HOME_NAV_NAME.equals(categoryName)) {
                    showAllHomeCategories();
                    return;
                }
                selectHomeCategory(categoryName);
            }
        });
        return button;
    }

    /**
     * 生成左侧主导航按钮背景。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 18:55:00</p>
     *
     * <p>说明：选择小分类时弹层会先隐藏，Android 焦点系统可能在一帧内把焦点临时给到左侧第一个“首页”按钮。
     * 分类页仍在加载时不允许首页按钮使用焦点高亮，避免用户看到“首页”一闪而过；
     * 详情页打开时由右侧可见控件承接焦点，左侧导航仍保留当前分类的选中态，方便用户知道自己从哪里进入详情。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:34:32
     * 修改说明：详情改为右侧内嵌展示后，隐藏网页加载页时系统焦点可能短暂回落到左侧首页按钮；
     * 详情态统一压制左侧导航视觉焦点，避免选完影片后首页按钮一闪而过。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:41:15
     * 修改说明：左侧导航在详情态仍需要保留当前分类选中态和用户主动切过去时的焦点态；
     * 只继续拦截分类加载期首页按钮的误高亮，避免导航状态整块消失。</p>
     *
     * @param categoryName 按钮文案。
     * @param focused 当前按钮是否获得系统焦点。
     * @return 左侧导航按钮背景。
     */
    private GradientDrawable makeHomeCategoryNavButtonBackground(String categoryName, boolean focused) {
        boolean selected = isHomeNavSelected(categoryName);
        boolean effectiveFocused = focused
                && !(HOME_NAV_NAME.equals(categoryName) && categoryNavigationState.isLoadingCategoryPage());
        return makeCategoryNavBackground(selected, effectiveFocused);
    }

    /**
     * 判断顶部导航按钮是否选中。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:30:50</p>
     *
     * <p>说明：首页状态没有真实分类名，统一用空字符串保存；按钮文案仍展示“首页”，
     * 因此需要单独判断首页选中态。详情面板显示时也要保留当前分类选中态，
     * 否则用户从影片列表进入详情后会误以为导航状态丢失。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:34:32
     * 修改说明：右侧详情面板显示期间不再给左侧导航计算选中态，消除影片打开时首页导航闪烁。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:41:15
     * 修改说明：恢复详情态下的导航选中态，仅依靠右侧焦点承接和分类加载态拦截解决首页误闪问题。</p>
     *
     * @param categoryName 按钮名称。
     * @return 是否选中。
     */
    private boolean isHomeNavSelected(String categoryName) {
        String activeCategoryName = categoryNavigationState.getActiveHomeNavCategoryName();
        if (HOME_NAV_NAME.equals(categoryName)) {
            return TextUtils.isEmpty(activeCategoryName) && !categoryNavigationState.isLoadingCategoryPage();
        }
        return categoryName != null && categoryName.equals(activeCategoryName);
    }

    /**
     * 进入指定首页分类。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:21:26</p>
     *
     * <p>说明：进入分类后只渲染该分类内容，避免“点击分类但仍看到所有分类”的歧义；
     * 如果分类名来自按钮但数据不存在，则直接提示异常，方便定位网站结构变化。</p>
     *
     * @param categoryName 分类名称。
     */
    private void selectHomeCategory(String categoryName) {
        VideoCategory category = findHomeCategoryByName(categoryName);
        if (category == null || TextUtils.isEmpty(category.url)) {
            showStatus("分类入口不存在：" + categoryName);
            Log.w(TAG, "======>>>>>>【首页分类按钮点击后未找到入口，category=" + categoryName + "】<<<<<<======");
            return;
        }
        stopDetailPreviewPlayback();
        hideInlineDetailPanel();
        categoryNavigationState.startMainCategoryLoad(categoryName);
        pendingCategoryContentFocusFilterName = "";
        showCategoryLoading("正在加载" + categoryName + "...");
        refreshHomeCategoryNavStates();
        requestSelectedHomeCategoryNavFocus();
        homeCategoryContainer.removeAllViews();
        homePosterTargets.clear();
        showStatus("正在加载" + categoryName + "分类...");
        rssWebView.loadUrl(category.url);
        Log.i(TAG, "======>>>>>>【加载网站分类页，category=" + categoryName + "，url=" + category.url + "】<<<<<<======");
    }

    /**
     * 返回全部首页分类。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:21:26</p>
     */
    private void showAllHomeCategories() {
        stopDetailPreviewPlayback();
        hideInlineDetailPanel();
        categoryNavigationState.showHome();
        refreshHomeCategoryNavStates();
        requestSelectedHomeCategoryNavFocus();
        homeScrollView.scrollTo(0, 0);
        renderCategorySections(currentHomeCategories);
        Log.i(TAG, "======>>>>>>【返回全部首页分类】<<<<<<======");
    }

    /**
     * 按名称查找首页分类。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:21:26</p>
     *
     * @param categoryName 分类名称。
     * @return 匹配的分类列表，供内容渲染复用统一入口。
     */
    private VideoCategory findHomeCategoryByName(String categoryName) {
        if (TextUtils.isEmpty(categoryName)) {
            return null;
        }
        for (VideoCategory category : currentHomeCategories) {
            if (category != null && categoryName.equals(category.name)) {
                return category;
            }
        }
        return null;
    }

    /**
     * 生成分类导航按钮背景。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:21:26</p>
     *
     * @param selected 是否当前选中分类。
     * @param focused 是否遥控器焦点态。
     * @return 分类按钮背景。
     */
    private GradientDrawable makeCategoryNavBackground(boolean selected, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(8));
        drawable.setColor(focused ? Color.rgb(255, 106, 0) : selected ? Color.rgb(58, 62, 74) : Color.rgb(35, 38, 48));
        drawable.setStroke(focused ? dp(4) : dp(2), focused ? Color.WHITE : selected ? Color.rgb(255, 106, 0) : Color.rgb(92, 96, 110));
        return drawable;
    }

    /**
     * 生成详情页大预览区背景。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:01:28</p>
     *
     * <p>说明：预览区播放前显示封面，播放后显示局部视频，同时需要参与遥控器焦点；
     * 使用高对比边框让用户知道确认键可以进入全屏。</p>
     *
     * @param focused 是否处于遥控器焦点态。
     * @return 预览区背景。
     */
    private GradientDrawable makePreviewBackground(boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(8));
        drawable.setColor(Color.rgb(10, 12, 18));
        drawable.setStroke(dp(2), Color.rgb(72, 76, 90));
        return drawable;
    }

    /**
     * 生成详情页大预览区焦点边框。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:04:50</p>
     *
     * <p>说明：播放器视图和封面图片都会铺在预览区内部，单靠容器背景边框容易被视觉上盖住；
     * 独立覆盖层放在最上方，确保遥控器选中预览区时有清晰白色焦点框。</p>
     *
     * @param focused 是否处于遥控器焦点态。
     * @return 焦点边框背景。
     */
    private GradientDrawable makePreviewFocusBorder(boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(8));
        drawable.setColor(Color.TRANSPARENT);
        drawable.setStroke(focused ? dp(6) : dp(0), focused ? Color.WHITE : Color.TRANSPARENT);
        return drawable;
    }

    /**
     * 计算首页卡片尺寸。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:07:18</p>
     *
     * <p>说明：电视盒子的 dp density 往往和网页视觉预期不一致，固定 292dp 会在 1080p 盒子上变成很大的像素宽度；
     * 这里直接按屏幕像素宽度切 5 列，让同一屏能看到更多剧集。布局参数本身接收像素值，因此计算结果不再二次套 dp。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:48:00
     * 修改说明：卡片宽度计算扣除左侧导航栏占用，避免内容区变窄后 5 列海报横向溢出。</p>
     *
     * @return 首页卡片列数、宽高、边距和底部文字区高度。
     */
    private HomeCardMetrics createHomeCardMetrics() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int margin = dp(5);
        int availableWidth = getHomeContentWidthPx() - margin * HOME_COLUMN_COUNT * 2;
        int cardWidth = availableWidth / HOME_COLUMN_COUNT;
        int cardHeight = cardWidth * 4 / 3;
        int overlayHeight = cardHeight * 3 / 10;
        HomeCardMetrics metrics = new HomeCardMetrics();
        metrics.columnCount = HOME_COLUMN_COUNT;
        metrics.cardWidth = cardWidth;
        metrics.cardHeight = cardHeight;
        metrics.posterDecodeWidth = cardWidth;
        metrics.posterDecodeHeight = cardHeight;
        metrics.marginHorizontal = margin;
        metrics.marginVertical = dp(6);
        metrics.overlayHeight = overlayHeight;
        Log.i(TAG, "======>>>>>>【首页卡片尺寸计算完成，contentWidth=" + getHomeContentWidthPx()
                + "，density=" + displayMetrics.density
                + "，columnCount=" + metrics.columnCount
                + "，cardWidth=" + metrics.cardWidth
                + "，cardHeight=" + metrics.cardHeight + "】<<<<<<======");
        return metrics;
    }

    /**
     * 获取右侧内容区可用宽度。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:34:00</p>
     *
     * <p>说明：左侧导航宽度、屏幕密度和真实布局宽度会共同影响内容区；
     * 卡片网格和分类筛选入口都走这里取宽，避免修改导航后每个模块各算各的导致显示不全。</p>
     *
     * @return 右侧内容区宽度，单位 px。
     */
    private int getHomeContentWidthPx() {
        if (homeScrollView != null && homeScrollView.getWidth() > 0) {
            return homeScrollView.getWidth();
        }
        return getResources().getDisplayMetrics().widthPixels - dp(48 + HOME_SIDE_NAV_WIDTH_DP);
    }

    /**
     * 计算右侧详情页选集按钮尺寸。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:35:54</p>
     *
     * <p>说明：详情页已经放在首页右侧内容区，不能再用固定三列或四列；
     * 这里先扣掉详情页左右内边距，再按目标按钮宽度推导列数，并限制按钮最大宽度，避免 1080p 盒子上按钮被拉得过大。
     * 布局参数接收 px，因此这里统一使用 px 计算，不能再把结果二次套 dp。</p>
     *
     * @return 右侧详情页选集网格尺寸。
     */
    private DetailEpisodeMetrics createDetailEpisodeMetrics() {
        int horizontalPadding = dp(28) * 2;
        int margin = dp(6);
        int contentWidth = getHomeContentWidthPx() - horizontalPadding;
        int targetButtonWidth = dp(176);
        int maxButtonWidth = dp(228);
        int minColumnCount = 2;
        int maxColumnCount = 7;
        int columnCount = contentWidth / targetButtonWidth;
        columnCount = Math.max(minColumnCount, Math.min(maxColumnCount, columnCount));
        int buttonWidth = calculateDetailEpisodeButtonWidth(contentWidth, margin, columnCount);
        while (buttonWidth > maxButtonWidth && columnCount < maxColumnCount) {
            columnCount++;
            buttonWidth = calculateDetailEpisodeButtonWidth(contentWidth, margin, columnCount);
        }
        DetailEpisodeMetrics metrics = new DetailEpisodeMetrics();
        metrics.columnCount = columnCount;
        metrics.buttonWidth = buttonWidth;
        metrics.buttonHeight = Math.max(dp(48), Math.min(dp(58), buttonWidth * 31 / 100));
        metrics.margin = margin;
        Log.i(TAG, "======>>>>>>【详情页选集尺寸计算完成，contentWidth=" + contentWidth
                + "，columnCount=" + metrics.columnCount
                + "，buttonWidth=" + metrics.buttonWidth
                + "，buttonHeight=" + metrics.buttonHeight + "】<<<<<<======");
        return metrics;
    }

    /**
     * 按列数计算选集按钮宽度。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:35:54</p>
     *
     * <p>说明：每个按钮左右都有 margin，GridLayout 的可用宽度必须先扣除全部左右间距；
     * 这样不同列数切换时不会出现最后一列横向溢出。</p>
     *
     * @param contentWidth 详情网格可用宽度，单位 px。
     * @param margin 单侧外边距，单位 px。
     * @param columnCount 当前列数。
     * @return 单个选集按钮宽度，单位 px。
     */
    private int calculateDetailEpisodeButtonWidth(int contentWidth, int margin, int columnCount) {
        return (contentWidth - margin * columnCount * 2) / columnCount;
    }

    /**
     * 创建首页分类标题文本。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:01:00</p>
     *
     * @param category 分类数据。
     * @return 分类标题文本控件。
     */
    private TextView createCategoryTitleText(VideoCategory category) {
        TextView titleView = new TextView(this);
        String name = category == null ? "" : category.name;
        int count = category == null || category.items == null ? 0 : category.items.size();
        String title = (TextUtils.isEmpty(name) ? "未命名分类" : name) + "  ·  " + count + " 部";
        if (category != null && !TextUtils.isEmpty(category.pageInfo)) {
            title = title + "  ·  " + category.pageInfo;
        }
        titleView.setText(title);
        titleView.setTextColor(Color.rgb(255, 170, 88));
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        return titleView;
    }

    /**
     * 在分类影片列表底部渲染分页和剧名搜索。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:02:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:26:00
     * 修改说明：移除分类底部搜索入口，分类底部只保留分页；剧名搜索改为全局悬浮入口和独立结果页。</p>
     *
     * @param category 分类数据。
     */
    private void renderCategoryBottomControls(final VideoCategory category) {
        if (category == null || category.totalPage <= 0) {
            return;
        }
        LinearLayout controlRow = new LinearLayout(this);
        controlRow.setOrientation(LinearLayout.HORIZONTAL);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);
        controlRow.setPadding(0, dp(6), 0, dp(22));

        TextView pageInfoView = new TextView(this);
        pageInfoView.setSingleLine(true);
        pageInfoView.setEllipsize(TextUtils.TruncateAt.END);
        pageInfoView.setGravity(Gravity.CENTER_VERTICAL);
        pageInfoView.setTextColor(Color.rgb(255, 170, 88));
        pageInfoView.setTextSize(20);
        pageInfoView.setTypeface(Typeface.DEFAULT_BOLD);
        pageInfoView.setText(TextUtils.isEmpty(category.pageInfo) ? "分页" : category.pageInfo);
        controlRow.addView(pageInfoView, new LinearLayout.LayoutParams(
                0,
                dp(42),
                1));

        TextView previousButton = createPageControlButton("上一页", category.previousPageUrl);
        controlRow.addView(previousButton, new LinearLayout.LayoutParams(dp(118), dp(42)));

        TextView nextButton = createPageControlButton("下一页", category.nextPageUrl);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(118), dp(42));
        nextParams.setMargins(dp(8), 0, dp(8), 0);
        controlRow.addView(nextButton, nextParams);

        TextView jumpButton = createJumpPageButton(category);
        LinearLayout.LayoutParams jumpParams = new LinearLayout.LayoutParams(dp(108), dp(42));
        jumpParams.setMargins(0, 0, dp(8), 0);
        controlRow.addView(jumpButton, jumpParams);

        homeCategoryContainer.addView(controlRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /**
     * 创建分类页翻页按钮。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 15:45:00</p>
     *
     * @param text 按钮文案。
     * @param url 翻页地址。
     * @return 翻页按钮。
     */
    private TextView createPageControlButton(String text, final String url) {
        final TextView button = new TextView(this);
        button.setFocusable(!TextUtils.isEmpty(url));
        button.setFocusableInTouchMode(!TextUtils.isEmpty(url));
        button.setClickable(!TextUtils.isEmpty(url));
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setText(text);
        button.setTextColor(TextUtils.isEmpty(url) ? Color.rgb(120, 124, 138) : Color.WHITE);
        button.setTextSize(17);
        button.setBackground(makeFilterOptionBackground(false, false));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                button.setBackground(makeFilterOptionBackground(false, hasFocus));
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadCategoryPageUrl(url, "翻页");
            }
        });
        return button;
    }

    /**
     * 创建页码跳转按钮。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 15:45:00</p>
     *
     * @param category 当前分类页数据。
     * @return 跳转按钮。
     */
    private TextView createJumpPageButton(final VideoCategory category) {
        final TextView button = new TextView(this);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setText("跳页");
        button.setTextColor(Color.WHITE);
        button.setTextSize(17);
        button.setBackground(makeFilterOptionBackground(false, false));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                button.setBackground(makeFilterOptionBackground(false, hasFocus));
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPageJumpDialog(category);
            }
        });
        return button;
    }

    /**
     * 创建剧名搜索按钮。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:02:00</p>
     *
     * @return 剧名搜索按钮。
     */
    private TextView createSearchTitleButton() {
        final TextView button = new TextView(this);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setText("搜索剧名");
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setBackground(makeFilterOptionBackground(false, false));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                button.setBackground(makeFilterOptionBackground(false, hasFocus));
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTitleSearchDialog();
            }
        });
        return button;
    }

    /**
     * 打开页码跳转输入框。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:02:00</p>
     *
     * <p>说明：遥控焦点移动到“跳页”按钮时不打开输入法，只有确认键点击按钮后才显示输入框。</p>
     */
    private void showPageJumpDialog(final VideoCategory category) {
        final EditText pageInput = createDialogInput("输入页码", InputType.TYPE_CLASS_NUMBER);
        if (category != null && category.currentPage > 0) {
            pageInput.setText(String.valueOf(category.currentPage));
            pageInput.setSelection(pageInput.getText().length());
        }
        new AlertDialog.Builder(this)
                .setTitle("跳转页码")
                .setView(pageInput)
                .setPositiveButton("跳转", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        jumpToCategoryPage(category, pageInput.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 跳转到用户输入的分类页页码。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 15:45:00</p>
     *
     * <p>说明：页码输入只接受 1 到总页数之间的数字；目标 URL 由原站分页链接推导，
     * 这样电影、电视剧、筛选和排序后的分页都沿用网站自己的路由格式。</p>
     *
     * @param category 当前分类页数据。
     * @param pageText 页码文本。
     */
    private void jumpToCategoryPage(VideoCategory category, String pageText) {
        if (TextUtils.isEmpty(pageText)) {
            showStatus("请输入页码");
            return;
        }
        int pageNumber;
        try {
            pageNumber = Integer.parseInt(pageText);
        } catch (NumberFormatException exception) {
            showStatus("页码格式不正确");
            return;
        }
        if (category == null || pageNumber < 1 || pageNumber > category.totalPage) {
            showStatus("页码范围：1-" + (category == null ? 1 : category.totalPage));
            return;
        }
        String url = CategoryPageUrlBuilder.build(category, pageNumber);
        if (TextUtils.isEmpty(url)) {
            showStatus("暂时无法生成该页地址");
            return;
        }
        loadCategoryPageUrl(url, "跳转到第" + pageNumber + "页");
    }

    /**
     * 打开剧名搜索输入框。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:02:00</p>
     *
     * <p>说明：这里走原网站剧名搜索路由，搜索结果仍复用分类页的影片列表、分页和底部搜索控件。</p>
     */
    private void showTitleSearchDialog() {
        final EditText searchInput = createDialogInput("输入剧名", InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("搜索剧名")
                .setView(searchInput)
                .setPositiveButton("搜索", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        searchVideoTitle(searchInput.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 创建弹窗输入框。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:02:00</p>
     *
     * @param hint 输入提示。
     * @param inputType 输入类型。
     * @return 弹窗输入框。
     */
    private EditText createDialogInput(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSelectAllOnFocus(false);
        return input;
    }

    /**
     * 按剧名搜索影片。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:02:00</p>
     *
     * @param keyword 剧名关键词。
     */
    private void searchVideoTitle(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            showStatus("请输入剧名");
            return;
        }
        stopDetailPreviewPlayback();
        categoryNavigationState.startSearchLoad(keyword);
        pendingCategoryContentFocusFilterName = "";
        showCategoryLoading("正在搜索：" + keyword + "...");
        showStatus("正在搜索：" + keyword);
        String keywordLiteral = WxjswTextHelper.quoteJavascriptString(keyword);
        String script = "javascript:(function(){"
                + "var keyword=" + keywordLiteral + ";"
                + "var input=document.getElementById('txt');"
                + "if(input){input.value=keyword;}"
                + "if(typeof qrsearch==='function'){qrsearch();}"
                + "else{location.href='/vodsearch/'+encodeURIComponent(keyword)+'-------------.html';}"
                + "})();";
        rssWebView.loadUrl(script);
        Log.i(TAG, "======>>>>>>【调用原站搜索函数，keyword=" + keyword + "】<<<<<<======");
    }

    /**
     * 加载分类分页地址。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 15:45:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 18:12:00
     * 修改说明：分类筛选、翻页和跳页统一走同一个加载入口；加载前只标记分类页状态并刷新内容区，
     * 不重建左侧主导航，避免选择小分类时导航栏闪动或被迟到首页回调覆盖。</p>
     *
     * @param url 目标分类页地址。
     * @param actionName 操作名称，用于状态和日志。
     */
    private void loadCategoryPageUrl(String url, String actionName) {
        loadCategoryPageUrl(url, actionName, "");
    }

    /**
     * 加载分类分页地址，并记录加载完成后需要恢复焦点的筛选分组。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 21:03:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 21:03:00
     * 修改说明：筛选项点击后保存筛选分组名称，分类页重新渲染完成后把焦点放回同一分组入口，
     * 避免选择“年份”后焦点错误回到“类型”。</p>
     *
     * @param url 目标分类页地址。
     * @param actionName 操作名称，用于状态和日志。
     * @param preferredFilterName 加载完成后优先恢复焦点的筛选分组名称。
     */
    private void loadCategoryPageUrl(String url, String actionName, String preferredFilterName) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        stopDetailPreviewPlayback();
        categoryNavigationState.startInlineCategoryLoad();
        pendingCategoryContentFocusFilterName = preferredFilterName;
        showCategoryLoading("正在" + actionName + "...");
        filterPanelController.hide();
        showStatus("正在" + actionName + "...");
        rssWebView.loadUrl(url);
        Log.i(TAG, "======>>>>>>【加载分类分页，action=" + actionName
                + "，focusFilter=" + preferredFilterName
                + "，url=" + url + "】<<<<<<======");
    }

    /**
     * 请求首页第一个影片卡片获得焦点。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:01:00</p>
     *
     * <p>说明：首页切回时不能再依赖单一网格控件，需要从分类容器里找到第一个 GridLayout 子项，
     * 保持返回列表后的遥控器操作入口稳定。</p>
     */
    private void requestFirstHomeCardFocus() {
        if (homeCategoryContainer == null) {
            return;
        }
        for (int i = 0; i < homeCategoryContainer.getChildCount(); i++) {
            View child = homeCategoryContainer.getChildAt(i);
            if (child instanceof GridLayout) {
                GridLayout gridLayout = (GridLayout) child;
                if (gridLayout.getChildCount() > 0) {
                    gridLayout.getChildAt(0).requestFocus();
                    return;
                }
            }
        }
    }

    /**
     * 渲染单个分类下的影片卡片。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:01:00</p>
     *
     * @param items 分类内影片数据。
     * @param sectionGrid 分类对应的网格容器。
     * @param cardMetrics 按屏幕实际像素计算出的卡片尺寸。
     */
    private void renderSectionItems(List<VideoItem> items, GridLayout sectionGrid, HomeCardMetrics cardMetrics) {
        for (int i = 0; i < items.size(); i++) {
            final VideoItem item = items.get(i);
            View card = createCard(item, cardMetrics);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardMetrics.cardWidth;
            params.height = cardMetrics.cardHeight;
            params.setMargins(cardMetrics.marginHorizontal, cardMetrics.marginVertical,
                    cardMetrics.marginHorizontal, cardMetrics.marginVertical);
            card.setLayoutParams(params);
            sectionGrid.addView(card);
        }
    }

    /**
     * 创建单个影片卡片。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 18:02:00
     * 修改说明：卡片变窄后底部信息层只保留标题和评分/集数两行，去掉更新时间，避免评分被裁切显示不全。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 18:08:00
     * 修改说明：增加标题行高度权重，保证剧名显示完整；评分/集数保留较小行高，避免重新裁切。</p>
     *
     * @param item 影片数据。
     * @param cardMetrics 按屏幕实际像素计算出的卡片尺寸。
     * @return 可聚焦、可点击的原生卡片。
     */
    private View createCard(final VideoItem item, final HomeCardMetrics cardMetrics) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        card.setBackground(makeCardBackground(false));

        final ImageView posterView = new ImageView(this);
        posterView.setBackgroundColor(Color.rgb(25, 28, 36));
        posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(posterView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout infoOverlay = new LinearLayout(this);
        infoOverlay.setOrientation(LinearLayout.VERTICAL);
        infoOverlay.setPadding(dp(12), dp(8), dp(12), dp(8));
        infoOverlay.setBackgroundColor(Color.argb(205, 7, 9, 14));
        card.addView(infoOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                cardMetrics.overlayHeight,
                Gravity.BOTTOM));

        TextView titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(14);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setText(WxjswTextHelper.getDisplayTitle(item));
        infoOverlay.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                3));

        TextView episodeView = new TextView(this);
        episodeView.setTextColor(Color.rgb(255, 170, 88));
        episodeView.setTextSize(12);
        episodeView.setMaxLines(1);
        episodeView.setEllipsize(TextUtils.TruncateAt.END);
        episodeView.setText(WxjswTextHelper.getDisplayEpisode(item));
        infoOverlay.addView(episodeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                2));

        registerHomePosterTarget(posterView, item.posterUrl, cardMetrics);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                view.setBackground(makeCardBackground(hasFocus));
                view.animate().scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f).setDuration(140).start();
                if (hasFocus) {
                    posterLoader.loadInto(posterView, item.posterUrl, cardMetrics, true);
                    Log.i(TAG, "======>>>>>>【原生卡片获得焦点，title=" + item.title + "】<<<<<<======");
                }
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openDetail(item);
            }
        });
        return card;
    }

    /**
     * 登记首页海报控件。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:15:34</p>
     *
     * <p>说明：卡片创建阶段只记录图片地址和目标控件，不立即下载所有图片；
     * 后续由 {@link #loadVisibleHomePosters()} 按当前滚动位置决定哪些海报进入加载队列。</p>
     *
     * @param imageView 海报控件。
     * @param posterUrl 海报地址。
     * @param cardMetrics 首页卡片尺寸。
     */
    private void registerHomePosterTarget(ImageView imageView, String posterUrl, HomeCardMetrics cardMetrics) {
        if (imageView == null || TextUtils.isEmpty(posterUrl)) {
            return;
        }
        imageView.setTag(posterUrl);
        PosterTarget target = new PosterTarget();
        target.imageView = imageView;
        target.url = posterUrl;
        target.targetWidth = cardMetrics.posterDecodeWidth;
        target.targetHeight = cardMetrics.posterDecodeHeight;
        homePosterTargets.add(target);
    }

    /**
     * 自动加载首页可视区域附近的海报。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:15:34</p>
     *
     * <p>说明：缓冲区取一个屏幕高度，用户往下滑时下一屏海报会提前进入队列；
     * 这样既不会一次性加载完整首页的几十张图片，也不会出现滑到下面只有占位图、必须逐个移焦点才加载的问题。</p>
     */
    private void loadVisibleHomePosters() {
        if (homeScrollView == null || homeCategoryContainer == null || homePosterTargets.isEmpty()) {
            return;
        }
        int visibleTop = homeScrollView.getScrollY() - homeScrollView.getHeight();
        int visibleBottom = homeScrollView.getScrollY() + homeScrollView.getHeight() * 2;
        for (PosterTarget target : homePosterTargets) {
            if (target == null || target.imageView == null || TextUtils.isEmpty(target.url)) {
                continue;
            }
            Rect rect = new Rect(0, 0, target.imageView.getWidth(), target.imageView.getHeight());
            homeCategoryContainer.offsetDescendantRectToMyCoords(target.imageView, rect);
            if (rect.bottom >= visibleTop && rect.top <= visibleBottom) {
                posterLoader.loadInto(target.imageView, target.url, target.targetWidth, target.targetHeight, false);
            }
        }
    }

    /**
     * 生成卡片背景，焦点态使用高对比橙色边框。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     *
     * @param focused 是否处于遥控器焦点态。
     * @return 卡片背景。
     */
    private GradientDrawable makeCardBackground(boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(10));
        drawable.setColor(focused ? Color.rgb(255, 106, 0) : Color.rgb(35, 38, 48));
        drawable.setStroke(focused ? dp(6) : dp(2), focused ? Color.WHITE : Color.rgb(72, 76, 90));
        return drawable;
    }

    /**
     * 打开原生详情页，并用隐藏 WebView 抓取网站详情 HTML 中的集数数据。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:08:00
     * 修改说明：详情页面由网站 WebView 改为原生布局，网站详情页只在隐藏 WebView 里提供播放集数链接。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:46:06
     * 修改说明：打开详情时先清空占位文案，等待网站详情 HTML 返回后再渲染封面、主演、导演等真实信息。</p>
     *
     * @param item 当前选中的影片。
     */
    private void openDetail(VideoItem item) {
        if (item == null || TextUtils.isEmpty(item.link)) {
            showStatus("影片链接为空，无法打开");
            return;
        }
        Log.i(TAG, "======>>>>>>【在右侧区域打开剧集详情，title=" + item.title + "，url=" + item.link + "】<<<<<<======");
        currentItem = item;
        showingDetailPage = true;
        showInlineDetailPanel();
        playerController.getPlayerView().setVisibility(View.GONE);
        detailTitleView.setText(item.title == null ? "未命名影片" : item.title);
        detailInfoView.setText("");
        detailStaffView.setText("");
        detailDescriptionView.setText(TextUtils.isEmpty(item.description) ? "" : item.description);
        detailPosterView.setImageDrawable(null);
        detailPosterView.setVisibility(View.VISIBLE);
        detailInlinePlayerHost.setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(item.posterUrl)) {
            posterLoader.loadInto(detailPosterView, item.posterUrl, dp(420), dp(236), true);
        }
        detailEpisodeHintView.setText("正在读取详情和集数...");
        detailEpisodeHintView.setVisibility(View.VISIBLE);
        episodeGrid.removeAllViews();
        showStatus("正在读取详情数据...");
        rssWebView.loadUrl(item.link);
    }

    /**
     * 渲染网站详情页解析出的影片信息。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:46:06</p>
     *
     * <p>说明：详情面板顶部始终保留标题和封面；年份、地区、类型、状态、主演、导演等字段有值才显示。
     * 不再为缺失字段拼接“未知”或“详情数据来自”等兜底文案，避免用户看到没有信息量的占位内容。</p>
     *
     * @param detailInfo 从网站详情 HTML 解析出的影片信息。
     */
    private void renderDetailInfo(VideoDetailInfo detailInfo) {
        if (detailInfo == null) {
            return;
        }
        if (!TextUtils.isEmpty(detailInfo.title)) {
            detailTitleView.setText(detailInfo.title);
        }
        String posterUrl = TextUtils.isEmpty(detailInfo.posterUrl)
                ? currentItem == null ? "" : currentItem.posterUrl
                : detailInfo.posterUrl;
        if (!TextUtils.isEmpty(posterUrl)) {
            posterLoader.loadInto(detailPosterView, posterUrl, dp(420), dp(236), true);
        }
        detailInfoView.setText(buildDetailMetaText(detailInfo));
        detailStaffView.setText(buildDetailStaffText(detailInfo));
        String description = TextUtils.isEmpty(detailInfo.description)
                ? currentItem == null ? "" : currentItem.description
                : detailInfo.description;
        detailDescriptionView.setText(TextUtils.isEmpty(description) ? "" : "简介：" + description);
    }

    /**
     * 拼接年份、地区、类型和状态信息。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:46:06</p>
     *
     * <p>说明：这些字段来自同一行基础资料，用间隔符合并展示，字段为空时直接跳过，保持右侧详情简洁。</p>
     *
     * @param detailInfo 详情信息。
     * @return 可直接展示的基础资料文案。
     */
    private String buildDetailMetaText(VideoDetailInfo detailInfo) {
        List<String> parts = new ArrayList<String>();
        addDetailPart(parts, detailInfo.year);
        addDetailPart(parts, detailInfo.area);
        addDetailPart(parts, detailInfo.type);
        addDetailPart(parts, detailInfo.status);
        addDetailPart(parts, TextUtils.isEmpty(detailInfo.updateDate) ? "" : "更新 " + detailInfo.updateDate);
        return joinDetailParts(parts, " / ");
    }

    /**
     * 拼接主演和导演信息。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:46:06</p>
     *
     * <p>说明：主演列表可能很长，TextView 会按行数自动省略；这里仅负责拼接有值字段，不生成无意义占位。</p>
     *
     * @param detailInfo 详情信息。
     * @return 可直接展示的演职员文案。
     */
    private String buildDetailStaffText(VideoDetailInfo detailInfo) {
        List<String> parts = new ArrayList<String>();
        if (!TextUtils.isEmpty(detailInfo.actors)) {
            parts.add("主演：" + detailInfo.actors);
        }
        if (!TextUtils.isEmpty(detailInfo.director)) {
            parts.add("导演：" + detailInfo.director);
        }
        return joinDetailParts(parts, "\n");
    }

    /**
     * 追加非空详情字段。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:46:06</p>
     *
     * @param parts 待展示字段列表。
     * @param value 当前字段值。
     */
    private void addDetailPart(List<String> parts, String value) {
        if (!TextUtils.isEmpty(value)) {
            parts.add(value);
        }
    }

    /**
     * 合并详情字段。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:46:06</p>
     *
     * @param parts 待展示字段列表。
     * @param separator 分隔符。
     * @return 合并后的文案。
     */
    private String joinDetailParts(List<String> parts, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(separator);
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    /**
     * 渲染原生详情页集数按钮。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:08:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:35:54
     * 修改说明：详情页移到首页右侧区域后，选集按钮改为按右侧真实宽度计算，减少单个按钮占屏比例。</p>
     *
     * @param episodes 从详情页 HTML 解析出的播放集数。
     */
    private void renderEpisodes(List<EpisodeItem> episodes) {
        episodeGrid.removeAllViews();
        if (episodes == null || episodes.isEmpty()) {
            playerController.setEpisodes(new ArrayList<EpisodeItem>());
            detailEpisodeHintView.setText("暂未读取到播放集数，请稍后返回重试");
            detailEpisodeHintView.setVisibility(View.VISIBLE);
            detailEpisodeHintView.requestFocus();
            showStatus("详情页未解析到集数");
            Log.w(TAG, "======>>>>>>【详情页未解析到集数，title=" + (currentItem == null ? "" : currentItem.title) + "】<<<<<<======");
            return;
        }
        playerController.setEpisodes(episodes);
        detailEpisodeHintView.setText("");
        detailEpisodeHintView.setVisibility(View.GONE);
        hideStatus();
        DetailEpisodeMetrics metrics = createDetailEpisodeMetrics();
        episodeGrid.setColumnCount(metrics.columnCount);
        for (int i = 0; i < episodes.size(); i++) {
            final EpisodeItem episode = episodes.get(i);
            TextView button = createEpisodeButton(episode);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = metrics.buttonWidth;
            params.height = metrics.buttonHeight;
            params.setMargins(metrics.margin, metrics.margin, metrics.margin, metrics.margin);
            button.setLayoutParams(params);
            episodeGrid.addView(button);
            if (i == 0) {
                button.requestFocus();
            }
        }
        refreshHomeCategoryNavStates();
        Log.i(TAG, "======>>>>>>【原生详情页集数渲染完成，count=" + episodes.size() + "】<<<<<<======");
    }

    /**
     * 创建原生集数按钮。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:08:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:35:54
     * 修改说明：选集按钮跟随右侧内嵌详情页缩小字号，保证按钮变窄后长集数名称仍能优雅省略。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:51:14
     * 修改说明：集数按钮点击后先启动右侧局部播放器，避免用户选中某集时直接跳到全屏播放页。</p>
     *
     * @param episode 集数名称和播放页链接。
     * @return 适配遥控器焦点的按钮控件。
     */
    private TextView createEpisodeButton(final EpisodeItem episode) {
        TextView button = new TextView(this);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setText(episode.name);
        button.setBackground(makeCardBackground(false));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                view.setBackground(makeCardBackground(hasFocus));
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                lastInlineEpisodeFocusView = view;
                openNativePlayer(episode);
            }
        });
        return button;
    }

    /**
     * 在首页右侧内容区显示剧集详情。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 21:20:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 21:20:00
     * 修改说明：剧集详情不再切换到独立全屏页面，而是复用首页右侧内容区展示标题、简介和选集；
     * 左侧分类导航保持可见，用户返回时只从详情面板回到右侧影片列表。</p>
     */
    private void showInlineDetailPanel() {
        homeScrollView.setVisibility(View.GONE);
        detailView.setVisibility(View.VISIBLE);
        homeView.setVisibility(View.VISIBLE);
        // 详情数据和集数按钮还在解析时，先让右侧可见提示行承接焦点，避免系统把焦点临时退回左侧首页导航。
        detailEpisodeHintView.requestFocus();
        refreshHomeCategoryNavStates();
    }

    /**
     * 隐藏右侧详情面板并恢复影片列表。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 21:20:00</p>
     */
    private void hideInlineDetailPanel() {
        showingDetailPage = false;
        detailPosterView.setVisibility(View.VISIBLE);
        detailView.setVisibility(View.GONE);
        homeScrollView.setVisibility(View.VISIBLE);
        setFloatingSearchVisible(true);
        refreshHomeCategoryNavStates();
    }

    /**
     * 停止详情页局部预览播放。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:25:24</p>
     *
     * <p>说明：局部预览播放器会在详情页内持续播放；一旦用户切到分类、首页、搜索或分页等其它内容页，
     * 当前详情已经不再可见，必须主动停止并隐藏播放器，避免视频在后台继续播放占用解码器和网络。</p>
     */
    private void stopDetailPreviewPlayback() {
        if (playerController != null && playerController.isShowingPlayerPage()) {
            playerController.stopAndHide();
        }
        if (detailInlinePlayerHost != null) {
            detailInlinePlayerHost.setVisibility(View.VISIBLE);
        }
        if (detailPosterView != null) {
            detailPosterView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 处理右侧详情页和左侧导航之间的左右方向键。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:41:15</p>
     *
     * <p>说明：详情页嵌入首页右侧后，Android 默认焦点搜索会在左侧导航、提示行、ScrollView 和集数按钮之间自由选择；
     * 其中 ScrollView 与详情根容器没有可见焦点样式，焦点落上去就会像“光标消失”。这里只接管跨区域的左右移动：
     * 左侧导航按右键回到详情主焦点，集数第一列或加载提示行按左键回到当前导航；集数网格内部的左右移动仍交给系统处理。</p>
     *
     * @param keyCode 当前遥控器按键码。
     * @return 是否已经处理本次按键。
     */
    private boolean handleDetailDirectionalKey(int keyCode) {
        if (!showingDetailPage
                || (keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                && keyCode != KeyEvent.KEYCODE_DPAD_UP
                && keyCode != KeyEvent.KEYCODE_DPAD_DOWN)) {
            return false;
        }
        View focusedView = getCurrentFocus();
        if (focusedView == null || focusedView == detailView || focusedView == homeContentPanel) {
            requestDetailPrimaryFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isViewInside(focusedView, homeCategoryNav)) {
            requestDetailPrimaryFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && shouldMoveDetailFocusToHomeNav(focusedView)) {
            requestSelectedHomeCategoryNavFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                && shouldMoveEpisodeFocusToPreview(focusedView)
                && playerController.isInlineMode()) {
            detailInlinePlayerHost.requestFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && focusedView == detailInlinePlayerHost) {
            requestDetailPrimaryFocus();
            return true;
        }
        return false;
    }

    /**
     * 判断集数焦点是否应该上移到预览区。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:24:20</p>
     *
     * <p>说明：预览区只应该从集数第一行按上进入；集数网格内部的左右键和非第一行上下键必须交给系统处理。
     * 上一版把右键也拦到预览区，导致集数无法正常左右选择。</p>
     *
     * @param focusedView 当前焦点控件。
     * @return 是否应该把焦点移到预览区。
     */
    private boolean shouldMoveEpisodeFocusToPreview(View focusedView) {
        if (!isViewInside(focusedView, episodeGrid)) {
            return false;
        }
        int index = episodeGrid.indexOfChild(focusedView);
        if (index < 0) {
            return false;
        }
        int columnCount = Math.max(1, episodeGrid.getColumnCount());
        return index < columnCount;
    }

    /**
     * 判断详情页当前焦点是否应该左移回左侧导航。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:41:15</p>
     *
     * <p>说明：提示行本身没有横向邻居，左键直接回导航；集数按钮只有位于第一列时才回导航，
     * 其它列继续由系统在集数网格内部移动，保证正常选集手感。</p>
     *
     * @param focusedView 当前获得焦点的控件。
     * @return 是否需要把焦点移回左侧导航。
     */
    private boolean shouldMoveDetailFocusToHomeNav(View focusedView) {
        if (focusedView == detailEpisodeHintView) {
            return true;
        }
        if (focusedView == detailInlinePlayerHost) {
            return true;
        }
        if (!isViewInside(focusedView, episodeGrid)) {
            return false;
        }
        int index = episodeGrid.indexOfChild(focusedView);
        if (index < 0) {
            return false;
        }
        int columnCount = Math.max(1, episodeGrid.getColumnCount());
        return index % columnCount == 0;
    }

    /**
     * 请求详情页主焦点。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:41:15</p>
     *
     * <p>说明：集数已经渲染时优先回到第一个集数按钮；仍在加载或解析失败时回到可见提示行。
     * 这个方法只请求有焦点样式的控件，避免焦点落到不可见容器。</p>
     */
    private void requestDetailPrimaryFocus() {
        if (episodeGrid != null && episodeGrid.getChildCount() > 0) {
            episodeGrid.getChildAt(0).requestFocus();
            return;
        }
        if (detailEpisodeHintView != null) {
            detailEpisodeHintView.requestFocus();
        }
    }

    /**
     * 判断一个控件是否属于指定父容器。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:41:15</p>
     *
     * <p>说明：遥控器焦点可能落在按钮内部或容器子层级上，不能只做对象相等判断；
     * 逐级向上查父节点可以稳定识别当前焦点属于左侧导航还是右侧集数网格。</p>
     *
     * @param view 当前焦点控件。
     * @param parent 需要判断的父容器。
     * @return 当前控件是否在父容器内部。
     */
    private boolean isViewInside(View view, ViewGroup parent) {
        if (view == null || parent == null) {
            return false;
        }
        View current = view;
        while (current != null) {
            if (current == parent) {
                return true;
            }
            if (!(current.getParent() instanceof View)) {
                return false;
            }
            current = (View) current.getParent();
        }
        return false;
    }

    /**
     * 打开原生播放器，并通过隐藏 WebView 读取播放页里的真实视频地址。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:08:00</p>
     *
     * @param episode 当前选择的播放集数。
     */
    private void openNativePlayer(EpisodeItem episode) {
        if (episode == null || TextUtils.isEmpty(episode.url)) {
            showStatus("播放链接为空，无法播放");
            return;
        }
        showingDetailPage = true;
        homeScrollView.setVisibility(View.GONE);
        detailView.setVisibility(View.VISIBLE);
        homeView.setVisibility(View.VISIBLE);
        detailInlinePlayerHost.setVisibility(View.VISIBLE);
        detailPosterView.setVisibility(View.GONE);
        setFloatingSearchVisible(false);
        playerController.openEpisodeInline(episode, currentItem == null ? "" : currentItem.title, detailInlinePlayerHost);
        bringDetailPreviewFocusBorderToFront();
        restoreInlineEpisodeFocus();
        showStatus("正在解析播放地址...");
        Log.i(TAG, "======>>>>>>【读取播放页数据，episode=" + episode.name + "，url=" + episode.url + "】<<<<<<======");
        rssWebView.loadUrl(episode.url);
    }

    /**
     * 使用解析到的 m3u8 地址启动系统播放器。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:08:00</p>
     *
     * @param videoUrl 播放页 player_aaaa.url 字段中的真实视频地址。
     */
    private void playNativeVideo(String videoUrl) {
        hideStatus();
        playerController.playVideo(videoUrl);
        if (playerController.isInlineMode()) {
            restoreInlineEpisodeFocus();
        }
    }

    /**
     * 显示分类加载遮罩，并设置 10 秒自动解锁。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:45:00</p>
     *
     * <p>说明：隐藏 WebView 的分类页请求没有可靠的网络超时回调，必须由原生侧兜住用户体验。
     * 超时只解除操作锁，不取消 WebView 请求；如果迟到的分类 HTML 最终返回，仍允许正常渲染。</p>
     *
     * @param message 遮罩提示文案。
     */
    private void showCategoryLoading(String message) {
        categoryLoadingController.show(message, new Runnable() {
            @Override
            public void run() {
                categoryNavigationState.finishCategoryLoad();
                pendingCategoryContentFocusFilterName = "";
                refreshHomeCategoryNavStates();
                showStatus("分类加载超过10秒，已恢复操作");
                Log.w(TAG, "======>>>>>>【分类加载超过10秒，已解除操作锁】<<<<<<======");
            }
        });
    }

    /**
     * 隐藏分类加载遮罩。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:45:00</p>
     */
    private void hideCategoryLoading() {
        categoryLoadingController.hide();
    }

    /**
     * 处理返回键：播放器全屏 -> 网站详情页后退 -> 原生首页。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:21:26
     * 修改说明：在首页分类内容内按返回键先回到全部分类首页，避免用户进入电影、电视剧等分类后只能退出应用。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:45:23
     * 修改说明：筛选弹层打开时接管方向键，防止 GridLayout 边缘焦点逃逸到底层分类页。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 14:47:49
     * 修改说明：筛选弹层方向键不再依赖系统焦点搜索，改为按 5 列网格手动计算目标按钮，解决老盒子上方向键无法移动的问题。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:26:00
     * 修改说明：列表页菜单键打开全局剧名搜索弹窗，和右上角悬浮搜索入口保持同一套逻辑。</p>
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (categoryLoadingController.isBlocking()) {
            Log.i(TAG, "======>>>>>>【分类加载中，拦截遥控器按键，keyCode=" + keyCode + "】<<<<<<======");
            return true;
        }
        if (filterPanelController.isShowing()) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                filterPanelController.hide();
                return true;
            }
            if (filterPanelController.handleKey(keyCode)) {
                return true;
            }
        }
        if (shouldPlayerHandleKey() && playerController.handleKeyDown(keyCode, event)) {
            return true;
        }
        if (handleDetailDirectionalKey(keyCode)) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (playerController.isShowingPlayerPage()) {
                showDetailFromPlayer();
                return true;
            }
            if (showingDetailPage) {
                showHome();
                return true;
            }
            if (!TextUtils.isEmpty(categoryNavigationState.getActiveHomeNavCategoryName())) {
                showAllHomeCategories();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU && !showingDetailPage && !playerController.isShowingPlayerPage()) {
            showTitleSearchDialog();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 判断当前按键是否应该交给播放器处理。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:54:40</p>
     *
     * <p>说明：全屏播放器需要完整接管方向键、确认键和菜单键；详情页局部播放只是在右侧显示画面，
     * 遥控器焦点仍应留在集数按钮上，方便用户继续上下左右选集。只有焦点真的落在播放器视图内部时，
     * 局部播放器才消费播放控制按键。</p>
     *
     * @return 是否把本次按键优先交给播放器。
     */
    private boolean shouldPlayerHandleKey() {
        if (!playerController.isShowingPlayerPage()) {
            return false;
        }
        if (!playerController.isInlineMode()) {
            return true;
        }
        if (playerController.containsView(getCurrentFocus())) {
            restoreInlineEpisodeFocus();
        }
        return false;
    }

    /**
     * 恢复局部播放时的选集按钮焦点。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:58:20</p>
     *
     * <p>说明：局部播放器只负责展示画面，不参与遥控器焦点链路；选择集数后必须把焦点还给刚刚点击的集数按钮，
     * 否则 Android TV 可能把焦点落到 VideoView 或隐藏提示行，用户就会感觉遥控器失灵。</p>
     */
    private void restoreInlineEpisodeFocus() {
        final View targetView = lastInlineEpisodeFocusView != null && lastInlineEpisodeFocusView.getParent() == episodeGrid
                ? lastInlineEpisodeFocusView
                : episodeGrid.getChildCount() > 0 ? episodeGrid.getChildAt(0) : null;
        if (targetView == null) {
            return;
        }
        targetView.post(new Runnable() {
            @Override
            public void run() {
                targetView.requestFocus();
            }
        });
    }

    /**
     * 从详情页大预览区进入全屏播放。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:01:28</p>
     *
     * <p>说明：预览区播放前只是封面，按确认不做跳转；只有已经处于局部播放时，
     * 才把同一个播放器切换到全屏，避免用户误触封面导致空播放器打开。</p>
     */
    private void enterFullscreenFromInlinePreview() {
        if (!playerController.isShowingPlayerPage() || !playerController.isInlineMode()) {
            return;
        }
        showingDetailPage = false;
        setFloatingSearchVisible(false);
        playerController.enterFullscreenFromInline();
    }

    /**
     * 确保详情页预览区焦点边框位于最上层。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:18:50</p>
     *
     * <p>说明：视频开始播放后，播放器视图会挂载到预览容器中，可能盖住原先添加的焦点边框；
     * 每次播放器挂载或预览区获得焦点时，都把独立边框提到最上层，保证播放状态下也能看到选中效果。</p>
     */
    private void bringDetailPreviewFocusBorderToFront() {
        if (detailPreviewFocusBorder == null) {
            return;
        }
        detailPreviewFocusBorder.bringToFront();
        detailPreviewFocusBorder.invalidate();
    }

    /**
     * 分发音量键并同步播放器音量显示。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 15:39:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 15:39:00
     * 修改说明：音量键不再被应用层拦截，先交给盒子系统处理，避免 HDMI/电视音量通道被应用吞掉；
     * 播放器只在按键释放后读取当前媒体音量并刷新底部提示。</p>
     *
     * @param event 遥控器按键事件。
     * @return 系统按键分发结果。
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (categoryLoadingController.isBlocking()) {
            return true;
        }
        if (playerController.isInlineMode()
                && event.getAction() == KeyEvent.ACTION_DOWN
                && playerController.containsView(getCurrentFocus())
                && event.getKeyCode() != KeyEvent.KEYCODE_BACK
                && event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_UP
                && event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) {
            restoreInlineEpisodeFocus();
            return true;
        }
        boolean handled = super.dispatchKeyEvent(event);
        int keyCode = event.getKeyCode();
        if (playerController.isShowingPlayerPage()
                && event.getAction() == KeyEvent.ACTION_UP
                && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            playerController.onVolumeKeyUp(keyCode);
        }
        return handled;
    }

    /**
     * 从详情页返回原生首页。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     */
    private void showHome() {
        Log.i(TAG, "======>>>>>>【返回原生影片列表】<<<<<<======");
        hideCategoryLoading();
        playerController.stopAndHide();
        rssWebView.stopLoading();
        hideInlineDetailPanel();
        homeView.setVisibility(View.VISIBLE);
        hideStatus();
        requestSelectedHomeCategoryNavFocus();
    }

    /**
     * 从原生播放器返回原生详情页。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:08:00</p>
     *
     * <p>说明：停止 VideoView 后回到集数网格，避免老盒子后台继续解码占用 CPU。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 17:08:00
     * 修改说明：如果全屏播放来自详情页预览区，返回时不停止播放器，而是把同一个播放器挂回预览区继续显示。</p>
     */
    private void showDetailFromPlayer() {
        Log.i(TAG, "======>>>>>>【从原生播放器返回详情页，episode=" + getCurrentEpisodeName() + "】<<<<<<======");
        showingDetailPage = true;
        homeScrollView.setVisibility(View.GONE);
        detailView.setVisibility(View.VISIBLE);
        homeView.setVisibility(View.VISIBLE);
        if (playerController.isFullscreenFromInlineMode()) {
            detailInlinePlayerHost.setVisibility(View.VISIBLE);
            detailPosterView.setVisibility(View.GONE);
            playerController.returnFullscreenToInline(detailInlinePlayerHost);
            bringDetailPreviewFocusBorderToFront();
        } else {
            playerController.exitToDetail();
            detailPosterView.setVisibility(View.VISIBLE);
        }
        hideStatus();
        restoreInlineEpisodeFocus();
    }

    /**
     * 检测网络状态。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     */
    private boolean isNetworkConnected() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) {
            Log.w(TAG, "======>>>>>>【网络服务不可用，无法判断连接状态】<<<<<<======");
            return false;
        }
        NetworkInfo info = manager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    /**
     * 记录状态信息。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:41:00
     * 修改说明：取消界面顶部状态条展示，避免“正在加载”等提示长期停在最上方遮挡顶部导航；状态仅保留日志。</p>
     */
    private void showStatus(String message) {
        Log.i(TAG, "======>>>>>>【状态提示已记录但不展示，message=" + message + "】<<<<<<======");
        hideStatus();
    }

    /**
     * 隐藏顶部状态提示。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     */
    private void hideStatus() {
        if (statusView != null) {
            statusView.setVisibility(View.GONE);
        }
    }

    /**
     * 控制全局悬浮搜索入口显示状态。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:26:00</p>
     *
     * @param visible 是否显示。
     */
    private void setFloatingSearchVisible(boolean visible) {
        if (floatingSearchButton != null) {
            floatingSearchButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * dp 转 px。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 处理 RSS XML 回调。
     */
    private void handleRssXml(String xml) {
        try {
            if (TextUtils.isEmpty(xml)) {
                showStatus("影片列表为空，请稍后重试");
                return;
            }
            currentHomeItems = WxjswDataParser.parseRss(xml);
            rssWebView.loadUrl(HOME_URL);
        } catch (Exception e) {
            Log.e(TAG, "======>>>>>>【RSS XML 解析失败】<<<<<<======", e);
            showStatus("影片列表解析失败，请稍后重试");
        }
    }

    /**
     * 处理网站首页 HTML 回调。
     */
    private void handleHomeHtml(String html) {
        if (categoryNavigationState.isLoadingCategoryPage()) {
            Log.i(TAG, "======>>>>>>【分类页加载中，丢弃迟到的首页回调，pendingCategory=" + categoryNavigationState.getPendingCategoryName() + "】<<<<<<======");
            return;
        }
        List<VideoCategory> categories = WxjswDataParser.mergeHomeCategoryMetadata(
                currentHomeItems,
                WxjswDataParser.parseHomeCategories(html));
        if (categories.isEmpty()) {
            Log.w(TAG, "======>>>>>>【首页分类为空，停止渲染并提示用户稍后重试】<<<<<<======");
            homeCategoryContainer.removeAllViews();
            showStatus("首页分类解析失败，请稍后重试");
            return;
        }
        renderHomeCategories(categories);
    }

    /**
     * 处理分类页 HTML 回调。
     */
    private void handleCategoryHtml(String url, String html) {
        VideoCategory category = WxjswDataParser.parseCategoryPage(
                url,
                html,
                categoryNavigationState.getPendingCategoryName(),
                categoryNavigationState.getPendingSearchKeyword());
        if (category.items == null || category.items.isEmpty()) {
            hideCategoryLoading();
            categoryNavigationState.finishCategoryLoad();
            pendingCategoryContentFocusFilterName = "";
            refreshHomeCategoryNavStates();
            showStatus("分类页未解析到影片：" + category.name);
            Log.w(TAG, "======>>>>>>【分类页未解析到影片，category=" + category.name + "，url=" + url + "】<<<<<<======");
            return;
        }
        hideCategoryLoading();
        List<VideoCategory> categories = new ArrayList<VideoCategory>();
        categories.add(category);
        homeScrollView.scrollTo(0, 0);
        hideStatus();
        renderCategorySections(categories);
        if (categoryNavigationState.consumeFocusHomeNavAfterCategoryLoad()) {
            requestSelectedHomeCategoryNavFocus();
            categoryNavigationState.finishCategoryLoad();
            pendingCategoryContentFocusFilterName = "";
            refreshHomeCategoryNavStates();
        } else {
            final String focusFilterName = pendingCategoryContentFocusFilterName;
            pendingCategoryContentFocusFilterName = "";
            requestCategoryContentFocus(focusFilterName, true);
        }
    }

    /**
     * 处理详情页 HTML 回调。
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 16:46:06
     * 修改说明：详情 HTML 返回后同时解析影片资料和集数，右侧详情页展示原网站封面、主演、导演等真实信息。</p>
     */
    private void handleDetailHtml(String url, String html) {
        Log.i(TAG, "======>>>>>>【收到详情页 HTML，url=" + url + "】<<<<<<======");
        renderDetailInfo(WxjswDataParser.parseDetailInfo(html));
        renderEpisodes(WxjswDataParser.parseEpisodes(html));
    }

    /**
     * 处理播放页 HTML 回调。
     */
    private void handlePlayPageHtml(String url, String html) {
        Log.i(TAG, "======>>>>>>【收到播放页 HTML，url=" + url + "】<<<<<<======");
        playNativeVideo(WxjswDataParser.parseVideoUrl(html));
    }

    /**
     * 获取当前集数名，统一日志和播放页提示。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 14:08:00</p>
     */
    private String getCurrentEpisodeName() {
        return playerController == null ? "未知集数" : playerController.getCurrentEpisodeName();
    }

    /**
     * 销毁 WebView，释放老盒子内存。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 13:12:00</p>
     */
    @Override
    protected void onDestroy() {
        homePosterTargets.clear();
        if (posterLoader != null) {
            posterLoader.destroy();
            posterLoader = null;
        }
        if (playerController != null) {
            playerController.destroy();
            playerController = null;
        }
        if (categoryLoadingController != null) {
            categoryLoadingController.destroy();
            categoryLoadingController = null;
        }
        if (rssWebView != null) {
            rssWebView.stopLoading();
            rssWebView.loadUrl("about:blank");
            rssWebView.clearHistory();
            rssWebView.removeAllViews();
            rssWebView.destroy();
            rssWebView = null;
        }
        super.onDestroy();
    }
}
