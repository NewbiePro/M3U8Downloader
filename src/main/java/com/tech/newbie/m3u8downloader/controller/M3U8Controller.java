package com.tech.newbie.m3u8downloader.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;

public class M3U8Controller {
    @FXML
    public TextField inputField;
    @FXML
    private Label welcomeText;


    @FXML
    public void onEnterButtonClick(ActionEvent actionEvent) {
        String m3u8Url = inputField.getText();
        long start = System.currentTimeMillis();
        welcomeText.setText("downloading........");

        new Thread(() -> {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(m3u8Url))
                    .build();

            HttpResponse<String> response = null;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String content = response.body();

            //過濾出.ts
            var tsUrls = content.lines()
                    .filter(line -> !line.startsWith("#") && !line.isBlank())
                    .toList();

            System.out.println("列表");
            tsUrls.forEach(System.out::println);

            javafx.application.Platform.runLater(
                    () -> welcomeText.setText("清單中共有 "+ tsUrls.size()+" 個ts檔"));
        }).start();



        long end = System.currentTimeMillis();
        System.out.printf("下載耗時: [%s] %n", end - start);
    }
}