module com.tech.newbie.m3u8downloader {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.net.http;
    requires static lombok;
    requires org.apache.commons.lang3;
    requires java.desktop;
    requires javafx.media;

    opens com.tech.newbie.m3u8downloader to javafx.fxml;
    exports com.tech.newbie.m3u8downloader;
    exports com.tech.newbie.m3u8downloader.controller;
    opens com.tech.newbie.m3u8downloader.controller to javafx.fxml;
}