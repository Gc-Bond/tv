package com.gc.wxjswtv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * 首页海报缓存与加载队列。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 19:24:00</p>
 *
 * <p>说明：海报加载独立于 Activity 页面状态，固定小并发下载、按目标尺寸采样并写入 LRU 缓存。
 * Activity 只需要按可视区域提交请求，避免主界面文件继续承担线程和图片解码细节。</p>
 */
class HomePosterLoader {
    private static final String TAG = "WxjswTvShell";
    private static final int WORKER_COUNT = 3;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 8000;

    private final Activity activity;
    private final LruCache<String, Bitmap> posterBitmapCache;
    private final LinkedList<PosterRequest> posterLoadQueue = new LinkedList<PosterRequest>();
    private final Set<String> queuedPosterUrls = new HashSet<String>();
    private final Set<String> loadingPosterUrls = new HashSet<String>();
    private int runningPosterWorkerCount;
    private volatile boolean destroyed;

    HomePosterLoader(Activity activity) {
        this.activity = activity;
        int maxMemoryKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSizeKb = maxMemoryKb / 10;
        posterBitmapCache = new LruCache<String, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                if (bitmap == null) {
                    return 0;
                }
                return bitmap.getByteCount() / 1024;
            }
        };
        Log.i(TAG, "======>>>>>>【首页海报缓存初始化完成，cacheSizeKb=" + cacheSizeKb + "】<<<<<<======");
    }

    void loadInto(final ImageView imageView, final String posterUrl, HomeCardMetrics cardMetrics, boolean highPriority) {
        if (cardMetrics == null) {
            return;
        }
        loadInto(imageView, posterUrl, cardMetrics.posterDecodeWidth, cardMetrics.posterDecodeHeight, highPriority);
    }

    void loadInto(final ImageView imageView, final String posterUrl, int targetWidth, int targetHeight, boolean highPriority) {
        if (imageView == null || TextUtils.isEmpty(posterUrl)) {
            return;
        }
        imageView.setTag(posterUrl);
        Bitmap cachedBitmap = posterBitmapCache.get(posterUrl);
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap);
            return;
        }
        enqueuePosterLoad(new PosterRequest(posterUrl, imageView, targetWidth, targetHeight), highPriority);
    }

    void destroy() {
        destroyed = true;
        synchronized (posterLoadQueue) {
            posterLoadQueue.clear();
            queuedPosterUrls.clear();
            loadingPosterUrls.clear();
            runningPosterWorkerCount = 0;
        }
        posterBitmapCache.evictAll();
    }

    private void enqueuePosterLoad(PosterRequest request, boolean highPriority) {
        if (request == null || TextUtils.isEmpty(request.url)) {
            return;
        }
        synchronized (posterLoadQueue) {
            if (queuedPosterUrls.contains(request.url) || loadingPosterUrls.contains(request.url)) {
                return;
            }
            if (highPriority) {
                posterLoadQueue.addFirst(request);
            } else {
                posterLoadQueue.addLast(request);
            }
            queuedPosterUrls.add(request.url);
            while (runningPosterWorkerCount < WORKER_COUNT
                    && runningPosterWorkerCount < posterLoadQueue.size()) {
                runningPosterWorkerCount++;
                startPosterWorker();
            }
        }
    }

    private void startPosterWorker() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!destroyed) {
                    PosterRequest request;
                    synchronized (posterLoadQueue) {
                        if (posterLoadQueue.isEmpty()) {
                            runningPosterWorkerCount--;
                            return;
                        }
                        request = posterLoadQueue.removeFirst();
                        queuedPosterUrls.remove(request.url);
                        loadingPosterUrls.add(request.url);
                    }
                    try {
                        loadPosterRequest(request);
                    } finally {
                        synchronized (posterLoadQueue) {
                            loadingPosterUrls.remove(request.url);
                        }
                    }
                }
                synchronized (posterLoadQueue) {
                    runningPosterWorkerCount--;
                }
            }
        }).start();
    }

    private void loadPosterRequest(final PosterRequest request) {
        if (request == null || TextUtils.isEmpty(request.url)) {
            return;
        }
        Bitmap cachedBitmap = posterBitmapCache.get(request.url);
        if (cachedBitmap != null) {
            applyPosterBitmap(request, cachedBitmap);
            return;
        }
        InputStream inputStream = null;
        try {
            URLConnection connection = new URL(request.url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(true);
            inputStream = connection.getInputStream();
            byte[] imageBytes = readInputStreamBytes(inputStream);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inSampleSize = calculatePosterSampleSize(options.outWidth, options.outHeight,
                    request.targetWidth, request.targetHeight);
            final Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
            if (bitmap == null) {
                return;
            }
            posterBitmapCache.put(request.url, bitmap);
            applyPosterBitmap(request, bitmap);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "======>>>>>>【首页海报加载内存不足，url=" + request.url + "】<<<<<<======", e);
        } catch (Exception e) {
            Log.w(TAG, "======>>>>>>【首页海报加载失败，url=" + request.url + "】<<<<<<======", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                    Log.w(TAG, "======>>>>>>【首页海报输入流关闭失败】<<<<<<======");
                }
            }
        }
    }

    private byte[] readInputStreamBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    private int calculatePosterSampleSize(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        int sampleSize = 1;
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return sampleSize;
        }
        while (sourceWidth / (sampleSize * 2) >= targetWidth
                && sourceHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize = sampleSize * 2;
        }
        return sampleSize;
    }

    private void applyPosterBitmap(final PosterRequest request, final Bitmap bitmap) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!destroyed && request.url.equals(request.imageView.getTag())) {
                    request.imageView.setImageBitmap(bitmap);
                }
            }
        });
    }
}
