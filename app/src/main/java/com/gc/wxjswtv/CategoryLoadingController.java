package com.gc.wxjswtv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 分类页加载遮罩控制器。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 20:45:00</p>
 *
 * <p>说明：分类、筛选、翻页、跳页和搜索都会通过隐藏 WebView 异步加载网页 HTML。
 * 加载期间如果继续操作底层按钮，容易造成多个 WebView 请求交错返回，最终出现焦点、选中态和内容区不一致。
 * 因此加载开始后显示全屏遮罩并拦截遥控器操作；超过 10 秒仍未返回时自动解锁，让用户可以重新选择或返回。</p>
 */
class CategoryLoadingController {
    private static final int CATEGORY_LOAD_TIMEOUT_MS = 10000;

    private final Activity activity;
    private final FrameLayout rootView;
    private final Handler handler = new Handler();
    private FrameLayout overlayView;
    private TextView messageView;
    private Runnable timeoutRunnable;
    private Runnable timeoutCallback;
    private boolean blocking;

    CategoryLoadingController(Activity activity, FrameLayout rootView) {
        this.activity = activity;
        this.rootView = rootView;
    }

    /**
     * 创建加载遮罩视图。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:45:00</p>
     *
     * <p>副作用：遮罩会添加到根布局最上层，默认隐藏；显示时会抢占焦点并消费点击。</p>
     */
    void build() {
        overlayView = new FrameLayout(activity);
        overlayView.setFocusable(true);
        overlayView.setFocusableInTouchMode(true);
        overlayView.setClickable(true);
        overlayView.setVisibility(View.GONE);
        overlayView.setBackgroundColor(Color.argb(205, 7, 9, 14));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(42), dp(28), dp(42), dp(28));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(Color.rgb(17, 18, 24));
        panelBackground.setStroke(dp(2), Color.rgb(255, 106, 0));
        panelBackground.setCornerRadius(dp(8));
        panel.setBackground(panelBackground);

        ProgressBar progressBar = new ProgressBar(activity);
        panel.addView(progressBar, new LinearLayout.LayoutParams(dp(52), dp(52)));

        messageView = new TextView(activity);
        messageView.setTextColor(Color.WHITE);
        messageView.setTextSize(22);
        messageView.setTypeface(Typeface.DEFAULT_BOLD);
        messageView.setGravity(Gravity.CENTER);
        messageView.setText("正在加载...");
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(18), 0, 0);
        panel.addView(messageView, messageParams);

        overlayView.addView(panel, new FrameLayout.LayoutParams(
                dp(380),
                dp(180),
                Gravity.CENTER));
        rootView.addView(overlayView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /**
     * 显示加载遮罩并启动 10 秒超时解锁。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:45:00</p>
     *
     * @param message 加载提示文案。
     * @param timeoutCallback 超时后执行的业务回调，用于收尾分类加载状态。
     */
    void show(String message, Runnable timeoutCallback) {
        this.timeoutCallback = timeoutCallback;
        blocking = true;
        messageView.setText(message);
        overlayView.setVisibility(View.VISIBLE);
        overlayView.requestFocus();
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                blocking = false;
                overlayView.setVisibility(View.GONE);
                if (CategoryLoadingController.this.timeoutCallback != null) {
                    CategoryLoadingController.this.timeoutCallback.run();
                }
            }
        };
        handler.postDelayed(timeoutRunnable, CATEGORY_LOAD_TIMEOUT_MS);
    }

    /**
     * 正常结束加载并隐藏遮罩。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:45:00</p>
     */
    void hide() {
        blocking = false;
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        overlayView.setVisibility(View.GONE);
    }

    boolean isBlocking() {
        return blocking;
    }

    void destroy() {
        hide();
        timeoutCallback = null;
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
