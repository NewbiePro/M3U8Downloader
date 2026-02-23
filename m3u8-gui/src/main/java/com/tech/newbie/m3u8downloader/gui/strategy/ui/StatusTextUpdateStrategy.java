package com.tech.newbie.m3u8downloader.gui.strategy.ui;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatusTextUpdateStrategy implements com.tech.newbie.m3u8downloader.core.common.callback.UpdateCallback<String> {
    private final StringProperty statusText;

    public StatusTextUpdateStrategy(StringProperty statusText) {
        this.statusText = statusText;
    }

    @Override
    public void update(String status) {
        log.info("status: {}", status);
        Platform.runLater(()-> statusText.set(status));
    }
}
