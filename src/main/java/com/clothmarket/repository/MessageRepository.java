package com.clothmarket.repository;

import com.clothmarket.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // id is the tie-breaker so messages created in the same instant keep a stable order
    @Query("SELECT m FROM Message m WHERE " +
            "(m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
            "(m.senderId = :userId2 AND m.receiverId = :userId1) " +
            "ORDER BY m.createdAt ASC, m.id ASC")
    List<Message> findChatMessages(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    @Query("SELECT DISTINCT CASE " +
            "WHEN m.senderId = :userId THEN m.receiverId " +
            "ELSE m.senderId END " +
            "FROM Message m WHERE m.senderId = :userId OR m.receiverId = :userId")
    List<Long> findChatUserIds(@Param("userId") Long userId);

    @Query("SELECT m FROM Message m WHERE " +
            "(m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
            "(m.senderId = :userId2 AND m.receiverId = :userId1) " +
            "ORDER BY m.createdAt DESC, m.id DESC")
    List<Message> findChatMessagesDesc(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /** Same as above but paged, so the inbox can fetch only the newest message of a chat. */
    @Query("SELECT m FROM Message m WHERE " +
            "(m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
            "(m.senderId = :userId2 AND m.receiverId = :userId1) " +
            "ORDER BY m.createdAt DESC, m.id DESC")
    List<Message> findLatestInChat(@Param("userId1") Long userId1,
                                   @Param("userId2") Long userId2,
                                   Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE " +
            "m.senderId = :fromUserId AND m.receiverId = :toUserId AND m.isRead = false")
    long countUnreadInChat(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiverId = :userId AND m.isRead = false")
    long countTotalUnread(@Param("userId") Long userId);

    /** Total unread, ignoring messages from the given senders (blocked users the inbox hides). */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiverId = :userId AND m.isRead = false " +
            "AND m.senderId NOT IN :excludedSenderIds")
    long countTotalUnreadExcludingSenders(@Param("userId") Long userId,
                                          @Param("excludedSenderIds") java.util.Collection<Long> excludedSenderIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Message m SET m.isRead = true WHERE " +
            "m.senderId = :fromUserId AND m.receiverId = :toUserId AND m.isRead = false")
    void markAsRead(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);

    // ---- account deletion helpers ----
    @Query("SELECT m.voiceUrl FROM Message m WHERE (m.senderId = :userId OR m.receiverId = :userId) " +
            "AND m.voiceUrl IS NOT NULL")
    List<String> findVoiceUrlsInvolving(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Message m WHERE m.senderId = :userId OR m.receiverId = :userId")
    int deleteAllInvolving(@Param("userId") Long userId);
}