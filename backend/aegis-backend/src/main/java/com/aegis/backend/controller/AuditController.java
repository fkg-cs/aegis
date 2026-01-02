package com.aegis.backend.controller;

import com.aegis.backend.model.AuditLog;
import com.aegis.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/intel/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    // Solo il God Mode può leggere la Black Box
    @GetMapping
    @PreAuthorize("hasRole('SUPER_SUPERVISOR')")
    public List<AuditLog> getSystemLogs() {
        return auditService.getRecentLogs();
    }
}