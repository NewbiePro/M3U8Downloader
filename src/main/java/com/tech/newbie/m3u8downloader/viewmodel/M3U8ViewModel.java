package com.tech.newbie.m3u8downloader.viewmodel;

import com.tech.newbie.m3u8downloader.common.utils.ExecutionTimeUtil;
import com.tech.newbie.m3u8downloader.service.DownloadService;
import com.tech.newbie.m3u8downloader.service.M3U8ParserService;
import com.tech.newbie.m3u8downloader.service.MergeService;
import com.tech.newbie.m3u8downloader.service.strategy.AlertUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.ProgressBarUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.StatusTextUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.StatusUpdateStrategy;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

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

    // strategy
    private final StatusUpdateStrategy<String> statusUpdateStrategy = new StatusTextUpdateStrategy(statusText);
    private final StatusUpdateStrategy<Double> progressBarUpdateStrategy = new ProgressBarUpdateStrategy(progressBar);
    private final StatusUpdateStrategy<String> alertUpdateStrategy = new AlertUpdateStrategy();

    // dependency
    private final M3U8ParserService m3U8ParserService = new M3U8ParserService(statusUpdateStrategy);
    private final DownloadService downloadService = new DownloadService(statusUpdateStrategy, progressBarUpdateStrategy);
    private final MergeService mergeService = new MergeService(statusUpdateStrategy, alertUpdateStrategy);


    public void startDownload(){
        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call(){
                try{
                    String m3u8Url = inputArea.get();
                    HttpClient client = HttpClient.newHttpClient();
                    // 0- build request
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(m3u8Url))
                            .build();

                    HttpResponse<String> response;
                    // 1- fetch m3u8 file
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    // 2- get all ts urls
                    List<String> tsUrls = m3U8ParserService.parseM3U8Content(response.body());
                    // 3- download all ts files
                    downloadService.parallelDownloadTsFiles(tsUrls,
                            path,
                            fileName.get());
                    // 4- merge all ts files
                    mergeService.mergeTsToMp4(path , fileName.get(), tsUrls.size());
                    // 5- update done

                } catch (Exception e){
                    e.printStackTrace();
                    statusUpdateStrategy.updateStatus("error please check......" +  e.getMessage());
                }
                return null;
            }
        };

        // Execute the task
        ExecutionTimeUtil.measureExecutionTime(downloadTask, timeLabel);
    }



}
