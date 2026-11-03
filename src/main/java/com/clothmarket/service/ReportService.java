package com.clothmarket.service;

import com.clothmarket.dto.ReportRequest;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.Message;
import com.clothmarket.model.Post;
import com.clothmarket.model.Report;
import com.clothmarket.repository.MessageRepository;
import com.clothmarket.repository.PostRepository;
import com.clothmarket.repository.ReportRepository;
import com.clothmarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportService {

    public static final Set<String> TARGET_TYPES = Set.of("USER", "POST", "MESSAGE");
    public static final Set<String> REASONS = Set.of(
            "SPAM", "SCAM", "INAPPROPRIATE", "HARASSMENT", "FAKE", "COUNTERFEIT", "OTHER");

    private final ReportRepository reports;
    private final UserRepository users;
    private final PostRepository posts;
    private final MessageRepository messages;

    @Value("${reports.max-per-day:20}")
    private int maxPerDay;

    public Report create(Long reporterId, ReportRequest req) {
        String type = req.getTargetType().trim().toUpperCase();
        String reason = req.getReason().trim().toUpperCase();
        if (!TARGET_TYPES.contains(type)) {
            throw ApiException.badRequest("Invalid report type");
        }
        if (!REASONS.contains(reason)) {
            throw ApiException.badRequest("Invalid report reason");
        }

        Long reportedUserId = resolveReportedUser(reporterId, type, req.getTargetId());
        if (reporterId.equals(reportedUserId)) {
            throw ApiException.badRequest("You cannot report your own content");
        }

        // Same reporter + same target while still open = the same report (idempotent).
        var existing = reports.findFirstByReporterIdAndTargetTypeAndTargetIdAndStatus(
                reporterId, type, req.getTargetId(), "OPEN");
        if (existing.isPresent()) {
            return existing.get();
        }

        if (reports.countByReporterIdAndCreatedAtAfter(reporterId, LocalDateTime.now().minusDays(1)) >= maxPerDay) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many reports today. Please try again later.");
        }

        Report r = new Report();
        r.setReporterId(reporterId);
        r.setTargetType(type);
        r.setTargetId(req.getTargetId());
        r.setReportedUserId(reportedUserId);
        r.setReason(reason);
        String details = req.getDetails() == null ? null : req.getDetails().trim();
        r.setDetails(details == null || details.isEmpty() ? null : details);
        r.setStatus("OPEN");
        return reports.save(r);
    }

    private Long resolveReportedUser(Long reporterId, String type, Long targetId) {
        switch (type) {
            case "USER" -> {
                if (!users.existsById(targetId)) {
                    throw ApiException.notFound("User not found");
                }
                return targetId;
            }
            case "POST" -> {
                Post p = posts.findById(targetId)
                        .orElseThrow(() -> ApiException.notFound("Post not found"));
                return p.getUserId();
            }
            default -> {
                // MESSAGE: only a participant of the conversation may report it.
                Message m = messages.findById(targetId)
                        .orElseThrow(() -> ApiException.notFound("Message not found"));
                if (!reporterId.equals(m.getSenderId()) && !reporterId.equals(m.getReceiverId())) {
                    throw ApiException.notFound("Message not found");
                }
                return m.getSenderId();
            }
        }
    }
}
