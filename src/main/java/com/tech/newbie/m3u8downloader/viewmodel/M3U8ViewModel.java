package com.tech.newbie.m3u8downloader.viewmodel;

import com.tech.newbie.m3u8downloader.common.utils.ExecutionTimeUtil;
import com.tech.newbie.m3u8downloader.model.Video;
import com.tech.newbie.m3u8downloader.service.DownloadService;
import com.tech.newbie.m3u8downloader.service.M3U8ParserService;
import com.tech.newbie.m3u8downloader.service.MediaService;
import com.tech.newbie.m3u8downloader.service.MergeService;
import com.tech.newbie.m3u8downloader.service.strategy.AlertUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.ProgressBarUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.StatusTextUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.StatusUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.TimeLabelUpdateStrategy;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
@Getter
@Setter
public class M3U8ViewModel {

    // Properties for binding to the UI
    private final StringProperty statusText = new SimpleStringProperty();
    private final DoubleProperty progressBar = new SimpleDoubleProperty();
    private final StringProperty timeLabel = new SimpleStringProperty();
    private final StringProperty inputArea=  new SimpleStringProperty();
    private final StringProperty fileName= new SimpleStringProperty();
    private String path;
    // UI strategy
    private final StatusUpdateStrategy<String> statusUpdateStrategy = new StatusTextUpdateStrategy(statusText);
    private final StatusUpdateStrategy<Double> progressBarUpdateStrategy = new ProgressBarUpdateStrategy(progressBar);
    private final StatusUpdateStrategy<String> alertUpdateStrategy = new AlertUpdateStrategy();
    private final StatusUpdateStrategy<Long> timeLabelUpdateStrategy = new TimeLabelUpdateStrategy(timeLabel);
    // dependency
    private final M3U8ParserService m3U8ParserService = new M3U8ParserService(statusUpdateStrategy);
    private final DownloadService downloadService = new DownloadService(statusUpdateStrategy, progressBarUpdateStrategy);
    private final MergeService mergeService = new MergeService(statusUpdateStrategy, alertUpdateStrategy);
    private final MediaService mediaService = new MediaService();


    public void startDownload() {
        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() {
                performDownload();
                return null;
            };
        };
        measureExecutionTime(downloadTask);
    }

    private void performDownload() {
        try {
            String m3u8Url = inputArea.get();

            // clear previous output
            resetUIState();

            HttpClient client = HttpClient.newHttpClient();
            // 0- build request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(m3u8Url.replaceAll("\\s+","")))
                    .build();

            HttpResponse<String> response;
            // 1- send m3u8 request
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // 2- parse all ts urls by response
            List<String> tsUrls = m3U8ParserService.parseM3U8Content(response.body(), m3u8Url);
            // 3- download all ts files
            downloadService.parallelDownloadTsFiles(tsUrls,
                    path,
                    fileName.get());
            // 4- merge all ts files
            mergeService.mergeTsToMp4(path, fileName.get(), tsUrls.size());
            // 5- update done

        } catch (Exception e) {
            e.printStackTrace();
            statusUpdateStrategy.updateStatus("error please check......" + e.getMessage());
        }

    }

    private void resetUIState() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ((ProgressBarUpdateStrategy)progressBarUpdateStrategy).setLastProgress(0.0);
        Platform.runLater(
                () -> {
                    progressBar.set(0.0);
                    timeLabel.set(StringUtils.EMPTY);
                    latch.countDown();
                }
        );
        latch.await();
    }

    private void measureExecutionTime(Runnable task) throws RuntimeException {
        ExecutionTimeUtil.measureExecutionTime(task,
                duration -> {
                    timeLabelUpdateStrategy.updateStatus(duration);
                    alertUpdateStrategy.updateStatus(ExecutionTimeUtil.formatDuration(duration));
                }, e -> {
                    statusUpdateStrategy.updateStatus("錯誤: "+ e.getClass().getSimpleName());
                    alertUpdateStrategy.updateStatus("下載失敗: "+ e.getMessage());
                }
        );
    }

    public Optional<File> getVideoFile() {
        Video video = new Video(fileName.get(), path);
        if(!video.exists()){
            return Optional.empty();
        }

        return Optional.of(video.getFile());
    }
}
