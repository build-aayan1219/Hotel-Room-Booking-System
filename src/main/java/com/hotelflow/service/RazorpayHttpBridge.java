package com.hotelflow.service;

import com.hotelflow.model.Payment;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Embedded HTTP Bridge that serves the Razorpay Checkout client-side script
 * and processes backend webhook/callback requests for server-side verification.
 */
public class RazorpayHttpBridge {

    private static RazorpayHttpBridge instance;
    private HttpServer server;
    private int port = 18080;
    private final RazorpayService razorpayService;

    // Active order listeners: orderId -> Callback
    private final Map<String, Consumer<PaymentResult>> listeners = new ConcurrentHashMap<>();
    private final Map<String, RazorpayService.RazorpayOrderDto> activeOrders = new ConcurrentHashMap<>();

    public record PaymentResult(boolean success, Payment payment, String error, String receipt) {}

    private RazorpayHttpBridge(RazorpayService razorpayService) {
        this.razorpayService = razorpayService;
        startServer();
    }

    public static synchronized RazorpayHttpBridge getInstance(RazorpayService service) {
        if (instance == null) {
            instance = new RazorpayHttpBridge(service);
        }
        return instance;
    }

    private void startServer() {
        int attempts = 0;
        while (attempts < 10) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                server.createContext("/checkout", new CheckoutHandler());
                server.createContext("/callback", new CallbackHandler());
                server.createContext("/cancel", new CancelHandler());
                server.setExecutor(Executors.newCachedThreadPool());
                server.start();
                System.out.println("[RazorpayHttpBridge] Local checkout bridge listening at http://127.0.0.1:" + port);
                return;
            } catch (IOException e) {
                port++;
                attempts++;
            }
        }
        System.err.println("[RazorpayHttpBridge] Warning: Could not bind local HTTP server on ports 18080-18089.");
    }

    public int getPort() {
        return port;
    }

    public String getCheckoutUrl(String orderId) {
        return "http://127.0.0.1:" + port + "/checkout?order_id=" + orderId;
    }

    public void registerOrder(RazorpayService.RazorpayOrderDto orderDto, Consumer<PaymentResult> callback) {
        if (orderDto != null && orderDto.orderId() != null) {
            activeOrders.put(orderDto.orderId(), orderDto);
            if (callback != null) {
                listeners.put(orderDto.orderId(), callback);
            }
        }
    }

    public void unregisterOrder(String orderId) {
        if (orderId != null) {
            listeners.remove(orderId);
            activeOrders.remove(orderId);
        }
    }

    // ==================== HTTP HANDLERS ====================

    private class CheckoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String orderId = null;
            if (query != null && query.contains("order_id=")) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && pair[0].equals("order_id")) {
                        orderId = pair[1];
                        break;
                    }
                }
            }

            RazorpayService.RazorpayOrderDto orderDto = orderId != null ? activeOrders.get(orderId) : null;
            if (orderDto == null) {
                String errorHtml = "<html><body style='font-family:sans-serif;text-align:center;padding:50px;'>"
                        + "<h2>⚠️ Order Not Found or Expired</h2>"
                        + "<p>Order ID: " + (orderId != null ? orderId : "N/A") + "</p>"
                        + "<p>Please return to HotelFlow and initiate checkout again.</p>"
                        + "</body></html>";
                sendResponse(exchange, 404, "text/html; charset=UTF-8", errorHtml);
                return;
            }

            String html = generateCheckoutHtml(orderDto);
            sendResponse(exchange, 200, "text/html; charset=UTF-8", html);
        }
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange.getRequestBody());
            JSONObject json;
            try {
                json = new JSONObject(body);
            } catch (Exception e) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Invalid JSON payload\"}");
                return;
            }

            String orderId = json.optString("razorpay_order_id");
            String paymentId = json.optString("razorpay_payment_id");
            String signature = json.optString("razorpay_signature");

            System.out.println("[RazorpayHttpBridge] Callback received for Order: " + orderId + ", Payment: " + paymentId);

            JSONObject responseJson = new JSONObject();
            try {
                // Server-side cryptographic signature verification & database update
                Payment payment = razorpayService.verifyAndCompletePayment(orderId, paymentId, signature);
                responseJson.put("success", true);
                responseJson.put("payment_id", payment.getId());
                responseJson.put("razorpay_payment_id", payment.getRazorpayPaymentId());
                responseJson.put("message", "Payment verified and recorded successfully.");

                // Notify UI listener
                Consumer<PaymentResult> callback = listeners.get(orderId);
                if (callback != null) {
                    callback.accept(new PaymentResult(true, payment, null, null));
                }

                sendResponse(exchange, 200, "application/json", responseJson.toString());

            } catch (SecurityException se) {
                responseJson.put("success", false);
                responseJson.put("error", se.getMessage());

                Consumer<PaymentResult> callback = listeners.get(orderId);
                if (callback != null) {
                    callback.accept(new PaymentResult(false, null, se.getMessage(), null));
                }

                sendResponse(exchange, 400, "application/json", responseJson.toString());

            } catch (Exception e) {
                responseJson.put("success", false);
                responseJson.put("error", "Payment processing error: " + e.getMessage());

                Consumer<PaymentResult> callback = listeners.get(orderId);
                if (callback != null) {
                    callback.accept(new PaymentResult(false, null, e.getMessage(), null));
                }

                sendResponse(exchange, 500, "application/json", responseJson.toString());
            }
        }
    }

    private class CancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String orderId = null;
            if (query != null && query.contains("order_id=")) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && pair[0].equals("order_id")) {
                        orderId = pair[1];
                        break;
                    }
                }
            }

            if (orderId != null) {
                razorpayService.markPaymentFailed(orderId, "Customer dismissed checkout modal");
                Consumer<PaymentResult> callback = listeners.get(orderId);
                if (callback != null) {
                    callback.accept(new PaymentResult(false, null, "Payment was cancelled or closed by user.", null));
                }
            }

            sendResponse(exchange, 200, "application/json", "{\"status\":\"cancelled\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String contentType, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readRequestBody(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private String generateCheckoutHtml(RazorpayService.RazorpayOrderDto dto) {
        String keyId = dto.keyId();
        String orderId = dto.orderId();
        long amountPaise = dto.amountPaise();
        String guestName = dto.guestName() != null ? dto.guestName() : "Valued Guest";
        String guestPhone = dto.guestPhone() != null ? dto.guestPhone() : "9876543210";
        String guestEmail = "guest@hotelflow.internal";

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>HotelFlow - Razorpay Secure Checkout</title>
            <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    background: linear-gradient(135deg, #0f172a 0%%, #1e293b 100%%);
                    color: #0f172a;
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 20px;
                }
                .card {
                    background: #ffffff;
                    border-radius: 16px;
                    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
                    width: 100%%;
                    max-width: 480px;
                    overflow: hidden;
                }
                .header {
                    background: linear-gradient(135deg, #1e40af 0%%, #2563eb 100%%);
                    padding: 28px 24px;
                    color: white;
                    text-align: center;
                }
                .header h1 { font-size: 22px; font-weight: 700; margin-bottom: 6px; }
                .header p { font-size: 13px; opacity: 0.9; }
                .content { padding: 24px; }
                .bill-box {
                    background: #f8fafc;
                    border: 1px solid #e2e8f0;
                    border-radius: 10px;
                    padding: 16px;
                    margin-bottom: 20px;
                }
                .bill-row {
                    display: flex;
                    justify-content: space-between;
                    margin-bottom: 8px;
                    font-size: 14px;
                    color: #475569;
                }
                .bill-row.total {
                    margin-top: 12px;
                    padding-top: 12px;
                    border-top: 1px dashed #cbd5e1;
                    font-size: 18px;
                    font-weight: 700;
                    color: #0f172a;
                }
                .badge {
                    display: inline-block;
                    background: #dbeafe;
                    color: #1d4ed8;
                    padding: 4px 10px;
                    border-radius: 6px;
                    font-size: 12px;
                    font-weight: 600;
                    margin-bottom: 12px;
                }
                .btn {
                    display: block;
                    width: 100%%;
                    padding: 14px;
                    background: #2563eb;
                    color: white;
                    border: none;
                    border-radius: 8px;
                    font-size: 16px;
                    font-weight: 600;
                    cursor: pointer;
                    transition: background 0.2s;
                }
                .btn:hover { background: #1d4ed8; }
                .test-tips {
                    margin-top: 20px;
                    padding: 14px;
                    background: #fefce8;
                    border: 1px solid #fef08a;
                    border-radius: 8px;
                    font-size: 12px;
                    color: #854d0e;
                    line-height: 1.5;
                }
                .status-container {
                    display: none;
                    text-align: center;
                    padding: 30px 20px;
                }
                .status-icon { font-size: 48px; margin-bottom: 12px; }
                .status-title { font-size: 20px; font-weight: 700; margin-bottom: 8px; }
                .status-msg { font-size: 14px; color: #475569; margin-bottom: 20px; }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="header">
                    <h1>🏨 HotelFlow Luxury Stays</h1>
                    <p>Official Razorpay Payment Gateway</p>
                </div>
                
                <div id="checkout-view" class="content">
                    <span class="badge">🔒 256-Bit SSL Encrypted Checkout</span>
                    
                    <div class="bill-box">
                        <div class="bill-row">
                            <span>Booking ID:</span>
                            <strong>#%d</strong>
                        </div>
                        <div class="bill-row">
                            <span>Reserved Room:</span>
                            <strong>Room #%d</strong>
                        </div>
                        <div class="bill-row">
                            <span>Guest Name:</span>
                            <strong>%s</strong>
                        </div>
                        <div class="bill-row">
                            <span>Razorpay Order ID:</span>
                            <span style="font-family:monospace;font-size:12px;">%s</span>
                        </div>
                        <div class="bill-row total">
                            <span>Amount to Pay:</span>
                            <span style="color:#2563eb;">₹%s</span>
                        </div>
                    </div>

                    <button id="rzp-button" class="btn">💳 Proceed to Pay with Razorpay</button>

                    <div class="test-tips">
                        <strong>💡 Razorpay Test Mode Credentials:</strong><br>
                        • <strong>UPI:</strong> Use <code>success@razorpay</code> for instant success.<br>
                        • <strong>Card:</strong> Use <code>4111 1111 1111 1111</code>, any future MM/YY, CVV <code>123</code>, OTP <code>123456</code>.<br>
                        • <strong>Net Banking:</strong> Choose SBI / HDFC (Test Mode simulator).
                    </div>
                </div>

                <div id="status-view" class="status-container">
                    <div id="status-icon" class="status-icon">⏳</div>
                    <div id="status-title" class="status-title">Verifying Payment...</div>
                    <div id="status-msg" class="status-msg">Please wait while our backend verifies your transaction signature.</div>
                </div>
            </div>

            <script>
                const options = {
                    "key": "%s",
                    "amount": "%d",
                    "currency": "INR",
                    "name": "HotelFlow - Room Booking",
                    "description": "Booking #%d - Room #%d",
                    "image": "https://img.icons8.com/color/96/hotel-star.png",
                    "order_id": "%s",
                    "handler": function (response) {
                        showStatus("⏳", "Verifying Server-Side Signature...", "Contacting HotelFlow backend to verify cryptographic HMAC-SHA256 signature...");
                        fetch('/callback', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(response)
                        })
                        .then(res => res.json())
                        .then(data => {
                            if (data.success) {
                                showStatus("✅", "Payment Verified Successfully!", "Payment ID: " + response.razorpay_payment_id + "<br>Your booking is now confirmed. You can close this window or return to HotelFlow.");
                            } else {
                                showStatus("❌", "Verification Failed", data.error || "Payment signature mismatch.");
                            }
                        })
                        .catch(err => {
                            showStatus("❌", "Network Verification Error", err.message);
                        });
                    },
                    "prefill": {
                        "name": "%s",
                        "email": "%s",
                        "contact": "%s"
                    },
                    "notes": {
                        "booking_id": "%d",
                        "hotel": "HotelFlow"
                    },
                    "theme": {
                        "color": "#2563eb"
                    },
                    "modal": {
                        "ondismiss": function() {
                            fetch('/cancel?order_id=%s', { method: 'POST' });
                        }
                    }
                };

                const rzp = new Razorpay(options);

                rzp.on('payment.failed', function (response) {
                    showStatus("❌", "Payment Failed", response.error.description || "The payment transaction could not be processed.");
                    fetch('/cancel?order_id=%s', { method: 'POST' });
                });

                document.getElementById('rzp-button').onclick = function(e) {
                    rzp.open();
                    e.preventDefault();
                };

                // Auto open checkout after small delay
                window.addEventListener('load', () => {
                    setTimeout(() => { rzp.open(); }, 600);
                });

                function showStatus(icon, title, message) {
                    document.getElementById('checkout-view').style.display = 'none';
                    const sView = document.getElementById('status-view');
                    sView.style.display = 'block';
                    document.getElementById('status-icon').innerHTML = icon;
                    document.getElementById('status-title').innerText = title;
                    document.getElementById('status-msg').innerHTML = message;
                }
            </script>
        </body>
        </html>
        """.formatted(
                dto.bookingId(),
                dto.roomNumber(),
                guestName,
                orderId,
                dto.amountRupees().setScale(2),
                keyId,
                amountPaise,
                dto.bookingId(),
                dto.roomNumber(),
                orderId,
                guestName,
                guestEmail,
                guestPhone,
                dto.bookingId(),
                orderId,
                orderId
        );
    }
}

