package com.hotelflow.ui;

import com.hotelflow.model.Room;
import com.hotelflow.model.RoomStatus;
import com.hotelflow.model.RoomType;
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
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

/**
 * Room Management View: Room CRUD, live search, type filter, status filter, and safe deletion.
 */
public class RoomManagementView {
    private final HotelService service;
    private final ObservableList<Room> roomMasterList = FXCollections.observableArrayList();
    private FilteredList<Room> filteredList;
    private TableView<Room> roomTable;

    public RoomManagementView(HotelService service) {
        this.service = service;
    }

    public Node getView() {
        VBox root = new VBox(18);
        root.setPadding(new Insets(24));

        // Header and Actions
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("Room Inventory & Rates");
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        topBar.getChildren().addAll(lblTitle, UIUtils.createSpacer());

        Button btnAdd = new Button("+ Add New Room");
        btnAdd.getStyleClass().add("btn-primary");
        btnAdd.setOnAction(e -> showRoomDialog(null));

        Button btnRefresh = new Button("↻ Refresh");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> loadRooms());

        topBar.getChildren().addAll(btnAdd, btnRefresh);

        // Filter / Search Toolbar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("panel-card");
        filterBar.setPadding(new Insets(12, 16, 12, 16));

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search room number or description...");
        searchField.setPrefWidth(260);

        ComboBox<String> typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("All Room Types", "SINGLE", "DOUBLE", "DELUXE", "SUITE");
        typeFilter.setValue("All Room Types");

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All Statuses", "ACTIVE", "MAINTENANCE");
        statusFilter.setValue("All Statuses");

        filterBar.getChildren().addAll(
                new Label("Filter By:"), searchField, typeFilter, statusFilter
        );

        // Table
        roomTable = new TableView<>();
        roomTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(roomTable, Priority.ALWAYS);

        TableColumn<Room, Integer> colNum = new TableColumn<>("Room #");
        colNum.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        colNum.setMaxWidth(90);

