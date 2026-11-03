package com.clothmarket.controller;

import com.clothmarket.dto.ReportRequest;
import com.clothmarket.model.Report;
import com.clothmarket.security.CurrentUser;
import com.clothmarket.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ReportRequest request) {
        Report r = reportService.create(CurrentUser.id(), request);
        return ResponseEntity.ok(Map.of("id", r.getId(),
                "message", "Thanks. We will review this report."));
    }
}
