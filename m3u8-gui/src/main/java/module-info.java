module com.tech.newbie.m3u8downloader.gui {
    requires com.tech.newbie.m3u8downloader.core;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires static lombok;
    requires java.desktop;
    requires org.slf4j;
    requires org.apache.commons.lang3;
    requires java.net.http;
    requires org.apache.logging.log4j;
    
    opens com.tech.newbie.m3u8downloader.gui to javafx.fxml;
    exports com.tech.newbie.m3u8downloader.gui;
    exports com.tech.newbie.m3u8downloader.gui.controller;
    opens com.tech.newbie.m3u8downloader.gui.controller to javafx.fxml;
}
