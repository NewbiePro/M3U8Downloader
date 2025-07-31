package com.tech.newbie.m3u8downloader.viewmodel;

import com.tech.newbie.m3u8downloader.common.utils.TimeUtil;
import com.tech.newbie.m3u8downloader.model.Video;
import com.tech.newbie.m3u8downloader.service.M3U8ParserService;
import com.tech.newbie.m3u8downloader.service.MediaService;
import com.tech.newbie.m3u8downloader.service.MergeService;
import com.tech.newbie.m3u8downloader.service.strategy.download.DownloadService;
import com.tech.newbie.m3u8downloader.service.strategy.download.ThreadPoolDownloadService;
import com.tech.newbie.m3u8downloader.service.strategy.ui.AlertUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.ui.ProgressBarUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.ui.StatusTextUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.ui.StatusUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.ui.TimeLabelUpdateStrategy;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
@Slf4j
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
    private final M3U8ParserService m3U8ParserService =
            new M3U8ParserService(statusUpdateStrategy);

    private final DownloadService downloadService =
            new ThreadPoolDownloadService(statusUpdateStrategy, progressBarUpdateStrategy);

    private final MergeService mergeService = new MergeService(statusUpdateStrategy, alertUpdateStrategy);

    private final MediaService mediaService = new MediaService();


    public void startDownload() {
        Thread thread = new Thread(this::performDownload);
        // Set as a daemon thread to ensure it terminates automatically once the JAVAFX application thread(UI) terminate
        thread.setDaemon(true);
        thread.start();
    }

    private void performDownload() {
        try {
            long start = System.currentTimeMillis();

            String m3u8Url = inputArea.get().replaceAll("\\s+",StringUtils.EMPTY);

            // clear previous output
            resetUIState();

            HttpClient client = HttpClient.newHttpClient();
            // 0- build request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(m3u8Url))
                    .build();

            HttpResponse<String> response;
            // 1- send m3u8 request
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // 2- parse all ts urls by response
            List<String> tsUrls = m3U8ParserService.parseM3U8Content(response.body(), m3u8Url);
            // 3- download all ts files
            downloadService.downloadTsFiles(tsUrls, path, fileName.get());
            // 4- merge all ts files
            mergeService.mergeTsToMp4(path, fileName.get(), tsUrls.size());

            long duration = System.currentTimeMillis() - start;

            // 5- update UI
            timeLabelUpdateStrategy.updateStatus(duration);
            alertUpdateStrategy.updateStatus(TimeUtil.formatDuration(duration));
        } catch (Exception e) {
            log.error("error: ",e);
            statusUpdateStrategy.updateStatus("error please check......" + e.getMessage());
            alertUpdateStrategy.updateStatus("下載失敗: "+ e.getMessage());
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


    public Optional<File> getVideoFile() {
        Video video = new Video(fileName.get(), path);
        if(!video.exists()){
            return Optional.empty();
        }

        return Optional.of(video.getFile());
    }
}
