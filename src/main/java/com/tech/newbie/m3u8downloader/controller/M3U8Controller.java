package com.tech.newbie.m3u8downloader.controller;


import com.tech.newbie.m3u8downloader.viewmodel.M3U8ViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

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

    private M3U8ViewModel viewModel = new M3U8ViewModel();
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
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "not chosen any directory", ButtonType.OK);
            alert.showAndWait();
        }

    }

    @FXML
    public void onDownloadButtonClick(ActionEvent actionEvent) {
        viewModel.startDownload();
    }


}



