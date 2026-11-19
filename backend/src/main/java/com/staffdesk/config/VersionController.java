package com.staffdesk.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Public, non-sensitive deployment identity prevents CI accepting an old healthy replica. */
@RestController
public class VersionController {
  private final String revision;

  public VersionController(@Value("${APP_REVISION:${RENDER_GIT_COMMIT:development}}") String r) {
    revision = r;
  }

  @GetMapping("/api/v1/version")
  public Map<String, String> version() {
    return Map.of("application", "staffdesk", "revision", revision);
  }
}
