package com.tech.newbie.m3u8downloader.common.utils;

import javafx.beans.property.StringProperty;
import javafx.scene.control.Alert;

import java.util.concurrent.CompletableFuture;

public class ExecutionTimeUtil {
    public static void measureExecutionTime(Runnable task, StringProperty timeLabel) {
        long start = System.currentTimeMillis();
        CompletableFuture.runAsync(task).whenComplete((result, throwable) -> {
            long end = System.currentTimeMillis();
            long duration = end - start;
            long minutes = duration / (1000 * 60);
            long seconds = (duration / 1000) % 60;
            long milliseconds = duration % 1000;

            if (throwable != null) {
                System.out.println(throwable.getCause());
            }


            javafx.application.Platform.runLater(() -> {
                timeLabel.set(String.format("Time Consumed: [%d minutes %d seconds %d ms] ", minutes, seconds, milliseconds));
                // 彈窗顯示已完成的消息
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Download Completed");
                alert.setHeaderText("Download And Merged Finished");
                alert.setContentText(String.format("Time Consumed: [%d minutes %d seconds %d ms] ", minutes, seconds, milliseconds));
                System.out.printf("time consumed: [%d]", duration);
                alert.showAndWait();
                // 最後記錄下ms的紀錄
            });
        });
    }

}
