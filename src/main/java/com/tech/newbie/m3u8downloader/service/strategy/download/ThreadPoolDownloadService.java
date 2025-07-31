package com.tech.newbie.m3u8downloader.service.strategy.download;

import com.tech.newbie.m3u8downloader.common.enums.DownloadType;
import com.tech.newbie.m3u8downloader.service.strategy.ui.StatusUpdateStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@Slf4j
public class ThreadPoolDownloadService extends DownloadService {

    private ExecutorService executorService;

    public ThreadPoolDownloadService(StatusUpdateStrategy<String> statusUpdateStrategy,
                                     StatusUpdateStrategy<Double> progressUpdateStrategy) {
        super(statusUpdateStrategy, progressUpdateStrategy, DownloadType.THREAD_POOL);
        executorService = Executors.newFixedThreadPool(appConfig.getMaxThreads());
    }


    @Override
    protected List<CompletableFuture<Void>> createDownloadFutures(List<String> tsUrls, String outputDir, String fileName) {
        log.info("create download futures......");
        return IntStream.range(0, tsUrls.size())
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
                                        log.error("Error downloading, ", e);
                                    }
                                }
                        , executorService)
                ).toList();
    }

    @Override
    protected void cleanup() {
        log.info("shut down thread pool......");
        executorService.shutdown();

    }

}
