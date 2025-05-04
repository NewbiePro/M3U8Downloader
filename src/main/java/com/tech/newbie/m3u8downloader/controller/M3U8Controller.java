package com.tech.newbie.m3u8downloader.controller;

import com.tech.newbie.m3u8downloader.common.constant.Constant;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class M3U8Controller {
    @FXML
    public TextField inputField;
    @FXML
    private Label welcomeText;

    @FXML
    public Label timeLabel;


    @FXML
    public void onEnterButtonClick(ActionEvent actionEvent)  {
        String m3u8Url = inputField.getText();

        measureExecutionTime(new Thread(() -> {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(m3u8Url))
                    .build();

            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            //過濾出.ts
            var tsUrls =  parseM3U8Content(response.body());

            System.out.println("列表");
            tsUrls.forEach(System.out::println);

            javafx.application.Platform.runLater(
                    () -> welcomeText.setText("清單中共有 "+ tsUrls.size()+" 個ts檔"));
        })
        );


    }

    private List<String> parseM3U8Content (String content){
        if(!content.contains(Constant.M3U8_HEADER)) {
            javafx.application.Platform.runLater(() ->
                    welcomeText.setText("invalid m3u8 url"));
            return Collections.emptyList();
        }
        return content.lines()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .toList();
    }

    private void measureExecutionTime(Runnable task){
        long start = System.currentTimeMillis();
        task.run();
        long end = System.currentTimeMillis();
        javafx.application.Platform.runLater(() ->
                timeLabel.setText(String.format("下載耗時: [%s] %n", end - start)));
    }
}