package com.parceltracker;

import com.parceltracker.controller.AdminController;
import com.parceltracker.controller.AdminLoginDialog;
import com.parceltracker.service.ApiService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class DispatcherApp extends Application {
    private AdminController controller;
    private ApiService api;

    @Override
    public void start(Stage stage) {
        String apiUrl = System.getProperty("api.url", "http://localhost:8080");
        String wsUrl = System.getProperty("ws.url", "ws://localhost:8080");
        api = new ApiService(apiUrl);
        var credentials = AdminLoginDialog.showAndWait(stage);
        if (credentials.isEmpty()) {
            Platform.exit();
            return;
        }
        stage.setTitle("Admin & Dispatcher Command Center — Demo");
        stage.setScene(new Scene(new Label("Verifying admin credentials…"), 420, 120));
        stage.show();
        Thread login = new Thread(() -> {
            try {
                api.authenticateAdmin(credentials.get().username(), credentials.get().password());
                Platform.runLater(() -> {
                    if (!stage.isShowing()) return;
                    controller = new AdminController();
                    controller.init(api, wsUrl);
                    stage.setScene(new Scene(controller, 1320, 900));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!stage.isShowing()) return;
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Admin sign-in failed: " + e.getMessage());
                    alert.initOwner(stage);
                    alert.showAndWait();
                    stage.close();
                });
            }
        }, "admin-login");
        login.setDaemon(true);
        login.start();
    }

    @Override
    public void stop() {
        if (controller != null) controller.shutdown();
        if (api != null) api.clearAdminCredentials();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
