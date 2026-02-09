package com.tech.newbie.m3u8downloader.service.strategy.download;

import com.tech.newbie.m3u8downloader.common.enums.DownloadType;
import com.tech.newbie.m3u8downloader.service.strategy.ui.StatusUpdateStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class VirtualThreadDownloadService extends DownloadService{

    public VirtualThreadDownloadService(StatusUpdateStrategy<String> statusUpdateStrategy,
                                        StatusUpdateStrategy<Double> progressUpdateStrategy) {
        super(statusUpdateStrategy, progressUpdateStrategy, DownloadType.VIRTUAL_THREAD);
    }
    private static final ExecutorService VIRTUAL_THREAD_POOL = Executors.newVirtualThreadPerTaskExecutor();


    @Override
    protected List<CompletableFuture<Void>> createDownloadFutures(List<String> tsUrls, String outputDir, String fileName) {
        log.info("Creating download futures with virtual threads...");


        return tsUrls.stream().map(url -> CompletableFuture.runAsync(
                        () -> {
                            try {
                                downloadTsFile(url,
                                        outputDir,
                                        fileName,
                                        tsUrls.size(),
                                        progressUpdateStrategy::updateStatus);
                            } catch (Exception e) {
                                log.error("Error downloading with virtual thread", e);
                            }
                        },
                        VIRTUAL_THREAD_POOL))
                .toList();
    }

    @Override
    protected void cleanup() {
        log.info("Virtual thread download service cleanup completed");
        // 虚拟线程会自动回收，无需显式关闭
    }

}
