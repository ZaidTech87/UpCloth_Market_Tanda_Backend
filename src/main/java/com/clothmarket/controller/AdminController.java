package com.clothmarket.controller;

import com.clothmarket.exception.ApiException;
import com.clothmarket.model.Report;
import com.clothmarket.repository.ReportRepository;
import com.clothmarket.security.CurrentUser;
import com.clothmarket.service.AccountDeletionService;
import com.clothmarket.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Minimal moderation API for the operator. There is no admin role in the data model;
 * moderators are the user ids listed in ADMIN_USER_IDS (comma separated). Empty =
 * nobody is an admin and every endpoint answers 403.
 *
 * Intended to be driven with curl/Postman until a real admin UI exists (docs/launch).
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    static final Set<String> ACTIONS = Set.of("DISMISS", "REMOVE_POST", "DELETE_ACCOUNT");

    private final ReportRepository reports;
    private final PostService postService;
    private final AccountDeletionService accountDeletionService;

    @Value("${admin.user-ids:}")
    private String adminUserIds;

    @GetMapping("/reports")
    public ResponseEntity<List<Report>> openReports(
            @RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(defaultValue = "50") int limit) {
        requireAdmin();
        int size = Math.min(Math.max(limit, 1), 200);
        return ResponseEntity.ok(reports.findByStatusOrderByCreatedAtAsc(
                status.toUpperCase(), PageRequest.of(0, size)));
    }

    /** body: {"action": "DISMISS" | "REMOVE_POST" | "DELETE_ACCOUNT"} */
    @PostMapping("/reports/{id}/resolve")
    public ResponseEntity<Map<String, String>> resolve(@PathVariable Long id,
                                                       @RequestBody Map<String, String> body) {
        requireAdmin();
        String action = body == null || body.get("action") == null ? "" : body.get("action").toUpperCase();
        if (!ACTIONS.contains(action)) {
            throw ApiException.badRequest("action must be one of " + ACTIONS);
        }
        Report report = reports.findById(id).orElseThrow(() -> ApiException.notFound("Report not found"));

        switch (action) {
            case "REMOVE_POST" -> {
                if (!"POST".equals(report.getTargetType())) {
                    throw ApiException.badRequest("This report is not about a post");
                }
                postService.removePostAsModerator(report.getTargetId());
            }
            case "DELETE_ACCOUNT" -> {
                if (report.getReportedUserId() == null) {
                    throw ApiException.badRequest("No account is attached to this report");
                }
                accountDeletionService.deleteAccountAsModerator(report.getReportedUserId());
            }
            default -> { /* DISMISS */ }
        }

        // Close this report and any other open report on the same target.
        List<Report> same = reports.findByTargetTypeAndTargetIdAndStatus(
                report.getTargetType(), report.getTargetId(), "OPEN");
        if (same.stream().noneMatch(r -> r.getId().equals(report.getId()))) {
            same = new java.util.ArrayList<>(same);
            same.add(report);
        }
        same.removeIf(r -> !reports.existsById(r.getId())); // e.g. filed by an account just deleted
        LocalDateTime now = LocalDateTime.now();
        for (Report r : same) {
            r.setStatus("RESOLVED");
            r.setResolution(action);
            r.setResolvedAt(now);
        }
        reports.saveAll(same);
        return ResponseEntity.ok(Map.of("message", "Report resolved: " + action));
    }

    private void requireAdmin() {
        if (!admins().contains(CurrentUser.id())) {
            throw ApiException.forbidden("Access denied");
        }
    }

    private Set<Long> admins() {
        return Arrays.stream(adminUserIds.split(","))
                .map(String::trim).filter(s -> s.matches("\\d+"))
                .map(Long::valueOf).collect(Collectors.toSet());
    }
}
