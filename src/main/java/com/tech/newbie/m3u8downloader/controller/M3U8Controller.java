package com.tech.newbie.m3u8downloader.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class M3U8Controller {
    @FXML
    public TextArea inputArea;
    @FXML
    public Label text;
    @FXML
    public Label timeLabel;
    @FXML
    public TextField fileNameField;
    @FXML
    public ProgressBar progressBar;


    private static final String M3U8_HEADER = "#EXTM3U";
    private static final String TS_FORMAT = "%s_%d.ts";
    private static final String DOWNLOAD_FORMAT = "Downloading......%d/%d";
    private static String pathField;


    @FXML
    public void onSelectPathClick(ActionEvent actionEvent) {

        DirectoryChooser directoryChooser = new DirectoryChooser();

        Stage stage = (Stage) inputArea.getScene().getWindow();
        File selectedDir = directoryChooser.showDialog(stage);

        if (selectedDir != null) {
            pathField = selectedDir.getAbsolutePath();
            System.out.println("Path is: " + pathField);
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "not chosen any directory", ButtonType.OK);
            alert.showAndWait();
        }

    }

    @FXML
    public void onDownloadButtonClick(ActionEvent actionEvent) {
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

                        javafx.application.Platform.runLater(
                                () -> text.setText("清單中共有 " + tsUrls.size() + " 個ts檔"));

                        parallelDownloadTsFiles(tsUrls);
                    } catch (Exception e) {
                        e.printStackTrace();
                        javafx.application.Platform.runLater(
                                () -> text.setText("Error, please check......"));
                    }


                })
        );
    }

    private List<String> parseM3U8Content(String content) {
        if (!content.contains(M3U8_HEADER)) {
            javafx.application.Platform.runLater(() ->
                    text.setText("invalid m3u8 url"));
            return Collections.emptyList();
        }
        return content.lines()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .toList();
    }

    // 10min: original downloader
    // 6min: single thread on downloading all ts files
    private void downloadTsFiles(List<String> tsUrls) {
        HttpClient client = HttpClient.newHttpClient();
        String baseFilePath = pathField;
        String baseFileName = fileNameField.getText();
        int size = tsUrls.size();
        for (int i = 0; i < size; i++) {
            System.out.printf(DOWNLOAD_FORMAT, i, size);
            String url = tsUrls.get(i);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .build();

                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                File outputFile = new File(baseFilePath, String.format(TS_FORMAT, baseFileName, i));
                Files.write(outputFile.toPath(), response.body());

                // set progression bar
                int currentIndex = i + 1;
                double progress = (double) currentIndex / size;
                javafx.application.Platform.runLater(() -> progressBar.setProgress(progress));

            } catch (Exception e) {
                System.out.println("下載失敗: " + url);
            }
        }
    }

    private void parallelDownloadTsFiles(List<String> tsUrls) {
        HttpClient client = HttpClient.newHttpClient();
        String baseFilePath = pathField;
        String baseFileName = fileNameField.getText();
        int size = tsUrls.size();

        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < size; i++) {
            final int index = i;
            executor.submit(() -> {
                String url = tsUrls.get(index);
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .build();
                    HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                    File outputFile = new File(baseFilePath, String.format(TS_FORMAT, baseFileName, index));
                    Files.write(outputFile.toPath(), response.body());
                    mergeTsToMp4(baseFilePath, baseFileName, size);
                } catch (Exception e) {
                    System.out.println("下載失敗: " + url);
                } finally {
                    int currentIndex = counter.incrementAndGet();
                    double progress = (double) currentIndex / size;
                    Platform.runLater(() -> progressBar.setProgress(progress));
                }
            });
        }
        executor.shutdown();
    }

    private void mergeTsToMp4(String baseFilePath, String baseFileName, int totalFiles) throws IOException {

        // create FileList.txt that includes all ts files
        StringBuilder fileListContent = new StringBuilder();
        for (int i = 0; i < totalFiles; i++) {
            fileListContent.append("file '").append(baseFilePath)
                    .append(File.separator).append(String.format(TS_FORMAT, baseFileName, i))
                    .append(" '\n");
        }

        // save fileList.txt
        File fileList = new File(baseFilePath, "fileList.txt");
        Files.write(fileList.toPath(), fileListContent.toString().getBytes());

        // call ffmpeg
        String command = String.format("ffmpeg -f concat -safe 0 -i %s -c copy %s",
                fileList.getAbsolutePath(),
                new File(baseFilePath, baseFileName + ".mp4").getAbsolutePath());

        // execute command
        ProcessBuilder pb = new ProcessBuilder(command.split(" "));
        pb.redirectErrorStream(true); // merge stdError & stdOutput
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            //等待命令執行完畢
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("合併完成，生成 " + baseFileName + ".mp4");
            } else {
                System.out.println("ffmpeg執行失敗，錯誤代碼: " + exitCode);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


    private void measureExecutionTime(Runnable task) {
        long start = System.currentTimeMillis();
        Future<?> future = Executors.newSingleThreadExecutor().submit(task);
        try {
            future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }

        long end = System.currentTimeMillis();
        long duration = end - start;
        long minutes = duration / (1000 * 60);
        long seconds = (duration / 1000) % 60;
        long milliseconds = duration % 1000;

        javafx.application.Platform.runLater(() ->
                timeLabel.setText(String.format("Time Consumed: [%d minutes %d seconds %d ms] ", minutes, seconds, milliseconds)));
        javafx.application.Platform.runLater(() ->
                text.setText("Completed! "));
        System.out.printf("time consumed: [%d]", duration);
    }

}

