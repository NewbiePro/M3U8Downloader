module com.tech.newbie.m3u8downloader.core {
    requires java.net.http;
    requires static lombok;
    requires org.slf4j;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;

    exports com.tech.newbie.m3u8downloader.core.model;
    exports com.tech.newbie.m3u8downloader.core.service;
    exports com.tech.newbie.m3u8downloader.core.service.strategy.download;
    exports com.tech.newbie.m3u8downloader.core.common.utils;
    exports com.tech.newbie.m3u8downloader.core.common.callback;
    exports com.tech.newbie.m3u8downloader.core.common.enums;
    exports com.tech.newbie.m3u8downloader.core.config;
}
