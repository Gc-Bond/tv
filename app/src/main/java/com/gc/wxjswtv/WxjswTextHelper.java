package com.gc.wxjswtv;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 电视端展示文案与脚本文本工具。
 *
 * <p>作者：gc
 * 创建时间：2026-05-23 19:48:00</p>
 *
 * <p>说明：这些方法只依赖入参，不访问 Activity 或控件，拆出后便于复用和单独维护。</p>
 */
class WxjswTextHelper {
    private WxjswTextHelper() {
    }

    static String getDisplayTitle(VideoItem item) {
        if (item == null || TextUtils.isEmpty(item.title)) {
            return "未命名影片";
        }
        String title = item.title.trim();
        int index = title.indexOf(" 更新");
        if (index > 0) {
            return title.substring(0, index).trim();
        }
        index = title.indexOf(" 完结");
        if (index > 0) {
            return title.substring(0, index).trim();
        }
        return title;
    }

    static String getDisplayEpisode(VideoItem item) {
        if (item == null) {
            return "集数未知";
        }
        if (!TextUtils.isEmpty(item.episodeText)) {
            return item.episodeText;
        }
        if (!TextUtils.isEmpty(item.title)) {
            Matcher matcher = Pattern.compile("(更新至.+|完结|全\\d+集|第\\d+集.+)$").matcher(item.title.trim());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "集数未知";
    }

    static String quoteJavascriptString(String value) {
        if (value == null) {
            return "''";
        }
        StringBuilder builder = new StringBuilder("'");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '\'') {
                builder.append('\\').append(c);
            } else if (c == '\n') {
                builder.append("\\n");
            } else if (c == '\r') {
                builder.append("\\r");
            } else {
                builder.append(c);
            }
        }
        builder.append('\'');
        return builder.toString();
    }

    static String formatTime(int millis) {
        if (millis < 0) {
            millis = 0;
        }
        int totalSeconds = millis / 1000;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
