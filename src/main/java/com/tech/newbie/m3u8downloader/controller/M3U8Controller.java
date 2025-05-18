package com.tech.newbie.m3u8downloader.controller;


import com.tech.newbie.m3u8downloader.common.utils.HttpUtil;
import com.tech.newbie.m3u8downloader.service.strategy.AlertUpdateStrategy;
import com.tech.newbie.m3u8downloader.service.strategy.StatusUpdateStrategy;
import com.tech.newbie.m3u8downloader.viewmodel.M3U8ViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
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
        try{
            FXMLLoader loader = new FXMLLoader(getClass().getResource("PlayerView.fxml"));
            Parent root = loader.load();

            PlayerController controller = loader.getController();

            // construct video file
            Optional<File> videoFileOpt = m3U8ViewModel.getVideoFile();

            if(videoFileOpt.isEmpty()){
                alert.updateStatus("video not exists, please download first");
                return;
            }

            // call player
            controller.initializePlayer(videoFileOpt.get().toURI().toString());

            // build up a new scene to play the video
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Video Player");
            stage.show();


        } catch (IOException e) {
            e.printStackTrace();
            alert.updateStatus("無法啟動播放器: "+ e.getMessage());
        }

    }

}



