package com.hotelflow.ui;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.User;
import com.hotelflow.service.AuthService;
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
 * 2. Professional Top Navigation Bar with active user profile, database badge, clock, and Logout
 * 3. Dynamic Center Content with lazy caching for smooth navigation
 * 4. Periodic background Timeline to keep dashboard metrics fresh
 */
public class MainView extends BorderPane {
    private final HotelService service;
    private final AuthService authService;
    private final Runnable onLogout;

    private Label lblPageTitle;
    private Label lblSystemClock;
    private StackPane contentArea;

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
        this(service, null, null);
    }

    public MainView(HotelService service, AuthService authService, Runnable onLogout) {
        this.service = service;
        this.authService = authService;
        this.onLogout = onLogout;
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
        Label lblSub = new Label("Management Suite");
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
        topBar.setAlignment(Pos.CENTER_LEFT);

        lblPageTitle = new Label("Dashboard");
        lblPageTitle.getStyleClass().add("page-title");

        // Database Engine Badge
        String dbLabel = DatabaseManager.isMySql() ? "🐬 MySQL Connected" : "📦 Local SQLite Active";
        Label dbBadge = new Label(dbLabel);
        dbBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-padding: 4px 10px; " +
                         "-fx-background-radius: 12px; -fx-font-size: 11.5px; -fx-font-weight: bold;");

        // Active User Profile Pill
        User currentUser = authService != null ? authService.getCurrentUser() : null;
        String userName = currentUser != null ? currentUser.getFullName() : "Administrator";
        String userRole = currentUser != null ? currentUser.getRole().name() : "ADMIN";

        HBox userPill = new HBox(8);
        userPill.setAlignment(Pos.CENTER_LEFT);
        userPill.setStyle("-fx-background-color: #eff6ff; -fx-padding: 4px 12px; -fx-background-radius: 20px;");

        Label lblUserIcon = new Label("👤");
        Label lblUserName = new Label(userName);
        lblUserName.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e40af; -fx-font-size: 12px;");

        Label lblUserRole = new Label(userRole);
        lblUserRole.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: #ffffff; -fx-font-size: 10px; " +
                             "-fx-font-weight: bold; -fx-padding: 2px 6px; -fx-background-radius: 6px;");

        userPill.getChildren().addAll(lblUserIcon, lblUserName, lblUserRole);

        // System Clock
        lblSystemClock = new Label();
        lblSystemClock.setStyle("-fx-font-weight: bold; -fx-text-fill: #64748b; -fx-font-size: 12px;");

        // Logout Button
        Button btnLogout = new Button("Logout");
        btnLogout.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-weight: bold; " +
                           "-fx-background-radius: 8px; -fx-padding: 5px 12px; -fx-cursor: hand;");
        btnLogout.setOnAction(e -> {
            boolean confirm = UIUtils.showConfirmation("Confirm Logout", "Are you sure you want to sign out?");
            if (confirm) {
                if (authService != null) authService.logout();
                if (onLogout != null) onLogout.run();
            }
        });

        topBar.getChildren().addAll(lblPageTitle, dbBadge, UIUtils.createSpacer(), lblSystemClock, userPill, btnLogout);
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
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy  HH:mm:ss");

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
