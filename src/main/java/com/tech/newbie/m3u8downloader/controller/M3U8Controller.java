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

    private final M3U8ViewModel viewModel = new M3U8ViewModel();
    private final StatusUpdateStrategy<String> alert = new AlertUpdateStrategy();
    @FXML
    public void initialize(){
        // Bind UI components to ViewModel Properties
        statusText.textProperty().bindBidirectional(viewModel.getStatusText());
        progressBar.progressProperty().bindBidirectional(viewModel.getProgressBar());
        timeLabel.textProperty().bindBidirectional(viewModel.getTimeLabel());
        inputArea.textProperty().bindBidirectional(viewModel.getInputArea());
        fileNameField.textProperty().bindBidirectional(viewModel.getFileName());
    }

    @FXML
    public void onSelectPathClick(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        Stage stage = (Stage) inputArea.getScene().getWindow();
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            String path = selectedDir.getAbsolutePath();
            viewModel.setPath(path);
            System.out.println("Path is: " + path);
        } else {
            alert.updateStatus("not chosen any directory, please check");
        }

    }

    @FXML
    public void onDownloadButtonClick(ActionEvent actionEvent) {
        String inputUrl = inputArea.getText();
        String path = viewModel.getPath();
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

        viewModel.startDownload();
    }

    @FXML
    public void onPlayButtonClick(){
        String path = viewModel.getPath();
        String file = fileNameField.getText();
        File videoFile = new File(path, file + ".mp4");

        if(!videoFile.exists()){
            alert.updateStatus("video not exists, please download first");
            return;
        }

        MediaPlayer player = viewModel.playVideo(videoFile);
        MediaView mediaView = new MediaView(player);

        BorderPane root = new BorderPane(mediaView);
        Scene scene = new Scene(root, 800, 600);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("Video Player");
        stage.show();

        player.play();
    }

}



