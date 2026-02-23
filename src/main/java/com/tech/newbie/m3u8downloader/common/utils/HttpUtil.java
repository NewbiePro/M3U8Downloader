package com.tech.newbie.m3u8downloader.core.common.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpUtil {

    public static boolean isValidUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }

        // Allow valid cURL commands to pass through to the parser
        if (url.trim().startsWith("curl ")) {
            return true;
        }

        String regex = "^(https?)://\\S+\\.m3u8(\\?.*)?\\s*$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }
}
