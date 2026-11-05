package com.hotelflow.ui;

import com.hotelflow.model.Booking;
import com.hotelflow.model.Payment;
import com.hotelflow.model.PaymentStatus;
import com.hotelflow.service.RazorpayConfig;
import com.hotelflow.service.RazorpayHttpBridge;
import com.hotelflow.service.RazorpayService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URI;
import java.util.function.Consumer;

/**
 * Modern JavaFX dialog integrating Razorpay Checkout, live verification status,
 * in-app webview / browser handoff, and immediate payment receipt generation.
 */
public class RazorpayCheckoutDialog {

    private final RazorpayService razorpayService;
    private final Booking booking;
    private final BigDecimal amountToPay;
    private final Runnable onSuccessCallback;

    public RazorpayCheckoutDialog(RazorpayService razorpayService, Booking booking, BigDecimal amountToPay, Runnable onSuccessCallback) {
        this.razorpayService = razorpayService;
        this.booking = booking;
        this.amountToPay = amountToPay;
        this.onSuccessCallback = onSuccessCallback;
    }

    public void show() {
        if (!RazorpayConfig.isConfigured()) {
            showCredentialConfigDialog();
            if (!RazorpayConfig.isConfigured()) {
                return;
            }
        }

        try {
            // 1. Create order on Razorpay backend
            RazorpayService.RazorpayOrderDto orderDto = razorpayService.createOrder(booking, amountToPay);

            // 2. Start / obtain local HTTP bridge
            RazorpayHttpBridge bridge = RazorpayHttpBridge.getInstance(razorpayService);
            String checkoutUrl = bridge.getCheckoutUrl(orderDto.orderId());

            // 3. Build Dialog UI
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Razorpay Secure Payment - Booking #" + booking.getId());
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

            VBox root = new VBox(16);
            root.setPadding(new Insets(24));
            root.setPrefWidth(540);

            // Header Banner
            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setStyle("-fx-background-color: #2563eb; -fx-padding: 16px; -fx-background-radius: 8px;");

            Label lblLogo = new Label("💳");
            lblLogo.setStyle("-fx-font-size: 28px;");
            VBox titleBox = new VBox(2);
            Label lblTitle = new Label("Razorpay Payment Gateway");
            lblTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");
            Label lblSubtitle = new Label("Test Mode • UPI, Cards, NetBanking, Wallets");
            lblSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #bfdbfe;");
            titleBox.getChildren().addAll(lblTitle, lblSubtitle);
            header.getChildren().addAll(lblLogo, titleBox);

            // Transaction Details Grid
            GridPane detailsGrid = new GridPane();
            detailsGrid.setHgap(12);
            detailsGrid.setVgap(8);
            detailsGrid.setStyle("-fx-background-color: #f8fafc; -fx-padding: 14px; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px;");

            detailsGrid.add(new Label("Booking Reference:"), 0, 0);
            detailsGrid.add(new Label("#" + booking.getId() + " (Room #" + booking.getRoomNumber() + ")"), 1, 0);

            detailsGrid.add(new Label("Guest:"), 0, 1);
            detailsGrid.add(new Label(booking.getGuestName()), 1, 1);

            detailsGrid.add(new Label("Razorpay Order ID:"), 0, 2);
            Label lblOrderId = new Label(orderDto.orderId());
            lblOrderId.setStyle("-fx-font-family: monospace; -fx-font-weight: bold;");
            detailsGrid.add(lblOrderId, 1, 2);

            detailsGrid.add(new Label("Payment Amount:"), 0, 3);
            Label lblAmt = new Label(UIUtils.formatCurrency(amountToPay));
            lblAmt.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");
            detailsGrid.add(lblAmt, 1, 3);

            // Live Status Indicator Box
            HBox statusBox = new HBox(10);
            statusBox.setAlignment(Pos.CENTER_LEFT);
            statusBox.setStyle("-fx-background-color: #eff6ff; -fx-padding: 12px 16px; -fx-background-radius: 6px; -fx-border-color: #bfdbfe; -fx-border-radius: 6px;");

            ProgressIndicator progress = new ProgressIndicator();
            progress.setPrefSize(20, 20);

            Label lblStatus = new Label("Awaiting customer payment completion...");
            lblStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e40af;");
            statusBox.getChildren().addAll(progress, lblStatus);

            // Action Buttons
            HBox buttonBox = new HBox(10);
            buttonBox.setAlignment(Pos.CENTER);

            Button btnOpenBrowser = new Button("🌐 Open Checkout in Browser");
            btnOpenBrowser.getStyleClass().add("btn-primary");
            btnOpenBrowser.setStyle("-fx-padding: 10px 18px; -fx-font-size: 14px; -fx-font-weight: bold;");

            btnOpenBrowser.setOnAction(e -> {
                openBrowser(checkoutUrl);
            });

            Button btnViewReceipt = new Button("📄 View Payment Receipt");
            btnViewReceipt.getStyleClass().add("btn-secondary");
            btnViewReceipt.setStyle("-fx-padding: 10px 18px; -fx-font-size: 14px;");
            btnViewReceipt.setVisible(false);

            buttonBox.getChildren().addAll(btnOpenBrowser, btnViewReceipt);

            // Instructions Box
            VBox instructionsBox = new VBox(4);
            instructionsBox.setStyle("-fx-background-color: #fefce8; -fx-padding: 12px; -fx-background-radius: 6px; -fx-border-color: #fef08a; -fx-border-radius: 6px;");
            Label lblTipsTitle = new Label("💡 Razorpay Test Mode Simulator Guide:");
            lblTipsTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #854d0e; -fx-font-size: 12px;");
            Label lblTip1 = new Label("• UPI: Enter 'success@razorpay' for instant payment success.");
            lblTip1.setStyle("-fx-font-size: 11px; -fx-text-fill: #854d0e;");
            Label lblTip2 = new Label("• Cards: Use card '4111 1111 1111 1111', any future MM/YY, CVV '123', OTP '123456'.");
            lblTip2.setStyle("-fx-font-size: 11px; -fx-text-fill: #854d0e;");
            Label lblTip3 = new Label("• NetBanking: Choose any bank (e.g. SBI, HDFC) and click 'Success'.");
            lblTip3.setStyle("-fx-font-size: 11px; -fx-text-fill: #854d0e;");
            instructionsBox.getChildren().addAll(lblTipsTitle, lblTip1, lblTip2, lblTip3);

            root.getChildren().addAll(header, detailsGrid, statusBox, buttonBox, instructionsBox);
            dialog.getDialogPane().setContent(root);

            // Register backend callback listener
            bridge.registerOrder(orderDto, result -> {
                Platform.runLater(() -> {
                    if (result.success()) {
                        progress.setVisible(false);
                        lblStatus.setText("✅ Payment Verified & Successfully Recorded!");
                        lblStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #059669;");
                        statusBox.setStyle("-fx-background-color: #ecfdf5; -fx-padding: 12px 16px; -fx-background-radius: 6px; -fx-border-color: #a7f3d0; -fx-border-radius: 6px;");

                        btnOpenBrowser.setVisible(false);
                        btnViewReceipt.setVisible(true);
                        btnViewReceipt.setOnAction(ev -> {
                            showReceiptDialog(result.payment(), booking);
                        });

                        if (onSuccessCallback != null) {
                            onSuccessCallback.run();
                        }
                    } else {
                        progress.setVisible(false);
                        lblStatus.setText("❌ " + (result.error() != null ? result.error() : "Payment Failed / Cancelled"));
                        lblStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #dc2626;");
                        statusBox.setStyle("-fx-background-color: #fef2f2; -fx-padding: 12px 16px; -fx-background-radius: 6px; -fx-border-color: #fecaca; -fx-border-radius: 6px;");
                    }
                });
            });

            // Automatically open browser for the user
            openBrowser(checkoutUrl);

            dialog.setOnCloseRequest(e -> {
                bridge.unregisterOrder(orderDto.orderId());
            });

            dialog.showAndWait();

        } catch (Exception e) {
            UIUtils.showError("Razorpay Gateway Error", "Failed to initiate Razorpay payment: " + e.getMessage());
        }
    }

