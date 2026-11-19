package com.staffdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.staffdesk.contracts.*;
import com.staffdesk.users.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs against PostgreSQL: Testcontainers in CI, explicit TEST_DB_URL for Docker-less environments.
 */
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationIT {
  static PostgreSQLContainer<?> database;
  static final String PASSWORD = "Test-Only-Passphrase!25";
  static final UUID ADMIN = UUID.fromString("eebce730-bf57-4f58-af61-b1db41aeb451"),
      EDITOR = UUID.fromString("87b7fe32-0971-408c-b62b-51ea775e0b79"),
      VIEWER = UUID.fromString("fcd4b5e0-bd96-4c30-b06d-903a36b54cab");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry p) {
    if (System.getenv("TEST_DB_URL") != null
        && !System.getenv("TEST_DB_URL")
            .matches("jdbc:postgresql://[^/]+/[^?;]*_test(?:[?;].*)?")) {
      throw new IllegalStateException(
          "Destructive integration tests require a database name ending in _test");
    }
    if (System.getenv("TEST_DB_URL") == null) {
      database = new PostgreSQLContainer<>("postgres:17-alpine");
      database.start();
      p.add("spring.datasource.url", database::getJdbcUrl);
      p.add("spring.datasource.username", database::getUsername);
      p.add("spring.datasource.password", database::getPassword);
    } else {
      p.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
      p.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
      p.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }
    p.add(
        "app.jwt-secret",
        () ->
            Base64.getEncoder()
                .encodeToString("TEST-ONLY-secret-at-least-32-bytes-long!!".getBytes()));
    p.add("app.bootstrap-email", () -> "");
    p.add("app.bootstrap-password", () -> "");
    p.add("springdoc.api-docs.enabled", () -> true);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  @Autowired JwtEncoder jwt;
  static String hash;

  @BeforeEach
  void setup() {
    jdbc.execute("truncate table audit_events,employees,app_users,login_attempts restart identity");
    if (hash == null) hash = passwords.encode(PASSWORD);
    seed(ADMIN, "admin@example.test", "ADMIN");
    seed(EDITOR, "editor@example.test", "EDITOR");
    seed(VIEWER, "viewer@example.test", "VIEWER");
  }

  private void seed(UUID id, String email, String role) {
    jdbc.update(
        "insert into app_users(id,email,password_hash,role) values (?,?,?,?)",
        id,
        email,
        hash,
        role);
  }

  private String token(String email) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(new Api.Login(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("accessToken")
        .asText();
  }

  private String admin() throws Exception {
    return token("admin@example.test");
  }

  private String editor() throws Exception {
    return token("editor@example.test");
  }

  private String viewer() throws Exception {
    return token("viewer@example.test");
  }

  private Api.EmployeeInput employee(String email, Long version) {
    return new Api.EmployeeInput(
        "Asha",
        "Sharma",
        email,
        "Engineering",
        "Software Engineer",
        "+91 555 0100",
        LocalDate.of(2022, 3, 1),
        EmployeeStatus.ACTIVE,
        version);
  }

  private JsonNode create(String token, String email) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/v1/employees")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(employee(email, null))))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void healthAndDocumentation() throws Exception {
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/employees']").exists());
  }

  @Test
  void anonymousForbiddenAndErrorHasRequestId() throws Exception {
    mvc.perform(get("/api/v1/employees"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(header().exists("WWW-Authenticate"));
  }

  @Test
  void fullCrudAndAudit() throws Exception {
    String t = editor();
    JsonNode e = create(t, "Asha@EXAMPLE.test");
    String id = e.get("id").asText();
    assertEquals("asha@example.test", e.get("email").asText());
    mvc.perform(get("/api/v1/employees/" + id).header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Asha"));
    var changed =
        new Api.EmployeeInput(
            "Asha",
            "Sharma",
            "asha@example.test",
            "Product",
            "Lead",
            null,
            LocalDate.of(2022, 3, 1),
            EmployeeStatus.ON_LEAVE,
            0L);
    mvc.perform(
            put("/api/v1/employees/" + id)
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(changed)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            delete("/api/v1/employees/" + id + "?version=1").header("Authorization", "Bearer " + t))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/employees/" + id).header("Authorization", "Bearer " + t))
        .andExpect(status().isNotFound());
    assertEquals(3, jdbc.queryForObject("select count(*) from audit_events", Integer.class));
  }

  @Test
  void roleMatrix() throws Exception {
    String v = viewer(), e = editor();
    mvc.perform(get("/api/v1/employees").header("Authorization", "Bearer " + v))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/employees")
                .header("Authorization", "Bearer " + v)
                .contentType("application/json")
                .content(json.writeValueAsString(employee("a@test.org", null))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + e))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + v))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + admin()))
        .andExpect(status().isOk());
  }

  @Test
  void viewerCannotUpdateOrDelete() throws Exception {
    String t = admin();
    JsonNode e = create(t, "a@example.test");
    String v = viewer(), url = "/api/v1/employees/" + e.get("id").asText();
    mvc.perform(
            put(url)
                .header("Authorization", "Bearer " + v)
                .contentType("application/json")
                .content(json.writeValueAsString(employee("a@example.test", 0L))))
        .andExpect(status().isForbidden());
    mvc.perform(delete(url + "?version=0").header("Authorization", "Bearer " + v))
        .andExpect(status().isForbidden());
  }

  @Test
  void validationAndDuplicateRollback() throws Exception {
    String t = admin();
    create(t, "unique@example.test");
    mvc.perform(
            post("/api/v1/employees")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(employee("UNIQUE@example.test", null))))
        .andExpect(status().isConflict());
    var bad =
        new Api.EmployeeInput(
            " ",
            "Sharma",
            "not-email",
            "Dept",
            "Title",
            null,
            LocalDate.now().plusDays(1),
            EmployeeStatus.ACTIVE,
            null);
    mvc.perform(
            post("/api/v1/employees")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(bad)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.email").exists());
    assertEquals(1, jdbc.queryForObject("select count(*) from audit_events", Integer.class));
  }

  @Test
  void staleUpdatesAndDeletesConflict() throws Exception {
    String t = admin();
    var e = create(t, "a@example.test");
    String url = "/api/v1/employees/" + e.get("id").asText();
    mvc.perform(
            put(url)
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(employee("b@example.test", 0L))))
        .andExpect(status().isOk());
    mvc.perform(
            put(url)
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(employee("c@example.test", 0L))))
        .andExpect(status().isConflict());
    mvc.perform(delete(url + "?version=0").header("Authorization", "Bearer " + t))
        .andExpect(status().isConflict());
    mvc.perform(
            put(url)
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(employee("c@example.test", null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchingFilteringSortingPagination() throws Exception {
    String t = admin();
    create(t, "z@example.test");
    create(t, "a@example.test");
    create(t, "m@example.test");
    mvc.perform(
            get("/api/v1/employees?q=ASHA&department=Engineering&status=ACTIVE&sort=email&direction=desc&size=1&page=1")
                .header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.content[0].email").value("m@example.test"));
    mvc.perform(get("/api/v1/employees?q=%25").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/v1/employees?status=INACTIVE").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/v1/employees/departments").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("Engineering"));
  }

  @Test
  void pageAndSortGuards() throws Exception {
    String t = viewer();
    for (String q :
        List.of(
            "size=101",
            "page=-1",
            "page=100001",
            "sort=passwordHash",
            "direction=drop",
            "status=BAD",
            "size=abc"))
      mvc.perform(get("/api/v1/employees?" + q).header("Authorization", "Bearer " + t))
          .andExpect(status().isBadRequest());
  }

  @Test
  void malformedAndUnknownJsonRejected() throws Exception {
    String t = admin();
    mvc.perform(
            post("/api/v1/employees")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content("{bad"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"a@test.org\",\"password\":\"abc\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void loginGenericErrorsAndThrottle() throws Exception {
    for (int i = 0; i < 5; i++)
      mvc.perform(
              post("/api/v1/auth/login")
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(new Api.Login("unknown@example.test", PASSWORD))))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(new Api.Login("unknown@example.test", PASSWORD))))
        .andExpect(status().isTooManyRequests());
    jdbc.update("update login_attempts set window_start=now()-interval '16 minutes'");
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(new Api.Login("unknown@example.test", PASSWORD))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void disablingAccountRevokesExistingJwt() throws Exception {
    String v = viewer(), t = admin();
    mvc.perform(
            put("/api/v1/users/" + VIEWER)
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(new Api.UserUpdate(Role.VIEWER, false))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/employees").header("Authorization", "Bearer " + v))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(new Api.Login("viewer@example.test", PASSWORD))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void roleChangeAndPasswordResetRevokeTokens() throws Exception {
    String e = editor(), t = admin();
    mvc.perform(
            put("/api/v1/users/" + EDITOR)
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(new Api.UserUpdate(Role.VIEWER, true))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/employees").header("Authorization", "Bearer " + e))
        .andExpect(status().isUnauthorized());
    String v = viewer();
    mvc.perform(
            put("/api/v1/users/" + VIEWER + "/password")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(json.writeValueAsString(new Api.PasswordChange("New-Test-Passphrase!25"))))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/employees").header("Authorization", "Bearer " + v))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void accountCreationPasswordHashAndNoHashLeak() throws Exception {
    String t = admin();
    mvc.perform(
            post("/api/v1/users")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        new Api.NewUser("new@example.test", PASSWORD, Role.EDITOR))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    String stored =
        jdbc.queryForObject(
            "select password_hash from app_users where email='new@example.test'", String.class);
    assertTrue(passwords.matches(PASSWORD, stored));
    assertNotEquals(PASSWORD, stored);
    mvc.perform(get("/api/v1/users?size=1").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
  }

  @Test
  void shortAndOverBytePasswordsRejected() throws Exception {
    String t = admin();
    for (String p : List.of("short", "密".repeat(30)))
      mvc.perform(
              post("/api/v1/users")
                  .header("Authorization", "Bearer " + t)
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(new Api.NewUser("new@example.test", p, Role.VIEWER))))
          .andExpect(status().isBadRequest());
  }

  @Test
  void administratorCannotDisableOrDemoteSelf() throws Exception {
    String t = admin();
    for (Api.UserUpdate change :
        List.of(new Api.UserUpdate(Role.VIEWER, true), new Api.UserUpdate(Role.ADMIN, false)))
      mvc.perform(
              put("/api/v1/users/" + ADMIN)
                  .header("Authorization", "Bearer " + t)
                  .contentType("application/json")
                  .content(json.writeValueAsString(change)))
          .andExpect(status().isConflict());
  }

  @Test
  void expiredWrongAudienceAndTamperedTokensRejected() throws Exception {
    String valid = viewer();
    String tampered = valid.substring(0, valid.lastIndexOf('.') + 1) + "invalid";
    for (String value :
        List.of(
            tampered,
            signed(Instant.now().minusSeconds(300), "staffdesk-desktop"),
            signed(Instant.now().plusSeconds(300), "other-client")))
      mvc.perform(get("/api/v1/employees").header("Authorization", "Bearer " + value))
          .andExpect(status().isUnauthorized());
  }

  private String signed(Instant expires, String audience) {
    return jwt.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .issuer("staffdesk-api")
                    .subject(VIEWER.toString())
                    .audience(List.of(audience))
                    .issuedAt(Instant.now().minusSeconds(600))
                    .expiresAt(expires)
                    .claim("role", "VIEWER")
                    .claim("ver", 0)
                    .build()))
        .getTokenValue();
  }

  @Test
  void bodySizeLimit() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login").contentType("application/json").content("x".repeat(17000)))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void normalizationAndNoSqlInjection() throws Exception {
    String t = token(" ADMIN@EXAMPLE.TEST ");
    mvc.perform(
            get("/api/v1/employees")
                .param("q", "' OR 1=1 --")
                .header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void frameworkErrorsAndDeploymentIdentity() throws Exception {
    String t = admin();
    mvc.perform(get("/api/v1/version"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.application").value("staffdesk"));
    mvc.perform(patch("/api/v1/employees").header("Authorization", "Bearer " + t))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(
            post("/api/v1/employees")
                .header("Authorization", "Bearer " + t)
                .contentType("text/plain")
                .content("bad"))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(
            delete("/api/v1/employees/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/employees/not-a-uuid").header("Authorization", "Bearer " + t))
        .andExpect(status().isBadRequest());
  }

  @Test
  void everyAllowlistedSortExecutes() throws Exception {
    String t = admin();
    create(t, "a@example.test");
    for (String sort :
        List.of(
            "firstName",
            "lastName",
            "email",
            "department",
            "jobTitle",
            "hireDate",
            "status",
            "updatedAt"))
      mvc.perform(
              get("/api/v1/employees?sort=" + sort + "&direction=desc")
                  .header("Authorization", "Bearer " + t))
          .andExpect(status().isOk());
  }

  @Test
  void concurrentEditsCannotLoseUpdates() throws Exception {
    String t = admin();
    JsonNode created = create(t, "a@example.test");
    String url = "/api/v1/employees/" + created.get("id").asText();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      java.util.List<java.util.concurrent.Future<Integer>> results = new ArrayList<>();
      for (String email : List.of("one@example.test", "two@example.test"))
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          put(url)
                              .header("Authorization", "Bearer " + t)
                              .contentType("application/json")
                              .content(json.writeValueAsString(employee(email, 0L))))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      start.countDown();
      List<Integer> codes = new ArrayList<>();
      for (var f : results) codes.add(f.get(20, java.util.concurrent.TimeUnit.SECONDS));
      Collections.sort(codes);
      assertEquals(List.of(200, 409), codes);
    }
    assertEquals(2, jdbc.queryForObject("select count(*) from audit_events", Integer.class));
  }

  @Test
  void bootstrapIsExplicitAndIdempotent() throws Exception {
    jdbc.execute("truncate app_users,audit_events");
    // Obtain the transactional bean, not a hand-built service.
    bootstrapUsers.bootstrap("owner@example.test", PASSWORD);
    bootstrapUsers.bootstrap("ignored@example.test", PASSWORD);
    assertEquals(1, jdbc.queryForObject("select count(*) from app_users", Integer.class));
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(new Api.Login("owner@example.test", PASSWORD))))
        .andExpect(status().isOk());
  }

  @Autowired UserService bootstrapUsers;
}
