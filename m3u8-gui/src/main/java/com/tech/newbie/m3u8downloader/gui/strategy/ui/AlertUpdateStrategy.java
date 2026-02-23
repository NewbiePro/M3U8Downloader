package com.tech.newbie.m3u8downloader.gui.strategy.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class AlertUpdateStrategy implements com.tech.newbie.m3u8downloader.core.common.callback.UpdateCallback<String> {
    @Override
    public void update(String message) {
        Platform.runLater(()->{
            Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
            alert.showAndWait();
        });
    }
}
