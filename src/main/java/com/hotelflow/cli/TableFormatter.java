package com.hotelflow.cli;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Terminal ASCII formatting helper for printing clean boxes, headers,
 * dividers, and aligned tables directly in the Eclipse Console tab.
 * Pure Standard Java SE (No JavaFX dependency).
 */
public class TableFormatter {

    public static final String LINE_CHAR = "=";
    public static final String SUB_LINE_CHAR = "-";
    public static final int DEFAULT_WIDTH = 84;

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) return "₹0.00";
        return "₹" + String.format("%,.2f", amount);
    }

    public static void printHeader(String title) {
        System.out.println();
        printDivider(LINE_CHAR, DEFAULT_WIDTH);
        printCentered(title, DEFAULT_WIDTH);
        printDivider(LINE_CHAR, DEFAULT_WIDTH);
    }

    public static void printSubHeader(String subtitle) {
        System.out.println();
        printCentered(subtitle, DEFAULT_WIDTH);
        printDivider(SUB_LINE_CHAR, DEFAULT_WIDTH);
    }

    public static void printDivider() {
        printDivider(SUB_LINE_CHAR, DEFAULT_WIDTH);
    }

    public static void printDivider(String ch, int width) {
        System.out.println(ch.repeat(Math.max(1, width)));
    }

    public static void printCentered(String text, int width) {
        if (text == null) text = "";
        int padding = Math.max(0, (width - text.length()) / 2);
        System.out.println(" ".repeat(padding) + text);
    }

    public static void printAlert(String icon, String message) {
        System.out.println("  " + icon + " " + message);
    }

    public static void printSuccess(String message) {
        System.out.println("  [✓] " + message);
    }

    public static void printError(String message) {
        System.out.println("  [✗] ERROR: " + message);
    }

    public static void printWarning(String message) {
        System.out.println("  [!] WARNING: " + message);
    }

    /**
     * Prints an auto-spaced ASCII data table.
     */
    public static void printTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) return;

        int numCols = headers.size();
        int[] colWidths = new int[numCols];

        // Measure header widths
        for (int i = 0; i < numCols; i++) {
            colWidths[i] = Math.max(headers.get(i).length(), 4);
        }

        // Measure cell widths
        if (rows != null) {
            for (List<String> row : rows) {
                for (int i = 0; i < Math.min(row.size(), numCols); i++) {
                    String val = row.get(i) != null ? row.get(i) : "";
                    colWidths[i] = Math.max(colWidths[i], val.length());
                }
            }
        }

        // Build format string
        StringBuilder fmtBuilder = new StringBuilder();
        StringBuilder sepBuilder = new StringBuilder();
        for (int i = 0; i < numCols; i++) {
            fmtBuilder.append(" %-").append(colWidths[i]).append("s ");
            sepBuilder.append("-".repeat(colWidths[i] + 2));
            if (i < numCols - 1) {
                fmtBuilder.append("|");
                sepBuilder.append("+");
            }
        }
        String format = fmtBuilder.toString();
        String separator = sepBuilder.toString();

        System.out.println(separator);
        System.out.printf(format + "%n", (Object[]) headers.toArray(new String[0]));
        System.out.println(separator);

        if (rows == null || rows.isEmpty()) {
            System.out.println("  (No records found)");
        } else {
            for (List<String> row : rows) {
                String[] cells = new String[numCols];
                for (int i = 0; i < numCols; i++) {
                    cells[i] = i < row.size() && row.get(i) != null ? row.get(i) : "";
                }
                System.out.printf(format + "%n", (Object[]) cells);
            }
        }
        System.out.println(separator);
    }
}
