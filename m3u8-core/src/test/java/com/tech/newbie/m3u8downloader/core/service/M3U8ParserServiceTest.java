package com.tech.newbie.m3u8downloader.core.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class M3U8ParserServiceTest {

    private final M3U8ParserService parser = new M3U8ParserService(status -> {
    });

    private static String playlist(String... segments) {
        StringBuilder sb = new StringBuilder("#EXTM3U\r\n#EXT-X-VERSION:3\r\n#EXT-X-TARGETDURATION:10\r\n");
        for (String s : segments) {
            sb.append("#EXTINF:10.0,\r\n").append(s).append("\r\n");
        }
        return sb.append("#EXT-X-ENDLIST\r\n").toString();
    }

    @Test
    void resolvesRootRelativeSegmentsAgainstHost() {
        List<String> urls = parser.parseM3U8Content(
                playlist("/hls/abc/seg-1.ts", "/hls/abc/seg-2.ts?t=9"),
                "https://cdn.example.com/hls/abc/");

        assertEquals(List.of(
                "https://cdn.example.com/hls/abc/seg-1.ts",
                "https://cdn.example.com/hls/abc/seg-2.ts?t=9"), urls);
    }

    @Test
    void resolvesRelativeSegmentsAgainstPlaylistDirectory() {
        List<String> urls = parser.parseM3U8Content(
                playlist("seg-1.ts", "sub/seg-2.ts", "../other/seg-3.ts"),
                "https://cdn.example.com/hls/abc/index.m3u8?token=x/y&exp=1");

        assertEquals(List.of(
                "https://cdn.example.com/hls/abc/seg-1.ts",
                "https://cdn.example.com/hls/abc/sub/seg-2.ts",
                "https://cdn.example.com/hls/other/seg-3.ts"), urls);
    }

    @Test
    void keepsAbsoluteSegments() {
        List<String> urls = parser.parseM3U8Content(
                playlist("https://other.example.com/a.ts"),
                "https://cdn.example.com/hls/abc/index.m3u8");

        assertEquals(List.of("https://other.example.com/a.ts"), urls);
    }

    @Test
    void rootRelativeSegmentsInLocalFileWithoutBaseUrlStillAskForBaseUrl() {
        assertThrows(RuntimeException.class, () -> parser.parseM3U8Content(
                playlist("/hls/abc/seg-1.ts"), "file:/D:/download/xxx.m3u8"));
    }
}
