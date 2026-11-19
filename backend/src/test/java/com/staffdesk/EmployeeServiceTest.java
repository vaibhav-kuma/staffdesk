package com.staffdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.staffdesk.common.*;
import com.staffdesk.employees.*;
import org.junit.jupiter.api.Test;

class EmployeeServiceTest {
  @Test
  void unknownSortRejectedBeforeDatabaseAccess() {
    var repo = mock(EmployeeRepository.class);
    var service = new EmployeeService(repo, mock(Audit.class));
    assertThrows(ApiException.class, () -> service.search("", "", null, 0, 25, "secret", "asc"));
    verifyNoInteractions(repo);
  }

  @Test
  void missingEmployeeHasDomainError() {
    var repo = mock(EmployeeRepository.class);
    var service = new EmployeeService(repo, mock(Audit.class));
    var e = assertThrows(ApiException.class, () -> service.get(java.util.UUID.randomUUID()));
    assertEquals(404, e.status.value());
  }
}
