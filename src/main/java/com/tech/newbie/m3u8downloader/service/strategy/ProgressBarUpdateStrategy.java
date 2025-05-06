package com.tech.newbie.m3u8downloader.service.strategy;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;

public class ProgressBarUpdateStrategy implements StatusUpdateStrategy<Double>{
    private final DoubleProperty progressBar;

    public ProgressBarUpdateStrategy(DoubleProperty progressBar) {
        this.progressBar = progressBar;
    }

    @Override
    public void updateStatus(Double progress) {
        Platform.runLater(()-> progressBar.set(progress));
    }
}
