package com.hotelflow.ui;

import com.hotelflow.service.HotelService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Main application window layout containing:
 * 1. Deep Navy Modern Sidebar
 * 2. Professional Top Navigation Bar with active view indicator and live system clock
 * 3. Dynamic Center Content with lazy caching for smooth navigation
 * 4. Periodic background Timeline to keep dashboard and desk metrics fresh
 */
public class MainView extends BorderPane {
    private final HotelService service;

    private Label lblPageTitle;
    private Label lblSystemClock;
    private StackPane contentArea;

    private final Map<String, Node> viewCache = new HashMap<>();
    private final Map<String, Button> navButtonMap = new HashMap<>();
    private String currentView = "";

    private DashboardView dashboardView;
    private RoomManagementView roomView;
    private BookingManagementView bookingView;
    private GuestManagementView guestView;
    private CheckInOutView checkInOutView;
    private BillingView billingView;
    private ReportsView reportsView;

    public MainView(HotelService service) {
        this.service = service;
        initializeViews();
        buildUI();
        navigateTo("Dashboard");
        startClockAndAutoRefresh();
    }

    private void initializeViews() {
        dashboardView = new DashboardView(service, this::navigateTo);
        roomView = new RoomManagementView(service);
        bookingView = new BookingManagementView(service);
        guestView = new GuestManagementView(service);
        checkInOutView = new CheckInOutView(service);
        billingView = new BillingView(service);
        reportsView = new ReportsView(service);
    }

    private void buildUI() {
        // --- 1. SIDEBAR ---
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");

        // Brand
        VBox brandBox = new VBox(2);
        brandBox.getStyleClass().add("brand-container");
        Label lblBrand = new Label("HotelFlow");
        lblBrand.getStyleClass().add("brand-title");
        Label lblSub = new Label("Management & Dashboard");
        lblSub.getStyleClass().add("brand-subtitle");
        brandBox.getChildren().addAll(lblBrand, lblSub);

        sidebar.getChildren().add(brandBox);

        // Navigation Items
        addNavItem(sidebar, "Dashboard", "📊  Dashboard");
        addNavItem(sidebar, "Room Management", "🚪  Room Inventory");
        addNavItem(sidebar, "Bookings", "📅  Reservations");
        addNavItem(sidebar, "Guests", "👥  Guests Directory");
        addNavItem(sidebar, "Check-In / Check-Out", "🛎️  Front Desk & Stay");
        addNavItem(sidebar, "Billing / Payments", "💳  Billing & Invoices");
        addNavItem(sidebar, "Reports", "📈  Reports & Analytics");

        setLeft(sidebar);

        // --- 2. TOP BAR ---
        HBox topBar = new HBox(16);
        topBar.getStyleClass().add("top-bar");

        lblPageTitle = new Label("Dashboard");
        lblPageTitle.getStyleClass().add("page-title");

        Label badge = new Label("VIT Pune Demo MVP • SQLite Persisted");
        badge.getStyleClass().add("top-bar-badge");

        lblSystemClock = new Label();
        lblSystemClock.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        topBar.getChildren().addAll(lblPageTitle, badge, UIUtils.createSpacer(), lblSystemClock);
        setTop(topBar);

        // --- 3. CENTER CONTENT AREA ---
        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        setCenter(contentArea);
    }

    private void addNavItem(VBox sidebar, String key, String title) {
        Button btn = new Button(title);
        btn.getStyleClass().add("nav-button");
        btn.setOnAction(e -> navigateTo(key));
        navButtonMap.put(key, btn);
        sidebar.getChildren().add(btn);
    }

    public void navigateTo(String page) {
        if (page.equals(currentView)) return;
        currentView = page;

        // Update active class on nav buttons
        navButtonMap.forEach((k, btn) -> {
            btn.getStyleClass().remove("nav-button-active");
            if (k.equals(page)) {
                btn.getStyleClass().add("nav-button-active");
            }
        });

        // Update page header
        lblPageTitle.setText(page);

        // Load node
        Node viewNode = switch (page) {
            case "Dashboard" -> {
                dashboardView.refreshData();
                yield dashboardView.getView();
            }
            case "Room Management" -> {
                roomView.loadRooms();
                yield roomView.getView();
            }
            case "Bookings" -> {
                bookingView.loadBookings();
                yield bookingView.getView();
            }
            case "Guests" -> {
                guestView.loadGuests("");
                yield guestView.getView();
            }
            case "Check-In / Check-Out" -> {
                checkInOutView.loadDeskData();
                yield checkInOutView.getView();
            }
            case "Billing / Payments" -> {
                billingView.loadBillingData();
                yield billingView.getView();
            }
            case "Reports" -> reportsView.getView();
            default -> new Label("Page not found");
        };

        contentArea.getChildren().setAll(viewNode);
    }

    private void startClockAndAutoRefresh() {
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy  HH:mm:ss");

        // 1-second clock ticker
        Timeline clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            lblSystemClock.setText(LocalDateTime.now().format(timeFmt));
        }));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();

        // 30-second silent background refresh for live dashboard
        Timeline refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if ("Dashboard".equals(currentView)) {
                dashboardView.refreshData();
            }
        }));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }
}
