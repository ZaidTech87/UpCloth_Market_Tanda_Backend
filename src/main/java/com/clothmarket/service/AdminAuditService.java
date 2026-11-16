package com.clothmarket.service;

import com.clothmarket.model.AdminAuditLog;
import com.clothmarket.repository.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit records for admin actions. It is called AFTER the action succeeded. If the audit
 * write itself fails, the failure is logged at ERROR (with the facts, so there is still a trail)
 * and is NOT rethrown: an already-completed deletion must not be reported to the admin as failed.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    private final AdminAuditLogRepository repository;

    public void record(Long adminId, String action, String targetType, Long targetId,
                       String reason, String details) {
        try {
            AdminAuditLog entry = new AdminAuditLog();
            entry.setAdminId(adminId);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setReason(clip(reason, 500));
            entry.setDetails(clip(details, 500));
            entry.setIpAddress(clip(clientIp(), 64));
            repository.save(entry);
        } catch (RuntimeException e) {
            log.error("AUDIT WRITE FAILED admin={} action={} target={}:{} cause={}",
                    adminId, action, targetType, targetId, e.getClass().getSimpleName());
        }
    }

    /** Client address as resolved by Tomcat (server.forward-headers-strategy=native). */
    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}