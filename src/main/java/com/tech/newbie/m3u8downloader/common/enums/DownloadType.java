package com.tech.newbie.m3u8downloader.core.common.enums;

import lombok.Getter;

@Getter
public enum DownloadType {
    SEQUENTIAL("SEQUENTIAL"),
    THREAD_POOL("THREAD POOL"),
    VIRTUAL_THREAD("VIRTUAL THREAD");

    private final String displayName;

    DownloadType(String displayName) {
        this.displayName = displayName;
    }
}
