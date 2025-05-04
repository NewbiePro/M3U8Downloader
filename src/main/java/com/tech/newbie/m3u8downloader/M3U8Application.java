package com.tech.newbie.m3u8downloader;

import com.tech.newbie.m3u8downloader.common.constant.Constant;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class M3U8Application extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(M3U8Application.class.getResource("m3u8-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), Constant.WINDOW_WIDTH, Constant.WINDOW_HEIGHT);
        stage.setTitle("hello myM3U8!");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}