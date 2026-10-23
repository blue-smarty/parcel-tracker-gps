package com.parceltracker;

import com.parceltracker.controller.AdminController;
import com.parceltracker.service.ApiService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DispatcherApp extends Application {
    private AdminController controller;

    @Override
    public void start(Stage stage) {
        String apiUrl = System.getProperty("api.url", "http://localhost:8080");
        String wsUrl = System.getProperty("ws.url", "ws://localhost:8080");
        controller = new AdminController();
        controller.init(new ApiService(apiUrl), wsUrl);
        stage.setTitle("Admin & Dispatcher Command Center — Demo");
        stage.setScene(new Scene(controller, 1320, 900));
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
