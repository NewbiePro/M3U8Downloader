package com.tech.newbie.m3u8downloader.service;

import com.tech.newbie.m3u8downloader.service.strategy.ui.StatusUpdateStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

import static com.tech.newbie.m3u8downloader.core.common.constant.Constant.M3U8_HEADER;

@Slf4j
public class M3U8ParserService {

    private final StatusUpdateStrategy<String> strategy;

    public M3U8ParserService(StatusUpdateStrategy<String> strategy) {
        this.strategy = strategy;
    }

    public List<String> parseM3U8Content(String content, String requestUrl) {
        strategy.updateStatus("parsing M3U8..........");
        if (!content.contains(M3U8_HEADER)) {
            strategy.updateStatus("invalid m3u8 url");
            return Collections.emptyList();
        }
        String urlPath = requestUrl.substring(0, requestUrl.lastIndexOf("/") + 1);
        List<String> tsFiles = content.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("/"))
                .map(line -> line.startsWith("https") ? line : urlPath + line)
                .toList();
        strategy.updateStatus("There are " + tsFiles.size() + " files");
        return tsFiles;
    }

}
