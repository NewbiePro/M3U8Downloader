package com.tech.newbie.m3u8downloader.service.strategy;

public interface StatusUpdateStrategy<T> {
    void updateStatus(T status);
}
