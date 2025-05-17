package com.tech.newbie.m3u8downloader.service.strategy;

import com.tech.newbie.m3u8downloader.common.utils.ExecutionTimeUtil;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;

public class TimeLabelUpdateStrategy implements StatusUpdateStrategy<Long>{
    private final StringProperty timeLabel;

    public TimeLabelUpdateStrategy(StringProperty stringProperty) {
        this.timeLabel = stringProperty;
    }

    @Override
    public void updateStatus(Long duration) {
        String formatted = "Time Consumed: " + ExecutionTimeUtil.formatDuration(duration);
        System.out.printf(formatted);
        Platform.runLater(()-> timeLabel.set(formatted));
    }
}
