package com.tech.newbie.m3u8downloader.service.strategy;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;

import java.util.concurrent.atomic.AtomicReference;

public class ProgressBarUpdateStrategy implements StatusUpdateStrategy<Double>{
    private final DoubleProperty progressBar;
    private final AtomicReference<Double> lastProgress = new AtomicReference<>(0.0);

    public ProgressBarUpdateStrategy(DoubleProperty progressBar) {
        this.progressBar = progressBar;
    }

    @Override
    public void updateStatus(Double progress) {
        if(progress == null){
            return;
        }

        while (true) {
            double current = lastProgress.get();
            if (progress > current) {
                if (lastProgress.compareAndSet(current, progress)) {
                    System.out.println("progress bar: " + progress);
                    Platform.runLater(() -> progressBar.set(progress));
                    break;
                } else {
                    System.out.println(Thread.currentThread().getName() + "update fails " + current + " " + progress);
                }
            } else {
                break;
            }

        }

    }
}
