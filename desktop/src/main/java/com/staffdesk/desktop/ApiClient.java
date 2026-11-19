package com.staffdesk.desktop;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.staffdesk.contracts.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** No network operation runs on the JavaFX application thread. No token is persisted. */
public final class ApiClient implements AutoCloseable {
  private final URI base;
  private final ObjectMapper json =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final ExecutorService tasks = Executors.newVirtualThreadPerTaskExecutor();
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private volatile String token;

  public ApiClient(String url, boolean allowLocalHttp) {
    base = validateBase(url, allowLocalHttp);
  }

  static URI validateBase(String url, boolean allowLocalHttp) {
    URI u;
    try {
      u = URI.create(url.replaceAll("/+$", ""));
    } catch (Exception e) {
      throw new IllegalArgumentException("API URL is invalid.");
    }
    String host = u.getHost();
    boolean local = host != null && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(host);
    if (host == null
        || u.getRawUserInfo() != null
        || u.getRawQuery() != null
        || u.getRawFragment() != null
        || (!u.getPath().isEmpty() && !u.getPath().equals("/")))
      throw new IllegalArgumentException(
          "API URL must be an origin, e.g. https://api.example.com (no path, credentials or query).");
    if (!u.getScheme().equalsIgnoreCase("https")
        && !(allowLocalHttp && local && u.getScheme().equalsIgnoreCase("http")))
      throw new IllegalArgumentException(
          "HTTPS is required. For loopback development only, set STAFFDESK_ALLOW_LOCAL_HTTP=true.");
    return u;
  }

  public void clearToken() {
    token = null;
  }

  public CompletableFuture<Api.Token> login(String email, String password) {
    return call(
            "POST",
            "/api/v1/auth/login",
            new Api.Login(email, password),
            json.constructType(Api.Token.class))
        .thenApply(
            t -> {
              Api.Token session = (Api.Token) t;
              token = session.accessToken();
              return session;
            });
  }

  public CompletableFuture<Api.Page<Api.Employee>> employees(
      String q,
      String department,
      String status,
      int page,
      int size,
      String sort,
      String direction) {
    return typed(
        "GET",
        "/api/v1/employees?q="
            + enc(q)
            + "&department="
            + enc(department)
            + "&status="
            + enc(status)
            + "&page="
            + page
            + "&size="
            + size
            + "&sort="
            + enc(Objects.toString(sort, "lastName"))
            + "&direction="
            + enc(Objects.toString(direction, "asc")),
        null,
        pageType(Api.Employee.class));
  }

  public CompletableFuture<List<String>> departments() {
    return typed(
        "GET",
        "/api/v1/employees/departments",
        null,
        json.getTypeFactory().constructCollectionType(List.class, String.class));
  }

  public CompletableFuture<Api.Employee> save(Api.Employee old, Api.EmployeeInput input) {
    return typed(
        old == null ? "POST" : "PUT",
        "/api/v1/employees" + (old == null ? "" : "/" + old.id()),
        input,
        json.constructType(Api.Employee.class));
  }

  public CompletableFuture<Void> delete(Api.Employee e) {
    return typed(
        "DELETE",
        "/api/v1/employees/" + e.id() + "?version=" + e.version(),
        null,
        json.constructType(Void.class));
  }

  public CompletableFuture<Api.Page<Api.User>> users(int page) {
    return typed("GET", "/api/v1/users?page=" + page + "&size=25", null, pageType(Api.User.class));
  }

  public CompletableFuture<Api.User> createUser(Api.NewUser u) {
    return typed("POST", "/api/v1/users", u, json.constructType(Api.User.class));
  }

  public CompletableFuture<Api.User> updateUser(UUID id, Api.UserUpdate u) {
    return typed("PUT", "/api/v1/users/" + id, u, json.constructType(Api.User.class));
  }

  public CompletableFuture<Void> password(UUID id, String p) {
    return typed(
        "PUT",
        "/api/v1/users/" + id + "/password",
        new Api.PasswordChange(p),
        json.constructType(Void.class));
  }

  private JavaType pageType(Class<?> type) {
    return json.getTypeFactory().constructParametricType(Api.Page.class, type);
  }

  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> typed(String method, String path, Object body, JavaType type) {
    return call(method, path, body, type).thenApply(x -> (T) x);
  }

  private CompletableFuture<Object> call(String method, String path, Object body, JavaType type) {
    String bearer = token;
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            var builder =
                HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
            if (body != null) builder.header("Content-Type", "application/json");
            var request =
                builder
                    .method(
                        method,
                        body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
              String message = "API request failed (HTTP " + response.statusCode() + ").";
              try {
                var p = json.readTree(response.body());
                message = p.path("detail").asText(message);
                if (p.has("fields")) message += "\n" + p.get("fields").toPrettyString();
                if (p.has("requestId")) message += "\nRequest ID: " + p.get("requestId").asText();
              } catch (Exception ignored) {
              }
              throw new ApiFailure(response.statusCode(), message);
            }
            return type.hasRawClass(Void.class) ? null : json.readValue(response.body(), type);
          } catch (ApiFailure e) {
            throw e;
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiFailure(0, "Request was interrupted.");
          } catch (Exception e) {
            throw new ApiFailure(
                0, "Cannot reach the API. Check the URL, connection and TLS certificate.");
          }
        },
        tasks);
  }

  private static String enc(String v) {
    return URLEncoder.encode(Objects.toString(v, ""), java.nio.charset.StandardCharsets.UTF_8);
  }

  public void close() {
    token = null;
    tasks.shutdownNow();
    http.close();
  }

  public static final class ApiFailure extends RuntimeException {
    public final int status;

    ApiFailure(int s, String m) {
      super(m);
      status = s;
    }
  }
}
