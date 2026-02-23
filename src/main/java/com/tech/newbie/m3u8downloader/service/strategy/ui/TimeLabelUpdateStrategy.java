package com.tech.newbie.m3u8downloader.service.strategy.ui;

import com.tech.newbie.m3u8downloader.core.common.utils.TimeUtil;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeLabelUpdateStrategy implements StatusUpdateStrategy<Long> {
    private final StringProperty timeLabel;

    public TimeLabelUpdateStrategy(StringProperty stringProperty) {
        this.timeLabel = stringProperty;
    }

    @Override
    public void updateStatus(Long duration) {
        String formatted = TimeUtil.formatDuration(duration);
        log.info(formatted);
        Platform.runLater(() -> timeLabel.set(formatted));
    }
}
