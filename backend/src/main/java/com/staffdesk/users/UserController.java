package com.staffdesk.users;

import com.staffdesk.contracts.Api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
  private final UserService service;

  public UserController(UserService s) {
    service = s;
  }

  @GetMapping
  public Api.Page<Api.User> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Api.User create(@Valid @RequestBody Api.NewUser input, JwtAuthenticationToken auth) {
    return service.create(input, UUID.fromString(auth.getName()));
  }

  @PutMapping("/{id}")
  public Api.User update(
      @PathVariable UUID id,
      @Valid @RequestBody Api.UserUpdate input,
      JwtAuthenticationToken auth) {
    return service.update(id, input, UUID.fromString(auth.getName()));
  }

  @PutMapping("/{id}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(
      @PathVariable UUID id,
      @Valid @RequestBody Api.PasswordChange input,
      JwtAuthenticationToken auth) {
    service.resetPassword(id, input.password(), UUID.fromString(auth.getName()));
  }
}
