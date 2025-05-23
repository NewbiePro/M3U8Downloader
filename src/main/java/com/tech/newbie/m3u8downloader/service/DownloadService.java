package com.tech.newbie.m3u8downloader.service;

import com.tech.newbie.m3u8downloader.service.strategy.ProgressBarUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.StatusUpdateStrategy;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static com.tech.newbie.m3u8downloader.common.Constant.DOWNLOADED_FORMAT;
import static com.tech.newbie.m3u8downloader.common.Constant.TS_FORMAT;

public class DownloadService {
    private final StatusUpdateStrategy<String> statusUpdateStrategy;
    private final StatusUpdateStrategy<Double> progressUpdateStrategy;
    private final AtomicInteger counter = new AtomicInteger(1);

    public DownloadService(StatusUpdateStrategy<String> statusUpdateStrategy, StatusUpdateStrategy<Double> progressUpdateStrategy) {
        this.statusUpdateStrategy = statusUpdateStrategy;
        this.progressUpdateStrategy = progressUpdateStrategy;
    }

    // 10min: original downloader
    // 6min: single thread on downloading all ts files
    public void parallelDownloadTsFiles(List<String> tsUrls, String outputDir, String fileName) {
        statusUpdateStrategy.updateStatus("downloading.........");
        counter.set(1);
        // 創建一個固定的線程池
        // TODO create a customized threadpool
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = IntStream.range(0, tsUrls.size())
                .mapToObj(
                        index -> CompletableFuture.runAsync(
                                () -> {
                                    String tsUrl = tsUrls.get(index);
                                    try {
                                        downloadTsFile(tsUrl,
                                                outputDir,
                                                fileName,
                                                tsUrls.size(),
                                                progressUpdateStrategy::updateStatus);
                                    } catch (Exception e) {
                                        throw new CompletionException(e);
                                    }
                                }, executorService))
                .toList();
        //等待所有任務完成
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        // 阻塞直到所有任務都完成
        allDone.join();
        ((ProgressBarUpdateStrategy) progressUpdateStrategy).forceComplete();

        //關閉線程池
        executorService.shutdown();
        System.out.println("shut down thread pool......");
        statusUpdateStrategy.updateStatus("shut down thread pool.........");
    }

    public void downloadTsFile(String tsUrl, String outputDir, String fileName, int size, Consumer<Double> progressCallback) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();
        int index = counter.getAndIncrement();
        File outputFile = new File(outputDir, String.format(TS_FORMAT, fileName, index));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tsUrl))
                .build();
        HttpResponse<byte[]> response = null;
        int maxRetries = 10;
        int attempt = 0;
        // retries 10 times
        while (true){
            try{
                // ts file
                response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
                    System.out.println("invalid response for ts segment: "+ tsUrl + " [status: "+ response.statusCode() +" ]");
                    throw new IOException("invalid response");
                }
                // ts file output to directory
                Files.write(outputFile.toPath(), response.body());
                break;
            } catch (IOException e){
                attempt++;
                System.out.println(e.getMessage());
                if(attempt >= maxRetries){
                    throw new RuntimeException(String.format("Attempt %d failed to fetch ts: %s",attempt,tsUrl));
                }
                Files.deleteIfExists(outputFile.toPath());
                Thread.sleep(200L * attempt);
            }
        }

        //update progress bar
        double progress = (double) index / size;
        progressCallback.accept(progress);
        System.out.printf(DOWNLOADED_FORMAT, Thread.currentThread().getName(), index, size);
    }

}
