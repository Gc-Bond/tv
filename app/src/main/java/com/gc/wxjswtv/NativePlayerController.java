package com.gc.wxjswtv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 原生播放器页面控制器。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 20:04:00</p>
 *
 * <p>说明：播放器页面包含 TextureView 播放器、顶部标题、底部控制层、右侧选集浮层、进度刷新和画面比例切换。
 * 这些状态和首页、详情页的渲染关系较弱，集中到控制器后，Activity 只负责页面跳转和播放地址解析流程。</p>
 */
class NativePlayerController {
    private static final String TAG = "WxjswTvShell";
    private static final int SEEK_STEP_MS = 30000;
    private static final int RESIZE_MODE_FIT = 0;
    private static final int RESIZE_MODE_FILL = 1;
    private static final int RESIZE_MODE_STRETCH = 2;

    private final Activity activity;
    private final FrameLayout rootView;
    private final AudioManager audioManager;
    private final Callback callback;
    private final Handler progressHandler = new Handler();
    private final Handler controlHandler = new Handler();

    private FrameLayout playerView;
    private TextureMediaPlayerView videoView;
    private TextView playerTitleView;
    private TextView playerHintView;
    private TextView playerTimeView;
    private TextView playerVolumeView;
    private TextView playerOperationView;
    private ProgressBar playerProgressBar;
    private LinearLayout playerTopOverlay;
    private LinearLayout playerControlOverlay;
    private ScrollView playerEpisodePanel;
    private GridLayout playerEpisodeGrid;
    private EpisodeItem currentEpisode;
    private List<EpisodeItem> currentEpisodes = new ArrayList<EpisodeItem>();
    private String currentVideoUrl;
    private int currentResizeMode = RESIZE_MODE_FILL;
    private boolean showingPlayerPage;
    private boolean showingPlayerEpisodePanel;
    private boolean inlineMode;
    private boolean fullscreenFromInlineMode;
    private Runnable progressRunnable;
    private Runnable controlHideRunnable;

    NativePlayerController(Activity activity, FrameLayout rootView, AudioManager audioManager, Callback callback) {
        this.activity = activity;
        this.rootView = rootView;
        this.audioManager = audioManager;
        this.callback = callback;
    }

