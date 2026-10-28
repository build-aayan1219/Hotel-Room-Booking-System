package com.hotelflow.ui;

import com.hotelflow.model.Booking;
import com.hotelflow.model.Payment;
import com.hotelflow.model.PaymentStatus;
import com.hotelflow.service.HotelService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Billing & Payments View: Itemized billing, payment transactions (Cash, UPI, Cards),
 * payment history, status badges, and invoice generator/text-export.
 */
public class BillingView {
    private final HotelService service;
    private final ObservableList<Booking> bookingMasterList = FXCollections.observableArrayList();
    private FilteredList<Booking> filteredList;
    private TableView<Booking> billingTable;

    public BillingView(HotelService service) {
        this.service = service;
    }

    public Node getView() {
        VBox root = new VBox(18);
        root.setPadding(new Insets(24));

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("Billing, Invoices & Payment Gateway Simulation");
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        topBar.getChildren().addAll(lblTitle, UIUtils.createSpacer());

        Button btnRefresh = new Button("↻ Refresh");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> loadBillingData());

        topBar.getChildren().add(btnRefresh);

        // Filter Bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("panel-card");
        filterBar.setPadding(new Insets(12, 16, 12, 16));

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search by guest name, room # or booking ID...");
        searchField.setPrefWidth(300);

        ComboBox<String> payStatusFilter = new ComboBox<>();
        payStatusFilter.getItems().addAll("All Payments", "PAID", "PARTIALLY_PAID", "UNPAID");
        payStatusFilter.setValue("All Payments");

        filterBar.getChildren().addAll(new Label("Filter By:"), searchField, payStatusFilter);

        // Table
        billingTable = new TableView<>();
        billingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(billingTable, Priority.ALWAYS);

        TableColumn<Booking, Integer> colId = new TableColumn<>("Booking #");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setMaxWidth(80);

        TableColumn<Booking, String> colGuest = new TableColumn<>("Guest");
        colGuest.setCellValueFactory(new PropertyValueFactory<>("guestName"));
        colGuest.setMaxWidth(180);

        TableColumn<Booking, Integer> colRoom = new TableColumn<>("Room");
        colRoom.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        colRoom.setMaxWidth(70);

        TableColumn<Booking, String> colTotal = new TableColumn<>("Invoice Total (₹)");
        colTotal.setCellValueFactory(b -> new SimpleStringProperty(UIUtils.formatCurrency(b.getValue().getTotalAmount())));
        colTotal.setMaxWidth(130);

        TableColumn<Booking, String> colPaid = new TableColumn<>("Amount Paid (₹)");
        colPaid.setCellValueFactory(b -> new SimpleStringProperty(UIUtils.formatCurrency(b.getValue().getPaidAmount())));
        colPaid.setMaxWidth(130);

        TableColumn<Booking, String> colBal = new TableColumn<>("Balance Due (₹)");
        colBal.setCellValueFactory(b -> new SimpleStringProperty(UIUtils.formatCurrency(b.getValue().getBalanceAmount())));
        colBal.setMaxWidth(130);

