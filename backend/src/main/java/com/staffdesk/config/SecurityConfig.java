package com.staffdesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffdesk.common.RequestFilter;
import com.staffdesk.users.*;
import java.util.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.*;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKey jwtKey(@Value("${app.jwt-secret}") String s) {
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(s);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("JWT_SECRET must be Base64", e);
    }
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT_SECRET must decode to at least 32 random bytes");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey key) {
    return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(
      SecretKey key,
      UserRepository users,
      @Value("${app.jwt-issuer}") String issuer,
      @Value("${app.jwt-audience}") String audience) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> account =
        jwt -> {
          try {
            if (!jwt.getAudience().contains(audience)
                || jwt.getExpiresAt() == null
                || jwt.getIssuedAt() == null) return invalid();
            var u = users.findById(UUID.fromString(jwt.getSubject()));
            Number version = jwt.getClaim("ver");
            return u.isPresent()
                    && u.get().isEnabled()
                    && version != null
                    && u.get().getTokenVersion() == version.longValue()
                    && u.get().getRole().name().equals(jwt.getClaimAsString("role"))
                ? OAuth2TokenValidatorResult.success()
                : invalid();
          } catch (IllegalArgumentException | ClassCastException e) {
            return invalid();
          }
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), account));
    return decoder;
  }

  private static OAuth2TokenValidatorResult invalid() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Session is no longer valid", null));
  }

  @Bean
  FilterRegistrationBean<RequestFilter> disableAutoRegistration(RequestFilter f) {
    var b = new FilterRegistrationBean<>(f);
    b.setEnabled(false);
    return b;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      RequestFilter filter,
      ObjectMapper mapper,
      @Value("${springdoc.api-docs.enabled}") boolean docs)
      throws Exception {
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("role");
    roles.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    http.csrf(c -> c.disable())
        .cors(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .addFilterBefore(filter, SecurityContextHolderFilter.class)
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            docs
                                ? "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'"
                                : "default-src 'none'; frame-ancestors 'none'")))
        .authorizeHttpRequests(
            a -> {
              a.requestMatchers(HttpMethod.GET, "/api/v1/version").permitAll();
              a.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
              a.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                  .permitAll();
              if (docs)
                a.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              a.requestMatchers("/actuator/**").hasRole("ADMIN");
              a.requestMatchers("/api/v1/**").authenticated();
              a.anyRequest().denyAll();
            })
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(
                        (q, s, e) ->
                            writeProblem(
                                mapper, q, s, 401, "Authentication required or session expired.")))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (q, s, x) -> writeProblem(mapper, q, s, 401, "Authentication required."))
                    .accessDeniedHandler(
                        (q, s, x) ->
                            writeProblem(mapper, q, s, 403, "This action is not permitted.")));
    return http.build();
  }

  private static void writeProblem(
      ObjectMapper m,
      jakarta.servlet.http.HttpServletRequest q,
      jakarta.servlet.http.HttpServletResponse s,
      int status,
      String detail)
      throws java.io.IOException {
    s.setStatus(status);
    s.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    s.setHeader("Cache-Control", "no-store");
    if (status == 401) s.setHeader("WWW-Authenticate", "Bearer");
    m.writeValue(
        s.getOutputStream(),
        Map.of(
            "status",
            status,
            "detail",
            detail,
            "requestId",
            String.valueOf(q.getAttribute("requestId"))));
  }
}
