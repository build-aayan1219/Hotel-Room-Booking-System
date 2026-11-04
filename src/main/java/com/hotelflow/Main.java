package com.hotelflow;

import com.hotelflow.cli.HotelFlowCLI;
import com.hotelflow.database.DatabaseManager;
import com.hotelflow.service.AuthService;
import com.hotelflow.service.HotelService;

import java.util.Arrays;

/**
 * HotelFlow Primary Entry Point.
 * 
 * Running directly in Eclipse (Right-Click -> Run As -> Java Application)
 * starts the interactive Terminal/Console Interface inside Eclipse's Console tab.
 * 
 * To launch the Desktop JavaFX Graphical Interface:
 * 1. Run GuiApp.java in Eclipse, OR
 * 2. Run via terminal: mvn javafx:run, OR
 * 3. Pass '--gui' as a program argument.
 */
public class Main {

    public static void main(String[] args) {
        // 1. Initialize SQLite / MySQL database & schema
        DatabaseManager.initializeDatabase();

        // 2. Check for GUI flag
        boolean launchGui = args != null && Arrays.stream(args)
                .anyMatch(a -> a.equalsIgnoreCase("--gui") || a.equalsIgnoreCase("-g"));

        if (launchGui) {
            GuiApp.main(args);
        } else {
            // Default Eclipse mode: Interactive Terminal UI
            HotelService hotelService = new HotelService();
            AuthService authService = new AuthService();
            HotelFlowCLI cli = new HotelFlowCLI(hotelService, authService);
            cli.start();
        }
    }
}
