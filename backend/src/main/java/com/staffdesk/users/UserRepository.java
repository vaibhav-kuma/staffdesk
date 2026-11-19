package com.staffdesk.users;

import com.staffdesk.contracts.Role;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByEmail(String email);

  long countByRoleAndEnabledTrue(Role role);
}
