package com.clothmarket.controller;

import com.clothmarket.dto.PublicUser;
import com.clothmarket.security.CurrentUser;
import com.clothmarket.service.BlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @GetMapping
    public ResponseEntity<List<PublicUser>> list() {
        return ResponseEntity.ok(blockService.listBlocked(CurrentUser.id()));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<Map<String, String>> block(@PathVariable Long userId) {
        blockService.block(CurrentUser.id(), userId);
        return ResponseEntity.ok(Map.of("message", "User blocked"));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, String>> unblock(@PathVariable Long userId) {
        blockService.unblock(CurrentUser.id(), userId);
        return ResponseEntity.ok(Map.of("message", "User unblocked"));
    }
}
