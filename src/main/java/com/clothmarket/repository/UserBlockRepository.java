package com.clothmarket.repository;

import com.clothmarket.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    /** Everyone the user blocked OR who blocked the user. */
    @Query("SELECT CASE WHEN b.blockerId = :userId THEN b.blockedId ELSE b.blockerId END " +
            "FROM UserBlock b WHERE b.blockerId = :userId OR b.blockedId = :userId")
    List<Long> findRelatedUserIds(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserBlock b WHERE b.blockerId = :userId OR b.blockedId = :userId")
    int deleteAllInvolving(@Param("userId") Long userId);
}
