package com.staffdesk.auth;

import com.staffdesk.common.ApiException;
import com.staffdesk.contracts.Api;
import com.staffdesk.users.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final LoginThrottle throttle;
  private final String issuer, audience, dummy;
  private final long minutes;

  public AuthService(
      UserRepository u,
      PasswordEncoder p,
      JwtEncoder e,
      LoginThrottle t,
      @Value("${app.jwt-issuer}") String i,
      @Value("${app.jwt-audience}") String a,
      @Value("${app.token-minutes}") long m) {
    users = u;
    passwords = p;
    encoder = e;
    throttle = t;
    issuer = i;
    audience = a;
    minutes = m;
    dummy = p.encode(UUID.randomUUID().toString());
    if (m < 1 || m > 30)
      throw new IllegalArgumentException("TOKEN_MINUTES must be between 1 and 30");
  }

  public Api.Token login(Api.Login input) {
    String email = UserService.normalize(input.email());
    if (!throttle.allow(email))
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Wait 15 minutes.");
    if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes.");
    var user = users.findByEmail(email);
    boolean matches =
        passwords.matches(input.password(), user.map(AppUser::getPasswordHash).orElse(dummy));
    if (user.isEmpty() || !matches || !user.get().isEnabled())
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    AppUser u = user.get();
    Instant now = Instant.now(), expires = now.plusSeconds(minutes * 60);
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(u.getId().toString())
            .audience(List.of(audience))
            .issuedAt(now)
            .expiresAt(expires)
            .id(UUID.randomUUID().toString())
            .claim("role", u.getRole().name())
            .claim("ver", u.getTokenVersion())
            .build();
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build(),
                    claims))
            .getTokenValue();
    return new Api.Token(token, expires, u.getId(), u.getEmail(), u.getRole());
  }
}
