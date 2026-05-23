package com.gc.wxjswtv;

import android.text.TextUtils;

/**
 * 分类导航状态管理器。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:42:00</p>
 *
 * <p>说明：左侧主导航、右侧筛选结果和隐藏 WebView 加载回调之间存在异步时序。
 * 这个类集中保存“当前主分类、正在加载的分类、搜索关键词、加载完成后焦点归属”等状态，
 * 避免 MainActivity 在多个回调里直接改散落字段，导致首页按钮短暂误判为选中。</p>
 */
class CategoryNavigationState {
    private String selectedCategoryName = "";
    private String pendingCategoryName = "";
    private String pendingSearchKeyword = "";
    private boolean loadingCategoryPage;
    private boolean focusHomeNavAfterCategoryLoad;

    /**
     * 进入左侧主分类。
     *
     * <p>说明：主分类点击后加载完成应回到左侧按钮，方便用户继续切换电影、电视剧等入口。</p>
     *
     * @param categoryName 主分类名称。
     */
    void startMainCategoryLoad(String categoryName) {
        selectedCategoryName = categoryName;
        pendingCategoryName = categoryName;
        loadingCategoryPage = true;
        focusHomeNavAfterCategoryLoad = true;
    }

    /**
     * 返回首页聚合内容。
     *
     * <p>说明：首页没有真实分类名，因此清空主分类和待加载分类，并关闭分类加载态。</p>
     */
    void showHome() {
        selectedCategoryName = "";
        pendingCategoryName = "";
        pendingSearchKeyword = "";
        loadingCategoryPage = false;
        focusHomeNavAfterCategoryLoad = false;
    }

    /**
     * 进入搜索结果页。
     *
     * <p>说明：搜索结果没有对应左侧主分类按钮，加载期间不能让首页按钮借空分类状态变成选中态。</p>
     *
     * @param keyword 搜索关键词。
     */
    void startSearchLoad(String keyword) {
        selectedCategoryName = "";
        pendingCategoryName = "搜索：" + keyword;
        pendingSearchKeyword = keyword;
        loadingCategoryPage = true;
        focusHomeNavAfterCategoryLoad = false;
    }

    /**
     * 进入右侧内容流触发的分类页。
     *
     * <p>说明：小分类、排序、翻页和跳页都属于当前主分类内部操作。
     * 加载期间继续用当前主分类维持左侧选中态，加载完成后焦点回右侧内容区。</p>
     */
    void startInlineCategoryLoad() {
        if (!TextUtils.isEmpty(selectedCategoryName)) {
            pendingCategoryName = selectedCategoryName;
        }
        loadingCategoryPage = true;
        focusHomeNavAfterCategoryLoad = false;
    }

    /**
     * 标记分类页解析完成。
     */
    void finishCategoryLoad() {
        loadingCategoryPage = false;
    }

    /**
     * 消费“分类加载后是否回左侧导航”的标记。
     *
     * @return true 表示焦点应回左侧主导航；false 表示焦点应回右侧内容区。
     */
    boolean consumeFocusHomeNavAfterCategoryLoad() {
        boolean result = focusHomeNavAfterCategoryLoad;
        focusHomeNavAfterCategoryLoad = false;
        return result;
    }

    /**
     * 获取左侧导航当前应呈现选中的主分类。
     *
     * <p>说明：加载期间优先使用 pendingCategoryName，避免迟到回调造成“首页”短暂选中。</p>
     *
     * @return 主分类名称；没有主分类时返回空字符串。
     */
    String getActiveHomeNavCategoryName() {
        if (!TextUtils.isEmpty(selectedCategoryName)) {
            return selectedCategoryName;
        }
        if (loadingCategoryPage
                && !TextUtils.isEmpty(pendingCategoryName)
                && !pendingCategoryName.startsWith("搜索：")) {
            return pendingCategoryName;
        }
        return "";
    }

    String getPendingCategoryName() {
        return pendingCategoryName;
    }

    String getPendingSearchKeyword() {
        return pendingSearchKeyword;
    }

    boolean isLoadingCategoryPage() {
        return loadingCategoryPage;
    }
}
