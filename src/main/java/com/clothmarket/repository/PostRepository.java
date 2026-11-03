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

    // ---- account deletion helpers ----
    @Query("SELECT p.mediaUrl FROM Post p WHERE p.userId = :userId AND p.mediaUrl IS NOT NULL")
    List<String> findMediaUrlsByUser(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Post p WHERE p.userId = :userId")
    int deleteAllByUser(@Param("userId") Long userId);
}
