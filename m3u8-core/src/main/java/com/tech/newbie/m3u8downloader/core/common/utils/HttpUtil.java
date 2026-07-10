package com.tech.newbie.m3u8downloader.core.common.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpUtil {

    public static boolean isValidUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }

        String trimmed = url.trim();

        // Allow valid cURL commands to pass through to the parser
        if (trimmed.startsWith("curl ")) {
            return true;
        }

        // Allow direct m3u8 content (starts with #EXTM3U)
        if (trimmed.startsWith("#EXTM3U") || trimmed.startsWith("#extm3u")) {
            return true;
        }

        // Allow file:// URLs
        if (trimmed.startsWith("file://")) {
            return true;
        }

        // Allow local file paths (Windows: C:\ or C:/ or absolute Unix paths)
        // Windows: C:\path\to\file.m3u8 or C:/path/to/file.m3u8
        // Unix: /path/to/file.m3u8
        if (trimmed.matches("^[a-zA-Z]:[/\\\\].*") || trimmed.startsWith("/")) {
            return new java.io.File(trimmed).exists();
        }

        // Check for valid http/https m3u8 URL
        String regex = "^(https?)://\\S+\\.m3u8(\\?.*)?\\s*$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(trimmed);
        return matcher.matches();
    }
}