    private void openBrowser(String url) {
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                } else {
                    // Windows fallback
                    Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                }
            } catch (Exception ex) {
                System.err.println("[RazorpayCheckoutDialog] Could not launch browser: " + ex.getMessage());
            }
        }).start();
    }

    private void showReceiptDialog(Payment payment, Booking booking) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Payment Receipt - " + payment.getRazorpayPaymentId());

        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.setPrefWidth(550);

        String receiptText = razorpayService.generateReceipt(payment, booking);

        TextArea area = new TextArea(receiptText);
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(18);
        area.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");

        Button btnSave = new Button("💾 Save Receipt Slip");
        btnSave.getStyleClass().add("btn-primary");
        btnSave.setOnAction(e -> {
            try {
                String filename = "HotelFlow_Receipt_" + payment.getRazorpayPaymentId() + ".txt";
                File file = new File(System.getProperty("user.home"), filename);
                try (PrintWriter out = new PrintWriter(file)) {
                    out.print(receiptText);
                }
                UIUtils.showInfo("Receipt Saved", "Receipt exported to:\n" + file.getAbsolutePath());
            } catch (Exception ex) {
                UIUtils.showError("Export Failed", ex.getMessage());
            }
        });

        box.getChildren().addAll(area, btnSave);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showCredentialConfigDialog() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Razorpay API Configuration Required");
        dialog.setHeaderText("Please provide your Razorpay Test API Key ID & Secret");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        Label lblInfo = new Label("""
            Razorpay API keys were not detected in environment variables.
            You can get your free Test API Key from:
            https://dashboard.razorpay.com/#/app/keys
            
            Enter them below to continue testing:
        """);
        lblInfo.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
        grid.add(lblInfo, 0, 0, 2, 1);

        TextField txtKeyId = new TextField();
        txtKeyId.setPromptText("rzp_test_...");
        txtKeyId.setPrefWidth(320);

        PasswordField txtKeySecret = new PasswordField();
        txtKeySecret.setPromptText("Your Razorpay Key Secret");
        txtKeySecret.setPrefWidth(320);

        grid.add(new Label("Razorpay Key ID:"), 0, 1);
        grid.add(txtKeyId, 1, 1);

        grid.add(new Label("Razorpay Key Secret:"), 0, 2);
        grid.add(txtKeySecret, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String id = txtKeyId.getText().trim();
                String sec = txtKeySecret.getText().trim();
                if (id.isEmpty() || sec.isEmpty()) {
                    UIUtils.showError("Validation Error", "Both Key ID and Key Secret are required.");
                    return false;
                }
                RazorpayConfig.setCredentials(id, sec);
                return true;
            }
            return false;
        });

        dialog.showAndWait();
    }
}
