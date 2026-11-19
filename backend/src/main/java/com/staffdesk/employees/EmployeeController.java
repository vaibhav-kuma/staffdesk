package com.staffdesk.employees;

import com.staffdesk.contracts.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {
  private final EmployeeService service;

  public EmployeeController(EmployeeService s) {
    service = s;
  }

  @GetMapping
  public Api.Page<Api.Employee> search(
      @RequestParam(defaultValue = "") @Size(max = 200) String q,
      @RequestParam(defaultValue = "") @Size(max = 80) String department,
      @RequestParam(required = false) EmployeeStatus status,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "lastName") String sort,
      @RequestParam(defaultValue = "asc") String direction) {
    return service.search(q, department, status, page, size, sort, direction);
  }

  @GetMapping("/departments")
  public List<String> departments() {
    return service.departments();
  }

  @GetMapping("/{id}")
  public Api.Employee get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
  public ResponseEntity<Api.Employee> create(
      @RequestBody @Valid Api.EmployeeInput input, JwtAuthenticationToken auth) {
    var e = service.create(input, actor(auth));
    return ResponseEntity.created(java.net.URI.create("/api/v1/employees/" + e.id())).body(e);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
  public Api.Employee update(
      @PathVariable UUID id,
      @RequestBody @Valid Api.EmployeeInput input,
      JwtAuthenticationToken auth) {
    return service.update(id, input, actor(auth));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID id, @RequestParam @Min(0) long version, JwtAuthenticationToken auth) {
    service.delete(id, version, actor(auth));
  }

  private UUID actor(JwtAuthenticationToken a) {
    return UUID.fromString(a.getName());
  }
}
