package com.staffdesk.employees;

import com.staffdesk.common.*;
import com.staffdesk.contracts.*;
import com.staffdesk.users.UserService;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmployeeService {
  private final EmployeeRepository repo;
  private final Audit audit;
  private static final Map<String, String> SORTS =
      Map.of(
          "lastName",
          "last_name",
          "firstName",
          "first_name",
          "email",
          "email",
          "department",
          "department",
          "jobTitle",
          "job_title",
          "hireDate",
          "hire_date",
          "status",
          "status",
          "updatedAt",
          "updated_at");

  public EmployeeService(EmployeeRepository r, Audit a) {
    repo = r;
    audit = a;
  }

  @Transactional(readOnly = true)
  public Api.Page<Api.Employee> search(
      String query,
      String department,
      EmployeeStatus status,
      int page,
      int size,
      String field,
      String direction) {
    if (!SORTS.containsKey(field) || !(direction.equals("asc") || direction.equals("desc")))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported sort field or direction.");
    String q = query.strip().toLowerCase(Locale.ROOT);
    String pattern = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    Sort sort = Sort.by(Sort.Direction.fromString(direction), SORTS.get(field)).and(Sort.by("id"));
    var p =
        repo.search(
            q,
            pattern,
            department.strip(),
            status == null ? "" : status.name(),
            PageRequest.of(page, size, sort));
    return new Api.Page<>(
        p.map(EmployeeService::view).getContent(),
        page,
        size,
        p.getTotalElements(),
        p.getTotalPages());
  }

  @Transactional(readOnly = true)
  public Api.Employee get(UUID id) {
    return view(entity(id));
  }

  @Transactional(readOnly = true)
  public List<String> departments() {
    return repo.departments();
  }

  public Api.Employee create(Api.EmployeeInput input, UUID actor) {
    if (input.version() != null)
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "Version must be omitted when creating an employee.");
    EmployeeEntity e = new EmployeeEntity();
    e.id = UUID.randomUUID();
    apply(e, input);
    repo.saveAndFlush(e);
    audit.record(actor, "EMPLOYEE_CREATED", e.id);
    return view(e);
  }

  public Api.Employee update(UUID id, Api.EmployeeInput input, UUID actor) {
    EmployeeEntity e = entity(id);
    checkVersion(e, input.version());
    apply(e, input);
    repo.flush();
    audit.record(actor, "EMPLOYEE_UPDATED", id);
    return view(e);
  }

  public void delete(UUID id, long version, UUID actor) {
    EmployeeEntity e = entity(id);
    checkVersion(e, version);
    repo.delete(e);
    repo.flush();
    audit.record(actor, "EMPLOYEE_DELETED", id);
  }

  private EmployeeEntity entity(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found."));
  }

  private void checkVersion(EmployeeEntity e, Long version) {
    if (version == null)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Version is required for updates.");
    if (e.version != version)
      throw new ApiException(
          HttpStatus.CONFLICT, "Employee changed since it was opened. Reload before editing.");
  }

  private static void apply(EmployeeEntity e, Api.EmployeeInput i) {
    e.firstName = i.firstName().strip();
    e.lastName = i.lastName().strip();
    e.email = UserService.normalize(i.email());
    e.department = i.department().strip();
    e.jobTitle = i.jobTitle().strip();
    e.phone = i.phone() == null ? null : i.phone().strip();
    e.hireDate = i.hireDate();
    e.status = i.status();
  }

  private static Api.Employee view(EmployeeEntity e) {
    return new Api.Employee(
        e.id,
        e.firstName,
        e.lastName,
        e.email,
        e.department,
        e.jobTitle,
        e.phone,
        e.hireDate,
        e.status,
        e.version,
        e.createdAt,
        e.updatedAt);
  }
}
