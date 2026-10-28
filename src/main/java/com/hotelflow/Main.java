package com.hotelflow;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.service.HotelService;
import com.hotelflow.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * HotelFlow Application Launcher.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        // 1. Initialize SQLite Database and schema
        DatabaseManager.initializeDatabase();

        // 2. Initialize Core Service & UI Shell
        HotelService service = new HotelService();
        MainView mainView = new MainView(service);

        Scene scene = new Scene(mainView, 1280, 800);

        // Load CSS stylesheet
        URL cssUrl = getClass().getResource("/css/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("Warning: /css/style.css not found in classpath.");
        }

        primaryStage.setTitle("HotelFlow — Java-Based Hotel Booking & Management MVP");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1080);
        primaryStage.setMinHeight(700);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
