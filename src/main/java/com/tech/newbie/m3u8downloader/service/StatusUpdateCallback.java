package com.tech.newbie.m3u8downloader.service;

public interface StatusUpdateCallback {
    void onStatusUpdate(String service, String message);
}
