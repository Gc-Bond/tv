package com.gc.wxjswtv;

import android.text.TextUtils;

/**
 * 分类分页 URL 推导工具。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 19:50:00</p>
 *
 * <p>说明：分页跳转只依赖分类页解析结果，不应该留在 Activity 里和界面状态混在一起。</p>
 */
class CategoryPageUrlBuilder {
    private CategoryPageUrlBuilder() {
    }

    static String build(VideoCategory category, int targetPage) {
        if (category == null) {
            return "";
        }
        if (targetPage == category.currentPage) {
            return category.url;
        }
        if (targetPage == 1 && !TextUtils.isEmpty(category.firstPageUrl)) {
            return category.firstPageUrl;
        }
        if (targetPage == category.totalPage && !TextUtils.isEmpty(category.lastPageUrl)) {
            return category.lastPageUrl;
        }
        String templateUrl = "";
        int templatePage = 0;
        if (!TextUtils.isEmpty(category.nextPageUrl) && category.currentPage > 0) {
            templateUrl = category.nextPageUrl;
            templatePage = category.currentPage + 1;
        } else if (!TextUtils.isEmpty(category.previousPageUrl) && category.currentPage > 1) {
            templateUrl = category.previousPageUrl;
            templatePage = category.currentPage - 1;
        } else if (!TextUtils.isEmpty(category.lastPageUrl) && category.totalPage > 0) {
            templateUrl = category.lastPageUrl;
            templatePage = category.totalPage;
        }
        return replacePageNumber(templateUrl, templatePage, targetPage);
    }

    private static String replacePageNumber(String url, int sourcePage, int targetPage) {
        if (TextUtils.isEmpty(url) || sourcePage <= 0) {
            return "";
        }
        String sourceText = String.valueOf(sourcePage);
        String targetText = String.valueOf(targetPage);
        String replaced = url.replace("-" + sourceText + ".html", "-" + targetText + ".html");
        if (!replaced.equals(url)) {
            return replaced;
        }
        replaced = url.replace("-" + sourceText + "---.html", "-" + targetText + "---.html");
        if (!replaced.equals(url)) {
            return replaced;
        }
        return url.replace(sourceText + ".html", targetText + ".html");
    }
}
