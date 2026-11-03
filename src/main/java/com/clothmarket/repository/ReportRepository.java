package com.clothmarket.repository;

import com.clothmarket.model.Report;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findFirstByReporterIdAndTargetTypeAndTargetIdAndStatus(
            Long reporterId, String targetType, Long targetId, String status);

    long countByReporterIdAndCreatedAtAfter(Long reporterId, LocalDateTime since);

    List<Report> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    /** Open reports that point at this post (resolved together when the post is removed). */
    List<Report> findByTargetTypeAndTargetIdAndStatus(String targetType, Long targetId, String status);

    /** Reports filed BY a deleted account are not needed any more. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Report r WHERE r.reporterId = :userId")
    int deleteByReporter(@Param("userId") Long userId);
}
