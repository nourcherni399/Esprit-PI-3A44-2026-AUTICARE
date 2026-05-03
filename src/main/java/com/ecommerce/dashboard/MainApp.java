package com.ecommerce.dashboard;

import com.ecommerce.dashboard.controller.DashboardController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) {
        DashboardController controller = new DashboardController();
        Scene scene = new Scene(controller.buildView(), 1460, 920);
        String css = getClass().getResource("/com/ecommerce/dashboard/view/styles/dashboard.css") != null
            ? getClass().getResource("/com/ecommerce/dashboard/view/styles/dashboard.css").toExternalForm()
            : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }
        stage.setTitle("E-Commerce Intelligence Dashboard");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
