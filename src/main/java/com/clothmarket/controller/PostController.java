package com.clothmarket.controller;

import com.clothmarket.dto.PostRequest;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.Post;
import com.clothmarket.security.CurrentUser;
import com.clothmarket.service.PostService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostService postService;
    private final ObjectMapper objectMapper;

    /** The author is the authenticated user, never a client-supplied id. */
    @PostMapping("/create")
    public ResponseEntity<Post> createPost(
            @RequestParam("postData") String postDataJson,
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {

        PostRequest postRequest;
        try {
            postRequest = objectMapper.readValue(postDataJson, PostRequest.class);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Invalid post data");
        }
        return ResponseEntity.ok(postService.createPost(CurrentUser.id(), postRequest, file));
    }

    @GetMapping("/feed")
    public ResponseEntity<Page<Post>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ResponseEntity.ok(postService.getFeed(CurrentUser.id(), safePage, safeSize));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Post>> getUserPosts(@PathVariable Long userId) {
        return ResponseEntity.ok(postService.getUserPosts(CurrentUser.id(), userId));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<Post> getPostById(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPostById(CurrentUser.id(), postId));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable Long postId) {
        postService.deletePost(postId, CurrentUser.id());
        return ResponseEntity.ok("Post deleted successfully");
    }
}
