package com.tech.newbie.m3u8downloader.service.strategy.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class AlertUpdateStrategy implements StatusUpdateStrategy<String> {
    @Override
    public void updateStatus(String message) {
        Platform.runLater(()->{
            Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
            alert.showAndWait();
        });
    }
}
