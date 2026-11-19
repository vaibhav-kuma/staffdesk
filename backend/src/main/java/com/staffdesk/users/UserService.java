package com.staffdesk.users;

import com.staffdesk.common.*;
import com.staffdesk.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {
  private final UserRepository repo;
  private final PasswordEncoder encoder;
  private final Audit audit;
  private final JdbcTemplate jdbc;

  public UserService(UserRepository r, PasswordEncoder p, Audit a, JdbcTemplate j) {
    repo = r;
    encoder = p;
    audit = a;
    jdbc = j;
  }

  public static String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  public Api.User create(Api.NewUser input, UUID actor) {
    lock();
    checkPassword(input.password());
    AppUser u = new AppUser();
    u.id = UUID.randomUUID();
    u.email = normalize(input.email());
    u.passwordHash = encoder.encode(input.password());
    u.role = input.role();
    u.enabled = true;
    u.createdAt = Instant.now();
    repo.saveAndFlush(u);
    audit.record(actor == null ? u.id : actor, "USER_CREATED", u.id);
    return view(u);
  }

  public void bootstrap(String email, String password) {
    lock();
    if (repo.countByRoleAndEnabledTrue(Role.ADMIN) > 0) return;
    if (email.isBlank() && password.isBlank()) return;
    if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 254)
      throw new IllegalStateException("Invalid bootstrap email");
    if (password.length() < 12)
      throw new IllegalStateException("Bootstrap password must have at least 12 characters");
    create(new Api.NewUser(email, password, Role.ADMIN), null);
  }

  @Transactional(readOnly = true)
  public Api.Page<Api.User> list(int page, int size) {
    var p =
        repo.findAll(PageRequest.of(page, size, Sort.by("email").ascending().and(Sort.by("id"))));
    return new Api.Page<>(
        p.map(UserService::view).getContent(), page, size, p.getTotalElements(), p.getTotalPages());
  }

  public Api.User update(UUID id, Api.UserUpdate input, UUID actor) {
    lock();
    AppUser u = get(id);
    if (id.equals(actor) && (!input.enabled() || input.role() != Role.ADMIN))
      throw new ApiException(
          HttpStatus.CONFLICT, "You cannot disable or demote your own administrator account.");
    if (u.role == Role.ADMIN
        && u.enabled
        && (!input.enabled() || input.role() != Role.ADMIN)
        && repo.countByRoleAndEnabledTrue(Role.ADMIN) <= 1)
      throw new ApiException(
          HttpStatus.CONFLICT, "At least one enabled administrator is required.");
    u.role = input.role();
    u.enabled = input.enabled();
    u.tokenVersion++;
    audit.record(actor, "USER_UPDATED", id);
    return view(u);
  }

  public void resetPassword(UUID id, String password, UUID actor) {
    lock();
    checkPassword(password);
    AppUser u = get(id);
    u.passwordHash = encoder.encode(password);
    u.tokenVersion++;
    audit.record(actor, "PASSWORD_RESET", id);
  }

  private void lock() {
    jdbc.execute("select pg_advisory_xact_lock(791318245)");
  }

  private AppUser get(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
  }

  private static void checkPassword(String p) {
    if (p.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes.");
  }

  private static Api.User view(AppUser u) {
    return new Api.User(u.id, u.email, u.role, u.enabled, u.createdAt);
  }
}
