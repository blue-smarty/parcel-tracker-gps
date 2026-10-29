package com.parceltracker;

import com.parceltracker.controller.MainController;
import com.parceltracker.service.BrandingTheme;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ParcelTrackerApp extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        controller.init(System.getProperty("api.url", "http://localhost:8080"),
                System.getProperty("ws.url", "ws://localhost:8080"));
        stage.setTitle("Parcel Tracker GPS");
        stage.setScene(new Scene(root, 1200, 750));
        BrandingTheme.apply(stage, BrandingTheme.load(controller.getApi()));
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) controller.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
