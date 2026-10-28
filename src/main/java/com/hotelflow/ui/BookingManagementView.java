package com.hotelflow.ui;

import com.hotelflow.model.Booking;
import com.hotelflow.model.BookingStatus;
import com.hotelflow.model.Guest;
import com.hotelflow.model.Payment;
import com.hotelflow.model.PaymentStatus;
import com.hotelflow.model.Room;
import com.hotelflow.model.RoomType;
import com.hotelflow.service.HotelConfig;
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
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Booking Management View: 5-step booking creation flow, date collision validation,
 * status filtering, cancellation, and detailed booking inspect modal.
 */
public class BookingManagementView {
    private final HotelService service;
    private final ObservableList<Booking> bookingList = FXCollections.observableArrayList();
    private FilteredList<Booking> filteredList;
    private TableView<Booking> bookingTable;

    public BookingManagementView(HotelService service) {
        this.service = service;
    }

    public Node getView() {
        VBox root = new VBox(18);
        root.setPadding(new Insets(24));

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("Reservation & Booking Management");
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        topBar.getChildren().addAll(lblTitle, UIUtils.createSpacer());

        Button btnNewBooking = new Button("+ New Reservation");
        btnNewBooking.getStyleClass().add("btn-primary");
        btnNewBooking.setOnAction(e -> showNewBookingWizard());

        Button btnRefresh = new Button("↻ Refresh");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> loadBookings());

        topBar.getChildren().addAll(btnNewBooking, btnRefresh);

        // Filter / Search Toolbar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("panel-card");
        filterBar.setPadding(new Insets(12, 16, 12, 16));

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search booking ID, guest, or room #...");
        searchField.setPrefWidth(280);

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All Statuses", "CONFIRMED", "CHECKED_IN", "CHECKED_OUT", "CANCELLED");
        statusFilter.setValue("All Statuses");

        filterBar.getChildren().addAll(new Label("Filter By:"), searchField, statusFilter);

        // Booking Table
        bookingTable = new TableView<>();
        bookingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(bookingTable, Priority.ALWAYS);

        TableColumn<Booking, Integer> colId = new TableColumn<>("Booking #");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setMaxWidth(80);

        TableColumn<Booking, String> colGuest = new TableColumn<>("Guest Name");
        colGuest.setCellValueFactory(new PropertyValueFactory<>("guestName"));
        colGuest.setMaxWidth(180);

        TableColumn<Booking, Integer> colRoom = new TableColumn<>("Room");
        colRoom.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        colRoom.setMaxWidth(70);

        TableColumn<Booking, String> colDates = new TableColumn<>("Stay Period");
        colDates.setCellValueFactory(b -> {
            Booking bk = b.getValue();
            return new SimpleStringProperty(bk.getCheckInDate() + " to " + bk.getCheckOutDate() + " (" + bk.getNumberOfNights() + "n)");
        });
        colDates.setMaxWidth(220);

        TableColumn<Booking, String> colTotal = new TableColumn<>("Total (₹)");
        colTotal.setCellValueFactory(b -> new SimpleStringProperty(UIUtils.formatCurrency(b.getValue().getTotalAmount())));
        colTotal.setMaxWidth(110);

        TableColumn<Booking, String> colPaid = new TableColumn<>("Paid (₹)");
        colPaid.setCellValueFactory(b -> new SimpleStringProperty(UIUtils.formatCurrency(b.getValue().getPaidAmount())));
        colPaid.setMaxWidth(110);

