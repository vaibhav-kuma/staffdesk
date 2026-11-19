package com.staffdesk.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecurityConfigTest {
  @Test
  void signingKeyMustBeBase64AndAtLeast256Bits() {
    var config = new SecurityConfig();
    assertThrows(IllegalArgumentException.class, () -> config.jwtKey("not base64!?"));
    assertThrows(
        IllegalArgumentException.class,
        () -> config.jwtKey(Base64.getEncoder().encodeToString(new byte[16])));
    assertEquals(
        "HmacSHA256",
        config.jwtKey(Base64.getEncoder().encodeToString(new byte[48])).getAlgorithm());
  }
}
