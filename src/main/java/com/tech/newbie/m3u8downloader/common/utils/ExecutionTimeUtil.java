package com.tech.newbie.m3u8downloader.common.utils;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ExecutionTimeUtil {
    public static void measureExecutionTime(Runnable task, Consumer<Long> onFinished, Consumer<Throwable> onError) {
        long start = System.currentTimeMillis();
        CompletableFuture.runAsync(task).whenComplete((result, throwable) -> {
            long end = System.currentTimeMillis();
            long duration = end - start;

            if (throwable != null){
                onError.accept(throwable);
            } else {
                onFinished.accept(duration);
            }
        });
    }

    public static String formatDuration(long durationMillis){
        long minutes = durationMillis / (1000 * 60);
        long seconds = (durationMillis / 1000) % 60;
        long milliseconds = durationMillis % 1000;
        return String.format("[%d minutes %d seconds %d millis]", minutes, seconds, milliseconds);
    }
}
