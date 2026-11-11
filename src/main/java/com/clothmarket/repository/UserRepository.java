package com.clothmarket.repository;

import com.clothmarket.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMobile(String mobile);
    boolean existsByMobile(String mobile);

    // Email is stored trimmed and lower-case (User.setEmail), so callers must pass a
    // normalised address (trim + lower-case) for these exact-match lookups.
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    List<User> findTop10ByNameContainingIgnoreCase(String name);
}