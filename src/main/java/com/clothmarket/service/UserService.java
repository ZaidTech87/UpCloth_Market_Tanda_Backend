package com.clothmarket.service;

import com.clothmarket.exception.ApiException;
import com.clothmarket.model.User;
import com.clothmarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    public List<User> searchUsersByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return List.of();
        }
        String query = name.trim();
        if (query.length() > 50) {
            query = query.substring(0, 50);
        }
        return userRepository.findTop10ByNameContainingIgnoreCase(query);
    }

    // Profile photos are stored in the private R2 bucket via StorageService.
    // users.profile_image holds the permanent object key, never a signed URL.
    public User updateProfileImage(Long userId, MultipartFile file) throws IOException {
        User user = getUserById(userId);
        String oldKey = user.getProfileImage();

        String newKey = storageService.store(file, "profiles", MediaKind.IMAGE);
        user.setProfileImage(newKey);

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (RuntimeException e) {
            // The new key was not saved: do not leave the uploaded object orphaned in R2.
            storageService.deleteQuietly(newKey);
            throw e;
        }

        // Remove the replaced photo only after the database update succeeded.
        if (oldKey != null && !oldKey.equals(newKey)) {
            storageService.deleteQuietly(oldKey);
        }
        return saved;
    }
}