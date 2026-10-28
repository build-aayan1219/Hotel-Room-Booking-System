package com.hotelflow.ui;

import com.hotelflow.model.Booking;
import com.hotelflow.model.BookingStatus;
import com.hotelflow.service.HotelService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Check-In / Check-Out View: Separate clear operations for checking in arriving guests
 * and checking out departing guests with balance warning and instant room occupancy update.
 */
public class CheckInOutView {
    private final HotelService service;
    private final ObservableList<Booking> checkInList = FXCollections.observableArrayList();
    private final ObservableList<Booking> stayList = FXCollections.observableArrayList();

    private TableView<Booking> checkInTable;
    private TableView<Booking> stayTable;

    public CheckInOutView(HotelService service) {
        this.service = service;
    }

    public Node getView() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(24));

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("Front Desk Operations: Check-In & Check-Out");
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        topBar.getChildren().addAll(lblTitle, UIUtils.createSpacer());

        Button btnRefresh = new Button("↻ Refresh Desk");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> loadDeskData());
        topBar.getChildren().add(btnRefresh);

        // Section 1: Eligible for Check-In (CONFIRMED Bookings)
        VBox checkInBox = new VBox(10);
        checkInBox.getStyleClass().add("panel-card");

        Label lblSection1 = new Label("1. Eligible for Check-In (Confirmed Reservations)");
        lblSection1.getStyleClass().add("panel-header");

        checkInTable = new TableView<>();
        checkInTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        checkInTable.setPrefHeight(230);

        TableColumn<Booking, Integer> c1Id = new TableColumn<>("Booking #");
        c1Id.setCellValueFactory(new PropertyValueFactory<>("id"));
        c1Id.setMaxWidth(80);

        TableColumn<Booking, String> c1Guest = new TableColumn<>("Guest Name");
        c1Guest.setCellValueFactory(new PropertyValueFactory<>("guestName"));

        TableColumn<Booking, Integer> c1Room = new TableColumn<>("Room");
        c1Room.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        c1Room.setMaxWidth(70);

        TableColumn<Booking, LocalDate> c1In = new TableColumn<>("Scheduled In");
        c1In.setCellValueFactory(new PropertyValueFactory<>("checkInDate"));
        c1In.setMaxWidth(130);

        TableColumn<Booking, LocalDate> c1Out = new TableColumn<>("Scheduled Out");
        c1Out.setCellValueFactory(new PropertyValueFactory<>("checkOutDate"));
        c1Out.setMaxWidth(130);

        TableColumn<Booking, String> c1Total = new TableColumn<>("Total (₹)");
        c1Total.setCellValueFactory(b -> new SimpleStringProperty(UIUtils.formatCurrency(b.getValue().getTotalAmount())));
        c1Total.setMaxWidth(110);

        TableColumn<Booking, Void> c1Action = new TableColumn<>("Action");
        c1Action.setMaxWidth(130);
        c1Action.setCellFactory(col -> new TableCell<>() {
            private final Button btnCheckIn = new Button("✓ Check In");
            {
                btnCheckIn.getStyleClass().add("btn-success");
                btnCheckIn.setOnAction(e -> {
                    Booking b = getTableView().getItems().get(getIndex());
                    boolean confirm = UIUtils.showConfirmation("Confirm Check-In",
                            "Check in guest " + b.getGuestName() + " to Room #" + b.getRoomNumber() + "?\nThis will mark the room as OCCUPIED.");
                    if (confirm) {
                        try {
                            service.checkIn(b.getId());
                            UIUtils.showInfo("Checked In", "Guest checked in successfully! Room #" + b.getRoomNumber() + " is now occupied.");
                            loadDeskData();
                        } catch (Exception ex) {
                            UIUtils.showError("Check-In Failed", ex.getMessage());
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnCheckIn);
            }
        });

        checkInTable.getColumns().addAll(c1Id, c1Guest, c1Room, c1In, c1Out, c1Total, c1Action);
        checkInTable.setItems(checkInList);
        checkInBox.getChildren().addAll(lblSection1, checkInTable);

        // Section 2: Currently Checked In Guests (Stays In Progress)
        VBox stayBox = new VBox(10);
        stayBox.getStyleClass().add("panel-card");

        Label lblSection2 = new Label("2. Currently In-House (Checked-In Guests)");
        lblSection2.getStyleClass().add("panel-header");

        stayTable = new TableView<>();
        stayTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        stayTable.setPrefHeight(250);

        TableColumn<Booking, Integer> c2Id = new TableColumn<>("Booking #");
        c2Id.setCellValueFactory(new PropertyValueFactory<>("id"));
        c2Id.setMaxWidth(80);

        TableColumn<Booking, String> c2Guest = new TableColumn<>("Guest Name");
        c2Guest.setCellValueFactory(new PropertyValueFactory<>("guestName"));

        TableColumn<Booking, Integer> c2Room = new TableColumn<>("Room");
        c2Room.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        c2Room.setMaxWidth(70);

        TableColumn<Booking, LocalDate> c2Out = new TableColumn<>("Departure Date");
        c2Out.setCellValueFactory(new PropertyValueFactory<>("checkOutDate"));
        c2Out.setMaxWidth(130);

        TableColumn<Booking, String> c2Total = new TableColumn<>("Total (₹)");
        c2Total.setCellValueFactory(b -> new SimpleStringProperty(UIUtils.formatCurrency(b.getValue().getTotalAmount())));
        c2Total.setMaxWidth(110);

        TableColumn<Booking, String> c2Paid = new TableColumn<>("Paid (₹)");
        c2Paid.setCellValueFactory(b -> new SimpleStringProperty(UIUtils.formatCurrency(b.getValue().getPaidAmount())));
        c2Paid.setMaxWidth(110);

        TableColumn<Booking, String> c2Bal = new TableColumn<>("Balance Due (₹)");
        c2Bal.setCellValueFactory(b -> new SimpleStringProperty(UIUtils.formatCurrency(b.getValue().getBalanceAmount())));
        c2Bal.setMaxWidth(130);

        TableColumn<Booking, Void> c2Action = new TableColumn<>("Action");
        c2Action.setMaxWidth(140);
        c2Action.setCellFactory(col -> new TableCell<>() {
            private final Button btnCheckOut = new Button("Checkout");
            {
                btnCheckOut.getStyleClass().add("btn-primary");
                btnCheckOut.setOnAction(e -> {
                    Booking b = getTableView().getItems().get(getIndex());
                    BigDecimal balance = b.getBalanceAmount();

                    String msg = "Proceed with check-out for Room #" + b.getRoomNumber() + " (" + b.getGuestName() + ")?";
                    if (balance.compareTo(BigDecimal.ZERO) > 0) {
                        msg += "\n\n⚠️ ATTENTION: Guest has an outstanding balance of " + UIUtils.formatCurrency(balance) + "!\nPlease ensure payment is settled in Billing before guest departure.";
                    }

                    boolean confirm = UIUtils.showConfirmation("Guest Checkout", msg);
                    if (confirm) {
                        try {
                            service.checkOut(b.getId());
                            UIUtils.showInfo("Checked Out", "Guest successfully checked out. Room #" + b.getRoomNumber() + " is now released.");
                            loadDeskData();
                        } catch (Exception ex) {
                            UIUtils.showError("Checkout Failed", ex.getMessage());
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnCheckOut);
            }
        });

        stayTable.getColumns().addAll(c2Id, c2Guest, c2Room, c2Out, c2Total, c2Paid, c2Bal, c2Action);
        stayTable.setItems(stayList);
        stayBox.getChildren().addAll(lblSection2, stayTable);

        root.getChildren().addAll(topBar, checkInBox, stayBox);
        loadDeskData();
        return root;
    }

    public void loadDeskData() {
        try {
            List<Booking> all = service.getAllBookings();
            checkInList.setAll(all.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).toList());
            stayList.setAll(all.stream().filter(b -> b.getStatus() == BookingStatus.CHECKED_IN).toList());
        } catch (Exception e) {
            UIUtils.showError("Error Loading Desk", e.getMessage());
        }
    }
}
