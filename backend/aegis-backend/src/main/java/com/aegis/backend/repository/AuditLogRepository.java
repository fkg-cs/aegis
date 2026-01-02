package com.aegis.backend.repository;

import com.aegis.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    // Ci serve prendere gli ultimi X log ordinati per data decrescente
    List<AuditLog> findTop50ByOrderByTimestampDesc();
}