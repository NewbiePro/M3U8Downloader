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
public class VirtualThreadDownloadService extends DownloadService{

    public VirtualThreadDownloadService(StatusUpdateStrategy<String> statusUpdateStrategy,
                                        StatusUpdateStrategy<Double> progressUpdateStrategy) {
        super(statusUpdateStrategy, progressUpdateStrategy, DownloadType.VIRTUAL_THREAD);
    }


    @Override
    protected List<CompletableFuture<Void>> createDownloadFutures(List<String> tsUrls, String outputDir, String fileName) {
        log.info("Creating download futures with virtual threads...");
        ExecutorService virtualThreadPool = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("m3u8-virtual-thread-", 0 ).factory());

        return IntStream.range(0, tsUrls.size())
                .mapToObj(index ->
                        CompletableFuture.runAsync(
                                () -> {
                                    String tsUrl = tsUrls.get(index);
                                    try {
                                        downloadTsFile(tsUrl,
                                                outputDir,
                                                fileName,
                                                tsUrls.size(),
                                                progressUpdateStrategy::updateStatus);
                                    } catch (Exception e) {
                                        //TODO 優化
                                        log.error("Error downloading with virtual thread", e);
                                    }
                                },
                                virtualThreadPool))
                .toList();
    }

    @Override
    protected void cleanup() {
        log.info("Virtual thread download service cleanup completed");
        // 虚拟线程会自动回收，无需显式关闭
    }

}
