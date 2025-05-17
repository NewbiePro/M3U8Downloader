package com.tech.newbie.m3u8downloader.common.utils;

import java.util.function.Consumer;

public class ExecutionTimeUtil {
    public static void measureExecutionTime(Runnable task, Consumer<Long> onFinished, Consumer<Throwable> onError) {
        long start = System.currentTimeMillis();
        Thread thread = new Thread(() -> {
            try {
                task.run();
                long end = System.currentTimeMillis();
                onFinished.accept(end - start);
            } catch (Throwable t){
                onError.accept(t);
            }
        });
        thread.start();
    }

    public static String formatDuration(long durationMillis){
        long minutes = durationMillis / (1000 * 60);
        long seconds = (durationMillis / 1000) % 60;
        long milliseconds = durationMillis % 1000;
        return String.format("[%d minutes %d seconds %d millis]", minutes, seconds, milliseconds);
    }
}
