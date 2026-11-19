package com.staffdesk.employees;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface EmployeeRepository extends JpaRepository<EmployeeEntity, UUID> {
  @Query(
      value =
          """
  select * from employees where
   (:q = '' or lower(first_name || ' ' || last_name || ' ' || email) like :pattern escape '!')
   and (:department = '' or department = :department) and (:status = '' or status = :status)
  """,
      countQuery =
          """
  select count(*) from employees where
   (:q = '' or lower(first_name || ' ' || last_name || ' ' || email) like :pattern escape '!')
   and (:department = '' or department = :department) and (:status = '' or status = :status)
  """,
      nativeQuery = true)
  Page<EmployeeEntity> search(
      String q, String pattern, String department, String status, Pageable pageable);

  @Query("select distinct e.department from EmployeeEntity e order by e.department")
  List<String> departments();
}
