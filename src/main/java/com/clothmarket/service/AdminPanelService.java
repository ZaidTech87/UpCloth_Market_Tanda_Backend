package com.clothmarket.service;

import com.clothmarket.dto.AdminMeResponse;
import com.clothmarket.dto.AdminPostResponse;
import com.clothmarket.dto.AdminUserResponse;
import com.clothmarket.dto.AdminUserUpdateRequest;
import com.clothmarket.dto.PageResponse;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.AdminAuditLog;
import com.clothmarket.model.Post;
import com.clothmarket.model.User;
import com.clothmarket.repository.PostRepository;
import com.clothmarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Everything the admin panel can do to posts and users. Callers (AdminPanelController) have
 * ALREADY verified the caller is a Super Admin and pass that admin's id in; the rules that protect
 * accounts are enforced here, whatever the client sends:
 *  - a Super Admin account can never be blocked or deleted through this API
 *  - an admin can never block or delete their own account
 *  - only name, location, email and mobile can be edited (never role, blocked flag or password)
 *  - another Super Admin's email/mobile (their login identity) cannot be changed
 * Every destructive action is written to the audit log after it succeeds.
 */
@Service
@RequiredArgsConstructor
public class AdminPanelService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern MOBILE_FORMAT = Pattern.compile("^[0-9]{10,15}$");

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final AccountDeletionService accountDeletionService;
    private final AdminAccess adminAccess;
    private final AdminAuditService audit;

    // ------------------------------------------------------------------ who am I

    @Transactional(readOnly = true)
    public AdminMeResponse me(Long adminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return new AdminMeResponse(user.getId(), user.getName(), user.getEmail(), "SUPER_ADMIN");
    }

    // ------------------------------------------------------------------ posts

    @Transactional(readOnly = true)
    public PageResponse<AdminPostResponse> listPosts(String q, int page, int size) {
        // The newest-first ordering is part of the query, so the Pageable carries no sort.
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return PageResponse.of(postRepository.searchForAdmin(searchPattern(q), pageable),
                AdminPostResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminPostResponse getPost(Long postId) {
        return AdminPostResponse.from(postRepository.findByIdWithOwner(postId)
                .orElseThrow(() -> ApiException.notFound("Post not found")));
    }

    /** Deletes the post row and (best effort) its R2 media, exactly like moderator removal. */
    public void deletePost(Long adminId, Long postId, String reason) {
        Post post = postRepository.findByIdWithOwner(postId)
                .orElseThrow(() -> ApiException.notFound("Post not found"));
        Long ownerId = post.getUserId();
        String mediaType = post.getMediaType();

        postService.removePostAsModerator(postId);

        audit.record(adminId, AdminAuditLog.POST_DELETE, "POST", postId, reason,
                "ownerId=" + ownerId + " mediaType=" + mediaType);
    }

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String q, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(userRepository.searchForAdmin(searchPattern(q), pageable),
                user -> AdminUserResponse.from(user, adminAccess.isSuperAdmin(user.getId()), null));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long userId) {
        return toDetail(findUser(userId));
    }

    /**
     * Edits name, location, email and mobile. Blank email/mobile means "leave unchanged".
     * Everything is validated BEFORE anything on the entity is touched.
     */
    @Transactional
    public AdminUserResponse updateUser(Long adminId, Long userId, AdminUserUpdateRequest request) {
        User user = findUser(userId);

        String newName = request.getName().trim();
        String newLocation = request.getLocation().trim();
        String newEmail = AuthService.normalizeEmail(request.getEmail());
        String newMobile = request.getMobile() == null ? "" : request.getMobile().trim();

        boolean nameChanged = !newName.equals(user.getName());
        boolean locationChanged = !newLocation.equals(user.getLocation());
        boolean emailChanged = !newEmail.isEmpty()
                && !newEmail.equals(AuthService.normalizeEmail(user.getEmail()));
        boolean mobileChanged = !newMobile.isEmpty() && !newMobile.equals(user.getMobile());

        if (emailChanged || mobileChanged) {
            // Email and mobile are a person's login identity: another Super Admin's is off limits.
            if (adminAccess.isSuperAdmin(userId) && !adminId.equals(userId)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "The email or mobile number of another Super Admin cannot be changed.");
            }
        }
        if (emailChanged) {
            if (newEmail.length() > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.matcher(newEmail).matches()) {
                throw ApiException.badRequest("Enter a valid email address");
            }
            if (userRepository.existsByEmail(newEmail)) {
                throw new ApiException(HttpStatus.CONFLICT, "That email is already used by another account.");
            }
        }
        if (mobileChanged) {
            if (!MOBILE_FORMAT.matcher(newMobile).matches()) {
                throw ApiException.badRequest("Enter a valid mobile number (10-15 digits)");
            }
            if (userRepository.existsByMobile(newMobile)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "That mobile number is already used by another account.");
            }
        }

        List<String> changed = new ArrayList<>();
        if (nameChanged) {
            user.setName(newName);
            changed.add("name");
        }
        if (locationChanged) {
            user.setLocation(newLocation);
            changed.add("location");
        }
        if (emailChanged) {
            user.setEmail(newEmail);
            changed.add("email");
        }
        if (mobileChanged) {
            user.setMobile(newMobile);
            changed.add("mobile");
        }
        if (changed.isEmpty()) {
            return toDetail(user); // nothing to save, nothing to audit
        }

        User saved;
        try {
            // Flush now so a unique-index clash (two simultaneous edits) surfaces here as a clean 409.
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "That email or mobile number is already used by another account.");
        }

        // Field NAMES only: the audit log never stores old or new personal values.
        audit.record(adminId, AdminAuditLog.USER_UPDATE, "USER", userId, null,
                "fields=" + String.join(",", changed));
        return toDetail(saved);
    }

    @Transactional
    public AdminUserResponse blockUser(Long adminId, Long userId, String reason) {
        User user = findUser(userId);
        requireNotProtected(adminId, userId, "blocked");
        if (Boolean.TRUE.equals(user.getBlocked())) {
            return toDetail(user); // already blocked: nothing to do, nothing to audit
        }
        user.setBlocked(true);
        User saved = userRepository.save(user);

        audit.record(adminId, AdminAuditLog.USER_BLOCK, "USER", userId, reason, null);
        return toDetail(saved);
    }

    @Transactional
    public AdminUserResponse unblockUser(Long adminId, Long userId) {
        User user = findUser(userId);
        if (!Boolean.TRUE.equals(user.getBlocked())) {
            return toDetail(user);
        }
        user.setBlocked(false);
        User saved = userRepository.save(user);

        audit.record(adminId, AdminAuditLog.USER_UNBLOCK, "USER", userId, null, null);
        return toDetail(saved);
    }

    /** Permanent deletion of the account and its data (same routine as moderator deletion). */
    public void deleteUser(Long adminId, Long userId, String reason) {
        findUser(userId);
        requireNotProtected(adminId, userId, "deleted");

        accountDeletionService.deleteAccountAsModerator(userId);

        audit.record(adminId, AdminAuditLog.USER_DELETE, "USER", userId, reason, null);
    }

    // ------------------------------------------------------------------ helpers

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private AdminUserResponse toDetail(User user) {
        return AdminUserResponse.from(user, adminAccess.isSuperAdmin(user.getId()),
                postRepository.countByUserId(user.getId()));
    }

    /**
     * 409 (not 403) on purpose: 401/403 are what the mobile app treats as "session expired".
     */
    private void requireNotProtected(Long adminId, Long targetId, String verb) {
        if (adminAccess.isSuperAdmin(targetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "A Super Admin account cannot be " + verb + ".");
        }
        if (adminId.equals(targetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot have your own account " + verb + ".");
        }
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /**
     * Turns what the admin typed into a lower-case LIKE pattern. The LIKE wildcards (% _) and the
     * escape character are removed, so the search text is always treated literally.
     */
    static String searchPattern(String q) {
        if (q == null) {
            return "%";
        }
        String cleaned = q.trim().toLowerCase(Locale.ROOT)
                .replace("%", "").replace("_", "").replace("\\", "");
        if (cleaned.length() > MAX_SEARCH_LENGTH) {
            cleaned = cleaned.substring(0, MAX_SEARCH_LENGTH);
        }
        return cleaned.isEmpty() ? "%" : "%" + cleaned + "%";
    }
}