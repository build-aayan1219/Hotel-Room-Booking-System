package com.hotelflow.ui;

import com.hotelflow.model.Booking;
import com.hotelflow.model.BookingStatus;
import com.hotelflow.model.Room;
import com.hotelflow.service.HotelService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
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
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports & Analytics View: Charts for Revenue, Booking & Cancellation statistics,
 * Occupancy distribution, Outstanding balances, and Room performance table.
 */
public class ReportsView {
    private final HotelService service;

    public ReportsView(HotelService service) {
        this.service = service;
    }

    public Node getView() {
        VBox root = new VBox(22);
        root.setPadding(new Insets(24));

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("HotelFlow Executive Reports & Performance Analytics");
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        topBar.getChildren().addAll(lblTitle, UIUtils.createSpacer());

        Button btnRefresh = new Button("↻ Refresh Reports");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> populateCharts(root));
        topBar.getChildren().add(btnRefresh);

        root.getChildren().add(topBar);

        populateCharts(root);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }

    private void populateCharts(VBox root) {
        // Clear any previous chart rows except topBar
        if (root.getChildren().size() > 1) {
            root.getChildren().remove(1, root.getChildren().size());
        }

        try {
            List<Booking> bookings = service.getAllBookings();
            List<Room> rooms = service.getAllRooms();

            // 1. KPI Cards Row
            GridPane kpiGrid = new GridPane();
            kpiGrid.setHgap(16);
            kpiGrid.setVgap(16);

            BigDecimal totalRevenue = service.getTotalRevenue();
            BigDecimal totalInvoiced = bookings.stream()
                    .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                    .map(Booking::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalOutstanding = bookings.stream()
                    .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                    .map(Booking::getBalanceAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long totalBookingsCount = bookings.size();
            long cancelledCount = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();
            double cancelRate = totalBookingsCount > 0 ? ((double) cancelledCount / totalBookingsCount) * 100.0 : 0.0;

            VBox cRev = UIUtils.createMetricCard("Collected Revenue", UIUtils.formatCurrency(totalRevenue), "Actual cash in hand", "metric-accent-green");
            VBox cInv = UIUtils.createMetricCard("Total Invoiced", UIUtils.formatCurrency(totalInvoiced), "All active reservations", "metric-accent-blue");
            VBox cBal = UIUtils.createMetricCard("Outstanding Balance", UIUtils.formatCurrency(totalOutstanding), "Receivables from guests", "metric-accent-amber");
            VBox cCan = UIUtils.createMetricCard("Cancellation Rate", String.format("%.1f%%", cancelRate), cancelledCount + " cancelled of " + totalBookingsCount, "metric-accent-purple");

            kpiGrid.add(cRev, 0, 0);
            kpiGrid.add(cInv, 1, 0);
            kpiGrid.add(cBal, 2, 0);
            kpiGrid.add(cCan, 3, 0);

            root.getChildren().add(kpiGrid);

            // 2. Charts Row: Revenue Bar Chart & Booking Status Pie Chart
            HBox chartsRow = new HBox(20);
            chartsRow.setPrefHeight(340);

            // Bar Chart: Daily revenue for the last 7 days
            VBox barCard = new VBox(10);
            barCard.getStyleClass().add("panel-card");
            HBox.setHgrow(barCard, Priority.ALWAYS);

            Label lblBarTitle = new Label("Daily Revenue Collections (Last 7 Days)");
            lblBarTitle.getStyleClass().add("panel-header");

            CategoryAxis bX = new CategoryAxis();
            bX.setLabel("Day");
            NumberAxis bY = new NumberAxis();
            bY.setLabel("Amount (₹)");

            BarChart<String, Number> barChart = new BarChart<>(bX, bY);
            barChart.setAnimated(false);
            barChart.setLegendVisible(false);
            VBox.setVgrow(barChart, Priority.ALWAYS);

            XYChart.Series<String, Number> barSeries = new XYChart.Series<>();
            LocalDate today = LocalDate.now();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MMM");
            for (int i = 6; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                BigDecimal rev = new com.hotelflow.dao.PaymentDao().getRevenueForDate(d);
                barSeries.getData().add(new XYChart.Data<>(d.format(fmt), rev.doubleValue()));
            }
            barChart.getData().add(barSeries);
            barCard.getChildren().addAll(lblBarTitle, barChart);

            // Pie Chart: Booking Status Breakdown
            VBox pieCard = new VBox(10);
            pieCard.getStyleClass().add("panel-card");
            HBox.setHgrow(pieCard, Priority.ALWAYS);

            Label lblPieTitle = new Label("Reservation Status Distribution");
            lblPieTitle.getStyleClass().add("panel-header");

            long confirmed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
            long checkedIn = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CHECKED_IN).count();
            long checkedOut = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CHECKED_OUT).count();

            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
                    new PieChart.Data("Confirmed (" + confirmed + ")", confirmed),
                    new PieChart.Data("In-House Occupied (" + checkedIn + ")", checkedIn),
                    new PieChart.Data("Completed Checkout (" + checkedOut + ")", checkedOut),
                    new PieChart.Data("Cancelled (" + cancelledCount + ")", cancelledCount)
            );

            PieChart pieChart = new PieChart(pieData);
            pieChart.setAnimated(false);
            VBox.setVgrow(pieChart, Priority.ALWAYS);
            pieCard.getChildren().addAll(lblPieTitle, pieChart);

            chartsRow.getChildren().addAll(barCard, pieCard);
            root.getChildren().add(chartsRow);

            // 3. Room Performance & Utilization Table
            VBox perfCard = new VBox(10);
            perfCard.getStyleClass().add("panel-card");

            Label lblPerf = new Label("Room Inventory Performance (Bookings count & Revenue generated)");
            lblPerf.getStyleClass().add("panel-header");

            TableView<RoomPerf> perfTable = new TableView<>();
            perfTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            perfTable.setPrefHeight(240);

            TableColumn<RoomPerf, Integer> colRNum = new TableColumn<>("Room #");
            colRNum.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
            colRNum.setMaxWidth(80);

            TableColumn<RoomPerf, String> colRType = new TableColumn<>("Type");
            colRType.setCellValueFactory(new PropertyValueFactory<>("roomType"));
            colRType.setMaxWidth(130);

            TableColumn<RoomPerf, Integer> colTotalB = new TableColumn<>("Total Bookings");
            colTotalB.setCellValueFactory(new PropertyValueFactory<>("totalBookings"));
            colTotalB.setMaxWidth(130);

            TableColumn<RoomPerf, Integer> colNights = new TableColumn<>("Total Nights Booked");
            colNights.setCellValueFactory(new PropertyValueFactory<>("totalNights"));
            colNights.setMaxWidth(150);

            TableColumn<RoomPerf, String> colRev = new TableColumn<>("Revenue Contribution");
            colRev.setCellValueFactory(r -> new SimpleStringProperty(UIUtils.formatCurrency(r.getValue().getRevenue())));

            perfTable.getColumns().addAll(colRNum, colRType, colTotalB, colNights, colRev);

            // Compute performance metrics per room
            ObservableList<RoomPerf> perfList = FXCollections.observableArrayList();
            for (Room r : rooms) {
                List<Booking> roomBookings = bookings.stream()
                        .filter(b -> b.getRoomNumber() == r.getRoomNumber() && b.getStatus() != BookingStatus.CANCELLED)
                        .toList();

                int totalB = roomBookings.size();
                int totalN = roomBookings.stream().mapToInt(Booking::getNumberOfNights).sum();
                BigDecimal rev = roomBookings.stream().map(Booking::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

                perfList.add(new RoomPerf(r.getRoomNumber(), r.getType().getDisplayName(), totalB, totalN, rev));
            }

            perfTable.setItems(perfList);
            perfCard.getChildren().addAll(lblPerf, perfTable);
            root.getChildren().add(perfCard);

        } catch (SQLException e) {
            UIUtils.showError("Error Generating Reports", e.getMessage());
        }
    }

    public static class RoomPerf {
        private final int roomNumber;
        private final String roomType;
        private final int totalBookings;
        private final int totalNights;
        private final BigDecimal revenue;

        public RoomPerf(int roomNumber, String roomType, int totalBookings, int totalNights, BigDecimal revenue) {
            this.roomNumber = roomNumber;
            this.roomType = roomType;
            this.totalBookings = totalBookings;
            this.totalNights = totalNights;
            this.revenue = revenue;
        }

        public int getRoomNumber() { return roomNumber; }
        public String getRoomType() { return roomType; }
        public int getTotalBookings() { return totalBookings; }
        public int getTotalNights() { return totalNights; }
        public BigDecimal getRevenue() { return revenue; }
    }
}
