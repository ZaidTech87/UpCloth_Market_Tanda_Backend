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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {

    private ReportRepository reports;
    private UserRepository users;
    private PostRepository posts;
    private MessageRepository messages;
    private ReportService service;

    @BeforeEach
    void setUp() {
        reports = mock(ReportRepository.class);
        users = mock(UserRepository.class);
        posts = mock(PostRepository.class);
        messages = mock(MessageRepository.class);
        service = new ReportService(reports, users, posts, messages);
        ReflectionTestUtils.setField(service, "maxPerDay", 2);
        when(reports.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));
        when(reports.findFirstByReporterIdAndTargetTypeAndTargetIdAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    private static ReportRequest req(String type, Long id, String reason) {
        ReportRequest r = new ReportRequest();
        r.setTargetType(type);
        r.setTargetId(id);
        r.setReason(reason);
        return r;
    }

    @Test
    void postReportRecordsTheAuthorAsReportedUser() {
        Post p = new Post();
        p.setId(10L);
        p.setUserId(7L);
        when(posts.findById(10L)).thenReturn(Optional.of(p));

        Report r = service.create(1L, req("post", 10L, "scam"));

        assertEquals("POST", r.getTargetType());
        assertEquals(7L, r.getReportedUserId());
        assertEquals("OPEN", r.getStatus());
    }

    @Test
    void cannotReportYourselfOrUnknownReasonOrType() {
        when(users.existsById(1L)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.create(1L, req("USER", 1L, "SPAM")));
        assertThrows(ApiException.class, () -> service.create(1L, req("USER", 2L, "NOPE")));
        assertThrows(ApiException.class, () -> service.create(1L, req("COMMENT", 2L, "SPAM")));
    }

    @Test
    void onlyParticipantsMayReportAMessage() {
        Message m = new Message();
        m.setId(5L);
        m.setSenderId(8L);
        m.setReceiverId(9L);
        when(messages.findById(5L)).thenReturn(Optional.of(m));

        assertThrows(ApiException.class, () -> service.create(1L, req("MESSAGE", 5L, "HARASSMENT")));
        Report ok = service.create(9L, req("MESSAGE", 5L, "HARASSMENT"));
        assertEquals(8L, ok.getReportedUserId());
    }

    @Test
    void duplicateOpenReportIsReturnedNotDuplicated() {
        when(users.existsById(2L)).thenReturn(true);
        Report existing = new Report();
        existing.setId(99L);
        when(reports.findFirstByReporterIdAndTargetTypeAndTargetIdAndStatus(1L, "USER", 2L, "OPEN"))
                .thenReturn(Optional.of(existing));

        assertSame(existing, service.create(1L, req("USER", 2L, "SPAM")));
        verify(reports, never()).save(any(Report.class));
    }

    @Test
    void dailyLimitIsEnforced() {
        when(users.existsById(2L)).thenReturn(true);
        when(reports.countByReporterIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(2L);
        assertThrows(ApiException.class, () -> service.create(1L, req("USER", 2L, "SPAM")));
    }
}
