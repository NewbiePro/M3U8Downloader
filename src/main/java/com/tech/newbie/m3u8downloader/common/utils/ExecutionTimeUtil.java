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
        // Set as a daemon thread to ensure it terminates automatically once the JAVAFX application thread(UI) terminate
        thread.setDaemon(true);
        thread.start();
    }

    public static String formatDuration(long durationMillis){
        long minutes = durationMillis / (1000 * 60);
        long seconds = (durationMillis / 1000) % 60;
        long milliseconds = durationMillis % 1000;
        return String.format("%d MIN %d S %d MS", minutes, seconds, milliseconds);
    }
}
