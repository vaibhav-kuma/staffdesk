package com.staffdesk.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestFilter.class);

  @Override
  protected void doFilterInternal(HttpServletRequest q, HttpServletResponse s, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    q.setAttribute("requestId", id);
    s.setHeader("X-Request-ID", id);
    long start = System.nanoTime();
    try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", id)) {
      HttpServletRequest request = q;
      if (Set.of("POST", "PUT", "PATCH").contains(q.getMethod())) {
        byte[] body = q.getInputStream().readNBytes(16385);
        if (body.length > 16384) {
          s.setStatus(413);
          s.setContentType("application/problem+json");
          s.getWriter()
              .write(
                  "{\"status\":413,\"detail\":\"Request body too large.\",\"requestId\":\""
                      + id
                      + "\"}");
          return;
        }
        request =
            new HttpServletRequestWrapper(q) {
              @Override
              public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                  public int read() {
                    return input.read();
                  }

                  public boolean isFinished() {
                    return input.available() == 0;
                  }

                  public boolean isReady() {
                    return true;
                  }

                  public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException(
                        "Async request streaming is not supported");
                  }
                };
              }

              @Override
              public BufferedReader getReader() {
                return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
              }
            };
      }
      chain.doFilter(request, s);
    } finally {
      log.info(
          "http method={} path={} status={} elapsedMs={} requestId={}",
          q.getMethod(),
          q.getRequestURI(),
          s.getStatus(),
          (System.nanoTime() - start) / 1_000_000,
          id);
    }
  }
}
