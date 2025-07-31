package com.tech.newbie.m3u8downloader.service.strategy.ui;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatusTextUpdateStrategy implements StatusUpdateStrategy<String> {
    private final StringProperty statusText;

    public StatusTextUpdateStrategy(StringProperty statusText) {
        this.statusText = statusText;
    }

    @Override
    public void updateStatus(String status) {
        log.info("status: {}", status);
        Platform.runLater(()-> statusText.set(status));
    }
}
