package pack_1.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import pack_1.BankSystem;
import pack_1.exception.*;
import pack_1.model.*;
import pack_1.util.ValidationUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * WebServer — High-performance embedded HTTP server for Rajarata Digital Banking System.
 * Connects the web frontend to the Java backend via RESTful JSON APIs.
 * Demonstrates: Controller layer, Session Management, REST API design, Security Headers, and Error Handling.
 */
public class WebServer {

    private static final BankSystem bank = new BankSystem();

    // Session cache: token -> Session (20-minute idle expiration)
    private static final long SESSION_TIMEOUT_MS = 20 * 60 * 1000L;
    private static final class UserSession {
        final User user;
        long lastAccessTime = System.currentTimeMillis();
        UserSession(User user) { this.user = user; }
    }
    private static final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    private static final int MAX_REQUEST_BODY = 64 * 1024; // 64 KB
    private static final Path FRONTEND_DIR = locateFrontendDirectory();

    private static Path locateFrontendDirectory() {
        Path[] candidates = {
                Paths.get("frontend", "public").toAbsolutePath().normalize(),
                Paths.get("src", "pack_1", "frontend", "public").toAbsolutePath().normalize(),
                Paths.get("src", "src (1)", "src", "pack_1", "frontend", "public").toAbsolutePath().normalize(),
                Paths.get("public").toAbsolutePath().normalize()
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return Paths.get("frontend", "public").toAbsolutePath().normalize();
    }

    public static void startServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", WebServer::handleHttpRequest);
        // Use a cached thread pool executor for responsive multi-user handling
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("===============================================================");
        System.out.println("       RAJARATA DIGITAL BANKING SYSTEM — SERVER STARTED        ");
        System.out.println("===============================================================");
        System.out.println(" Website URL : http://localhost:" + port);
        System.out.println(" Health API  : http://localhost:" + port + "/api/health");
        System.out.println(" Frontend Dir: " + FRONTEND_DIR);
        System.out.println(" Database    : data/rajarata_bank.db (SQLite JDBC)");
        System.out.println(" Demo Users  : admin / admin123  |  testuser / Secret123");
        System.out.println("===============================================================");
    }

    public static void main(String[] args) {
        try {
            startServer(8080);
        } catch (IOException e) {
            System.err.println("[FATAL] Could not start WebServer on port 8080: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── HTTP Entry Point ──────────────────────────────────────────────────────

    private static void handleHttpRequest(HttpExchange ex) throws IOException {
        // Apply CORS headers for development flexibility
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && origin.matches("http://(localhost|127\\.0\\.0\\.1)(:\\d+)?")) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ex.getResponseHeaders().set("Vary", "Origin");
        }
        // Standard security response headers
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "SAMEORIGIN");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");

        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }

