package com.staffdesk.contracts;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

/** Immutable HTTP boundary types. Entities never leave the backend. */
public final class Api {
  private Api() {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
    public Login {
      if (email != null) email = email.strip();
    }
  }

  public record Token(
      String accessToken, Instant expiresAt, UUID userId, String email, Role role) {}

  public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

  public record EmployeeInput(
      @NotBlank @Size(max = 80) String firstName,
      @NotBlank @Size(max = 80) String lastName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 80) String department,
      @NotBlank @Size(max = 120) String jobTitle,
      @Size(max = 32) @Pattern(regexp = "[+0-9() .-]*") String phone,
      @NotNull @PastOrPresent LocalDate hireDate,
      @NotNull EmployeeStatus status,
      @PositiveOrZero Long version) {}

  public record Employee(
      UUID id,
      String firstName,
      String lastName,
      String email,
      String department,
      String jobTitle,
      String phone,
      LocalDate hireDate,
      EmployeeStatus status,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record NewUser(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotNull Role role) {}

  public record UserUpdate(@NotNull Role role, boolean enabled) {}

  public record PasswordChange(@NotBlank @Size(min = 12, max = 72) String password) {}

  public record User(UUID id, String email, Role role, boolean enabled, Instant createdAt) {}
}
