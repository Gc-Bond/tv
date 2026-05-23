package com.gc.wxjswtv;

import android.content.Context;
import android.widget.VideoView;
import android.view.View;

/**
 * 强制铺满播放器区域的 VideoView。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 18:47:00</p>
 *
 * <p>说明：Android 原生 VideoView 默认会按视频比例重新测量自身，部分片源会在电视上留下黑边。
 * 该控件把播放器测量逻辑从 MainActivity 拆出，播放器页面只需要设置片源尺寸和缩放模式。</p>
 */
class FullScreenVideoView extends VideoView {
    private static final int RESIZE_MODE_FIT = 0;
    private static final int RESIZE_MODE_FILL = 1;
    private static final int RESIZE_MODE_STRETCH = 2;

    private int videoWidth;
    private int videoHeight;
    private int resizeMode = RESIZE_MODE_FILL;

    /**
     * 创建全屏 VideoView。
     *
     * @param context Android 上下文。
     */
    FullScreenVideoView(Context context) {
        super(context);
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
     * @param mode 画面比例模式，数值与 MainActivity 中的播放模式常量保持一致。
     */
    void setResizeMode(int mode) {
        resizeMode = mode;
        requestLayout();
    }

    /**
     * 按父容器给定尺寸测量，不使用 VideoView 默认的等比测量。
     */
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
