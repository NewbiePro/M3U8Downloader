package com.tech.newbie.m3u8downloader.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class M3U8Controller {
    @FXML
    public TextArea inputArea;
    @FXML
    public Label text;
    @FXML
    public Label timeLabel;
    @FXML
    public TextField pathField;
    @FXML
    public TextField fileNameField;

    private static final String M3U8_HEADER = "#EXTM3U";

    private static final String TS_FORMAT = "%s_%d.ts";
    public void onSelectPathClick(ActionEvent actionEvent) {

        DirectoryChooser directoryChooser = new DirectoryChooser();

        Stage stage = (Stage) inputArea.getScene().getWindow();
        File selectedDir = directoryChooser.showDialog(stage);

        if(selectedDir != null){
            pathField.setText(selectedDir.getAbsolutePath());
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "not chosen any directory", ButtonType.OK);
            alert.showAndWait();
        }

    }

    @FXML
    public void onDownloadButtonClick(ActionEvent actionEvent)  {
        String m3u8Url = inputArea.getText();

        measureExecutionTime(new Thread(() -> {
                    try {
                        HttpClient client = HttpClient.newHttpClient();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(m3u8Url))
                                .build();

                        HttpResponse<String> response;
                        response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        //過濾出.ts
                        var tsUrls = parseM3U8Content(response.body());

                        System.out.println("列表");
                        tsUrls.forEach(System.out::println);

                        javafx.application.Platform.runLater(
                                () -> text.setText("清單中共有 " + tsUrls.size() + " 個ts檔"));

                        downloadTsFiles(tsUrls);
                    } catch (Exception e) {
                        e.printStackTrace();
                        javafx.application.Platform.runLater(
                                () -> text.setText("error, please check......"));
                    }


                })
        );


    }

    private void downloadTsFiles(List<String> tsUrls) {
        HttpClient client = HttpClient.newHttpClient();
        String baseFilePath = pathField.getText();
        String baseFileName = fileNameField.getText();

        for (int i = 0; i < tsUrls.size(); i++) {
            String url = tsUrls.get(i);
            try{
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .build();

                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                File outputFile = new File(baseFilePath, String.format(TS_FORMAT, baseFileName, i));
                Files.write(outputFile.toPath(), response.body());

            } catch (Exception e){
                System.out.println("下載失敗: "+url);
            }
        }

    }

    private List<String> parseM3U8Content (String content){
        if(!content.contains(M3U8_HEADER)) {
            javafx.application.Platform.runLater(() ->
                    text.setText("invalid m3u8 url"));
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
                timeLabel.setText(String.format("下載耗時: [%s]ms", end - start)));
    }


}