package com.tech.newbie.m3u8downloader.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import lombok.Getter;
import lombok.Setter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@Getter
@Setter
public class M3U8ViewModel {
    private static final String M3U8_HEADER = "#EXTM3U";
    private static final String TS_FORMAT = "%s_%d.ts";
    private static final String DOWNLOAD_FORMAT = "Thread:%s Downloading......%d/%d\n";


    // Properties for binding to the UI
    private final StringProperty statusText = new SimpleStringProperty();
    private final DoubleProperty progressBar = new SimpleDoubleProperty();
    private final StringProperty timeLabel = new SimpleStringProperty();
    private final StringProperty inputArea=  new SimpleStringProperty();
    private final StringProperty fileName= new SimpleStringProperty();
    private String path;

    public void startDownload(){
        Task<Void> downloadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try{
                    String m3u8Url = inputArea.get();
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(m3u8Url))
                            .build();

                    HttpResponse<String> response;
                    // 1- fetch m3u8 文件
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    // 2- get all ts urls
                    var tsUrls = parseM3U8Content(response.body());
                    updateStatus("清單中共有 " + tsUrls.size() + " 個ts檔");
                    // 3- download all ts files
                    parallelDownloadTsFiles(tsUrls, path, fileName.get());
                    // 4- merge all ts files
                    mergeTsToMp4(path , fileName.get(), tsUrls.size());
                } catch (Exception e){
                    e.printStackTrace();
                    updateStatus("error please check......");
                }
                return null;
            }
        };

        // Execute the task
        new Thread(downloadTask).start();
    }


    private List<String> parseM3U8Content(String content) {
        System.out.println("parsing M3U8");
        if (!content.contains(M3U8_HEADER)) {
            updateStatus("invalid m3u8 url");
            return Collections.emptyList();
        }
        return content.lines()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .toList();
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> statusText.set(message));
    }



    // 10min: original downloader
    // 6min: single thread on downloading all ts files
    private void parallelDownloadTsFiles(List<String> tsUrls, String outputDir, String fileName) {
        // 創建一個固定的線程池
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<CompletableFuture<Void>> futures = IntStream.range(0, tsUrls.size())
                .mapToObj(
                        index -> CompletableFuture.runAsync(
                                () -> {
                                    String tsUrl = tsUrls.get(index);
                                    try {
                                        downloadTsFile(tsUrl, outputDir, fileName, index, tsUrls.size());
                                    } catch (Exception e) {
                                        throw new CompletionException(e);
                                    }
                                }, executorService))
                .toList();
        //等待所有任務完成
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        // 阻塞直到所有任務都完成
        allDone.join();

        //關閉線程池
        executorService.shutdown();
    }

    private void downloadTsFile(String tsUrl, String outputDir, String fileName, int index, int size) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();
        System.out.printf(DOWNLOAD_FORMAT, Thread.currentThread().getName(), index, size);
        File outputFile = new File(outputDir, String.format(TS_FORMAT, fileName, index));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tsUrl))
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        //寫入文件
        Files.write(outputFile.toPath(), response.body());
        //計入進度
        double progress = (double) index / size;
        javafx.application.Platform.runLater(() -> progressBar.set(progress));
    }


    private void mergeTsToMp4(String baseFilePath, String baseFileName, int totalFiles) throws IOException {
        System.out.println("Start merging");
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
        CompletableFuture.runAsync(task).whenComplete((result, throwable) -> {
            long end = System.currentTimeMillis();
            long duration = end - start;
            long minutes = duration / (1000 * 60);
            long seconds = (duration / 1000) % 60;
            long milliseconds = duration % 1000;

            if (throwable != null) {
                System.out.println(throwable.getCause());
            }


            javafx.application.Platform.runLater(() -> {
                timeLabel.set(String.format("Time Consumed: [%d minutes %d seconds %d ms] ", minutes, seconds, milliseconds));
                // 彈窗顯示已完成的消息
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Download Completed");
                alert.setHeaderText("Download And Merged Finished");
                alert.setContentText(String.format("Time Consumed: [%d minutes %d seconds %d ms] ", minutes, seconds, milliseconds));
                System.out.printf("time consumed: [%d]", duration);
                alert.showAndWait();
                // 最後記錄下ms的紀錄
            });


        });
    }


}
