package com.envguard.controller;

import com.envguard.domain.entity.AuditEvent;
import com.envguard.dto.response.ApiResponse;
import com.envguard.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /** Viewers can read; nobody can edit/delete (no mutating endpoints). */
    @GetMapping
    public ApiResponse<Page<AuditEvent>> search(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(auditService.search(projectId, userId, action, pageable));
    }
}
