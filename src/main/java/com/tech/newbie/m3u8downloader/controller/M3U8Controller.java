package com.tech.newbie.m3u8downloader.controller;


import com.tech.newbie.m3u8downloader.common.utils.HttpUtil;
import com.tech.newbie.m3u8downloader.service.strategy.AlertUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.StatusUpdateStrategy;
import com.tech.newbie.m3u8downloader.viewmodel.M3U8ViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Optional;

public class M3U8Controller {
    @FXML
    public TextArea inputArea;
    @FXML
    public TextField fileNameField;
    @FXML
    public Label statusText;
    @FXML
    public ProgressBar progressBar;
    @FXML
    public Label timeLabel;

    private final M3U8ViewModel m3U8ViewModel = new M3U8ViewModel();
    private final StatusUpdateStrategy<String> alert = new AlertUpdateStrategy();
    @FXML
    public void initialize(){
        // Bind UI components to ViewModel Properties
        statusText.textProperty().bindBidirectional(m3U8ViewModel.getStatusText());
        progressBar.progressProperty().bindBidirectional(m3U8ViewModel.getProgressBar());
        timeLabel.textProperty().bindBidirectional(m3U8ViewModel.getTimeLabel());
        inputArea.textProperty().bindBidirectional(m3U8ViewModel.getInputArea());
        fileNameField.textProperty().bindBidirectional(m3U8ViewModel.getFileName());
    }

    @FXML
    public void onSelectPathClick(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        Stage stage = (Stage) inputArea.getScene().getWindow();
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            String path = selectedDir.getAbsolutePath();
            m3U8ViewModel.setPath(path);
            System.out.println("Path is: " + path);
        } else {
            alert.updateStatus("not chosen any directory, please check");
        }

    }

    @FXML
    public void onDownloadButtonClick(ActionEvent actionEvent) {
        String inputUrl = inputArea.getText();
        String path = m3U8ViewModel.getPath();
        String file = fileNameField.getText();

        if (StringUtils.isBlank(path)){
            alert.updateStatus("Please select a downloading path");
            return;
        }

        if (!HttpUtil.isValidUrl(inputUrl)){
            alert.updateStatus("Please enter a valid url");
            return;
        }

        if(StringUtils.isBlank(file)){
            alert.updateStatus("Please enter a file name");
            return;
        }

        m3U8ViewModel.startDownload();
    }

    @FXML
    public void onPlayButtonClick(){
        Optional<MediaPlayer> player = m3U8ViewModel.getMediaPlayer();

        if(player.isEmpty()){
            alert.updateStatus("video not exists, please download first");
            return;
        }

        MediaView mediaView = new MediaView(player.get());

        BorderPane root = new BorderPane(mediaView);
        Scene scene = new Scene(root, 800, 600);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("Video Player");
        stage.show();

        player.get().play();
    }

}



