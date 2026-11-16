package com.clothmarket.controller;

import com.clothmarket.dto.AdminMeResponse;
import com.clothmarket.dto.AdminPostResponse;
import com.clothmarket.dto.AdminReasonRequest;
import com.clothmarket.dto.AdminUserResponse;
import com.clothmarket.dto.AdminUserUpdateRequest;
import com.clothmarket.dto.PageResponse;
import com.clothmarket.service.AdminAccess;
import com.clothmarket.service.AdminPanelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API behind the ClothMarket Admin web panel (posts and users only).
 *
 * EVERY method starts with adminAccess.requireAdmin(): a missing/invalid JWT is rejected by the
 * security filter (401), and an authenticated user who is not a Super Admin gets 403. The admin
 * id used for auditing always comes from the verified JWT, never from the request.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPanelController {

    private final AdminAccess adminAccess;
    private final AdminPanelService service;

    // ---- who am I ----

    @GetMapping("/me")
    public ResponseEntity<AdminMeResponse> me() {
        return ResponseEntity.ok(service.me(adminAccess.requireAdmin()));
    }

    // ---- posts ----

    @GetMapping("/posts")
    public ResponseEntity<PageResponse<AdminPostResponse>> listPosts(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        adminAccess.requireAdmin();
        return ResponseEntity.ok(service.listPosts(q, page, size));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<AdminPostResponse> getPost(@PathVariable Long id) {
        adminAccess.requireAdmin();
        return ResponseEntity.ok(service.getPost(id));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id,
                                           @Valid @RequestBody(required = false) AdminReasonRequest body) {
        Long adminId = adminAccess.requireAdmin();
        service.deletePost(adminId, id, body == null ? null : body.getReason());
        return ResponseEntity.noContent().build();
    }

    // ---- users ----

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminAccess.requireAdmin();
        return ResponseEntity.ok(service.listUsers(q, page, size));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable Long id) {
        adminAccess.requireAdmin();
        return ResponseEntity.ok(service.getUser(id));
    }

    /** Edits name and location only. */
    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> updateUser(@PathVariable Long id,
                                                        @Valid @RequestBody AdminUserUpdateRequest body) {
        Long adminId = adminAccess.requireAdmin();
        return ResponseEntity.ok(service.updateUser(adminId, id, body));
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<AdminUserResponse> blockUser(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false) AdminReasonRequest body) {
        Long adminId = adminAccess.requireAdmin();
        return ResponseEntity.ok(service.blockUser(adminId, id, body == null ? null : body.getReason()));
    }

    @PostMapping("/users/{id}/unblock")
    public ResponseEntity<AdminUserResponse> unblockUser(@PathVariable Long id) {
        Long adminId = adminAccess.requireAdmin();
        return ResponseEntity.ok(service.unblockUser(adminId, id));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           @Valid @RequestBody(required = false) AdminReasonRequest body) {
        Long adminId = adminAccess.requireAdmin();
        service.deleteUser(adminId, id, body == null ? null : body.getReason());
        return ResponseEntity.noContent().build();
    }
}