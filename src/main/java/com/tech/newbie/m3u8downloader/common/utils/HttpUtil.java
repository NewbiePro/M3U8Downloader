package com.tech.newbie.m3u8downloader.common.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpUtil {

    public static boolean isValidUrl(String url){
        String regex = "^(https?)://\\S+\\.m3u8(\\?.*)?$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
       return !StringUtils.isEmpty(url) && matcher.matches();
    }
}