    /**
     * 创建播放器根视图和所有覆盖层。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:04:00</p>
     *
     * <p>副作用：会把播放器页面添加到 Activity 根布局中，并注册 Texture 播放事件监听。</p>
     */
    void build() {
        playerView = new FrameLayout(activity);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setVisibility(View.GONE);
        rootView.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        videoView = new TextureMediaPlayerView(activity);
        videoView.setFocusable(true);
        videoView.setFocusableInTouchMode(true);
        playerView.addView(videoView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        buildTopOverlay();
        buildControlOverlay();
        buildEpisodePanel();
        bindVideoListeners();
    }

    FrameLayout getPlayerView() {
        return playerView;
    }

    boolean isShowingPlayerPage() {
        return showingPlayerPage;
    }

    boolean isShowingPlayerEpisodePanel() {
        return showingPlayerEpisodePanel;
    }

    /**
     * 判断当前是否为详情页局部播放模式。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:54:40</p>
     *
     * <p>说明：局部播放只负责在右侧详情区域显示画面，不应该像全屏播放器一样默认接管遥控器方向键；
     * Activity 会据此决定是否把方向键继续交给详情页集数网格。</p>
     *
     * @return 是否正在局部播放。
     */
    boolean isInlineMode() {
        return inlineMode;
    }

    /**
     * 判断当前全屏播放是否来自详情页预览区。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:08:00</p>
     *
     * <p>说明：从预览区进入全屏后，返回键应该把播放器挂回预览区继续显示，而不是按普通全屏播放关闭播放器。</p>
     *
     * @return 是否为预览区切入的全屏播放。
     */
    boolean isFullscreenFromInlineMode() {
        return fullscreenFromInlineMode;
    }

    EpisodeItem getCurrentEpisode() {
        return currentEpisode;
    }

    /**
     * 更新当前影片可切换的集数列表，并重绘播放页右侧选集浮层。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:04:00</p>
     *
     * @param episodes 当前影片详情页解析出的集数列表。
     */
    void setEpisodes(List<EpisodeItem> episodes) {
        currentEpisodes = episodes == null ? new ArrayList<EpisodeItem>() : episodes;
        renderPlayerEpisodePanel(currentEpisodes);
    }

    /**
     * 进入播放页并等待隐藏 WebView 解析真实播放地址。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:04:00</p>
     *
     * @param episode 用户选择的集数。
     * @param itemTitle 当前影片标题，用于播放页顶部显示。
     */
    void openEpisode(EpisodeItem episode, String itemTitle) {
        attachPlayerView(rootView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        inlineMode = false;
        fullscreenFromInlineMode = false;
        currentEpisode = episode;
        showingPlayerPage = true;
        hideEpisodePanel();
        playerView.setVisibility(View.VISIBLE);
        videoView.requestFocus();
        playerTitleView.setText((TextUtils.isEmpty(itemTitle) ? "正在播放" : itemTitle) + "  " + episode.name);
        playerHintView.setText("正在解析播放地址...");
        updateOverlayLayout(false);
        showControlsAlways();
    }

    /**
     * 在详情页局部区域内打开播放器。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:51:14</p>
     *
     * <p>说明：用户在详情页选中集数后，播放器先挂载到右侧详情面板里的局部容器，不再立即覆盖整个 Activity。
     * 复用同一套 Texture 播放器可以继续沿用播放地址解析、进度和遥控器控制逻辑；挂载前会先从原父容器移除，避免 View 重复添加崩溃。</p>
     *
     * @param episode 用户选择的集数。
     * @param itemTitle 当前影片标题。
     * @param inlineHost 详情页提供的局部播放器容器。
     */
    void openEpisodeInline(EpisodeItem episode, String itemTitle, FrameLayout inlineHost) {
        if (inlineHost == null) {
            openEpisode(episode, itemTitle);
            return;
        }
        attachPlayerView(inlineHost, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        inlineMode = true;
        fullscreenFromInlineMode = false;
        currentEpisode = episode;
        showingPlayerPage = true;
        hideEpisodePanel();
        playerView.setVisibility(View.VISIBLE);
        playerTitleView.setText((TextUtils.isEmpty(itemTitle) ? "正在播放" : itemTitle) + "  " + episode.name);
        playerHintView.setText("正在解析播放地址...");
        updateOverlayLayout(true);
        showControlsAlways();
    }

    /**
     * 判断指定控件是否在播放器视图内部。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:58:20</p>
     *
     * <p>说明：局部播放时如果系统把焦点误放到播放器视图或覆盖层，Activity 需要把焦点拉回选集按钮；
     * 这里向 Activity 暴露一个只读判断，避免 Activity 直接了解播放器内部层级。</p>
     *
     * @param view 当前焦点控件。
     * @return 当前控件是否属于播放器视图。
     */
    boolean containsView(View view) {
        if (view == null || playerView == null) {
            return false;
        }
        View current = view;
        while (current != null) {
            if (current == playerView) {
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
     * 将当前局部播放切换为全屏播放。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:01:28</p>
     *
     * <p>说明：局部预览已经完成播放地址解析并持有同一个 Texture 播放器；
     * 进入全屏时只移动播放器视图到 Activity 根布局，不重新加载视频地址，避免中断后重新缓冲。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 17:12:00
     * 修改说明：切换时只移动播放器容器，不重设播放地址；TextureView 会保留 SurfaceTexture，让播放尽量连续。</p>
     */
    void enterFullscreenFromInline() {
        if (!showingPlayerPage || !inlineMode) {
            return;
        }
        attachPlayerView(rootView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        inlineMode = false;
        fullscreenFromInlineMode = true;
        playerView.setVisibility(View.VISIBLE);
        updateOverlayLayout(false);
        videoView.requestFocus();
        showControlsAlways();
        Log.i(TAG, "======>>>>>>【局部播放切换到全屏，episode=" + getCurrentEpisodeName() + "】<<<<<<======");
    }

    /**
     * 从全屏播放返回详情页预览区。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:08:00</p>
     *
     * <p>说明：此路径不停止播放器，只移动播放器视图并隐藏全屏覆盖层，保证返回详情页后预览区仍有视频画面。</p>
     *
     * <p>修改人：gc
     * 修改时间：2026-05-23 17:12:00
     * 修改说明：返回预览区时不重设播放地址，依靠 TextureView 保留的 SurfaceTexture 继续显示视频。</p>
     *
     * @param inlineHost 详情页预览区容器。
     */
    void returnFullscreenToInline(FrameLayout inlineHost) {
        if (!showingPlayerPage || !fullscreenFromInlineMode || inlineHost == null) {
            return;
        }
        attachPlayerView(inlineHost, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        inlineMode = true;
        fullscreenFromInlineMode = false;
        showingPlayerEpisodePanel = false;
        playerEpisodePanel.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        updateOverlayLayout(true);
        Log.i(TAG, "======>>>>>>【全屏播放返回局部预览，episode=" + getCurrentEpisodeName() + "】<<<<<<======");
    }

    /**
     * 使用解析到的视频地址启动系统播放器。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:04:00</p>
     *
     * @param videoUrl 播放页解析出的真实视频地址；为空时只提示错误，不启动播放器。
     */
    void playVideo(String videoUrl) {
        if (TextUtils.isEmpty(videoUrl)) {
            playerHintView.setText("没有解析到视频地址，请返回后重试");
            Log.w(TAG, "======>>>>>>【播放页未解析到视频地址，episode=" + getCurrentEpisodeName() + "】<<<<<<======");
            return;
        }
        currentVideoUrl = videoUrl;
        playerHintView.setText("正在缓冲...");
        playerTimeView.setText("00:00 / 00:00");
        playerProgressBar.setProgress(0);
        updateVolume();
        showControlsAlways();
        stopProgressUpdater();
        videoView.stopPlayback();
        videoView.setVideoURI(Uri.parse(videoUrl));
        videoView.start();
        Log.i(TAG, "======>>>>>>【启动原生播放器，episode=" + getCurrentEpisodeName() + "，videoUrl=" + videoUrl + "】<<<<<<======");
    }

    /**
     * 处理播放页遥控器按键。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:04:00</p>
     *
     * @param keyCode 遥控器按键码。
     * @param event 原始按键事件，保留给后续需要区分长按/重复按键时使用。
     * @return true 表示播放器已消费该按键；false 表示交给 Activity 继续处理。
     */
    boolean handleKeyDown(int keyCode, KeyEvent event) {
        if (!showingPlayerPage) {
            return false;
        }
        if (inlineMode) {
            return false;
        }
        if (showingPlayerEpisodePanel) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                hideEpisodePanel();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                performFocusedEpisodeClick();
                return true;
            }
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            togglePlayback();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            seekBy(-SEEK_STEP_MS);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            seekBy(SEEK_STEP_MS);
            return true;
        }
        showControlsTemporarily();
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_MENU) {
            if (inlineMode) {
                playerHintView.setText("局部播放中，返回键回到选集");
                showControlsTemporarily();
                return true;
            }
            showEpisodePanel();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            toggleResizeMode();
            return true;
        }
        return false;
    }

    /**
     * 音量键释放后同步播放器底部音量文案。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:04:00</p>
     *
     * @param keyCode 系统已处理的音量键码。
     */
    void onVolumeKeyUp(int keyCode) {
        updateVolume();
        playerHintView.setText(keyCode == KeyEvent.KEYCODE_VOLUME_UP ? "音量已提高" : "音量已降低");
        Log.i(TAG, "======>>>>>>【系统音量键已处理，keyCode=" + keyCode + "】<<<<<<======");
    }

    /**
     * 停止播放并隐藏播放器页面，返回详情页时使用。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:04:00</p>
     */
    void exitToDetail() {
        showingPlayerPage = false;
        showingPlayerEpisodePanel = false;
        fullscreenFromInlineMode = false;
        stopProgressUpdater();
        videoView.stopPlayback();
        playerEpisodePanel.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
    }

    /**
     * 退出到首页或销毁 Activity 时释放播放状态。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 20:04:00</p>
     */
    void stopAndHide() {
        showingPlayerPage = false;
        showingPlayerEpisodePanel = false;
        inlineMode = false;
        fullscreenFromInlineMode = false;
        stopProgressUpdater();
        if (videoView != null) {
            videoView.stopPlayback();
        }
        if (playerView != null) {
            playerView.setVisibility(View.GONE);
        }
    }

    void destroy() {
        stopAndHide();
    }

    String getCurrentEpisodeName() {
        return currentEpisode == null || TextUtils.isEmpty(currentEpisode.name) ? "未知集数" : currentEpisode.name;
    }

    private void buildTopOverlay() {
        playerTopOverlay = new LinearLayout(activity);
        playerTopOverlay.setOrientation(LinearLayout.VERTICAL);
        playerTopOverlay.setPadding(dp(34), dp(20), dp(34), dp(16));
        playerTopOverlay.setBackgroundColor(Color.argb(150, 0, 0, 0));

        playerTitleView = new TextView(activity);
        playerTitleView.setTextColor(Color.WHITE);
        playerTitleView.setTextSize(24);
        playerTitleView.setSingleLine(true);
        playerTitleView.setEllipsize(TextUtils.TruncateAt.END);
        playerTopOverlay.addView(playerTitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)));

        playerHintView = new TextView(activity);
        playerHintView.setTextColor(Color.rgb(255, 170, 88));
        playerHintView.setTextSize(18);
        playerHintView.setSingleLine(true);
        playerHintView.setEllipsize(TextUtils.TruncateAt.END);
        playerTopOverlay.addView(playerHintView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(32)));

        playerView.addView(playerTopOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(96),
                Gravity.TOP));
    }

    /**
     * 把播放器视图挂载到指定容器。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:51:14</p>
     *
     * <p>说明：播放器既可以作为全屏页面挂在 Activity 根布局，也可以作为局部播放器挂在详情页容器。
     * Android View 只能有一个父容器，所以切换挂载点前必须先从旧父容器移除。</p>
     *
     * @param targetParent 目标父容器。
     * @param layoutParams 新父容器下的布局参数。
     */
    private void attachPlayerView(FrameLayout targetParent, FrameLayout.LayoutParams layoutParams) {
        if (playerView == null || targetParent == null) {
            return;
        }
        if (playerView.getParent() == targetParent) {
            playerView.setLayoutParams(layoutParams);
            return;
        }
        if (playerView.getParent() instanceof FrameLayout) {
            ((FrameLayout) playerView.getParent()).removeView(playerView);
        }
        targetParent.addView(playerView, layoutParams);
    }

    /**
     * 根据全屏或局部播放模式调整覆盖层尺寸。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 16:51:14</p>
     *
     * <p>说明：全屏播放器需要完整标题、提示、进度和操作文案；局部播放器高度有限，
     * 如果沿用全屏覆盖层会遮住画面，因此局部模式只保留较矮的上下提示区。</p>
     *
     * @param inline 是否为详情页局部播放模式。
     */
    private void updateOverlayLayout(boolean inline) {
        if (inline) {
            playerTopOverlay.setVisibility(View.GONE);
            playerControlOverlay.setVisibility(View.GONE);
            videoView.setFocusable(false);
            videoView.setFocusableInTouchMode(false);
            playerView.setFocusable(false);
            return;
        }
        playerTopOverlay.setVisibility(View.VISIBLE);
        playerControlOverlay.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(96),
                Gravity.TOP);
        playerTopOverlay.setPadding(dp(34), dp(20), dp(34), dp(16));
        playerTopOverlay.setLayoutParams(topParams);
        playerTitleView.setTextSize(24);
        playerHintView.setTextSize(18);
        videoView.setFocusable(true);
        videoView.setFocusableInTouchMode(true);
        playerView.setFocusable(false);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(142),
                Gravity.BOTTOM);
        playerControlOverlay.setPadding(dp(34), dp(16), dp(34), dp(22));
        playerControlOverlay.setLayoutParams(bottomParams);
        playerTimeView.setTextSize(18);
        playerVolumeView.setTextSize(18);
        playerOperationView.setVisibility(View.VISIBLE);
    }

    private void buildControlOverlay() {
        playerControlOverlay = new LinearLayout(activity);
        playerControlOverlay.setOrientation(LinearLayout.VERTICAL);
        playerControlOverlay.setPadding(dp(34), dp(16), dp(34), dp(22));
        playerControlOverlay.setBackgroundColor(Color.argb(170, 0, 0, 0));

        playerTimeView = new TextView(activity);
        playerTimeView.setTextColor(Color.WHITE);
        playerTimeView.setTextSize(18);
        playerTimeView.setText("00:00 / 00:00");
        playerControlOverlay.addView(playerTimeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(32)));

        playerProgressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        playerProgressBar.setMax(1000);
        playerProgressBar.setProgress(0);
        playerControlOverlay.addView(playerProgressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(18)));

        playerVolumeView = new TextView(activity);
        playerVolumeView.setTextColor(Color.rgb(255, 170, 88));
        playerVolumeView.setTextSize(18);
        playerVolumeView.setSingleLine(true);
        playerVolumeView.setText("音量 --");
        playerControlOverlay.addView(playerVolumeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(30)));

        playerOperationView = new TextView(activity);
        playerOperationView.setText("确认键暂停/继续  ·  左右键快退/快进30秒  ·  上键/菜单选集  ·  下键切换比例  ·  音量键调音量");
        playerOperationView.setTextColor(Color.rgb(214, 218, 226));
        playerOperationView.setTextSize(17);
        playerOperationView.setSingleLine(true);
        playerOperationView.setEllipsize(TextUtils.TruncateAt.END);
        playerControlOverlay.addView(playerOperationView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)));

        playerView.addView(playerControlOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(142),
                Gravity.BOTTOM));
    }

    private void buildEpisodePanel() {
        playerEpisodePanel = new ScrollView(activity);
        playerEpisodePanel.setFillViewport(true);
        playerEpisodePanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        playerEpisodePanel.setBackgroundColor(Color.argb(210, 17, 18, 24));
        playerEpisodePanel.setVisibility(View.GONE);

        playerEpisodeGrid = new GridLayout(activity);
        playerEpisodeGrid.setColumnCount(2);
        playerEpisodePanel.addView(playerEpisodeGrid, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        playerView.addView(playerEpisodePanel, new FrameLayout.LayoutParams(
                dp(620),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT));
    }

    private void bindVideoListeners() {
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                Log.i(TAG, "======>>>>>>【原生播放器准备完成，episode=" + getCurrentEpisodeName() + "】<<<<<<======");
                videoView.setVideoSize(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
                videoView.setResizeMode(currentResizeMode);
                playerHintView.setText("正在播放  ·  " + getResizeModeName());
                showControlsTemporarily();
                startProgressUpdater();
            }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                Log.e(TAG, "======>>>>>>【原生播放器播放失败，what=" + what + "，extra=" + extra + "，episode=" + getCurrentEpisodeName() + "】<<<<<<======");
                playerHintView.setText("播放失败：老盒子可能不支持当前视频格式，返回后可换一集重试");
                showControlsAlways();
                stopProgressUpdater();
                return true;
            }
        });
        videoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePlayback();
            }
        });
    }

    private void renderPlayerEpisodePanel(List<EpisodeItem> episodes) {
        playerEpisodeGrid.removeAllViews();
        for (int i = 0; i < episodes.size(); i++) {
            final EpisodeItem episode = episodes.get(i);
            TextView button = createPlayerEpisodeButton(episode);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(270);
            params.height = dp(70);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            button.setLayoutParams(params);
            playerEpisodeGrid.addView(button);
        }
    }

    private TextView createPlayerEpisodeButton(final EpisodeItem episode) {
        final TextView button = new TextView(activity);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTypeface(Typeface.DEFAULT);
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
                hideEpisodePanel();
                callback.onPlayerEpisodeSelected(episode);
            }
        });
        return button;
    }

    private void togglePlayback() {
        if (!showingPlayerPage) {
            return;
        }
        if (videoView.isPlaying()) {
            videoView.pause();
            playerHintView.setText("已暂停，确认键继续，返回键回到详情页");
            stopProgressUpdater();
            showControlsAlways();
            Log.i(TAG, "======>>>>>>【原生播放器暂停，episode=" + getCurrentEpisodeName() + "】<<<<<<======");
        } else {
            videoView.start();
            playerHintView.setText("正在播放  ·  " + getResizeModeName());
            startProgressUpdater();
            showControlsTemporarily();
            Log.i(TAG, "======>>>>>>【原生播放器继续播放，episode=" + getCurrentEpisodeName() + "】<<<<<<======");
        }
    }

    private void seekBy(int deltaMs) {
        int duration = videoView.getDuration();
        int current = videoView.getCurrentPosition();
        if (duration <= 0) {
            playerHintView.setText("正在读取进度，稍后再试快进");
            return;
        }
        int target = current + deltaMs;
        if (target < 0) {
            target = 0;
        }
        if (target > duration) {
            target = duration;
        }
        videoView.seekTo(target);
        updateProgress();
        playerHintView.setText((deltaMs > 0 ? "已快进到 " : "已快退到 ") + WxjswTextHelper.formatTime(target));
        showControlsTemporarily();
        Log.i(TAG, "======>>>>>>【播放器跳转进度，episode=" + getCurrentEpisodeName() + "，targetMs=" + target + "】<<<<<<======");
    }

    private void toggleResizeMode() {
        if (currentResizeMode == RESIZE_MODE_FIT) {
            currentResizeMode = RESIZE_MODE_FILL;
        } else if (currentResizeMode == RESIZE_MODE_FILL) {
            currentResizeMode = RESIZE_MODE_STRETCH;
        } else {
            currentResizeMode = RESIZE_MODE_FIT;
        }
        videoView.setResizeMode(currentResizeMode);
        playerHintView.setText("画面比例：" + getResizeModeName());
        showControlsTemporarily();
        Log.i(TAG, "======>>>>>>【播放器画面比例切换，mode=" + getResizeModeName() + "】<<<<<<======");
    }

    private String getResizeModeName() {
        if (currentResizeMode == RESIZE_MODE_FIT) {
            return "适应";
        }
        if (currentResizeMode == RESIZE_MODE_STRETCH) {
            return "拉伸";
        }
        return "铺满";
    }

    private void showControlsTemporarily() {
        if (inlineMode) {
            hideControlsForInline();
            return;
        }
        showControlsAlways();
        controlHideRunnable = new Runnable() {
            @Override
            public void run() {
                hideControlsIfPlaying();
            }
        };
        controlHandler.postDelayed(controlHideRunnable, 5000);
    }

    private void showControlsAlways() {
        if (inlineMode) {
            hideControlsForInline();
            return;
        }
        if (controlHideRunnable != null) {
            controlHandler.removeCallbacks(controlHideRunnable);
            controlHideRunnable = null;
        }
        playerTopOverlay.setVisibility(View.VISIBLE);
        playerControlOverlay.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏局部播放模式的所有播放器覆盖层。
     *
     * <p>作者：gc
     * 创建时间：2026-05-23 17:01:20</p>
     *
     * <p>说明：详情页局部播放只是视频预览区域，不提供快进、暂停、进度、选集等播放器控制；
     * 覆盖层全部隐藏后，遥控器焦点和操作都留给详情页集数按钮。</p>
     */
    private void hideControlsForInline() {
        if (playerTopOverlay != null) {
            playerTopOverlay.setVisibility(View.GONE);
        }
        if (playerControlOverlay != null) {
            playerControlOverlay.setVisibility(View.GONE);
        }
    }

    private void hideControlsIfPlaying() {
        if (!showingPlayerPage || showingPlayerEpisodePanel || videoView == null || !videoView.isPlaying()) {
            return;
        }
        playerTopOverlay.setVisibility(View.GONE);
        playerControlOverlay.setVisibility(View.GONE);
    }

    private void updateVolume() {
        if (audioManager == null) {
            playerVolumeView.setText("音量 --");
            return;
        }
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int percent = max <= 0 ? 0 : (int) (current * 100L / max);
        playerVolumeView.setText("音量 " + percent + "%");
    }

    private void showEpisodePanel() {
        if (currentEpisodes == null || currentEpisodes.isEmpty()) {
            playerHintView.setText("当前没有可切换的集数");
            return;
        }
        showingPlayerEpisodePanel = true;
        showControlsAlways();
        playerEpisodePanel.setVisibility(View.VISIBLE);
        playerHintView.setText("选集已打开，方向键选择，确认键切换，返回键关闭");
        if (playerEpisodeGrid.getChildCount() > 0) {
            int index = findCurrentEpisodeIndex();
            playerEpisodeGrid.getChildAt(index < 0 ? 0 : index).requestFocus();
        }
    }

    private void hideEpisodePanel() {
        showingPlayerEpisodePanel = false;
        if (playerEpisodePanel != null) {
            playerEpisodePanel.setVisibility(View.GONE);
        }
        if (videoView != null && !inlineMode) {
            videoView.requestFocus();
        }
        showControlsTemporarily();
    }

    private int findCurrentEpisodeIndex() {
        if (currentEpisode == null || currentEpisodes == null) {
            return -1;
        }
        for (int i = 0; i < currentEpisodes.size(); i++) {
            EpisodeItem item = currentEpisodes.get(i);
            if (item != null && currentEpisode.url != null && currentEpisode.url.equals(item.url)) {
                return i;
            }
        }
        return -1;
    }

    private void performFocusedEpisodeClick() {
        View focusedView = activity.getCurrentFocus();
        if (focusedView != null && focusedView.getParent() == playerEpisodeGrid) {
            focusedView.performClick();
            return;
        }
        if (playerEpisodeGrid.getChildCount() > 0) {
            playerEpisodeGrid.getChildAt(0).performClick();
        }
    }

    private void startProgressUpdater() {
        stopProgressUpdater();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                if (showingPlayerPage && videoView != null && videoView.isPlaying()) {
                    progressHandler.postDelayed(this, 1000);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdater() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
        if (controlHideRunnable != null) {
            controlHandler.removeCallbacks(controlHideRunnable);
            controlHideRunnable = null;
        }
    }

    private void updateProgress() {
        int duration = videoView.getDuration();
        int current = videoView.getCurrentPosition();
        if (duration <= 0) {
            playerProgressBar.setProgress(0);
            playerTimeView.setText(WxjswTextHelper.formatTime(current) + " / 直播流");
            return;
        }
        int progress = (int) (current * 1000L / duration);
        playerProgressBar.setProgress(progress);
        playerTimeView.setText(WxjswTextHelper.formatTime(current) + " / " + WxjswTextHelper.formatTime(duration));
    }

    private GradientDrawable makeCardBackground(boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(10));
        drawable.setColor(focused ? Color.rgb(255, 106, 0) : Color.rgb(35, 38, 48));
        drawable.setStroke(focused ? dp(6) : dp(2), focused ? Color.WHITE : Color.rgb(72, 76, 90));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    interface Callback {
        void onPlayerEpisodeSelected(EpisodeItem episode);
    }
}
