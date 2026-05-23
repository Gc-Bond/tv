package com.gc.wxjswtv;

import android.app.Activity;
import android.webkit.JavascriptInterface;

/**
 * 隐藏 WebView 与原生页面之间的 JavaScript 桥接。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 19:35:00</p>
 *
 * <p>说明：桥接类只负责接收网页侧回调并切回主线程，具体解析和页面渲染交给 Activity 回调处理。</p>
 */
class WxjswBridge {
    private final Activity activity;
    private final Callback callback;

    WxjswBridge(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    @JavascriptInterface
    public void onRss(final String xml) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onRss(xml);
            }
        });
    }

    @JavascriptInterface
    public void onHome(final String html) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onHome(html);
            }
        });
    }

    @JavascriptInterface
    public void onCategory(final String url, final String html) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onCategory(url, html);
            }
        });
    }

    @JavascriptInterface
    public void onDetail(final String url, final String html) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onDetail(url, html);
            }
        });
    }

    @JavascriptInterface
    public void onPlayPage(final String url, final String html) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onPlayPage(url, html);
            }
        });
    }

    interface Callback {
        void onRss(String xml);

        void onHome(String html);

        void onCategory(String url, String html);

        void onDetail(String url, String html);

        void onPlayPage(String url, String html);
    }
}
