package com.tech.newbie.m3u8downloader.service;

import com.tech.newbie.m3u8downloader.service.strategy.StatusUpdateStrategy;

import java.util.Collections;
import java.util.List;

import static com.tech.newbie.m3u8downloader.common.Constant.M3U8_HEADER;

public class M3U8ParserService {

    private final StatusUpdateStrategy<String> strategy;

    public M3U8ParserService(StatusUpdateStrategy<String> strategy) {
        this.strategy = strategy;
    }

    public List<String> parseM3U8Content(String content) {
        strategy.updateStatus("parsing M3U8..........");
        if (!content.contains(M3U8_HEADER)) {
            strategy.updateStatus("invalid m3u8 url");
            return Collections.emptyList();
        }
        List<String> tsFiles = content.lines()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .toList();
        strategy.updateStatus("There are "+ tsFiles.size()+" files");
        return tsFiles;
    }

}
