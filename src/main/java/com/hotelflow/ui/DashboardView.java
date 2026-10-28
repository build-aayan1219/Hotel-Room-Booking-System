package com.hotelflow.ui;

import com.hotelflow.model.Booking;
import com.hotelflow.service.HotelService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dashboard View with live KPI metrics, JavaFX Revenue Chart, quick actions, and recent activity.
 */
public class DashboardView {
    private final HotelService service;
    private final Consumer<String> navigationHandler;

    private Label lblTotalRoomsVal;
    private Label lblAvailRoomsVal;
    private Label lblOccupiedVal;
    private Label lblMaintenanceVal;
    private Label lblTodayCheckInsVal;
    private Label lblTodayCheckOutsVal;
    private Label lblTodayRevVal;
    private Label lblOccupancyRateVal;

    private LineChart<String, Number> revenueChart;
    private TableView<Booking> recentBookingsTable;

    public DashboardView(HotelService service, Consumer<String> navigationHandler) {
        this.service = service;
        this.navigationHandler = navigationHandler;
    }

    public Node getView() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(24));

        // 1. Metric Cards Grid
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(16);
        metricsGrid.setVgap(16);

        VBox cTotal = UIUtils.createMetricCard("Total Rooms", "0", "Inventory count", "metric-accent-blue");
        lblTotalRoomsVal = (Label) cTotal.lookup("#val_Total_Rooms");

        VBox cAvail = UIUtils.createMetricCard("Available Rooms", "0", "Ready to book", "metric-accent-green");
        lblAvailRoomsVal = (Label) cAvail.lookup("#val_Available_Rooms");

        VBox cOccupied = UIUtils.createMetricCard("Currently Occupied", "0", "Checked-in guests", "metric-accent-purple");
        lblOccupiedVal = (Label) cOccupied.lookup("#val_Currently_Occupied");

        VBox cMaint = UIUtils.createMetricCard("In Maintenance", "0", "Offline for service", "metric-accent-amber");
        lblMaintenanceVal = (Label) cMaint.lookup("#val_In_Maintenance");

        VBox cCheckIns = UIUtils.createMetricCard("Today's Check-Ins", "0", "Scheduled arrivals", "metric-accent-blue");
        lblTodayCheckInsVal = (Label) cCheckIns.lookup("#val_Today_s_Check_Ins");

        VBox cCheckOuts = UIUtils.createMetricCard("Today's Check-Outs", "0", "Scheduled departures", "metric-accent-purple");
        lblTodayCheckOutsVal = (Label) cCheckOuts.lookup("#val_Today_s_Check_Outs");

        VBox cRev = UIUtils.createMetricCard("Today's Revenue", "₹0.00", "Recorded collections", "metric-accent-green");
        lblTodayRevVal = (Label) cRev.lookup("#val_Today_s_Revenue");

        VBox cOccRate = UIUtils.createMetricCard("Occupancy Rate", "0%", "Based on active rooms", "metric-accent-amber");
        lblOccupancyRateVal = (Label) cOccRate.lookup("#val_Occupancy_Rate");

        metricsGrid.add(cTotal, 0, 0);
        metricsGrid.add(cAvail, 1, 0);
        metricsGrid.add(cOccupied, 2, 0);
        metricsGrid.add(cMaint, 3, 0);

        metricsGrid.add(cCheckIns, 0, 1);
        metricsGrid.add(cCheckOuts, 1, 1);
        metricsGrid.add(cRev, 2, 1);
        metricsGrid.add(cOccRate, 3, 1);

        // 2. Quick Action Bar
        HBox quickActions = new HBox(12);
        quickActions.setAlignment(Pos.CENTER_LEFT);
        quickActions.getStyleClass().add("panel-card");

        Label lblQuick = new Label("Quick Actions:");
        lblQuick.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Button btnNewBooking = new Button("+ New Booking");
        btnNewBooking.getStyleClass().add("btn-primary");
        btnNewBooking.setOnAction(e -> navigationHandler.accept("Bookings"));

        Button btnCheckInGuest = new Button("✓ Check-In Guest");
        btnCheckInGuest.getStyleClass().add("btn-success");
        btnCheckInGuest.setOnAction(e -> navigationHandler.accept("Check-In / Check-Out"));

        Button btnAddRoom = new Button("+ Add Room");
        btnAddRoom.getStyleClass().add("btn-secondary");
        btnAddRoom.setOnAction(e -> navigationHandler.accept("Room Management"));

        Button btnRecordPayment = new Button("💳 Record Payment");
        btnRecordPayment.getStyleClass().add("btn-secondary");
        btnRecordPayment.setOnAction(e -> navigationHandler.accept("Billing / Payments"));

        quickActions.getChildren().addAll(lblQuick, btnNewBooking, btnCheckInGuest, btnAddRoom, btnRecordPayment);

        // 3. Middle Section: Revenue Chart & Recent Bookings Table
        HBox middleSection = new HBox(20);
        middleSection.setPrefHeight(380);

        // Chart Card
        VBox chartCard = new VBox(10);
        chartCard.getStyleClass().add("panel-card");
        HBox.setHgrow(chartCard, Priority.ALWAYS);

        Label lblChartHeader = new Label("Revenue Trend (Recent Days)");
        lblChartHeader.getStyleClass().add("panel-header");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Date");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Revenue (₹)");

        revenueChart = new LineChart<>(xAxis, yAxis);
        revenueChart.setAnimated(false);
        revenueChart.setLegendVisible(false);
        VBox.setVgrow(revenueChart, Priority.ALWAYS);

        chartCard.getChildren().addAll(lblChartHeader, revenueChart);

        // Recent Bookings Card
        VBox tableCard = new VBox(10);
        tableCard.getStyleClass().add("panel-card");
        HBox.setHgrow(tableCard, Priority.ALWAYS);

        Label lblRecentHeader = new Label("Recent Bookings & Arrivals");
        lblRecentHeader.getStyleClass().add("panel-header");

        recentBookingsTable = new TableView<>();
        recentBookingsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Booking, Integer> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setMaxWidth(50);

        TableColumn<Booking, String> colGuest = new TableColumn<>("Guest");
        colGuest.setCellValueFactory(new PropertyValueFactory<>("guestName"));

        TableColumn<Booking, Integer> colRoom = new TableColumn<>("Room");
        colRoom.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        colRoom.setMaxWidth(65);

        TableColumn<Booking, LocalDate> colIn = new TableColumn<>("Check-In");
        colIn.setCellValueFactory(new PropertyValueFactory<>("checkInDate"));

        TableColumn<Booking, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        recentBookingsTable.getColumns().addAll(colId, colGuest, colRoom, colIn, colStatus);
        VBox.setVgrow(recentBookingsTable, Priority.ALWAYS);

        tableCard.getChildren().addAll(lblRecentHeader, recentBookingsTable);

        middleSection.getChildren().addAll(chartCard, tableCard);

        root.getChildren().addAll(metricsGrid, quickActions, middleSection);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        refreshData();
        return scrollPane;
    }

    public void refreshData() {
        try {
            HotelService.DashboardMetrics m = service.getDashboardMetrics();
            if (lblTotalRoomsVal != null) lblTotalRoomsVal.setText(String.valueOf(m.totalRooms()));
            if (lblAvailRoomsVal != null) lblAvailRoomsVal.setText(String.valueOf(m.availableRooms()));
            if (lblOccupiedVal != null) lblOccupiedVal.setText(String.valueOf(m.occupiedRooms()));
            if (lblMaintenanceVal != null) lblMaintenanceVal.setText(String.valueOf(m.maintenanceRooms()));
            if (lblTodayCheckInsVal != null) lblTodayCheckInsVal.setText(String.valueOf(m.todayCheckIns()));
            if (lblTodayCheckOutsVal != null) lblTodayCheckOutsVal.setText(String.valueOf(m.todayCheckOuts()));
            if (lblTodayRevVal != null) lblTodayRevVal.setText(UIUtils.formatCurrency(m.todayRevenue()));
            if (lblOccupancyRateVal != null) lblOccupancyRateVal.setText(String.format("%.1f%%", m.occupancyRate()));

            // Update recent bookings table
            List<Booking> bookings = service.getAllBookings();
            if (recentBookingsTable != null) {
                recentBookingsTable.setItems(FXCollections.observableArrayList(
                        bookings.stream().limit(8).toList()
                ));
            }

            // Update revenue line chart
            if (revenueChart != null) {
                revenueChart.getData().clear();
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Daily Collections");

                LocalDate today = LocalDate.now();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM");

                // Populate last 7 days from today
                for (int i = 6; i >= 0; i--) {
                    LocalDate d = today.minusDays(i);
                    BigDecimal rev = new com.hotelflow.dao.PaymentDao().getRevenueForDate(d);
                    series.getData().add(new XYChart.Data<>(d.format(fmt), rev.doubleValue()));
                }
                revenueChart.getData().add(series);
            }

        } catch (Exception e) {
            System.err.println("Error refreshing dashboard: " + e.getMessage());
        }
    }
}
