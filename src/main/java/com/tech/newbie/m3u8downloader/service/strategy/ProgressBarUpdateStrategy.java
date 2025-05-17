package com.tech.newbie.m3u8downloader.service.strategy;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import lombok.Setter;

public class ProgressBarUpdateStrategy implements StatusUpdateStrategy<Double>{
    private final DoubleProperty progressBar;
    @Setter
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
        // will not update progress bar if not reach the threshold
        if (progress <= lastProgress || progress - lastProgress < UPDATE_THRESHOLD){
            return;
        }
        synchronized (this){
            if ( progress > lastProgress && progress - lastProgress >=  UPDATE_THRESHOLD ){
                lastProgress = progress;
                System.out.println("progress bar: " + progress);
                Platform.runLater(() -> progressBar.set(progress));
            }

        }
    }

    public void forceComplete(){
        System.out.println("progress bar: " + 1);
        Platform.runLater(()->progressBar.set(1.0));
        lastProgress = 1;
    }
}
