package com.clothmarket.repository;

import com.clothmarket.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p ORDER BY p.createdAt DESC")
    Page<Post> findAllOrderByCreatedAtDesc(Pageable pageable);

    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT p FROM Post p WHERE p.userId = :userId ORDER BY p.createdAt DESC")
    List<Post> findUserPosts(Long userId);

    // Find a post only if it belongs to the specified user
    Optional<Post> findByIdAndUserId(Long id, Long userId);

    /** Feed without posts from the given authors (blocked users). Pass a non-empty collection. */
    @Query(value = "SELECT p FROM Post p WHERE p.userId NOT IN :excluded ORDER BY p.createdAt DESC",
            countQuery = "SELECT COUNT(p) FROM Post p WHERE p.userId NOT IN :excluded")
    Page<Post> findFeedExcluding(@Param("excluded") Collection<Long> excluded, Pageable pageable);

    // ---- admin panel ----

    /**
     * Admin post list: ALL posts from ALL users (nothing is hidden), newest first, with the owner
     * loaded in the same query. {@code pattern} must already be lower-case and wrapped like "%text%"
     * ("%" lists everything); the caller strips LIKE wildcards from what the admin typed.
     * Matches description, cloth type, or the owner's name, email or mobile. The ordering is part of
     * the query, so pass an UNSORTED Pageable (PageRequest.of(page, size)).
     */
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.user u WHERE " +
            "LOWER(COALESCE(p.description, '')) LIKE :pattern " +
            "OR LOWER(COALESCE(p.clothType, '')) LIKE :pattern " +
            "OR LOWER(u.name) LIKE :pattern " +
            "OR LOWER(COALESCE(u.email, '')) LIKE :pattern " +
            "OR u.mobile LIKE :pattern " +
            "ORDER BY p.createdAt DESC, p.id DESC",
            countQuery = "SELECT COUNT(p) FROM Post p JOIN p.user u WHERE " +
                    "LOWER(COALESCE(p.description, '')) LIKE :pattern " +
                    "OR LOWER(COALESCE(p.clothType, '')) LIKE :pattern " +
                    "OR LOWER(u.name) LIKE :pattern " +
                    "OR LOWER(COALESCE(u.email, '')) LIKE :pattern " +
                    "OR u.mobile LIKE :pattern")
    Page<Post> searchForAdmin(@Param("pattern") String pattern, Pageable pageable);

    /** One post with its owner loaded (the owner is a lazy relation). */
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id = :id")
    Optional<Post> findByIdWithOwner(@Param("id") Long id);

    /** Number of posts a user has published (shown on the admin user page). */
    long countByUserId(Long userId);

    // ---- account deletion helpers ----
    @Query("SELECT p.mediaUrl FROM Post p WHERE p.userId = :userId AND p.mediaUrl IS NOT NULL")
    List<String> findMediaUrlsByUser(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Post p WHERE p.userId = :userId")
    int deleteAllByUser(@Param("userId") Long userId);
}