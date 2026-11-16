package com.clothmarket.repository;

import com.clothmarket.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * True when the user exists and is not blocked by an administrator. Used on every authenticated
     * request, so a blocked account's JWT stops working immediately. A NULL "blocked" value (a row
     * created before the column existed) counts as not blocked.
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u " +
            "WHERE u.id = :id AND (u.blocked IS NULL OR u.blocked = false)")
    boolean existsActiveById(@Param("id") Long id);

    /**
     * Admin user list. {@code pattern} must already be lower-case and wrapped like "%text%"
     * (use "%" to list everyone); the caller strips LIKE wildcards from what the admin typed.
     * Matches name, email, mobile or location. Sorting comes from the Pageable.
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.name) LIKE :pattern " +
            "OR LOWER(COALESCE(u.email, '')) LIKE :pattern " +
            "OR u.mobile LIKE :pattern " +
            "OR LOWER(u.location) LIKE :pattern")
    Page<User> searchForAdmin(@Param("pattern") String pattern, Pageable pageable);
}