package com.gc.wxjswtv;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

/**
 * 基于 TextureView 和 MediaPlayer 的轻量播放器视图。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 17:18:00</p>
 *
 * <p>说明：VideoView 底层通常是 SurfaceView，局部预览和全屏之间切换父容器时会重建 surface，
 * 导致视频暂停或黑屏。这里通过 TextureView 保留同一个 SurfaceTexture，
 * 播放器切换显示区域时不需要重新创建解码器。</p>
 */
class TextureMediaPlayerView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = "WxjswTvShell";
    private static final int RESIZE_MODE_FIT = 0;
    private static final int RESIZE_MODE_FILL = 1;
    private static final int RESIZE_MODE_STRETCH = 2;

    private final Context context;
    private MediaPlayer mediaPlayer;
    private SurfaceTexture retainedSurfaceTexture;
    private Surface retainedSurface;
    private Uri pendingUri;
    private MediaPlayer.OnPreparedListener preparedListener;
    private MediaPlayer.OnErrorListener errorListener;
    private int videoWidth;
    private int videoHeight;
    private int resizeMode = RESIZE_MODE_FILL;
    private int pendingSeekPosition;
    private boolean pendingStart;
    private boolean prepared;

    /**
     * 创建 Texture 播放器视图。
     *
     * @param context Android 上下文。
     */
    TextureMediaPlayerView(Context context) {
        super(context);
        this.context = context;
        setSurfaceTextureListener(this);
    }

    /**
     * 设置播放地址。
     *
     * <p>说明：如果 SurfaceTexture 尚未准备好，只记录地址，等可用后再创建 MediaPlayer。</p>
     *
     * @param uri 播放地址。
     */
    void setVideoURI(Uri uri) {
        pendingUri = uri;
        pendingSeekPosition = 0;
        prepared = false;
        releaseMediaPlayer();
        if (retainedSurfaceTexture != null) {
            prepareMediaPlayer();
        }
    }

    /**
     * 开始或标记待开始播放。
     */
    void start() {
        pendingStart = true;
        if (prepared && mediaPlayer != null) {
            mediaPlayer.start();
        } else if (mediaPlayer == null && pendingUri != null && retainedSurfaceTexture != null) {
            prepareMediaPlayer();
        }
    }

    /**
     * 暂停播放。
     */
    void pause() {
        pendingStart = false;
        if (prepared && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    /**
     * 停止并释放播放器，但保留 TextureView 自身。
     */
    void stopPlayback() {
        pendingStart = false;
        pendingSeekPosition = 0;
        pendingUri = null;
        prepared = false;
        releaseMediaPlayer();
    }

    /**
     * 跳转播放进度。
     *
     * @param positionMs 目标进度，单位毫秒。
     */
    void seekTo(int positionMs) {
        pendingSeekPosition = Math.max(0, positionMs);
        if (prepared && mediaPlayer != null) {
            mediaPlayer.seekTo(pendingSeekPosition);
        }
    }

    /**
     * 获取当前播放进度。
     *
     * @return 当前进度，单位毫秒。
     */
    int getCurrentPosition() {
        if (prepared && mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return pendingSeekPosition;
    }

    /**
     * 获取视频总时长。
     *
     * @return 总时长，单位毫秒。
     */
    int getDuration() {
        if (prepared && mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    /**
     * 判断是否正在播放。
     *
     * @return 是否播放中。
     */
    boolean isPlaying() {
        return prepared && mediaPlayer != null && mediaPlayer.isPlaying();
    }

    void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        preparedListener = listener;
    }

    void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        errorListener = listener;
    }

    /**
     * 保存片源原始宽高。
     *
     * @param width 片源宽度。
     * @param height 片源高度。
     */
    void setVideoSize(int width, int height) {
        videoWidth = width;
        videoHeight = height;
        requestLayout();
    }

    /**
     * 设置画面比例模式。
     *
     * @param mode 画面比例模式。
     */
    void setResizeMode(int mode) {
        resizeMode = mode;
        requestLayout();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if (retainedSurfaceTexture == null) {
            retainedSurfaceTexture = surfaceTexture;
            retainedSurface = new Surface(retainedSurfaceTexture);
        } else if (surfaceTexture != retainedSurfaceTexture) {
            setSurfaceTexture(retainedSurfaceTexture);
        }
        if (mediaPlayer == null && pendingUri != null) {
            prepareMediaPlayer();
        } else if (mediaPlayer != null && retainedSurface != null) {
            mediaPlayer.setSurface(retainedSurface);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        requestLayout();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        // 返回 false 表示 SurfaceTexture 由本控件继续持有，切换局部/全屏父容器时不释放底层画面。
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    /**
     * 创建并异步准备 MediaPlayer。
     */
    private void prepareMediaPlayer() {
        if (pendingUri == null || retainedSurface == null) {
            return;
        }
        try {
            releaseMediaPlayer();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setSurface(retainedSurface);
            mediaPlayer.setDataSource(context, pendingUri);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer player) {
                    prepared = true;
                    videoWidth = player.getVideoWidth();
                    videoHeight = player.getVideoHeight();
                    requestLayout();
                    if (pendingSeekPosition > 0) {
                        player.seekTo(pendingSeekPosition);
                    }
                    if (pendingStart) {
                        player.start();
                    }
                    if (preparedListener != null) {
                        preparedListener.onPrepared(player);
                    }
                    Log.i(TAG, "======>>>>>>【Texture 播放器准备完成，width=" + videoWidth + "，height=" + videoHeight + "】<<<<<<======");
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer player, int what, int extra) {
                    prepared = false;
                    if (errorListener != null) {
                        return errorListener.onError(player, what, extra);
                    }
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception exception) {
            prepared = false;
            Log.e(TAG, "======>>>>>>【Texture 播放器准备失败，error=" + exception.getMessage() + "】<<<<<<======");
            if (errorListener != null) {
                errorListener.onError(mediaPlayer, -1, 0);
            }
        }
    }

    /**
     * 释放 MediaPlayer。
     */
    private void releaseMediaPlayer() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            mediaPlayer.setOnPreparedListener(null);
            mediaPlayer.setOnErrorListener(null);
            mediaPlayer.stop();
        } catch (RuntimeException ignored) {
            // MediaPlayer 未完成准备时 stop 可能抛异常；释放流程继续执行即可。
        }
        mediaPlayer.release();
        mediaPlayer = null;
        prepared = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = View.MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = View.MeasureSpec.getSize(heightMeasureSpec);
        if (resizeMode == RESIZE_MODE_STRETCH || videoWidth <= 0 || videoHeight <= 0) {
            setMeasuredDimension(parentWidth, parentHeight);
            return;
        }
        float videoRatio = videoWidth * 1f / videoHeight;
        float parentRatio = parentWidth * 1f / parentHeight;
        int measuredWidth = parentWidth;
        int measuredHeight = parentHeight;
        if (resizeMode == RESIZE_MODE_FIT) {
            if (videoRatio > parentRatio) {
                measuredHeight = (int) (parentWidth / videoRatio);
            } else {
                measuredWidth = (int) (parentHeight * videoRatio);
            }
        } else if (resizeMode == RESIZE_MODE_FILL) {
            if (videoRatio > parentRatio) {
                measuredWidth = (int) (parentHeight * videoRatio);
            } else {
                measuredHeight = (int) (parentWidth / videoRatio);
            }
        }
        setMeasuredDimension(measuredWidth, measuredHeight);
    }
}
