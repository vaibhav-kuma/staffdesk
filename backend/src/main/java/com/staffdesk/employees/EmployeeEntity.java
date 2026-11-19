package com.staffdesk.employees;

import com.staffdesk.contracts.EmployeeStatus;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "employees")
public class EmployeeEntity {
  @Id UUID id;
  String firstName, lastName, email, department, jobTitle, phone;
  LocalDate hireDate;

  @Enumerated(EnumType.STRING)
  EmployeeStatus status;

  @Version long version;
  Instant createdAt, updatedAt;

  protected EmployeeEntity() {}

  @PrePersist
  void created() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void updated() {
    updatedAt = Instant.now();
  }
}
