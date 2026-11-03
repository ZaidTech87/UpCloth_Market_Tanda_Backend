package com.clothmarket.service;

import com.clothmarket.dto.PostRequest;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.Post;
import com.clothmarket.model.User;
import com.clothmarket.repository.PostRepository;
import com.clothmarket.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ModerationService moderationService;
    private final BlockService blockService;
    // ================= CREATE POST =================
    public Post createPost(
            Long userId,
            PostRequest request,
            MultipartFile file
    ) throws IOException {

        validate(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Post post = new Post();
        post.setUser(user);
        post.setUserId(userId);
        post.setDescription(request.getDescription());
        post.setPrice(request.getPrice());
        post.setQuantity(request.getQuantity());
        post.setClothType(request.getClothType());

        // Permanent R2 object key of the uploaded media (null when the post has no file).
        String mediaKey = null;

        // ================= FILE UPLOAD (R2 via StorageService) =================
        if (file != null && !file.isEmpty()) {

            // AI MODERATION: unsafe images abort the whole post (nothing is saved).
            // Decide by the file's real signature as well as its declared type, so a
            // picture mislabelled as "video" cannot skip the check.
            String contentTypeCheck = file.getContentType();
            byte[] head;
            try (java.io.InputStream in = file.getInputStream()) {
                head = in.readNBytes(16);
            }
            boolean isImage = (contentTypeCheck != null && contentTypeCheck.startsWith("image/"))
                    || UploadValidator.looksLikeRasterImage(head);
            if (isImage) {
                moderationService.checkImage(file);
            }

            // The value stored in posts.media_url is the object key, never a signed URL.
            mediaKey = storageService.store(file, "posts", MediaKind.POST_MEDIA);
            post.setMediaUrl(mediaKey);

            String contentType = file.getContentType();
            if (contentType != null) {
                if (contentType.startsWith("image/")) {
                    post.setMediaType("image");
                } else if (contentType.startsWith("video/")) {
                    post.setMediaType("video");
                }
            }
        }

        // Post save karo — createdAt @PrePersist se auto set hoga
        Post savedPost;
        try {
            savedPost = postRepository.save(post);
        } catch (RuntimeException e) {
            // The row was not saved: do not leave the uploaded object orphaned in R2.
            if (mediaKey != null) {
                storageService.deleteQuietly(mediaKey);
            }
            throw e;
        }

        // Naye post me bhi user info set karo
        // taaki frontend ko turant sahi data mile
        savedPost.setUserName(user.getName());
        savedPost.setUserLocation(user.getLocation());
        savedPost.setUserProfileImage(user.getProfileImage());

        return savedPost;
    }

    private static void validate(PostRequest r) {
        if (r == null) {
            throw ApiException.badRequest("Invalid post data");
        }
        if (r.getDescription() != null && r.getDescription().length() > 2000) {
            throw ApiException.badRequest("Description is too long (max 2000 characters)");
        }
        if (r.getClothType() != null && r.getClothType().length() > 100) {
            throw ApiException.badRequest("Cloth type is too long");
        }
        if (r.getPrice() != null && (r.getPrice().isNaN() || r.getPrice().isInfinite()
                || r.getPrice() < 0 || r.getPrice() > 1_000_000_000d)) {
            throw ApiException.badRequest("Invalid price");
        }
        if (r.getQuantity() != null && (r.getQuantity() < 0 || r.getQuantity() > 10_000_000)) {
            throw ApiException.badRequest("Invalid quantity");
        }
    }

    // ================= FEED (N+1 fix) =================
    public Page<Post> getFeed(Long viewerId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        // Posts by users the viewer blocked (or who blocked the viewer) are hidden.
        java.util.Set<Long> hidden = blockService.relatedIds(viewerId);
        Page<Post> posts = hidden.isEmpty()
                ? postRepository.findAllOrderByCreatedAtDesc(pageable)
                : postRepository.findFeedExcluding(hidden, pageable);

        // ✅ Sab userIds ek baar nikalo
        List<Long> userIds = posts.stream()
                .map(Post::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // ✅ Ek hi DB query me saare users fetch karo
        Map<Long, User> userMap = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // ✅ Map se user info set karo — koi extra DB call nahi
        posts.forEach(post -> {
            User user = userMap.get(post.getUserId());
            if (user != null) {
                post.setUserName(user.getName());
                post.setUserLocation(user.getLocation());
                post.setUserProfileImage(user.getProfileImage());
            }
        });

        return posts;
    }

    // ================= USER POSTS =================
    public List<Post> getUserPosts(Long viewerId, Long userId) {

        if (blockService.isBlockedEitherWay(viewerId, userId)) {
            return List.of();
        }

        List<Post> posts = postRepository.findUserPosts(userId);

        // getUserPosts me sirf ek user ke posts hain
        // isliye ek query kaafi hai
        User user = userRepository.findById(userId).orElse(null);

        if (user != null) {
            posts.forEach(post -> {
                post.setUserName(user.getName());
                post.setUserLocation(user.getLocation());
                post.setUserProfileImage(user.getProfileImage());
            });
        }

        return posts;
    }

    // ================= SINGLE POST =================
    public Post getPostById(Long viewerId, Long postId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Post not found"));
        if (blockService.isBlockedEitherWay(viewerId, post.getUserId())) {
            throw ApiException.notFound("Post not found");
        }

        User user = userRepository
                .findById(post.getUserId())
                .orElse(null);

        if (user != null) {
            post.setUserName(user.getName());
            post.setUserLocation(user.getLocation());
            post.setUserProfileImage(user.getProfileImage());
        }

        return post;
    }

    // ================= DELETE POST =================
    public void deletePost(Long postId, Long userId) {

        // Only the owner can delete; a non-owner gets the same 404 as a missing post.
        Post post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> ApiException.notFound("Post not found or access denied"));

        // Delete the database row first; remove the R2 object only after that succeeds.
        String mediaKey = post.getMediaUrl();
        postRepository.delete(post);
        storageService.deleteQuietly(mediaKey);
    }

    /** Moderator removal of any post (media included). */
    public void removePostAsModerator(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Post not found"));
        String mediaKey = post.getMediaUrl();
        postRepository.delete(post);
        storageService.deleteQuietly(mediaKey);
    }
}