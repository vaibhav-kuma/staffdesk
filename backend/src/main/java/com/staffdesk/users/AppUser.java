package com.staffdesk.users;

import com.staffdesk.contracts.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
  @Id UUID id;

  @Column(nullable = false, unique = true)
  String email;

  @Column(nullable = false)
  String passwordHash;

  @Enumerated(EnumType.STRING)
  Role role;

  boolean enabled;
  long tokenVersion;
  Instant createdAt;

  protected AppUser() {}

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public long getTokenVersion() {
    return tokenVersion;
  }
}
