package com.tech.newbie.m3u8downloader.service.strategy;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;

public class StatusTextUpdateStrategy implements  StatusUpdateStrategy<String>{
    private final StringProperty statusText;

    public StatusTextUpdateStrategy(StringProperty statusText) {
        this.statusText = statusText;
    }

    @Override
    public void updateStatus(String status) {
        System.out.println("status: "+ status);
        Platform.runLater(()-> statusText.set(status));
    }
}
