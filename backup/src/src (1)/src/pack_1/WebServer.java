package pack_1;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebServer — connects the HTML/CSS/JS frontend to the Java banking classes.
 * All banking rules live in BankSystem / Account classes; this class only
 * translates HTTP + JSON into method calls and back.
 *
 * Runs on the default single-threaded executor, so requests are handled one at a time
 * (safe for the ArrayList-based data model).
 */
public class WebServer {

    private static final BankSystem bank = new BankSystem();

    // ── Sessions: random token -> logged-in user (expire after 20 idle minutes) ──
    private static final long SESSION_IDLE_MS = 20 * 60 * 1000L;
    private static final class Session {
        final User user; long lastSeen = System.currentTimeMillis();
        Session(User user) { this.user = user; }
    }
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private static final int MAX_BODY = 64 * 1024;

    private static final Path FRONTEND = locateFrontend();

    private static Path locateFrontend() {
        Path a = Paths.get("src", "pack_1", "frontend", "public").toAbsolutePath().normalize();
        if (Files.isDirectory(a)) return a;
        return Paths.get("frontend", "public").toAbsolutePath().normalize();
    }

    public static void main(String[] args) throws Exception {
        FileHandler.restoreSystem(bank);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/", WebServer::handleRequest);
        server.setExecutor(null);
        server.start();

        System.out.println("==========================================");
        System.out.println(" RAJARATA DIGITAL BANK — SERVER STARTED");
        System.out.println(" Website : http://localhost:8080");
        System.out.println(" API     : http://localhost:8080/api/health");
        System.out.println(" Frontend: " + FRONTEND);
        System.out.println("==========================================");
    }

    // ═════════════════════ REQUEST ENTRY POINT ═════════════════════