        TableColumn<Booking, BookingStatus> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setMaxWidth(130);
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BookingStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String cls = switch (item) {
                        case CONFIRMED -> "badge-confirmed";
                        case CHECKED_IN -> "badge-checked-in";
                        case CHECKED_OUT -> "badge-checked-out";
                        case CANCELLED -> "badge-cancelled";
                    };
                    setGraphic(UIUtils.createBadge(item.getDisplayName(), cls));
                }
            }
        });

        TableColumn<Booking, Void> colActions = new TableColumn<>("Actions");
        colActions.setMaxWidth(200);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnView = new Button("Details");
            private final Button btnCancel = new Button("Cancel");
            private final HBox pane = new HBox(6, btnView, btnCancel);

            {
                btnView.getStyleClass().add("btn-secondary");
                btnCancel.getStyleClass().add("btn-danger");
                pane.setAlignment(Pos.CENTER);

                btnView.setOnAction(e -> {
                    Booking b = getTableView().getItems().get(getIndex());
                    showBookingDetails(b);
                });

                btnCancel.setOnAction(e -> {
                    Booking b = getTableView().getItems().get(getIndex());
                    if (b.getStatus() == BookingStatus.CANCELLED) {
                        UIUtils.showInfo("Notice", "Booking is already cancelled.");
                        return;
                    }
                    if (b.getStatus() == BookingStatus.CHECKED_OUT) {
                        UIUtils.showError("Not Allowed", "Cannot cancel a booking that has already completed checkout.");
                        return;
                    }
                    boolean confirm = UIUtils.showConfirmation("Cancel Booking",
                            "Are you sure you want to cancel Booking #" + b.getId() + " for " + b.getGuestName() + "?\nThis will release Room #" + b.getRoomNumber() + ".");
                    if (confirm) {
                        try {
                            service.cancelBooking(b.getId());
                            UIUtils.showInfo("Cancelled", "Booking #" + b.getId() + " cancelled successfully.");
                            loadBookings();
                        } catch (Exception ex) {
                            UIUtils.showError("Error", ex.getMessage());
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        bookingTable.getColumns().addAll(colId, colGuest, colRoom, colDates, colTotal, colPaid, colStatus, colActions);

        filteredList = new FilteredList<>(bookingList, p -> true);
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter(searchField.getText(), statusFilter.getValue()));
        statusFilter.valueProperty().addListener((obs, oldV, newV) -> applyFilter(searchField.getText(), statusFilter.getValue()));
        bookingTable.setItems(filteredList);

        root.getChildren().addAll(topBar, filterBar, bookingTable);
        loadBookings();
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
            if (status != null && !status.equals("All Statuses")) {
                matchesStatus = b.getStatus().name().equalsIgnoreCase(status);
            }

            return matchesSearch && matchesStatus;
        });
    }

    public void loadBookings() {
        try {
            List<Booking> list = service.getAllBookings();
            bookingList.setAll(list);
        } catch (Exception e) {
            UIUtils.showError("Error Loading Bookings", e.getMessage());
        }
    }

    private void showNewBookingWizard() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Create New Reservation");
        dialog.setHeaderText("Follow the 5-step workflow to verify availability and confirm reservation");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(20));

        // 1. Select / Enter Guest
        ComboBox<Guest> cbGuests = new ComboBox<>();
        cbGuests.setPrefWidth(300);
        try {
            cbGuests.setItems(FXCollections.observableArrayList(service.getAllGuests()));
            if (!cbGuests.getItems().isEmpty()) cbGuests.setValue(cbGuests.getItems().get(0));
        } catch (Exception ignored) {}

        // 2. Room Type Filter (Optional)
        ComboBox<String> cbRoomType = new ComboBox<>();
        cbRoomType.getItems().addAll("Any Room Type", "SINGLE", "DOUBLE", "DELUXE", "SUITE");
        cbRoomType.setValue("Any Room Type");

        // 3. Dates
        DatePicker dpIn = new DatePicker(LocalDate.now());
        DatePicker dpOut = new DatePicker(LocalDate.now().plusDays(1));

        // 4. Available Rooms combo
        ComboBox<Room> cbAvailableRooms = new ComboBox<>();
        cbAvailableRooms.setPrefWidth(300);

        Button btnFindRooms = new Button("Search Available Rooms");
        btnFindRooms.getStyleClass().add("btn-secondary");

        // Price summary labels
        Label lblPriceSummary = new Label("Select dates and room to calculate bill...");
        lblPriceSummary.setStyle("-fx-font-weight: bold; -fx-text-fill: #2563eb; -fx-padding: 8px; -fx-background-color: #eff6ff; -fx-background-radius: 6px;");

        Runnable updateAvailableRooms = () -> {
            LocalDate in = dpIn.getValue();
            LocalDate out = dpOut.getValue();
            if (in == null || out == null || !out.isAfter(in)) {
                UIUtils.showError("Invalid Dates", "Check-out date must be strictly after check-in date.");
                return;
            }

            RoomType type = cbRoomType.getValue().equals("Any Room Type") ? null : RoomType.valueOf(cbRoomType.getValue());
            try {
                List<Room> available = service.getAvailableRooms(type, in, out);
                cbAvailableRooms.setItems(FXCollections.observableArrayList(available));
                if (!available.isEmpty()) {
                    cbAvailableRooms.setValue(available.get(0));
                } else {
                    cbAvailableRooms.setValue(null);
                    lblPriceSummary.setText("⚠️ No rooms available for the selected dates and type.");
                }
            } catch (Exception ex) {
                UIUtils.showError("Error", ex.getMessage());
            }
        };

        btnFindRooms.setOnAction(e -> updateAvailableRooms.run());

        Runnable recalculatePricing = () -> {
            LocalDate in = dpIn.getValue();
            LocalDate out = dpOut.getValue();
            Room selRoom = cbAvailableRooms.getValue();
            if (in != null && out != null && out.isAfter(in) && selRoom != null) {
                long nights = ChronoUnit.DAYS.between(in, out);
                BigDecimal sub = selRoom.getNightlyRate().multiply(BigDecimal.valueOf(nights));
                BigDecimal tax = sub.multiply(HotelConfig.getTaxRate());
                BigDecimal tot = sub.add(tax);
                lblPriceSummary.setText(String.format("%d Night(s) @ %s/night | Subtotal: %s | GST (12%%): %s | Total: %s",
                        nights, UIUtils.formatCurrency(selRoom.getNightlyRate()),
                        UIUtils.formatCurrency(sub), UIUtils.formatCurrency(tax), UIUtils.formatCurrency(tot)));
            }
        };

        cbAvailableRooms.valueProperty().addListener((obs, oldV, newV) -> recalculatePricing.run());

        // Layout Form
        grid.add(new Label("1. Select Guest:"), 0, 0);
        grid.add(cbGuests, 1, 0);

        grid.add(new Label("2. Room Type:"), 0, 1);
        grid.add(cbRoomType, 1, 1);

        grid.add(new Label("3. Check-In Date:"), 0, 2);
        grid.add(dpIn, 1, 2);

        grid.add(new Label("4. Check-Out Date:"), 0, 3);
        grid.add(dpOut, 1, 3);

        grid.add(btnFindRooms, 1, 4);

        grid.add(new Label("5. Available Room:"), 0, 5);
        grid.add(cbAvailableRooms, 1, 5);

        grid.add(new Label("Calculation:"), 0, 6);
        grid.add(lblPriceSummary, 1, 6);

        // Initial search
        updateAvailableRooms.run();

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == javafx.scene.control.ButtonType.OK) {
                Guest guest = cbGuests.getValue();
                Room room = cbAvailableRooms.getValue();
                LocalDate in = dpIn.getValue();
                LocalDate out = dpOut.getValue();

                if (guest == null) {
                    UIUtils.showError("Validation Error", "Please select a registered guest.");
                    return false;
                }
                if (room == null) {
                    UIUtils.showError("Validation Error", "Please select an available room.");
                    return false;
                }
                if (in == null || out == null || !out.isAfter(in)) {
                    UIUtils.showError("Validation Error", "Check-out date must be strictly after check-in date.");
                    return false;
                }

                try {
                    int bookingId = service.createBooking(guest.getId(), room.getRoomNumber(), in, out);
                    UIUtils.showInfo("Success", "Booking #" + bookingId + " confirmed for " + guest.getFullName() + " in Room #" + room.getRoomNumber() + "!");
                    loadBookings();
                    return true;
                } catch (Exception ex) {
                    UIUtils.showError("Reservation Failed", ex.getMessage());
                    return false;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void showBookingDetails(Booking b) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Booking #" + b.getId() + " - Summary & Bill");

        VBox box = new VBox(12);
        box.setPadding(new Insets(20));
        box.setPrefWidth(420);

        Label lblHeader = new Label("Reservation Details");
        lblHeader.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        grid.add(new Label("Guest Name:"), 0, 0);
        grid.add(new Label(b.getGuestName()), 1, 0);

        grid.add(new Label("Contact Phone:"), 0, 1);
        grid.add(new Label(b.getGuestPhone() != null ? b.getGuestPhone() : "N/A"), 1, 1);

        grid.add(new Label("Assigned Room:"), 0, 2);
        grid.add(new Label("#" + b.getRoomNumber() + " (" + (b.getRoomType() != null ? b.getRoomType().getDisplayName() : "") + ")"), 1, 2);

        grid.add(new Label("Check-In Date:"), 0, 3);
        grid.add(new Label(b.getCheckInDate().toString()), 1, 3);

        grid.add(new Label("Check-Out Date:"), 0, 4);
        grid.add(new Label(b.getCheckOutDate().toString()), 1, 4);

        grid.add(new Label("Number of Nights:"), 0, 5);
        grid.add(new Label(String.valueOf(b.getNumberOfNights())), 1, 5);

        grid.add(new Label("Nightly Rate:"), 0, 6);
        grid.add(new Label(UIUtils.formatCurrency(b.getNightlyRate())), 1, 6);

        grid.add(new Label("Subtotal:"), 0, 7);
        grid.add(new Label(UIUtils.formatCurrency(b.getSubtotal())), 1, 7);

        grid.add(new Label("GST (12%):"), 0, 8);
        grid.add(new Label(UIUtils.formatCurrency(b.getTaxAmount())), 1, 8);

        grid.add(new Label("Total Invoice:"), 0, 9);
        grid.add(new Label(UIUtils.formatCurrency(b.getTotalAmount())), 1, 9);

        grid.add(new Label("Amount Paid:"), 0, 10);
        grid.add(new Label(UIUtils.formatCurrency(b.getPaidAmount())), 1, 10);

        grid.add(new Label("Balance Due:"), 0, 11);
        Label lblBal = new Label(UIUtils.formatCurrency(b.getBalanceAmount()));
        lblBal.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (b.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0 ? "#dc2626;" : "#059669;"));
        grid.add(lblBal, 1, 11);

        grid.add(new Label("Booking Status:"), 0, 12);
        grid.add(new Label(b.getStatus().getDisplayName()), 1, 12);

        box.getChildren().addAll(lblHeader, grid);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.showAndWait();
    }
}