        TableColumn<Booking, PaymentStatus> colStatus = new TableColumn<>("Payment Status");
        colStatus.setCellValueFactory(b -> new javafx.beans.property.SimpleObjectProperty<>(b.getValue().getPaymentStatus()));
        colStatus.setMaxWidth(140);
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(PaymentStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String cls = switch (item) {
                        case PAID -> "badge-paid";
                        case PARTIALLY_PAID -> "badge-partial";
                        case UNPAID -> "badge-unpaid";
                    };
                    setGraphic(UIUtils.createBadge(item.getLabel(), cls));
                }
            }
        });

        TableColumn<Booking, Void> colActions = new TableColumn<>("Actions");
        colActions.setMaxWidth(240);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnPay = new Button("💳 Pay");
            private final Button btnInvoice = new Button("📄 Invoice");
            private final HBox pane = new HBox(6, btnPay, btnInvoice);

            {
                btnPay.getStyleClass().add("btn-success");
                btnInvoice.getStyleClass().add("btn-secondary");
                pane.setAlignment(Pos.CENTER);

                btnPay.setOnAction(e -> {
                    Booking b = getTableView().getItems().get(getIndex());
                    showPaymentDialog(b);
                });

                btnInvoice.setOnAction(e -> {
                    Booking b = getTableView().getItems().get(getIndex());
                    showInvoiceDialog(b);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        billingTable.getColumns().addAll(colId, colGuest, colRoom, colTotal, colPaid, colBal, colStatus, colActions);

        filteredList = new FilteredList<>(bookingMasterList, p -> true);
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter(searchField.getText(), payStatusFilter.getValue()));
        payStatusFilter.valueProperty().addListener((obs, oldV, newV) -> applyFilter(searchField.getText(), payStatusFilter.getValue()));
        billingTable.setItems(filteredList);

        root.getChildren().addAll(topBar, filterBar, billingTable);
        loadBillingData();
        return root;
    }

    private void applyFilter(String query, String status) {
        filteredList.setPredicate(b -> {
            boolean matchesSearch = true;
            if (query != null && !query.trim().isEmpty()) {
                String lower = query.trim().toLowerCase();
                matchesSearch = String.valueOf(b.getId()).contains(lower) ||
                                String.valueOf(b.getRoomNumber()).contains(lower) ||
                                (b.getGuestName() != null && b.getGuestName().toLowerCase().contains(lower));
            }

            boolean matchesStatus = true;
            if (status != null && !status.equals("All Payments")) {
                matchesStatus = b.getPaymentStatus().name().equalsIgnoreCase(status);
            }

            return matchesSearch && matchesStatus;
        });
    }

    public void loadBillingData() {
        try {
            List<Booking> list = service.getAllBookings();
            bookingMasterList.setAll(list);
        } catch (Exception e) {
            UIUtils.showError("Error Loading Billing", e.getMessage());
        }
    }

    private void showPaymentDialog(Booking b) {
        BigDecimal balance = b.getBalanceAmount();
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            UIUtils.showInfo("Payment Settled", "Booking #" + b.getId() + " is already fully paid!");
            return;
        }

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Record Payment - Booking #" + b.getId());
        dialog.setHeaderText("Record guest payment transaction against Booking #" + b.getId() + " (" + b.getGuestName() + ")");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        Label lblTotal = new Label(UIUtils.formatCurrency(b.getTotalAmount()));
        Label lblPaid = new Label(UIUtils.formatCurrency(b.getPaidAmount()));
        Label lblBalance = new Label(UIUtils.formatCurrency(balance));
        lblBalance.setStyle("-fx-font-weight: bold; -fx-text-fill: #dc2626;");

        TextField txtAmount = new TextField(balance.toPlainString());
        ComboBox<String> cbMethod = new ComboBox<>(FXCollections.observableArrayList("UPI", "CASH", "CREDIT_CARD", "DEBIT_CARD", "NET_BANKING"));
        cbMethod.setValue("UPI");

        TextField txtNote = new TextField();
        txtNote.setPromptText("e.g. Transaction ID / GPay Ref");

        grid.add(new Label("Total Invoice Amount:"), 0, 0);
        grid.add(lblTotal, 1, 0);
        grid.add(new Label("Already Paid:"), 0, 1);
        grid.add(lblPaid, 1, 1);
        grid.add(new Label("Outstanding Balance:"), 0, 2);
        grid.add(lblBalance, 1, 2);
        grid.add(new Label("Payment Amount (₹):"), 0, 3);
        grid.add(txtAmount, 1, 3);
        grid.add(new Label("Payment Method:"), 0, 4);
        grid.add(cbMethod, 1, 4);
        grid.add(new Label("Reference Note:"), 0, 5);
        grid.add(txtNote, 1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == javafx.scene.control.ButtonType.OK) {
                try {
                    BigDecimal amt = new BigDecimal(txtAmount.getText().trim());
                    String method = cbMethod.getValue();
                    String note = txtNote.getText().trim();

                    service.recordPayment(b.getId(), amt, method, note);
                    UIUtils.showInfo("Payment Recorded", "Payment of " + UIUtils.formatCurrency(amt) + " successfully recorded!");
                    loadBillingData();
                    return true;
                } catch (NumberFormatException nfe) {
                    UIUtils.showError("Validation Error", "Invalid payment amount entered.");
                    return false;
                } catch (Exception ex) {
                    UIUtils.showError("Transaction Failed", ex.getMessage());
                    return false;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void showInvoiceDialog(Booking b) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Tax Invoice - Booking #" + b.getId());

        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.setPrefWidth(520);

        List<Payment> payments;
        try {
            payments = service.getPaymentsForBooking(b.getId());
        } catch (Exception e) {
            payments = List.of();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("                  HOTELFLOW INVOICE                 \n");
        sb.append("       A Java-Based Hotel Management MVP            \n");
        sb.append("====================================================\n");
        sb.append(String.format("Invoice Date : %s%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"))));
        sb.append(String.format("Booking ID   : #%d%n", b.getId()));
        sb.append(String.format("Guest Name   : %s%n", b.getGuestName()));
        sb.append(String.format("Contact      : %s%n", b.getGuestPhone() != null ? b.getGuestPhone() : "N/A"));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("Assigned Room: Room #%d (%s)%n", b.getRoomNumber(), b.getRoomType()));
        sb.append(String.format("Check-In     : %s%n", b.getCheckInDate()));
        sb.append(String.format("Check-Out    : %s%n", b.getCheckOutDate()));
        sb.append(String.format("Duration     : %d Night(s)%n", b.getNumberOfNights()));
        sb.append(String.format("Tariff Rate  : %s per night%n", UIUtils.formatCurrency(b.getNightlyRate())));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("Subtotal     : %s%n", UIUtils.formatCurrency(b.getSubtotal())));
        sb.append(String.format("GST (12%%)    : %s%n", UIUtils.formatCurrency(b.getTaxAmount())));
        sb.append(String.format("TOTAL AMOUNT : %s%n", UIUtils.formatCurrency(b.getTotalAmount())));
        sb.append("----------------------------------------------------\n");
        sb.append("PAYMENT TRANSACTIONS:\n");
        if (payments.isEmpty()) {
            sb.append("  No payments recorded yet.\n");
        } else {
            for (Payment p : payments) {
                sb.append(String.format("  • %s | %s | %s | %s%n",
                        p.getPaymentDate().format(DateTimeFormatter.ofPattern("dd-MMM HH:mm")),
                        UIUtils.formatCurrency(p.getAmount()),
                        p.getPaymentMethod(),
                        p.getReferenceNote()));
            }
        }
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("Total Paid   : %s%n", UIUtils.formatCurrency(b.getPaidAmount())));
        sb.append(String.format("BALANCE DUE  : %s%n", UIUtils.formatCurrency(b.getBalanceAmount())));
        sb.append(String.format("Status       : %s%n", b.getPaymentStatus()));
        sb.append("====================================================\n");
        sb.append("        Thank you for staying at HotelFlow!        \n");

        TextArea area = new TextArea(sb.toString());
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(18);
        area.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");

        Button btnExport = new Button("💾 Save Invoice Text File");
        btnExport.getStyleClass().add("btn-primary");
        btnExport.setOnAction(e -> {
            try {
                String filename = "HotelFlow_Invoice_" + b.getId() + ".txt";
                File file = new File(System.getProperty("user.home"), filename);
                try (PrintWriter out = new PrintWriter(file)) {
                    out.print(sb);
                }
                UIUtils.showInfo("Invoice Saved", "Invoice exported to:\n" + file.getAbsolutePath());
            } catch (Exception ex) {
                UIUtils.showError("Export Failed", ex.getMessage());
            }
        });

        box.getChildren().addAll(area, btnExport);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.showAndWait();
    }
}
