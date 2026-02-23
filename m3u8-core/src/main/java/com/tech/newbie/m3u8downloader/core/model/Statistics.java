package com.tech.newbie.m3u8downloader.core.model;

import com.tech.newbie.m3u8downloader.core.common.utils.TimeUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Statistics {
    private long totalTime; // 总耗时
    private long downloadTime; // 下载耗时
    private long writeTime; // 写入耗时
    private long mergeTime; // 合并耗时

    private int totalTsFiles; // 总文件数
    private long totalBytes; // 总字节数
    private int successCount; // 成功下载数
    private int failedCount; // 失败数

    public Statistics() {
    }

    public String getFormattedReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Metrics ===\n");
        report.append("Total Ts Files: ").append(totalTsFiles).append("\n");
        report.append("Success Counts: ").append(successCount).append("\n");
        report.append("Failed Counts: ").append(failedCount).append("\n");
        report.append("Total Bytes : ").append(totalBytes).append(" bytes\n");
        report.append("Download Time: ").append(TimeUtil.formatDuration(downloadTime)).append("\n");
        report.append("Total Time: ").append(TimeUtil.formatDuration(totalTime)).append("\n");
        report.append("Success Rate: ").append(String.format("%.2f%%",
                totalTsFiles > 0 ? (successCount * 100.0 / totalTsFiles) : 0)).append("\n");
        return report.toString();
    }
}
