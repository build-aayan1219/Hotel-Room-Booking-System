package com.hotelflow;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.User;
import com.hotelflow.service.AuthService;
import com.hotelflow.service.HotelService;
import com.hotelflow.ui.LoginView;
import com.hotelflow.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * HotelFlow Desktop JavaFX Graphical Interface Application.
 * Features a modern card login page, top bar with user profile and logout,
 * and seamless dashboard navigation.
 */
public class GuiApp extends Application {

    private AuthService authService;
    private HotelService hotelService;
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        // 1. Initialize database and schema
        DatabaseManager.initializeDatabase();

        this.authService = new AuthService();
        this.hotelService = new HotelService();

        showLoginScene();

        primaryStage.setTitle("HotelFlow — Hotel Booking & Management Suite");
        primaryStage.setMinWidth(1120);
        primaryStage.setMinHeight(720);
        primaryStage.show();
    }

    public void showLoginScene() {
        LoginView loginView = new LoginView(authService, this::showMainScene);
        Scene scene = new Scene(loginView, 1280, 800);
        applyStylesheet(scene);
        stage.setScene(scene);
    }

    public void showMainScene(User user) {
        MainView mainView = new MainView(hotelService, authService, this::showLoginScene);
        Scene scene = new Scene(mainView, 1280, 800);
        applyStylesheet(scene);
        stage.setScene(scene);
    }

    private void applyStylesheet(Scene scene) {
        URL cssUrl = getClass().getResource("/css/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
