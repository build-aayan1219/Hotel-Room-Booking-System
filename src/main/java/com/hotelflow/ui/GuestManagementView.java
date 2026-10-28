package com.hotelflow.ui;

import com.hotelflow.model.Guest;
import com.hotelflow.service.HotelService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Guest Management View: Add, edit, search, and view guests.
 */
public class GuestManagementView {
    private final HotelService service;
    private final ObservableList<Guest> guestList = FXCollections.observableArrayList();
    private TableView<Guest> guestTable;

    public GuestManagementView(HotelService service) {
        this.service = service;
    }

    public Node getView() {
        VBox root = new VBox(18);
        root.setPadding(new Insets(24));

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("Guest Directory & Profiles");
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        topBar.getChildren().addAll(lblTitle, UIUtils.createSpacer());

        Button btnAdd = new Button("+ Add New Guest");
        btnAdd.getStyleClass().add("btn-primary");
        btnAdd.setOnAction(e -> showGuestDialog(null));

        Button btnRefresh = new Button("↻ Refresh");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> loadGuests(""));

        topBar.getChildren().addAll(btnAdd, btnRefresh);

        // Search Bar
        HBox searchBar = new HBox(12);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.getStyleClass().add("panel-card");
        searchBar.setPadding(new Insets(12, 16, 12, 16));

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search by guest name, phone, or email...");
        searchField.setPrefWidth(320);
        searchField.textProperty().addListener((obs, oldV, newV) -> loadGuests(newV));

        searchBar.getChildren().addAll(new Label("Search:"), searchField);

        // Table
        guestTable = new TableView<>();
        guestTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(guestTable, Priority.ALWAYS);

        TableColumn<Guest, Integer> colId = new TableColumn<>("Guest ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setMaxWidth(80);

        TableColumn<Guest, String> colName = new TableColumn<>("Full Name");
        colName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colName.setMaxWidth(200);

        TableColumn<Guest, String> colPhone = new TableColumn<>("Phone Number");
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colPhone.setMaxWidth(160);

        TableColumn<Guest, String> colEmail = new TableColumn<>("Email Address");
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colEmail.setMaxWidth(220);

        TableColumn<Guest, String> colAddress = new TableColumn<>("City / Address");
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));

        TableColumn<Guest, Void> colActions = new TableColumn<>("Actions");
        colActions.setMaxWidth(180);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("Edit");
            private final Button btnDelete = new Button("Delete");
            private final HBox pane = new HBox(8, btnEdit, btnDelete);

            {
                btnEdit.getStyleClass().add("btn-secondary");
                btnDelete.getStyleClass().add("btn-danger");
                pane.setAlignment(Pos.CENTER);

                btnEdit.setOnAction(e -> {
                    Guest guest = getTableView().getItems().get(getIndex());
                    showGuestDialog(guest);
                });

                btnDelete.setOnAction(e -> {
                    Guest guest = getTableView().getItems().get(getIndex());
                    boolean confirm = UIUtils.showConfirmation(
                            "Delete Guest",
                            "Are you sure you want to delete profile for " + guest.getFullName() + "?\n\nNote: Guests with recorded bookings cannot be deleted."
                    );
                    if (confirm) {
                        try {
                            service.deleteGuest(guest.getId());
                            UIUtils.showInfo("Success", "Guest record removed.");
                            loadGuests("");
                        } catch (Exception ex) {
                            UIUtils.showError("Cannot Delete Guest", ex.getMessage());
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

        guestTable.getColumns().addAll(colId, colName, colPhone, colEmail, colAddress, colActions);
        guestTable.setItems(guestList);

        root.getChildren().addAll(topBar, searchBar, guestTable);
        loadGuests("");
        return root;
    }

    public void loadGuests(String query) {
        try {
            List<Guest> list = service.searchGuests(query);
            guestList.setAll(list);
        } catch (Exception e) {
            UIUtils.showError("Error Loading Guests", e.getMessage());
        }
    }

    private void showGuestDialog(Guest existing) {
        boolean isEdit = existing != null;
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Guest #" + existing.getId() : "Register New Guest");
        dialog.setHeaderText(isEdit ? "Update guest profile details" : "Enter guest registration information");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField txtName = new TextField();
        txtName.setPromptText("e.g. Aarav Sharma");
        if (isEdit) txtName.setText(existing.getFullName());

        TextField txtPhone = new TextField();
        txtPhone.setPromptText("e.g. 9876543210");
        if (isEdit) txtPhone.setText(existing.getPhone());

        TextField txtEmail = new TextField();
        txtEmail.setPromptText("e.g. guest@example.com");
        if (isEdit) txtEmail.setText(existing.getEmail());

        TextField txtAddress = new TextField();
        txtAddress.setPromptText("e.g. Pune, Maharashtra");
        if (isEdit) txtAddress.setText(existing.getAddress());

        grid.add(new Label("Full Name: *"), 0, 0);
        grid.add(txtName, 1, 0);
        grid.add(new Label("Phone Number:"), 0, 1);
        grid.add(txtPhone, 1, 1);
        grid.add(new Label("Email:"), 0, 2);
        grid.add(txtEmail, 1, 2);
        grid.add(new Label("Address:"), 0, 3);
        grid.add(txtAddress, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == javafx.scene.control.ButtonType.OK) {
                try {
                    String name = txtName.getText().trim();
                    String phone = txtPhone.getText().trim();
                    String email = txtEmail.getText().trim();
                    String addr = txtAddress.getText().trim();

                    if (name.isEmpty()) {
                        UIUtils.showError("Validation Error", "Guest full name is required.");
                        return false;
                    }

                    Guest g = isEdit ? existing : new Guest();
                    g.setFullName(name);
                    g.setPhone(phone);
                    g.setEmail(email);
                    g.setAddress(addr);

                    service.saveGuest(g);
                    UIUtils.showInfo("Saved", "Guest profile saved successfully.");
                    loadGuests("");
                    return true;
                } catch (Exception ex) {
                    UIUtils.showError("Save Failed", ex.getMessage());
                    return false;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }
}