        TableColumn<Room, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().getType().getDisplayName()));
        colType.setMaxWidth(130);

        TableColumn<Room, String> colRate = new TableColumn<>("Nightly Rate (INR)");
        colRate.setCellValueFactory(r -> new SimpleStringProperty(UIUtils.formatCurrency(r.getValue().getNightlyRate())));
        colRate.setMaxWidth(150);

        TableColumn<Room, Integer> colFloor = new TableColumn<>("Floor");
        colFloor.setCellValueFactory(new PropertyValueFactory<>("floor"));
        colFloor.setMaxWidth(80);

        TableColumn<Room, String> colDesc = new TableColumn<>("Description & Features");
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));

        TableColumn<Room, RoomStatus> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setMaxWidth(160);
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(RoomStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String cls = item == RoomStatus.ACTIVE ? "badge-active" : "badge-maintenance";
                    setGraphic(UIUtils.createBadge(item.getLabel(), cls));
                }
            }
        });

        TableColumn<Room, Void> colActions = new TableColumn<>("Actions");
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
                    Room room = getTableView().getItems().get(getIndex());
                    showRoomDialog(room);
                });

                btnDelete.setOnAction(e -> {
                    Room room = getTableView().getItems().get(getIndex());
                    boolean confirm = UIUtils.showConfirmation(
                            "Delete Room",
                            "Are you sure you want to delete Room #" + room.getRoomNumber() + "?\n\nNote: If this room has historical bookings, deletion will be blocked to preserve financial audit trail."
                    );
                    if (confirm) {
                        try {
                            service.deleteRoom(room.getRoomNumber());
                            UIUtils.showInfo("Success", "Room #" + room.getRoomNumber() + " removed successfully.");
                            loadRooms();
                        } catch (Exception ex) {
                            UIUtils.showError("Cannot Delete Room", ex.getMessage());
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

        roomTable.getColumns().addAll(colNum, colType, colRate, colFloor, colDesc, colStatus, colActions);

        // Filter binding
        filteredList = new FilteredList<>(roomMasterList, p -> true);
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter(searchField.getText(), typeFilter.getValue(), statusFilter.getValue()));
        typeFilter.valueProperty().addListener((obs, oldV, newV) -> applyFilter(searchField.getText(), typeFilter.getValue(), statusFilter.getValue()));
        statusFilter.valueProperty().addListener((obs, oldV, newV) -> applyFilter(searchField.getText(), typeFilter.getValue(), statusFilter.getValue()));

        roomTable.setItems(filteredList);

        root.getChildren().addAll(topBar, filterBar, roomTable);
        loadRooms();
        return root;
    }

    private void applyFilter(String query, String type, String status) {
        filteredList.setPredicate(room -> {
            boolean matchesSearch = true;
            if (query != null && !query.trim().isEmpty()) {
                String lower = query.trim().toLowerCase();
                matchesSearch = String.valueOf(room.getRoomNumber()).contains(lower) ||
                                (room.getDescription() != null && room.getDescription().toLowerCase().contains(lower));
            }

            boolean matchesType = true;
            if (type != null && !type.equals("All Room Types")) {
                matchesType = room.getType().name().equalsIgnoreCase(type);
            }

            boolean matchesStatus = true;
            if (status != null && !status.equals("All Statuses")) {
                matchesStatus = room.getStatus().name().equalsIgnoreCase(status);
            }

            return matchesSearch && matchesType && matchesStatus;
        });
    }

    public void loadRooms() {
        try {
            List<Room> rooms = service.getAllRooms();
            roomMasterList.setAll(rooms);
        } catch (Exception e) {
            UIUtils.showError("Error Loading Rooms", e.getMessage());
        }
    }

    private void showRoomDialog(Room existing) {
        boolean isEdit = existing != null;
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Room #" + existing.getRoomNumber() : "Add New Room");
        dialog.setHeaderText(isEdit ? "Update room information and operational status" : "Enter room specifications");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField txtNum = new TextField();
        txtNum.setPromptText("e.g. 105");
        if (isEdit) {
            txtNum.setText(String.valueOf(existing.getRoomNumber()));
            txtNum.setDisable(true); // primary key
        }

        ComboBox<RoomType> cbType = new ComboBox<>(FXCollections.observableArrayList(RoomType.values()));
        cbType.setValue(isEdit ? existing.getType() : RoomType.SINGLE);

        TextField txtRate = new TextField();
        txtRate.setPromptText("e.g. 1800.00");
        if (isEdit) txtRate.setText(existing.getNightlyRate().toPlainString());

        TextField txtFloor = new TextField();
        txtFloor.setPromptText("e.g. 1");
        txtFloor.setText(isEdit ? String.valueOf(existing.getFloor()) : "1");

        TextField txtDesc = new TextField();
        txtDesc.setPromptText("e.g. Balcony view, Queen bed, Mini-bar");
        if (isEdit) txtDesc.setText(existing.getDescription());

        ComboBox<RoomStatus> cbStatus = new ComboBox<>(FXCollections.observableArrayList(RoomStatus.values()));
        cbStatus.setValue(isEdit ? existing.getStatus() : RoomStatus.ACTIVE);

        grid.add(new Label("Room Number:"), 0, 0);
        grid.add(txtNum, 1, 0);
        grid.add(new Label("Room Type:"), 0, 1);
        grid.add(cbType, 1, 1);
        grid.add(new Label("Nightly Rate (₹):"), 0, 2);
        grid.add(txtRate, 1, 2);
        grid.add(new Label("Floor:"), 0, 3);
        grid.add(txtFloor, 1, 3);
        grid.add(new Label("Description:"), 0, 4);
        grid.add(txtDesc, 1, 4);
        grid.add(new Label("Status:"), 0, 5);
        grid.add(cbStatus, 1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == javafx.scene.control.ButtonType.OK) {
                try {
                    int num = Integer.parseInt(txtNum.getText().trim());
                    RoomType type = cbType.getValue();
                    BigDecimal rate = new BigDecimal(txtRate.getText().trim());
                    int floor = Integer.parseInt(txtFloor.getText().trim());
                    String desc = txtDesc.getText().trim();
                    RoomStatus status = cbStatus.getValue();

                    Room r = new Room(num, type, rate, floor, desc, status);
                    if (isEdit) {
                        service.updateRoom(r);
                        UIUtils.showInfo("Saved", "Room #" + num + " updated successfully.");
                    } else {
                        service.addRoom(r);
                        UIUtils.showInfo("Saved", "Room #" + num + " created successfully.");
                    }
                    loadRooms();
                    return true;
                } catch (NumberFormatException nfe) {
                    UIUtils.showError("Validation Error", "Please ensure Room Number, Rate, and Floor are valid numbers.");
                    return false;
                } catch (Exception ex) {
                    UIUtils.showError("Operation Failed", ex.getMessage());
                    return false;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }
}
