package pack_1;

import pack_1.controller.WebServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * End-to-end HTTP REST API Integration Test.
 * Spins up the embedded WebServer on port 8085 and executes real HTTP requests using Java 25 HttpClient.
 */
public class TestWebEndpoints {

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("   HTTP REST API END-TO-END TEST SUITE (PORT 8085)             ");
        System.out.println("===============================================================");

        int port = 8085;
        try {
            WebServer.startServer(port);
            Thread.sleep(1000);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            // 1. GET /api/health
            HttpRequest reqHealth = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/health"))
                    .GET()
                    .build();
            HttpResponse<String> resHealth = client.send(reqHealth, HttpResponse.BodyHandlers.ofString());
            assertCheck("GET /api/health returned HTTP 200", resHealth.statusCode() == 200);
            assertCheck("Health response contains 'running'", resHealth.body().contains("\"running\""));

            // 2. GET static HTML (index.html)
            HttpRequest reqIndex = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/index.html"))
                    .GET()
                    .build();
            HttpResponse<String> resIndex = client.send(reqIndex, HttpResponse.BodyHandlers.ofString());
            assertCheck("GET /index.html returned HTTP 200", resIndex.statusCode() == 200);
            assertCheck("Index contains 'Rajarata Digital Banking System'", resIndex.body().contains("Rajarata"));

            // 3. POST /api/login for customer
            String loginBody = "{\"username\":\"testuser\",\"password\":\"Secret123\"}";
            HttpRequest reqLogin = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                    .build();
            HttpResponse<String> resLogin = client.send(reqLogin, HttpResponse.BodyHandlers.ofString());
            assertCheck("POST /api/login returned HTTP 200", resLogin.statusCode() == 200);
            assertCheck("Login returned auth token", resLogin.body().contains("\"token\""));

            // Extract token
            String body = resLogin.body();
            int tIdx = body.indexOf("\"token\":");
            String token = body.substring(tIdx + 9, body.indexOf("\"", tIdx + 10));

            // 4. GET /api/dashboard with token
            HttpRequest reqDash = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/dashboard"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> resDash = client.send(reqDash, HttpResponse.BodyHandlers.ofString());
            assertCheck("GET /api/dashboard returned HTTP 200", resDash.statusCode() == 200);
            assertCheck("Dashboard contains 'totalBalance'", resDash.body().contains("\"totalBalance\""));

            // 5. GET /api/dashboard without token -> 401
            HttpRequest reqDashNoAuth = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/dashboard"))
                    .GET()
                    .build();
            HttpResponse<String> resDashNoAuth = client.send(reqDashNoAuth, HttpResponse.BodyHandlers.ofString());
            assertCheck("GET /api/dashboard without token returns HTTP 401 Unauthorized", resDashNoAuth.statusCode() == 401);

            // 6. POST /api/login for admin
            String adminLoginBody = "{\"username\":\"admin\",\"password\":\"admin123\"}";
            HttpRequest reqAdminLogin = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(adminLoginBody))
                    .build();
            HttpResponse<String> resAdminLogin = client.send(reqAdminLogin, HttpResponse.BodyHandlers.ofString());
            assertCheck("Admin login returned HTTP 200", resAdminLogin.statusCode() == 200);
            assertCheck("Admin role confirmed in response", resAdminLogin.body().contains("\"role\":\"ADMIN\""));

            String adminBody = resAdminLogin.body();
            int atIdx = adminBody.indexOf("\"token\":");
            String adminToken = adminBody.substring(atIdx + 9, adminBody.indexOf("\"", atIdx + 10));

            // 7. GET /api/admin/stats with admin token
            HttpRequest reqAdminStats = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/admin/stats"))
                    .header("Authorization", "Bearer " + adminToken)
                    .GET()
                    .build();
            HttpResponse<String> resAdminStats = client.send(reqAdminStats, HttpResponse.BodyHandlers.ofString());
            assertCheck("GET /api/admin/stats returned HTTP 200", resAdminStats.statusCode() == 200);
            assertCheck("Admin stats contains 'totalDeposits'", resAdminStats.body().contains("\"totalDeposits\""));

            // 8. Customer attempting to access admin route -> 403 Forbidden
            HttpRequest reqForbidden = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/admin/stats"))
                    .header("Authorization", "Bearer " + token) // Customer token
                    .GET()
                    .build();
            HttpResponse<String> resForbidden = client.send(reqForbidden, HttpResponse.BodyHandlers.ofString());
            assertCheck("Customer access to /api/admin/stats returns HTTP 403 Forbidden", resForbidden.statusCode() == 403);

            System.out.println("\n===============================================================");
            System.out.println("  ALL HTTP REST API TESTS PASSED SUCCESSFULLY! (8/8)           ");
            System.out.println("===============================================================");
            System.exit(0);

        } catch (Exception e) {
            System.err.println("[HTTP TEST ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void assertCheck(String name, boolean pass) {
        if (pass) {
            System.out.println("  [HTTP PASS] " + name);
        } else {
            System.err.println("  [HTTP FAIL] " + name);
            throw new AssertionError("HTTP test assertion failed: " + name);
        }
    }
}
