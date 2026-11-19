package com.staffdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;

/** Idempotent maintenance is safe when run concurrently by multiple replicas. */
@Configuration
@EnableScheduling
public class Maintenance {
  private final JdbcTemplate jdbc;

  public Maintenance(JdbcTemplate j) {
    jdbc = j;
  }

  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
  public void expireLoginCounters() {
    jdbc.update("delete from login_attempts where window_start < now()-interval '1 day'");
  }
}
