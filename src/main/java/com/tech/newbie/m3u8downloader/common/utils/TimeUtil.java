package com.tech.newbie.m3u8downloader.common.utils;

public class TimeUtil {

    public static String formatDuration(long durationMillis){
        long minutes = durationMillis / (1000 * 60);
        long seconds = (durationMillis / 1000) % 60;
        long milliseconds = durationMillis % 1000;
        return String.format("%d MIN %d S %d MS", minutes, seconds, milliseconds);
    }
}
