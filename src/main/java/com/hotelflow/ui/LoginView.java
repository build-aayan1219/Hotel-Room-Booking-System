package com.hotelflow.ui;

import com.hotelflow.model.User;
import com.hotelflow.service.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Modern JavaFX Login Screen with authentication, error feedback,
 * and seamless transition to MainView.
 */
public class LoginView extends StackPane {

    private final AuthService authService;
    private final Consumer<User> onLoginSuccess;

    private TextField txtUsername;
    private PasswordField txtPassword;
    private Label lblError;
    private Button btnLogin;

    public LoginView(AuthService authService, Consumer<User> onLoginSuccess) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;
        buildUI();
    }

    private void buildUI() {
        this.setStyle("-fx-background-color: #0f172a;"); // Deep navy background

        VBox card = new VBox(18);
        card.setMaxWidth(420);
        card.setMaxHeight(480);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(36, 40, 36, 40));
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 16px; " +
                      "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.35), 25, 0, 0, 8);");

        // Brand Title
        VBox headerBox = new VBox(4);
        headerBox.setAlignment(Pos.CENTER);

        Label lblBrand = new Label("HotelFlow");
        lblBrand.setStyle("-fx-font-size: 28px; -fx-font-weight: 800; -fx-text-fill: #1e293b;");

        Label lblSub = new Label("Hotel Booking & Management Suite");
        lblSub.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");

        headerBox.getChildren().addAll(lblBrand, lblSub);

        // Form Fields
        VBox formBox = new VBox(12);
        formBox.setAlignment(Pos.CENTER_LEFT);

        Label lblUserPrompt = new Label("Username");
        lblUserPrompt.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155; -fx-font-size: 12px;");
        txtUsername = new TextField();
        txtUsername.setPromptText("e.g. admin or staff");
        txtUsername.setPrefHeight(38);
        txtUsername.setStyle("-fx-background-radius: 8px; -fx-border-radius: 8px; -fx-border-color: #cbd5e1;");

        Label lblPassPrompt = new Label("Password");
        lblPassPrompt.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155; -fx-font-size: 12px;");
        txtPassword = new PasswordField();
        txtPassword.setPromptText("Enter your password");
        txtPassword.setPrefHeight(38);
        txtPassword.setStyle("-fx-background-radius: 8px; -fx-border-radius: 8px; -fx-border-color: #cbd5e1;");

        lblError = new Label();
        lblError.setWrapText(true);
        lblError.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px; -fx-font-weight: bold;");
        lblError.setVisible(false);

        formBox.getChildren().addAll(lblUserPrompt, txtUsername, lblPassPrompt, txtPassword, lblError);

        // Submit Button
        btnLogin = new Button("Sign In to Dashboard");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setPrefHeight(40);
        btnLogin.setStyle("-fx-background-color: #2563eb; -fx-text-fill: #ffffff; -fx-font-weight: bold; " +
                          "-fx-font-size: 14px; -fx-background-radius: 8px; -fx-cursor: hand;");
        btnLogin.setOnAction(e -> handleLogin());

        // Trigger login on ENTER in password field
        txtPassword.setOnAction(e -> handleLogin());
        txtUsername.setOnAction(e -> txtPassword.requestFocus());

        // Quick hint card
        VBox hintBox = new VBox(2);
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPadding(new Insets(10, 8, 0, 8));
        Label lblHintTitle = new Label("Default Credentials:");
        lblHintTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
        Label lblHintAdmin = new Label("Administrator: admin  /  admin123");
        lblHintAdmin.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        Label lblHintStaff = new Label("Front Desk: staff  /  staff123");
        lblHintStaff.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        hintBox.getChildren().addAll(lblHintTitle, lblHintAdmin, lblHintStaff);

        card.getChildren().addAll(headerBox, formBox, btnLogin, hintBox);
        this.getChildren().add(card);
    }

    private void handleLogin() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        lblError.setVisible(false);

        try {
            User user = authService.login(username, password);
            onLoginSuccess.accept(user);
        } catch (AuthService.AuthenticationException ae) {
            lblError.setText("⚠️ " + ae.getMessage());
            lblError.setVisible(true);
        } catch (Exception ex) {
            lblError.setText("⚠️ Connection error: " + ex.getMessage());
            lblError.setVisible(true);
        }
    }
}
