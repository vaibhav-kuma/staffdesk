package com.staffdesk.desktop;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ApiClientTest {
  @Test
  void remotePlaintextAndCredentialUrlsRejected() {
    for (String url :
        new String[] {
          "http://api.example.com",
          "https://user:pass@example.com",
          "https://example.com/api",
          "https://example.com?key=x",
          "file:///tmp/data"
        }) assertThrows(IllegalArgumentException.class, () -> new ApiClient(url, true));
  }

  @Test
  void onlyExplicitLoopbackHttpAllowed() {
    assertThrows(
        IllegalArgumentException.class, () -> new ApiClient("http://localhost:8080", false));
    try (var api = new ApiClient("http://127.0.0.1:8080", true)) {}
    try (var api = new ApiClient("https://example.com/", false)) {}
  }

  @Test
  void nullableEmployeeFiltersAndSortUseSafeDefaults() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var query = new java.util.concurrent.atomic.AtomicReference<String>();
    server.createContext(
        "/api/v1/employees",
        e -> {
          query.set(e.getRequestURI().getRawQuery());
          byte[] body =
              "{\"content\":[],\"page\":0,\"size\":25,\"totalElements\":0,\"totalPages\":0}"
                  .getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(200, body.length);
          e.getResponseBody().write(body);
          e.close();
        });
    server.start();
    try (var api = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort(), true)) {
      assertTrue(api.employees(null, null, null, 0, 25, null, null).join().content().isEmpty());
    } finally {
      server.stop(0);
    }
    assertEquals("q=&department=&status=&page=0&size=25&sort=lastName&direction=asc", query.get());
  }

  @Test
  void problemResponseParsedWithoutLeakingBody() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/auth/login",
        e -> {
          byte[] body =
              "{\"status\":401,\"detail\":\"Invalid email or password.\",\"requestId\":\"test-id\"}"
                  .getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(401, body.length);
          e.getResponseBody().write(body);
          e.close();
        });
    server.start();
    try (var api = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort(), true)) {
      var ex =
          assertThrows(
              java.util.concurrent.CompletionException.class,
              () -> api.login("test@example.com", "invalid").join());
      assertTrue(ex.getCause().getMessage().contains("test-id"));
      assertInstanceOf(ApiClient.ApiFailure.class, ex.getCause());
    } finally {
      server.stop(0);
    }
  }
}
