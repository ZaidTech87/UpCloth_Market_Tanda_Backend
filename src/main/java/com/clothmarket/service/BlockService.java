package com.clothmarket.service;

import com.clothmarket.dto.PublicUser;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.User;
import com.clothmarket.model.UserBlock;
import com.clothmarket.repository.UserBlockRepository;
import com.clothmarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Blocking is symmetric in effect: if A blocks B, neither can message, call,
 * search or see the other's posts. Only A can see/undo the block.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository blocks;
    private final UserRepository users;

    public void block(Long blockerId, Long targetId) {
        if (targetId == null || targetId.equals(blockerId)) {
            throw ApiException.badRequest("You cannot block yourself");
        }
        if (!users.existsById(targetId)) {
            throw ApiException.notFound("User not found");
        }
        if (blocks.existsByBlockerIdAndBlockedId(blockerId, targetId)) {
            return; // idempotent
        }
        try {
            UserBlock b = new UserBlock();
            b.setBlockerId(blockerId);
            b.setBlockedId(targetId);
            blocks.save(b);
        } catch (DataIntegrityViolationException e) {
            // concurrent duplicate: the unique constraint already guarantees the block exists
        }
    }

    public void unblock(Long blockerId, Long targetId) {
        blocks.findByBlockerIdAndBlockedId(blockerId, targetId).ifPresent(blocks::delete);
    }

    public List<PublicUser> listBlocked(Long blockerId) {
        List<UserBlock> mine = blocks.findByBlockerIdOrderByCreatedAtDesc(blockerId);
        Map<Long, User> byId = users.findAllById(
                        mine.stream().map(UserBlock::getBlockedId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return mine.stream()
                .map(b -> byId.get(b.getBlockedId()))
                .filter(u -> u != null)
                .map(PublicUser::from)
                .toList();
    }

    /** True if either user has blocked the other. */
    public boolean isBlockedEitherWay(Long a, Long b) {
        if (a == null || b == null) {
            return false;
        }
        return blocks.existsByBlockerIdAndBlockedId(a, b) || blocks.existsByBlockerIdAndBlockedId(b, a);
    }

    /** Ids of every user the given user may not interact with (either direction). */
    public Set<Long> relatedIds(Long userId) {
        return new HashSet<>(blocks.findRelatedUserIds(userId));
    }
}
