package com.clothmarket.controller;

import com.clothmarket.dto.PublicUser;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.User;
import com.clothmarket.security.CurrentUser;
import com.clothmarket.service.AccountDeletionService;
import com.clothmarket.service.BlockService;
import com.clothmarket.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final BlockService blockService;

    // PRIVACY: other users never receive a mobile number (see PublicUser).
    // The password hash is never serialised (User.password is WRITE_ONLY).

    @GetMapping("/search")
    public ResponseEntity<List<PublicUser>> searchUsers(@RequestParam("name") String name) {
        Set<Long> hidden = blockService.relatedIds(CurrentUser.id());
        return ResponseEntity.ok(userService.searchUsersByName(name).stream()
                .filter(u -> !hidden.contains(u.getId()))
                .filter(u -> !u.getId().equals(CurrentUser.id()))
                .map(PublicUser::from)
                .toList());
    }

    /** The caller's own full record (includes their own mobile number). */
    @GetMapping("/me")
    public ResponseEntity<User> me() {
        return ResponseEntity.ok(userService.getUserById(CurrentUser.id()));
    }

    /** Own id -> full record; anyone else's id -> public fields only. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        if (CurrentUser.id().equals(id)) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.ok(PublicUser.from(user));
    }

    /** Users can only change their own profile image. */
    @PostMapping("/{id}/profile-image")
    public ResponseEntity<User> updateProfileImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (!CurrentUser.id().equals(id)) {
            throw ApiException.forbidden("You can only update your own profile image");
        }
        return ResponseEntity.ok(userService.updateProfileImage(id, file));
    }

    /**
     * Permanently deletes the caller's account and data. The password must be
     * re-entered so a stolen/unattended session cannot delete the account.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteMyAccount(@RequestBody Map<String, String> body) {
        accountDeletionService.deleteAccount(CurrentUser.id(), body == null ? null : body.get("password"));
        return ResponseEntity.ok(Map.of("message", "Your account and data have been deleted"));
    }
}
