package com.staffdesk.common;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Audit {
  private final JdbcTemplate jdbc;

  public Audit(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void record(UUID actor, String action, UUID entity) {
    jdbc.update(
        "insert into audit_events(actor_id,action,entity_id) values (?,?,?)",
        actor,
        action,
        entity);
  }
}
