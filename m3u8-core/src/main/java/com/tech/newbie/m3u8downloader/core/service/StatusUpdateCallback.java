package com.tech.newbie.m3u8downloader.core.service;

public interface StatusUpdateCallback {
    void onStatusUpdate(String service, String message);
}
