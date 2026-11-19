package com.staffdesk.config;

import com.staffdesk.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class Bootstrap implements ApplicationRunner {
  private final UserService users;
  private final String email, password;

  public Bootstrap(
      UserService u,
      @Value("${app.bootstrap-email}") String e,
      @Value("${app.bootstrap-password}") String p) {
    users = u;
    email = e;
    password = p;
  }

  public void run(ApplicationArguments a) {
    users.bootstrap(email, password);
  }
}
