package com.staffdesk.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Counts every attempt atomically before BCrypt. Five attempts per identity per 15 minutes. */
@Component
public class LoginThrottle {
  private final JdbcTemplate jdbc;

  public LoginThrottle(JdbcTemplate j) {
    jdbc = j;
  }

  public boolean allow(String identity) {
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(identity.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    Integer n =
        jdbc.queryForObject(
            """
   insert into login_attempts(identity_hash,attempts,window_start) values (?,1,now())
   on conflict(identity_hash) do update set
    attempts=case when login_attempts.window_start < now()-interval '15 minutes' then 1 else least(login_attempts.attempts+1,6) end,
    window_start=case when login_attempts.window_start < now()-interval '15 minutes' then now() else login_attempts.window_start end
   returning attempts
   """,
            Integer.class,
            hash);
    return n != null && n <= 5;
  }
}
