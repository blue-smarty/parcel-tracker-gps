package com.parceltracker;

import com.parceltracker.controller.CourierController;
import com.parceltracker.service.ApiService;
import com.parceltracker.service.BrandingTheme;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class CourierApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/courier.fxml"));
        Parent root = loader.load();
        CourierController controller = loader.getController();
        ApiService api = new ApiService(System.getProperty("api.url", "http://localhost:8080"));
        controller.init(api);
        stage.setTitle("Courier Dashboard");
        stage.setScene(new Scene(root, 1000, 850));
        BrandingTheme.apply(stage, BrandingTheme.load(api));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