            if (path.startsWith("/api/")) {
                ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
                handleRestApi(ex, path, method);
            } else if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                serveStaticFile(ex, path);
            } else {
                sendJsonResponse(ex, 405, errorJson("Method Not Allowed", "METHOD_NOT_ALLOWED"));
            }
        } catch (AuthenticationException e) {
            int status = "LOCKED".equals(e.getErrorCode()) ? 429 : 401;
            sendJsonResponse(ex, status, errorJson(e.getMessage(), e.getErrorCode()));
        } catch (AccountNotFoundException e) {
            sendJsonResponse(ex, 404, errorJson(e.getMessage(), e.getErrorCode()));
        } catch (DuplicateRegistrationException e) {
            sendJsonResponse(ex, 409, errorJson(e.getMessage(), e.getErrorCode()));
        } catch (UnauthorizedAccessException e) {
            sendJsonResponse(ex, 403, errorJson(e.getMessage(), e.getErrorCode()));
        } catch (InvalidAmountException e) {
            sendJsonResponse(ex, 400, errorJson(e.getMessage(), e.getErrorCode()));
        } catch (BankingException e) {
            sendJsonResponse(ex, 400, errorJson(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(ex, 500, errorJson("Internal Server Error: " + e.getMessage(), "INTERNAL_ERROR"));
        }
    }

    // ── Static Asset Serving ──────────────────────────────────────────────────

    private static void serveStaticFile(HttpExchange ex, String requestedPath) throws IOException {
        String fileName = requestedPath.equals("/") ? "index.html" : requestedPath.substring(1);
        Path targetFile = FRONTEND_DIR.resolve(fileName).normalize();

        // Prevent path traversal attacks
        if (!targetFile.startsWith(FRONTEND_DIR) || !Files.isRegularFile(targetFile)) {
            // Serve 404 page
            byte[] notFoundHtml = ("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>404 Not Found</title>"
                    + "<link rel='stylesheet' href='/portal.css'></head><body class='auth-body'>"
                    + "<div class='auth-card'><h1>Page Not Found</h1><p class='muted'>The requested page does not exist.</p>"
                    + "<a class='btn btn-primary' href='/'>Back to Home</a></div></body></html>")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(404, notFoundHtml.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(notFoundHtml); }
            return;
        }

        String mime = "application/octet-stream";
        if (fileName.endsWith(".html")) mime = "text/html; charset=UTF-8";
        else if (fileName.endsWith(".css")) mime = "text/css; charset=UTF-8";
        else if (fileName.endsWith(".js")) mime = "application/javascript; charset=UTF-8";
        else if (fileName.endsWith(".png")) mime = "image/png";
        else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) mime = "image/jpeg";
        else if (fileName.endsWith(".svg")) mime = "image/svg+xml";
        else if (fileName.endsWith(".ico")) mime = "image/x-icon";

        byte[] content = Files.readAllBytes(targetFile);
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(200, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(200, content.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(content); }
    }

    // ── REST API Routing & Handling ───────────────────────────────────────────

    private static void handleRestApi(HttpExchange ex, String path, String method) throws Exception {
        String subPath = path.substring(5); // Strips "/api/"
        String[] segments = subPath.split("/");
        String route = segments[0];

        // 1. Health check
        if ("health".equals(route)) {
            requireMethod(method, "GET");
            sendJsonResponse(ex, 200, "{\"success\":true,\"status\":\"running\",\"system\":\"Rajarata Digital Banking System\"}");
            return;
        }

        // 2. Registration
        if ("register".equals(route) || ("auth".equals(route) && segments.length > 1 && "register".equals(segments[1]))) {
            requireMethod(method, "POST");
            Map<String, Object> body = parseJson(readRequestBody(ex));
            Customer c = bank.registerCustomer(
                    str(body, "username"),
                    str(body, "password"),
                    str(body, "email"),
                    str(body, "phone"),
                    str(body, "fullName"),
                    str(body, "address"),
                    str(body, "nationalId")
            );
            sendJsonResponse(ex, 201, "{\"success\":true,\"message\":\"Registration successful! You can now log in.\","
                    + "\"userId\":" + quote(c.getUserId()) + ",\"accountNumber\":" + quote(c.getPrimaryAccount().getAccountNumber()) + "}");
            return;
        }

        // 3. Login
        if ("login".equals(route) || ("auth".equals(route) && segments.length > 1 && "login".equals(segments[1]))) {
            requireMethod(method, "POST");
            Map<String, Object> body = parseJson(readRequestBody(ex));
            String clientIp = ex.getRemoteAddress() != null ? ex.getRemoteAddress().getAddress().getHostAddress() : "127.0.0.1";
            User user = bank.login(str(body, "username"), str(body, "password"), clientIp);

            String token = UUID.randomUUID().toString();
            sessions.put(token, new UserSession(user));

            sendJsonResponse(ex, 200, "{\"success\":true,\"token\":" + quote(token) + ",\"user\":" + formatUserJson(user) + "}");
            return;
        }

        // 4. Utility Bills Info
        if ("bills".equals(route)) {
            requireMethod(method, "GET");
            sendJsonResponse(ex, 200, "{\"success\":true,\"bills\":" + formatBillsJson() + "}");
            return;
        }

        // ── Authentication Check for all remaining endpoints ──
        User currentUser = authenticateUser(ex);
        if (currentUser == null) {
            sendJsonResponse(ex, 401, errorJson("Authentication required. Please log in.", "UNAUTHORIZED"));
            return;
        }

        // 5. Logout
        if ("logout".equals(route) || ("auth".equals(route) && segments.length > 1 && "logout".equals(segments[1]))) {
            sessions.remove(extractBearerToken(ex));
            sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Logged out successfully.\"}");
            return;
        }

        // 6. Current User Me
        if ("me".equals(route) || ("auth".equals(route) && segments.length > 1 && "me".equals(segments[1]))) {
            requireMethod(method, "GET");
            sendJsonResponse(ex, 200, "{\"success\":true,\"user\":" + formatUserJson(currentUser) + "}");
            return;
        }

        // 7. Change Password
        if ("password".equals(route)) {
            requireMethod(method, "PUT");
            Map<String, Object> body = parseJson(readRequestBody(ex));
            bank.changePassword(currentUser, str(body, "currentPassword"), str(body, "newPassword"));
            sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Password changed successfully.\"}");
            return;
        }

        // ── Admin-Only Endpoints ──────────────────────────────────────────────
        if ("admin".equals(route)) {
            if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
                throw new UnauthorizedAccessException("Administrator privileges required.");
            }
            handleAdminEndpoints(ex, segments, method);
            return;
        }

        // ── Customer-Only Endpoints ───────────────────────────────────────────
        if (!(currentUser instanceof Customer customer)) {
            throw new UnauthorizedAccessException("Customer access required for this banking endpoint.");
        }

        // Refresh customer accounts from database
        customer.setAccountsList(bank.getCustomerAccounts(customer.getUserId()));

        switch (route) {
            case "dashboard" -> {
                requireMethod(method, "GET");
                sendJsonResponse(ex, 200, formatDashboardJson(customer));
            }
            case "profile" -> {
                if ("GET".equalsIgnoreCase(method)) {
                    sendJsonResponse(ex, 200, "{\"success\":true,\"profile\":" + formatProfileJson(customer) + "}");
                } else if ("PUT".equalsIgnoreCase(method)) {
                    Map<String, Object> body = parseJson(readRequestBody(ex));
                    bank.updateProfile(customer, str(body, "email"), str(body, "phone"), str(body, "address"));
                    sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Profile updated successfully.\",\"profile\":"
                            + formatProfileJson(customer) + "}");
                } else {
                    sendJsonResponse(ex, 405, errorJson("Method Not Allowed", "METHOD_NOT_ALLOWED"));
                }
            }
            case "accounts" -> {
                if ("GET".equalsIgnoreCase(method)) {
                    if (segments.length == 1) {
                        sendJsonResponse(ex, 200, "{\"success\":true,\"accounts\":" + formatAccountsListJson(customer.getAccounts()) + "}");
                    } else {
                        String accNum = segments[1];
                        Account acc = customer.getAccountByNumber(accNum);
                        if (acc == null) throw new AccountNotFoundException(accNum);
                        sendJsonResponse(ex, 200, "{\"success\":true,\"account\":" + formatAccountJson(acc) + "}");
                    }
                } else if ("POST".equalsIgnoreCase(method)) {
                    // Open account
                    Map<String, Object> body = parseJson(readRequestBody(ex));
                    BigDecimal initial = body.containsKey("amount") ? bigDecimalVal(body, "amount") : BigDecimal.ZERO;
                    int months = body.containsKey("fdMonths") ? (int) numVal(body, "fdMonths") : 12;
                    Account opened = bank.openAccount(customer, str(body, "accountType"), initial, months);
                    sendJsonResponse(ex, 201, "{\"success\":true,\"message\":\"Account opened successfully!\",\"account\":"
                            + formatAccountJson(opened) + "}");
                } else {
                    sendJsonResponse(ex, 405, errorJson("Method Not Allowed", "METHOD_NOT_ALLOWED"));
                }
            }
            case "open-account" -> {
                requireMethod(method, "POST");
                Map<String, Object> body = parseJson(readRequestBody(ex));
                BigDecimal initial = body.containsKey("amount") ? bigDecimalVal(body, "amount") : BigDecimal.ZERO;
                int months = body.containsKey("fdMonths") ? (int) numVal(body, "fdMonths") : 12;
                Account opened = bank.openAccount(customer, str(body, "accountType"), initial, months);
                sendJsonResponse(ex, 201, "{\"success\":true,\"message\":\"Account opened successfully!\",\"account\":"
                        + formatAccountJson(opened) + "}");
            }
            case "deposit" -> {
                requireMethod(method, "POST");
                Map<String, Object> body = parseJson(readRequestBody(ex));
                String accNum = str(body, "accountNumber");
                BigDecimal amt = bigDecimalVal(body, "amount");
                bank.deposit(customer, accNum, amt);
                Account updated = customer.getAccountByNumber(accNum);
                sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Deposit of LKR " + amt + " completed successfully!\","
                        + "\"balance\":" + (updated != null ? formatMoney(updated.getBalance()) : "null") + "}");
            }
            case "withdraw" -> {
                requireMethod(method, "POST");
                Map<String, Object> body = parseJson(readRequestBody(ex));
                String accNum = str(body, "accountNumber");
                BigDecimal amt = bigDecimalVal(body, "amount");
                bank.withdraw(customer, accNum, amt);
                Account updated = customer.getAccountByNumber(accNum);
                sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Withdrawal of LKR " + amt + " completed successfully!\","
                        + "\"balance\":" + (updated != null ? formatMoney(updated.getBalance()) : "null") + "}");
            }
            case "transfer" -> {
                requireMethod(method, "POST");
                Map<String, Object> body = parseJson(readRequestBody(ex));
                String fromAcc = str(body, "accountNumber");
                String toAcc = str(body, "toAccount");
                BigDecimal amt = bigDecimalVal(body, "amount");
                bank.transfer(customer, fromAcc, toAcc, amt);
                Account updated = customer.getAccountByNumber(fromAcc);
                sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Transfer of LKR " + amt + " to " + toAcc + " completed successfully!\","
                        + "\"balance\":" + (updated != null ? formatMoney(updated.getBalance()) : "null") + "}");
            }
            case "transactions" -> {
                requireMethod(method, "GET");
                if (segments.length == 1) {
                    Map<String, String> qp = parseQueryParams(ex);
                    String accFilter = qp.getOrDefault("account", "");
                    String typeFilter = qp.getOrDefault("type", "");
                    String search = qp.getOrDefault("search", "");
                    List<Transaction> list = bank.getTransactionService().getTransactions(customer, accFilter, typeFilter, search, 500);
                    sendJsonResponse(ex, 200, "{\"success\":true,\"count\":" + list.size() + ",\"transactions\":"
                            + formatTransactionListJson(list, 500) + "}");
                } else {
                    Transaction tx = bank.getTransactionService().getTransactionById(segments[1]);
                    // Security check: verify this transaction belongs to one of customer's accounts
                    if (customer.getAccountByNumber(tx.getAccountNumber()) == null) {
                        throw new UnauthorizedAccessException("Unauthorized to view this transaction.");
                    }
                    sendJsonResponse(ex, 200, "{\"success\":true,\"transaction\":" + formatTransactionJson(tx) + "}");
                }
            }
            case "pay-bill" -> {
                requireMethod(method, "POST");
                Map<String, Object> body = parseJson(readRequestBody(ex));
                bank.payBill(customer, str(body, "accountNumber"), str(body, "billType"));
                sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Bill payment completed successfully!\"}");
            }
            case "apply-loan" -> {
                requireMethod(method, "POST");
                Map<String, Object> body = parseJson(readRequestBody(ex));
                bank.applyLoan(customer, str(body, "accountNumber"), bigDecimalVal(body, "amount"));
                sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Loan approved and credited successfully!\"}");
            }
            case "repay-loan" -> {
                requireMethod(method, "POST");
                Map<String, Object> body = parseJson(readRequestBody(ex));
                bank.repayLoan(customer, str(body, "accountNumber"), bigDecimalVal(body, "amount"));
                sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Loan repayment processed successfully!\"}");
            }
            default -> sendJsonResponse(ex, 404, errorJson("API endpoint not found: " + path, "NOT_FOUND"));
        }
    }

    // ── Admin Endpoints ───────────────────────────────────────────────────────

    private static void handleAdminEndpoints(HttpExchange ex, String[] segments, String method) throws Exception {
        if (segments.length < 2) {
            sendJsonResponse(ex, 404, errorJson("Admin sub-route required", "NOT_FOUND"));
            return;
        }
        String adminAction = segments[1];
        switch (adminAction) {
            case "stats" -> {
                requireMethod(method, "GET");
                Map<String, Object> stats = bank.getAdminService().getSystemStatistics();
                StringBuilder json = new StringBuilder("{\"success\":true");
                for (Map.Entry<String, Object> entry : stats.entrySet()) {
                    json.append(",").append(quote(entry.getKey())).append(":");
                    if (entry.getValue() instanceof BigDecimal bd) {
                        json.append(formatMoney(bd));
                    } else if (entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                        json.append(entry.getValue());
                    } else {
                        json.append(quote(String.valueOf(entry.getValue())));
                    }
                }
                json.append("}");
                sendJsonResponse(ex, 200, json.toString());
            }
            case "customers" -> {
                requireMethod(method, "GET");
                List<Customer> customers = bank.getAdminService().getAllCustomers();
                StringBuilder sb = new StringBuilder("{\"success\":true,\"count\":").append(customers.size()).append(",\"customers\":[");
                for (int i = 0; i < customers.size(); i++) {
                    if (i > 0) sb.append(",");
                    Customer c = customers.get(i);
                    sb.append("{\"userId\":").append(quote(c.getUserId()))
                            .append(",\"username\":").append(quote(c.getUsername()))
                            .append(",\"fullName\":").append(quote(c.getFullName()))
                            .append(",\"email\":").append(quote(c.getEmail()))
                            .append(",\"phone\":").append(quote(c.getPhone()))
                            .append(",\"address\":").append(quote(c.getAddress()))
                            .append(",\"nationalId\":").append(quote(c.getNationalId()))
                            .append(",\"accountCount\":").append(c.getAccounts().size())
                            .append(",\"totalBalance\":").append(formatMoney(c.getTotalBalance()))
                            .append(",\"loanActive\":").append(c.isLoanActive())
                            .append(",\"loanBalance\":").append(formatMoney(c.getLoanBalance()))
                            .append("}");
                }
                sb.append("]}");
                sendJsonResponse(ex, 200, sb.toString());
            }
            case "accounts" -> {
                if ("GET".equalsIgnoreCase(method)) {
                    List<Account> accounts = bank.getAdminService().getAllAccounts();
                    sendJsonResponse(ex, 200, "{\"success\":true,\"count\":" + accounts.size() + ",\"accounts\":"
                            + formatAccountsListJson(accounts) + "}");
                } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    // Toggle account status
                    Map<String, Object> body = parseJson(readRequestBody(ex));
                    String accNum = str(body, "accountNumber");
                    boolean active = Boolean.parseBoolean(String.valueOf(body.get("active")));
                    bank.getAdminService().setAccountActiveStatus(accNum, active);
                    sendJsonResponse(ex, 200, "{\"success\":true,\"message\":\"Account status updated successfully.\"}");
                } else {
                    sendJsonResponse(ex, 405, errorJson("Method Not Allowed", "METHOD_NOT_ALLOWED"));
                }
            }
            case "transactions" -> {
                requireMethod(method, "GET");
                List<Transaction> txns = bank.getAdminService().getAllTransactions(200);
                sendJsonResponse(ex, 200, "{\"success\":true,\"count\":" + txns.size() + ",\"transactions\":"
                        + formatTransactionListJson(txns, 200) + "}");
            }
            case "audit-logs" -> {
                requireMethod(method, "GET");
                List<AuditLog> logs = bank.getAdminService().getAuditLogs(100);
                StringBuilder sb = new StringBuilder("{\"success\":true,\"count\":").append(logs.size()).append(",\"logs\":[");
                for (int i = 0; i < logs.size(); i++) {
                    if (i > 0) sb.append(",");
                    AuditLog l = logs.get(i);
                    sb.append("{\"logId\":").append(l.getLogId())
                            .append(",\"userId\":").append(quote(l.getUserId()))
                            .append(",\"action\":").append(quote(l.getAction()))
                            .append(",\"details\":").append(quote(l.getDetails()))
                            .append(",\"ipAddress\":").append(quote(l.getIpAddress()))
                            .append(",\"timestamp\":").append(quote(l.getTimestamp()))
                            .append("}");
                }
                sb.append("]}");
                sendJsonResponse(ex, 200, sb.toString());
            }
            default -> sendJsonResponse(ex, 404, errorJson("Unknown admin action: " + adminAction, "NOT_FOUND"));
        }
    }

    // ── Session & Authentication Helpers ──────────────────────────────────────

    private static String extractBearerToken(HttpExchange ex) {
        String authHeader = ex.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return "";
    }

    private static User authenticateUser(HttpExchange ex) {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> now - s.lastAccessTime > SESSION_TIMEOUT_MS);
        String token = extractBearerToken(ex);
        if (token.isEmpty()) return null;
        UserSession s = sessions.get(token);
        if (s == null) return null;
        s.lastAccessTime = now;
        return s.user;
    }

    private static void requireMethod(String actual, String expected) throws BankingException {
        if (!actual.equalsIgnoreCase(expected)) {
            throw new BankingException("METHOD_NOT_ALLOWED", "Expected HTTP method " + expected + ", received " + actual);
        }
    }

    // ── JSON Formatter Helpers ────────────────────────────────────────────────

    private static String formatMoney(BigDecimal v) {
        if (v == null) return "\"0.00\"";
        return quote(v.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private static String quote(String val) {
        if (val == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : val.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append("\"").toString();
    }

    private static String errorJson(String msg, String code) {
        return "{\"success\":false,\"message\":" + quote(msg) + ",\"code\":" + quote(code) + "}";
    }

    private static String formatUserJson(User u) {
        return "{\"userId\":" + quote(u.getUserId())
                + ",\"username\":" + quote(u.getUsername())
                + ",\"fullName\":" + quote(u instanceof Customer c ? c.getFullName() : u.getUsername())
                + ",\"email\":" + quote(u.getEmail())
                + ",\"phone\":" + quote(u.getPhone())
                + ",\"role\":" + quote(u.getRole()) + "}";
    }

    private static String formatProfileJson(Customer c) {
        String nic = c.getNationalId();
        String maskedNic = nic != null && nic.length() > 3 ? "*".repeat(nic.length() - 3) + nic.substring(nic.length() - 3) : "***";
        return "{\"userId\":" + quote(c.getUserId())
                + ",\"username\":" + quote(c.getUsername())
                + ",\"fullName\":" + quote(c.getFullName())
                + ",\"email\":" + quote(c.getEmail())
                + ",\"phone\":" + quote(c.getPhone())
                + ",\"address\":" + quote(c.getAddress())
                + ",\"nationalId\":" + quote(maskedNic)
                + ",\"accountCount\":" + c.getAccounts().size()
                + ",\"loanActive\":" + c.isLoanActive()
                + ",\"loanBalance\":" + formatMoney(c.getLoanBalance()) + "}";
    }

    private static String formatAccountJson(Account a) {
        return "{\"accountNumber\":" + quote(a.getAccountNumber())
                + ",\"ownerId\":" + quote(a.getOwnerId())
                + ",\"accountType\":" + quote(a.getAccountType())
                + ",\"balance\":" + formatMoney(a.getBalance())
                + ",\"interestRate\":" + a.getInterestRate()
                + ",\"features\":" + quote(a.getAccountFeatures())
                + ",\"dateOpened\":" + quote(a.getDateOpened())
                + ",\"active\":" + a.isActive()
                + ",\"transactionCount\":" + a.getTransactions().size() + "}";
    }

    private static String formatAccountsListJson(List<Account> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(formatAccountJson(list.get(i)));
        }
        return sb.append("]").toString();
    }

    private static String formatTransactionJson(Transaction t) {
        return "{\"transactionId\":" + quote(t.getTransactionId())
                + ",\"type\":" + quote(t.getType())
                + ",\"direction\":" + quote(t.isCredit() ? "CREDIT" : "DEBIT")
                + ",\"amount\":" + formatMoney(t.getAmount())
                + ",\"balanceAfter\":" + formatMoney(t.getBalanceAfter())
                + ",\"description\":" + quote(t.getDescription())
                + ",\"accountNumber\":" + quote(t.getAccountNumber())
                + ",\"timestamp\":" + quote(t.getTimestamp()) + "}";
    }

    private static String formatTransactionListJson(List<Transaction> list, int limit) {
        StringBuilder sb = new StringBuilder("[");
        int max = Math.min(limit, list.size());
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append(",");
            sb.append(formatTransactionJson(list.get(i)));
        }
        return sb.append("]").toString();
    }

    private static String formatDashboardJson(Customer c) {
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalDebits = BigDecimal.ZERO;

        for (Account a : c.getAccounts()) {
            totalBalance = totalBalance.add(a.getBalance());
            for (Transaction t : a.getTransactions()) {
                if (t.isCredit()) totalCredits = totalCredits.add(t.getAmount());
                else totalDebits = totalDebits.add(t.getAmount());
            }
        }

        List<Transaction> allTxns = bank.getTransactionService().getTransactions(c, "", "", "", 10);
        Account primary = c.getPrimaryAccount();

        return "{\"success\":true"
                + ",\"user\":" + formatUserJson(c)
                + ",\"totalBalance\":" + formatMoney(totalBalance)
                + ",\"primaryAccount\":" + (primary != null ? formatAccountJson(primary) : "null")
                + ",\"loanBalance\":" + formatMoney(c.getLoanBalance())
                + ",\"loanActive\":" + c.isLoanActive()
                + ",\"summary\":{\"totalCredits\":" + formatMoney(totalCredits)
                + ",\"totalDebits\":" + formatMoney(totalDebits)
                + ",\"transactionCount\":" + allTxns.size() + "}"
                + ",\"accounts\":" + formatAccountsListJson(c.getAccounts())
                + ",\"recentTransactions\":" + formatTransactionListJson(allTxns, 8) + "}";
    }

    private static String formatBillsJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> e : bank.getTransactionService().getStandardBills().entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(quote(e.getKey())).append(":").append(formatMoney(e.getValue()));
        }
        return sb.append("}").toString();
    }

    // ── HTTP Request Parsing & Response ───────────────────────────────────────

    private static String readRequestBody(HttpExchange ex) throws IOException, BankingException {
        byte[] data = ex.getRequestBody().readNBytes(MAX_REQUEST_BODY + 1);
        if (data.length > MAX_REQUEST_BODY) {
            throw new BankingException("Request body exceeds maximum allowed size.");
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQueryParams(HttpExchange ex) {
        Map<String, String> params = new HashMap<>();
        String rawQuery = ex.getRequestURI().getRawQuery();
        if (rawQuery == null) return params;
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            String k = java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(k, v);
        }
        return params;
    }

    private static String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s.trim() : "";
    }

    private static double numVal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return Double.NaN;
    }

    private static BigDecimal bigDecimalVal(Map<String, Object> map, String key) throws InvalidAmountException {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (val instanceof String s && !s.trim().isEmpty()) {
            try {
                return new BigDecimal(s.trim()).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {}
        }
        throw new InvalidAmountException("Field '" + key + "' must be a valid numeric amount.");
    }

    private static void sendJsonResponse(HttpExchange ex, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    // ── Lightweight JSON Parser ───────────────────────────────────────────────

    private static Map<String, Object> parseJson(String json) throws BankingException {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        int[] index = {0};
        skipWhitespace(json, index);
        if (index[0] >= json.length() || json.charAt(index[0]) != '{') throw badJson();
        index[0]++;
        skipWhitespace(json, index);
        if (index[0] < json.length() && json.charAt(index[0]) == '}') return map;

        while (true) {
            skipWhitespace(json, index);
            String key = parseJsonString(json, index);
            skipWhitespace(json, index);
            if (index[0] >= json.length() || json.charAt(index[0]++) != ':') throw badJson();
            skipWhitespace(json, index);
            Object value = parseJsonValue(json, index);
            map.put(key, value);
            skipWhitespace(json, index);
            if (index[0] >= json.length()) throw badJson();
            char sep = json.charAt(index[0]++);
            if (sep == '}') return map;
            if (sep != ',') throw badJson();
        }
    }

    private static BankingException badJson() {
        return new BankingException("BAD_REQUEST", "Malformed JSON request body.");
    }

    private static void skipWhitespace(String s, int[] idx) {
        while (idx[0] < s.length() && Character.isWhitespace(s.charAt(idx[0]))) idx[0]++;
    }

    private static String parseJsonString(String s, int[] idx) throws BankingException {
        if (idx[0] >= s.length() || s.charAt(idx[0]) != '"') throw badJson();
        idx[0]++;
        StringBuilder sb = new StringBuilder();
        while (idx[0] < s.length()) {
            char ch = s.charAt(idx[0]++);
            if (ch == '"') return sb.toString();
            if (ch == '\\') {
                if (idx[0] >= s.length()) throw badJson();
                char esc = s.charAt(idx[0]++);
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (idx[0] + 4 > s.length()) throw badJson();
                        try {
                            sb.append((char) Integer.parseInt(s.substring(idx[0], idx[0] + 4), 16));
                            idx[0] += 4;
                        } catch (NumberFormatException e) {
                            throw badJson();
                        }
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(ch);
            }
        }
        throw badJson();
    }

    private static Object parseJsonValue(String s, int[] idx) throws BankingException {
        if (idx[0] >= s.length()) throw badJson();
        char ch = s.charAt(idx[0]);
        if (ch == '"') return parseJsonString(s, idx);
        if (s.startsWith("true", idx[0])) { idx[0] += 4; return Boolean.TRUE; }
        if (s.startsWith("false", idx[0])) { idx[0] += 5; return Boolean.FALSE; }
        if (s.startsWith("null", idx[0])) { idx[0] += 4; return null; }

        int start = idx[0];
        while (idx[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(idx[0])) >= 0) idx[0]++;
        if (start == idx[0]) throw badJson();
        try {
            return Double.parseDouble(s.substring(start, idx[0]));
        } catch (NumberFormatException e) {
            throw badJson();
        }
    }
}
