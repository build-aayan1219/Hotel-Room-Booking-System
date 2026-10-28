package com.hotelflow.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

public class UIUtils {

    private static final Locale INDIA_LOCALE = new Locale("en", "IN");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(INDIA_LOCALE);

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) return "₹0.00";
        // Format with INR symbol
        return "₹" + String.format("%,.2f", amount);
    }

    public static VBox createMetricCard(String title, String initialValue, String caption, String accentStyleClass) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("metric-card", accentStyleClass);
        card.setPrefWidth(220);
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("metric-title");

        Label lblVal = new Label(initialValue);
        lblVal.getStyleClass().add("metric-value");
        lblVal.setId("val_" + title.replaceAll("[^a-zA-Z0-9]", "_"));

        Label lblCap = new Label(caption);
        lblCap.getStyleClass().add("metric-caption");

        card.getChildren().addAll(lblTitle, lblVal, lblCap);
        return card;
    }

    public static Label createBadge(String text, String badgeClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", badgeClass);
        return badge;
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static Node createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
