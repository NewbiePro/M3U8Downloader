package com.tech.newbie.m3u8downloader.service.strategy.download;

import com.tech.newbie.m3u8downloader.common.enums.DownloadType;
import com.tech.newbie.m3u8downloader.config.AppConfig;
import com.tech.newbie.m3u8downloader.model.Statistics;
import com.tech.newbie.m3u8downloader.service.strategy.ui.ProgressBarUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.ui.StatusUpdateStrategy;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.tech.newbie.m3u8downloader.common.constant.Constant.TS_FORMAT;

@Slf4j
public abstract class DownloadService {
    protected final StatusUpdateStrategy<String> statusUpdateStrategy;
    protected final StatusUpdateStrategy<Double> progressUpdateStrategy;
    protected final AppConfig appConfig;
    protected final HttpClient httpClient;
    protected final Statistics statistics;

    protected final AtomicInteger counter = new AtomicInteger(1);
    private static final Semaphore LIMITER = new Semaphore(32);

    protected DownloadService(StatusUpdateStrategy<String> statusUpdateStrategy,
                              StatusUpdateStrategy<Double> progressUpdateStrategy,
                              DownloadType downloadType) {
        this.statusUpdateStrategy = statusUpdateStrategy;
        this.progressUpdateStrategy = progressUpdateStrategy;
        this.appConfig = AppConfig.getInstance();
        this.statistics = new Statistics(downloadType);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(appConfig.getTimeout()))
                .sslContext(getInsecureSslContext())
                .version(HttpClient.Version.HTTP_1_1)
                .build();

    }

    // 10min: original downloader
    // 6min: single thread on downloading all ts files
    // template method - define download proces
    public void downloadTsFiles(List<String> tsUrls, String outputDir, String fileName) {
        long startTime = System.currentTimeMillis();
        statistics.setTotalTsFiles(tsUrls.size());
        statistics.setSuccessCount(0);
        statistics.setFailedCount(0);
        statistics.setTotalBytes(0);

        log.info("Start using [{}] to download [{}] tsFiles", statistics.getDownloadType(), tsUrls.size());
        statusUpdateStrategy.updateStatus("Downloading....");
        counter.set(1);

        try {
            List<CompletableFuture<Void>> futures = createDownloadFutures(tsUrls, outputDir, fileName);
            CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allDone.join();

            statistics.setDownloadTime(System.currentTimeMillis() - startTime);
            afterDownload();
        } catch (Exception e) {
            log.error("Download error", e);
            statusUpdateStrategy.updateStatus("Error Downloading");
        }

    }

    public void downloadTsFile(String tsUrl, String outputDir, String fileName, int size, Consumer<Double> progressCallback) throws IOException, InterruptedException {
        int index = counter.getAndIncrement();
        File outputFile = new File(outputDir, String.format(TS_FORMAT, fileName, index));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tsUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build();

        int maxRetries = appConfig.getMaxRetries();
        int attempt = 0;
        while (true) {
            LIMITER.acquire();
            try {
                // use InputStream mode, which saves memory
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int statusCode = response.statusCode();
                if (statusCode != 200) {
                    if (response.body() != null) response.body().close();
                    log.error("invalid response for ts segment: [{}] status: [{}]", tsUrl, response.statusCode());
                    throw new IOException("invalid response");
                }
                // ts file output to directory
                try (InputStream is = response.body()) {
                    Files.copy(is, outputFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                break;
            } catch (IOException e) {
                attempt++;
                log.warn("Attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt >= maxRetries) {
                    log.error("Max retries reached for ts segment: [{}]", tsUrl);
                    throw new RuntimeException(String.format("Attempt %d failed to fetch ts: %s", attempt, tsUrl));
                }
                Files.deleteIfExists(outputFile.toPath());
            } finally {
                LIMITER.release();
            }
        }

        //update progress bar
        double progress = (double) index / size;
        progressCallback.accept(progress);
        log.info("Thread:{} Downloaded......{}/{}", Thread.currentThread().getName(), index, size);
    }


    protected void afterDownload() {
        log.info("Finish Downloading, Download Duration: [{}], Total Ts Files: [{}]", statistics.getDownloadTime(), statistics.getTotalTsFiles());
        statusUpdateStrategy.updateStatus("Download completed");
        if (progressUpdateStrategy instanceof ProgressBarUpdateStrategy) {
            ((ProgressBarUpdateStrategy) progressUpdateStrategy).forceComplete();
        }
    }

    // for override
    protected abstract void cleanup();

    protected abstract List<CompletableFuture<Void>> createDownloadFutures(List<String> tsUrls, String outputDir, String fileName);


    private SSLContext getInsecureSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, trustAllCerts, new SecureRandom());
            return sc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create insecure SSL context", e);
        }
    }

}
