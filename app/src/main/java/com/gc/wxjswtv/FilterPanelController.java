package com.gc.wxjswtv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 分类筛选弹层控制器。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 20:24:00</p>
 *
 * <p>说明：筛选弹层固定 5 列网格，并接管方向键焦点移动，避免老电视盒子的系统焦点搜索把焦点带到底层页面。
 * 控制器只负责弹层显示、按钮创建和按键处理；实际加载分类 URL 仍回调给 Activity 统一处理。</p>
 */
class FilterPanelController {
    private static final String TAG = "WxjswTvShell";

    private final Activity activity;
    private final FrameLayout rootView;
    private final Callback callback;
    private FrameLayout filterPanelOverlay;
    private TextView filterPanelTitleView;
    private ScrollView filterPanelScrollView;
    private GridLayout filterPanelGrid;
    private FilterGroup currentFilterGroup;
    private int filterPanelOptionWidth;
    private boolean showing;

    FilterPanelController(Activity activity, FrameLayout rootView, Callback callback) {
        this.activity = activity;
        this.rootView = rootView;
        this.callback = callback;
    }

    /**
     * 创建筛选弹层视图。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:24:00</p>
     *
     * <p>副作用：会把全屏半透明弹层添加到根布局中，默认隐藏。</p>
     */
    void build() {
        filterPanelOverlay = new FrameLayout(activity);
        filterPanelOverlay.setFocusable(true);
        filterPanelOverlay.setFocusableInTouchMode(true);
        filterPanelOverlay.setClickable(true);
        filterPanelOverlay.setVisibility(View.GONE);
        filterPanelOverlay.setBackgroundColor(Color.argb(225, 7, 9, 14));
        rootView.addView(filterPanelOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(48), dp(34), dp(48), dp(34));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(Color.rgb(17, 18, 24));
        panelBackground.setStroke(dp(2), Color.rgb(255, 106, 0));
        panelBackground.setCornerRadius(dp(8));
        panel.setBackground(panelBackground);

        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        int panelWidth = displayMetrics.widthPixels - dp(220);
        int panelHeight = displayMetrics.heightPixels - dp(140);
        int panelHorizontalPadding = dp(96);
        int optionHorizontalMargins = dp(16) * 5;
        filterPanelOptionWidth = (panelWidth - panelHorizontalPadding - optionHorizontalMargins) / 5;
        filterPanelOverlay.addView(panel, new FrameLayout.LayoutParams(
                panelWidth,
                panelHeight,
                Gravity.CENTER));

        filterPanelTitleView = new TextView(activity);
        filterPanelTitleView.setTextColor(Color.WHITE);
        filterPanelTitleView.setTextSize(24);
        filterPanelTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        filterPanelTitleView.setSingleLine(true);
        filterPanelTitleView.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(filterPanelTitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)));

        filterPanelScrollView = new ScrollView(activity);
        filterPanelScrollView.setFillViewport(false);
        filterPanelScrollView.setVerticalScrollBarEnabled(false);
        panel.addView(filterPanelScrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1));

        filterPanelGrid = new GridLayout(activity);
        filterPanelGrid.setColumnCount(5);
        filterPanelScrollView.addView(filterPanelGrid, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
    }

    boolean isShowing() {
        return showing;
    }

    /**
     * 打开筛选弹层并渲染当前分组选项。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:24:00</p>
     *
     * @param filterGroup 分类页解析出的筛选分组；为空或无选项时不显示弹层。
     */
    void show(FilterGroup filterGroup) {
        if (filterGroup == null || filterGroup.options == null || filterGroup.options.isEmpty()) {
            return;
        }
        currentFilterGroup = filterGroup;
        showing = true;
        filterPanelTitleView.setText(filterGroup.name + "筛选");
        filterPanelGrid.removeAllViews();
        filterPanelScrollView.scrollTo(0, 0);

        int selectedIndex = -1;
        for (int i = 0; i < filterGroup.options.size(); i++) {
            FilterOption option = filterGroup.options.get(i);
            TextView button = createOptionButton(option);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = filterPanelOptionWidth;
            params.height = dp(58);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            button.setLayoutParams(params);
            filterPanelGrid.addView(button);
            if (option != null && option.selected) {
                selectedIndex = i;
            }
        }
        filterPanelOverlay.setVisibility(View.VISIBLE);
        if (filterPanelGrid.getChildCount() > 0) {
            int focusIndex = selectedIndex >= 0 ? selectedIndex : 0;
            View focusView = filterPanelGrid.getChildAt(focusIndex);
            focusView.requestFocus();
            scrollOptionIntoView(focusView);
        }
    }

    /**
     * 关闭筛选弹层。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:24:00</p>
     */
    void hide() {
        showing = false;
        if (filterPanelOverlay != null) {
            filterPanelOverlay.setVisibility(View.GONE);
        }
    }

    /**
     * 处理筛选弹层方向键和确认键。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:24:00</p>
     *
     * @param keyCode 遥控器按键码。
     * @return true 表示弹层已消费该按键。
     */
    boolean handleKey(int keyCode) {
        if (!showing || filterPanelGrid == null || filterPanelGrid.getChildCount() == 0) {
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            View focusedView = activity.getCurrentFocus();
            if (!isViewInside(focusedView, filterPanelGrid)) {
                filterPanelGrid.getChildAt(0).requestFocus();
                return true;
            }
            focusedView.performClick();
            return true;
        }
        int delta = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            delta = -1;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            delta = 1;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            delta = -5;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            delta = 5;
        } else {
            return false;
        }
        moveFocus(delta);
        return true;
    }

    private TextView createOptionButton(final FilterOption option) {
        final TextView button = new TextView(activity);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setText(option == null ? "" : option.name);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setBackground(makeOptionBackground(option != null && option.selected, false));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                button.setBackground(makeOptionBackground(option != null && option.selected, hasFocus));
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (option == null || TextUtils.isEmpty(option.url)) {
                    return;
                }
                callback.onFilterOptionSelected(currentFilterGroup, option);
                Log.i(TAG, "======>>>>>>【加载分类筛选页，group=" + (currentFilterGroup == null ? "" : currentFilterGroup.name)
                        + "，name=" + option.name + "，url=" + option.url + "】<<<<<<======");
            }
        });
        return button;
    }

    private void moveFocus(int delta) {
        int currentIndex = findFocusedIndex();
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int targetIndex = currentIndex + delta;
        if (targetIndex < 0) {
            targetIndex = 0;
        }
        if (targetIndex >= filterPanelGrid.getChildCount()) {
            targetIndex = filterPanelGrid.getChildCount() - 1;
        }
        View targetView = filterPanelGrid.getChildAt(targetIndex);
        targetView.requestFocus();
        scrollOptionIntoView(targetView);
    }

    private void scrollOptionIntoView(final View targetView) {
        if (targetView == null) {
            return;
        }
        filterPanelScrollView.post(new Runnable() {
            @Override
            public void run() {
                int targetTop = targetView.getTop();
                int targetBottom = targetView.getBottom();
                int visibleTop = filterPanelScrollView.getScrollY();
                int visibleBottom = visibleTop + filterPanelScrollView.getHeight();
                if (targetTop < visibleTop) {
                    filterPanelScrollView.smoothScrollTo(0, targetTop);
                } else if (targetBottom > visibleBottom) {
                    filterPanelScrollView.smoothScrollTo(0, targetBottom - filterPanelScrollView.getHeight());
                }
            }
        });
    }

    private int findFocusedIndex() {
        View focusedView = activity.getCurrentFocus();
        for (int i = 0; i < filterPanelGrid.getChildCount(); i++) {
            if (filterPanelGrid.getChildAt(i) == focusedView) {
                return i;
            }
        }
        return -1;
    }

    private boolean isViewInside(View child, View parent) {
        View current = child;
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

    private GradientDrawable makeOptionBackground(boolean selected, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(6));
        drawable.setColor(selected || focused ? Color.rgb(255, 106, 0) : Color.rgb(35, 38, 48));
        drawable.setStroke(focused ? dp(3) : dp(1), focused ? Color.WHITE : Color.rgb(72, 76, 90));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    interface Callback {
        void onFilterOptionSelected(FilterGroup filterGroup, FilterOption option);
    }
}