    private static void handleRequest(HttpExchange ex) throws IOException {
        // CORS: lets the pages call this API even if they were opened from another local port
        // (e.g. VS Code Live Server on :5500). Only http://localhost / 127.0.0.1 origins are allowed.
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && origin.matches("http://(localhost|127\\.0\\.0\\.1)(:\\d+)?")) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ex.getResponseHeaders().set("Vary", "Origin");
        }
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'");

        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                ex.getResponseHeaders().set("Cache-Control", "no-store");
                handleAPI(ex, path, ex.getRequestMethod());
            } else if (ex.getRequestMethod().equals("GET") || ex.getRequestMethod().equals("HEAD")) {
                serveFrontend(ex, path);
            } else {
                sendJson(ex, 405, error("Method not allowed", "METHOD_NOT_ALLOWED"));
            }
        } catch (MethodException e) {
            sendJson(ex, 405, error(e.getMessage(), e.getCode()));
        } catch (AuthenticationException e) {
            sendJson(ex, "LOCKED".equals(e.getCode()) ? 429 : 401, error(e.getMessage(), e.getCode()));
        } catch (AccountNotFoundException e) {
            sendJson(ex, 404, error(e.getMessage(), e.getCode()));
        } catch (DuplicateRegistrationException e) {
            sendJson(ex, 409, error(e.getMessage(), e.getCode()));
        } catch (BankingException e) {                      // every other validation / banking rule
            sendJson(ex, 400, error(e.getMessage(), e.getCode()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, error("Internal server error", "SERVER_ERROR"));
        }
    }

    // ═════════════════════ STATIC FRONTEND ═════════════════════

    private static void serveFrontend(HttpExchange ex, String requestedPath) throws IOException {
        String fileName = requestedPath.equals("/") ? "index.html" : requestedPath.substring(1);
        Path file = FRONTEND.resolve(fileName).normalize();

        if (!file.startsWith(FRONTEND) || !Files.isRegularFile(file)) {
            byte[] msg = ("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Not found</title>"
                    + "<link rel=\"stylesheet\" href=\"/portal.css\"></head><body class=\"auth-body\">"
                    + "<div class=\"auth-card\"><h1>Page not found</h1><p class=\"muted\">The page you asked for does not exist.</p>"
                    + "<a class=\"btn btn-primary\" href=\"/\">Back to home</a></div></body></html>")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }

        String type = "application/octet-stream";
        if      (fileName.endsWith(".html")) type = "text/html; charset=UTF-8";
        else if (fileName.endsWith(".css"))  type = "text/css; charset=UTF-8";
        else if (fileName.endsWith(".js"))   type = "application/javascript; charset=UTF-8";
        else if (fileName.endsWith(".png"))  type = "image/png";
        else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) type = "image/jpeg";
        else if (fileName.endsWith(".svg"))  type = "image/svg+xml";
        else if (fileName.endsWith(".ico"))  type = "image/x-icon";

        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        if (ex.getRequestMethod().equals("HEAD")) { ex.sendResponseHeaders(200, -1); ex.close(); return; }
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    // ═════════════════════ API ROUTES ═════════════════════

    private static void handleAPI(HttpExchange ex, String path, String method) throws Exception {
        if (method.equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); ex.close(); return; }

        String[] seg = path.substring(5).split("/");    // "/api/accounts/SAV1" -> [accounts, SAV1]
        String route = seg[0];

        // ── Public routes ──
        if (route.equals("health")) { requireMethod(method, "GET"); sendJson(ex, 200, "{\"success\":true,\"status\":\"running\"}"); return; }

        if (route.equals("register")) {
            requireMethod(method, "POST");
            Map<String, Object> b = parseJson(readBody(ex));
            Customer c = bank.registerCustomer(str(b, "username"), str(b, "password"), str(b, "email"),
                    str(b, "phone"), str(b, "fullName"), str(b, "address"), str(b, "nationalId"));
            sendJson(ex, 201, "{\"success\":true,\"message\":\"Registration successful. You can now log in.\","
                    + "\"userId\":" + q(c.getUserId()) + ",\"accountNumber\":" + q(c.getPrimaryAccount().getAccountNumber()) + "}");
            return;
        }

        if (route.equals("login")) {
            requireMethod(method, "POST");
            Map<String, Object> b = parseJson(readBody(ex));
            User user = bank.login(str(b, "username"), str(b, "password"));
            String token = UUID.randomUUID().toString();
            sessions.put(token, new Session(user));
            sendJson(ex, 200, "{\"success\":true,\"token\":" + q(token) + ",\"user\":" + userJson(user) + "}");
            return;
        }

        // ── Everything below needs a valid session token ──
        User user = authenticate(ex);
        if (user == null) { sendJson(ex, 401, error("Please log in first", "UNAUTHORIZED")); return; }

        if (route.equals("logout")) {
            if (!method.equals("POST") && !method.equals("DELETE")) throw new MethodException();
            sessions.remove(getToken(ex));
            sendJson(ex, 200, "{\"success\":true,\"message\":\"Logged out\"}");
            return;
        }
        if (route.equals("me"))    { requireMethod(method, "GET"); sendJson(ex, 200, "{\"success\":true,\"user\":" + userJson(user) + "}"); return; }
        if (route.equals("bills")) { requireMethod(method, "GET"); sendJson(ex, 200, "{\"success\":true,\"bills\":" + billsJson() + "}"); return; }

        if (route.equals("password")) {
            requireMethod(method, "PUT");
            Map<String, Object> b = parseJson(readBody(ex));
            bank.changePassword(user, str(b, "currentPassword"), str(b, "newPassword"));
            success(ex, "Password changed successfully");
            return;
        }

        // ── Customer-only routes ──
        if (!(user instanceof Customer customer)) {
            sendJson(ex, 403, error("Customer access required", "FORBIDDEN"));
            return;
        }

        switch (route) {
            case "dashboard" -> { requireMethod(method, "GET"); sendJson(ex, 200, dashboardJson(customer)); }

            case "profile" -> {
                if (method.equals("GET")) { sendJson(ex, 200, "{\"success\":true,\"profile\":" + profileJson(customer) + "}"); }
                else if (method.equals("PUT")) {
                    Map<String, Object> b = parseJson(readBody(ex));
                    bank.updateProfile(customer, str(b, "email"), str(b, "phone"), str(b, "address"));
                    sendJson(ex, 200, "{\"success\":true,\"message\":\"Profile updated\",\"profile\":" + profileJson(customer) + "}");
                } else throw new MethodException();
            }

            case "accounts" -> {
                requireMethod(method, "GET");
                if (seg.length == 1) {
                    sendJson(ex, 200, "{\"success\":true,\"accounts\":" + accountsJson(customer) + "}");
                } else {
                    Account a = customer.getAccountByNumber(seg[1].toUpperCase());
                    if (a == null) throw new AccountNotFoundException("Account not found: " + seg[1]);
                    sendJson(ex, 200, "{\"success\":true,\"account\":" + accountJson(a) + "}");
                }
            }

            case "transactions" -> {
                requireMethod(method, "GET");
                if (seg.length == 1) {
                    Map<String, String> qp = queryParams(ex);
                    String acc = qp.getOrDefault("account", "");
                    if (!acc.isEmpty() && customer.getAccountByNumber(acc.toUpperCase()) == null)
                        throw new AccountNotFoundException("Account not found: " + acc);
                    List<Transaction> list = allTransactions(customer, acc.toUpperCase(), qp.getOrDefault("type", ""));
                    sendJson(ex, 200, "{\"success\":true,\"count\":" + list.size() + ",\"transactions\":" + txnListJson(list, 500) + "}");
                } else {
                    for (Transaction t : allTransactions(customer, "", ""))
                        if (t.getTransactionId().equals(seg[1])) {
                            sendJson(ex, 200, "{\"success\":true,\"transaction\":" + txnJson(t) + "}");
                            return;
                        }
                    sendJson(ex, 404, error("Transaction not found", "NOT_FOUND"));
                }
            }

            case "deposit" -> {
                requireMethod(method, "POST");
                Map<String, Object> b = parseJson(readBody(ex));
                bank.deposit(customer, str(b, "accountNumber"), num(b, "amount"));
                moneyResult(ex, "Deposit successful", customer, str(b, "accountNumber"));
            }
            case "withdraw" -> {
                requireMethod(method, "POST");
                Map<String, Object> b = parseJson(readBody(ex));
                bank.withdraw(customer, str(b, "accountNumber"), num(b, "amount"));
                moneyResult(ex, "Withdrawal successful", customer, str(b, "accountNumber"));
            }
            case "transfer" -> {
                requireMethod(method, "POST");
                Map<String, Object> b = parseJson(readBody(ex));
                bank.transfer(customer, str(b, "accountNumber"), str(b, "toAccount"), num(b, "amount"));
                moneyResult(ex, "Transfer successful", customer, str(b, "accountNumber"));
            }
            case "open-account" -> {
                requireMethod(method, "POST");
                Map<String, Object> b = parseJson(readBody(ex));
                double initial = b.containsKey("amount") ? num(b, "amount") : 0;
                double months  = b.containsKey("fdMonths") ? num(b, "fdMonths") : 12;
                Account a = bank.openAccount(customer, str(b, "accountType"), initial, (int) months);
                sendJson(ex, 201, "{\"success\":true,\"message\":\"Account opened\",\"account\":" + accountJson(a) + "}");
            }
            case "apply-loan" -> {
                requireMethod(method, "POST");
                Map<String, Object> b = parseJson(readBody(ex));
                bank.applyLoan(customer, str(b, "accountNumber"), num(b, "amount"));
                success(ex, "Loan application successful");
            }
            case "repay-loan" -> {
                requireMethod(method, "POST");
                Map<String, Object> b = parseJson(readBody(ex));
                bank.repayLoan(customer, str(b, "accountNumber"), num(b, "amount"));
                success(ex, "Loan repayment successful");
            }
            case "pay-bill" -> {
                requireMethod(method, "POST");
                Map<String, Object> b = parseJson(readBody(ex));
                bank.payBill(customer, str(b, "accountNumber"), str(b, "billType"));
                success(ex, "Bill payment successful");
            }
            default -> sendJson(ex, 404, error("API endpoint not found", "NOT_FOUND"));
        }
    }

    /** Thrown for a wrong HTTP method; turned into a 405 response. */
    private static final class MethodException extends BankingException {
        private static final long serialVersionUID = 1L;
        MethodException() { super("METHOD_NOT_ALLOWED", "Method not allowed"); }
    }

    private static void requireMethod(String actual, String expected) throws BankingException {
        if (!actual.equals(expected)) throw new MethodException();
    }

    // ═════════════════════ SESSIONS ═════════════════════

    private static String getToken(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        return (h == null || !h.startsWith("Bearer ")) ? "" : h.substring(7).trim();
    }

    private static User authenticate(HttpExchange ex) {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> now - s.lastSeen > SESSION_IDLE_MS);
        Session s = sessions.get(getToken(ex));
        if (s == null) return null;
        s.lastSeen = now;
        return s.user;
    }

    // ═════════════════════ JSON RESPONSES ═════════════════════

    private static String userJson(User u) {
        return "{\"userId\":" + q(u.getUserId())
             + ",\"username\":" + q(u.getUsername())
             + ",\"fullName\":" + q(u instanceof Customer c ? c.getFullName() : u.getUsername())
             + ",\"email\":" + q(u.getEmail())
             + ",\"phone\":" + q(u.getPhone())
             + ",\"role\":" + q(u.getRole()) + "}";
    }

    private static String profileJson(Customer c) {
        String nic = c.getNationalId();
        String masked = nic.length() <= 3 ? "***" : "*".repeat(nic.length() - 3) + nic.substring(nic.length() - 3);
        return "{\"userId\":" + q(c.getUserId())
             + ",\"username\":" + q(c.getUsername())
             + ",\"fullName\":" + q(c.getFullName())
             + ",\"email\":" + q(c.getEmail())
             + ",\"phone\":" + q(c.getPhone())
             + ",\"address\":" + q(c.getAddress())
             + ",\"nationalId\":" + q(masked)               // masked: never the full ID
             + ",\"accountCount\":" + c.getAccounts().size()
             + ",\"loanActive\":" + c.isLoanActive()
             + ",\"loanBalance\":" + money(c.getLoanBalance()) + "}";
    }

    private static String accountJson(Account a) {
        return "{\"accountNumber\":" + q(a.getAccountNumber())
             + ",\"accountType\":" + q(a.getAccountType())
             + ",\"balance\":" + money(a.getBalance())
             + ",\"interestRate\":" + a.getInterestRate()          // polymorphic: differs per account class
             + ",\"features\":" + q(a.getAccountFeatures())        // polymorphic: differs per account class
             + ",\"dateOpened\":" + q(a.getDateOpened())
             + ",\"active\":" + a.isActive()
             + ",\"transactionCount\":" + a.getTransactions().size() + "}";
    }

    private static String accountsJson(Customer c) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < c.getAccounts().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(accountJson(c.getAccounts().get(i)));
        }
        return sb.append("]").toString();
    }

    private static String txnJson(Transaction t) {
        return "{\"transactionId\":" + q(t.getTransactionId())
             + ",\"type\":" + q(t.getType())
             + ",\"direction\":" + q(t.isCredit() ? "CREDIT" : "DEBIT")
             + ",\"amount\":" + money(t.getAmount())
             + ",\"balanceAfter\":" + money(t.getBalanceAfter())
             + ",\"description\":" + q(t.getDescription())
             + ",\"accountNumber\":" + q(t.getAccountNumber())
             + ",\"timestamp\":" + q(t.getTimestamp()) + "}";
    }

    private static String txnListJson(List<Transaction> list, int limit) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            if (i > 0) sb.append(",");
            sb.append(txnJson(list.get(i)));
        }
        return sb.append("]").toString();
    }

    /** Newest first. Optional filters: one account number, one transaction type. */
    private static List<Transaction> allTransactions(Customer c, String accNo, String type) {
        List<Transaction> all = new ArrayList<>();
        for (Account a : c.getAccounts()) {
            if (!accNo.isEmpty() && !a.getAccountNumber().equals(accNo)) continue;
            for (Transaction t : a.getTransactions())
                if (type.isEmpty() || t.getType().equalsIgnoreCase(type)) all.add(t);
        }
        all.sort((x, y) -> Long.compare(y.getSequence(), x.getSequence()));
        return all;
    }

    private static String dashboardJson(Customer c) {
        double total = 0, credits = 0, debits = 0;
        for (Account a : c.getAccounts()) {
            total += a.getBalance();
            for (Transaction t : a.getTransactions()) {
                if (t.isCredit()) credits += t.getAmount(); else debits += t.getAmount();
            }
        }
        List<Transaction> all = allTransactions(c, "", "");
        Account primary = c.getPrimaryAccount();
        return "{\"success\":true"
             + ",\"user\":" + userJson(c)
             + ",\"totalBalance\":" + money(total)
             + ",\"primaryAccount\":" + (primary == null ? "null" : accountJson(primary))
             + ",\"loanBalance\":" + money(c.getLoanBalance())
             + ",\"loanActive\":" + c.isLoanActive()
             + ",\"summary\":{\"totalCredits\":" + money(credits)
             + ",\"totalDebits\":" + money(debits)
             + ",\"transactionCount\":" + all.size() + "}"
             + ",\"accounts\":" + accountsJson(c)
             + ",\"recentTransactions\":" + txnListJson(all, 8) + "}";
    }

    private static void moneyResult(HttpExchange ex, String message, Customer c, String accNo) throws IOException {
        Account a = c.getAccountByNumber(accNo.trim().toUpperCase());
        sendJson(ex, 200, "{\"success\":true,\"message\":" + q(message)
                + ",\"balance\":" + (a == null ? "null" : money(a.getBalance())) + "}");
    }

    private static String billsJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : BankSystem.getBills().entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(q(e.getKey())).append(":").append(money(e.getValue()));
        }
        return sb.append("}").toString();
    }

    // ═════════════════════ JSON HELPERS ═════════════════════

    private static String money(double v) {
        return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String q(String value) {
        if (value == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> { if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch)); else sb.append(ch); }
            }
        }
        return sb.append("\"").toString();
    }

    private static String error(String message, String code) {
        return "{\"success\":false,\"message\":" + q(message) + ",\"code\":" + q(code) + "}";
    }

    private static void success(HttpExchange ex, String message) throws IOException {
        sendJson(ex, 200, "{\"success\":true,\"message\":" + q(message) + "}");
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v instanceof String s ? s : "";
    }

    /** Missing / non-numeric amounts become NaN, which Account.checkAmount rejects. */
    private static double num(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v instanceof Double d ? d : Double.NaN;
    }

    private static Map<String, String> queryParams(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }

    private static String readBody(HttpExchange ex) throws IOException, BankingException {
        byte[] data = ex.getRequestBody().readNBytes(MAX_BODY + 1);
        if (data.length > MAX_BODY) throw new BankingException("Request body too large.");
        return new String(data, StandardCharsets.UTF_8);
    }

    /** Small parser for flat JSON objects: {"key":"text","n":12.5,"flag":true,"x":null}. */
    private static Map<String, Object> parseJson(String s) throws BankingException {
        Map<String, Object> map = new HashMap<>();
        if (s == null || s.isBlank()) return map;
        int[] p = {0};
        skipWs(s, p);
        if (p[0] >= s.length() || s.charAt(p[0]) != '{') throw badJson();
        p[0]++;
        skipWs(s, p);
        if (p[0] < s.length() && s.charAt(p[0]) == '}') return map;
        while (true) {
            skipWs(s, p);
            String key = readString(s, p);
            skipWs(s, p);
            if (p[0] >= s.length() || s.charAt(p[0]++) != ':') throw badJson();
            skipWs(s, p);
            map.put(key, readValue(s, p));
            skipWs(s, p);
            if (p[0] >= s.length()) throw badJson();
            char ch = s.charAt(p[0]++);
            if (ch == '}') return map;
            if (ch != ',') throw badJson();
        }
    }

    private static BankingException badJson() { return new BankingException("BAD_REQUEST", "Malformed request."); }

    private static void skipWs(String s, int[] p) {
        while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++;
    }

    private static String readString(String s, int[] p) throws BankingException {
        if (p[0] >= s.length() || s.charAt(p[0]) != '"') throw badJson();
        p[0]++;
        StringBuilder sb = new StringBuilder();
        while (p[0] < s.length()) {
            char ch = s.charAt(p[0]++);
            if (ch == '"') return sb.toString();
            if (ch == '\\') {
                if (p[0] >= s.length()) throw badJson();
                char e = s.charAt(p[0]++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (p[0] + 4 > s.length()) throw badJson();
                        try { sb.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16)); }
                        catch (NumberFormatException nfe) { throw badJson(); }
                        p[0] += 4;
                    }
                    default -> sb.append(e);   // \" \\ \/
                }
            } else sb.append(ch);
        }
        throw badJson();
    }

    private static Object readValue(String s, int[] p) throws BankingException {
        if (p[0] >= s.length()) throw badJson();
        char ch = s.charAt(p[0]);
        if (ch == '"') return readString(s, p);
        if (s.startsWith("true", p[0]))  { p[0] += 4; return Boolean.TRUE; }
        if (s.startsWith("false", p[0])) { p[0] += 5; return Boolean.FALSE; }
        if (s.startsWith("null", p[0]))  { p[0] += 4; return null; }
        int start = p[0];
        while (p[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(p[0])) >= 0) p[0]++;
        if (start == p[0]) throw badJson();
        try { return Double.parseDouble(s.substring(start, p[0])); }
        catch (NumberFormatException e) { throw badJson(); }
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
}
