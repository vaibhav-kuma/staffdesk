package com.staffdesk.auth;

import com.staffdesk.contracts.Api;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService s) {
    service = s;
  }

  @PostMapping("/login")
  public Api.Token login(@RequestBody @Valid Api.Login input) {
    return service.login(input);
  }
}
