package com.tech.newbie.m3u8downloader.service.strategy;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;

public class ProgressBarUpdateStrategy implements StatusUpdateStrategy<Double>{
    private final DoubleProperty progressBar;
    private volatile double lastProgress = 0.0;
    // only if reaches threshold that will update the progress bar
    private static final double UPDATE_THRESHOLD = 0.05;

    public ProgressBarUpdateStrategy(DoubleProperty progressBar) {
        this.progressBar = progressBar;
    }

    @Override
    public void updateStatus(Double progress) {
        if(progress == null){
            return;
        }

        double current = lastProgress;
        if (progress <= current || progress - current < UPDATE_THRESHOLD){
            return;
        }
        synchronized (this){
            current = lastProgress;
            if (progress > current && progress - current >=  UPDATE_THRESHOLD){
                lastProgress = progress;
                System.out.println("progress bar: " + progress);
                Platform.runLater(() -> progressBar.set(progress));
            }

        }
    }
}
