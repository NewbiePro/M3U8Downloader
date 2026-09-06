package com.tech.newbie.m3u8downloader.core.common.constant;

public class Constant {
    public static final String M3U8_HEADER = "#EXTM3U";
    public static final String TS_FORMAT = "%s_%d.ts";
    // "<name>_<index>.<ext>" - ext is "ts" for MPEG-TS or "m4s" for fMP4 fragments
    public static final String SEGMENT_FORMAT = "%s_%d.%s";
    // fMP4 init segment (from #EXT-X-MAP), one per download
    public static final String INIT_SEGMENT_FORMAT = "%s_init.mp4";
}
