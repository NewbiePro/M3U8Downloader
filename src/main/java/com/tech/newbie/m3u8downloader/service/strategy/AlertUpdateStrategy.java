package com.tech.newbie.m3u8downloader.service.strategy;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class AlertUpdateStrategy implements StatusUpdateStrategy<String> {
    @Override
    public void updateStatus(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.showAndWait();
    }
}
