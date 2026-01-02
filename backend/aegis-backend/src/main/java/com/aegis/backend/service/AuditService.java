package com.aegis.backend.service;

import com.aegis.backend.model.AuditLog;
import com.aegis.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    // REQUIRES_NEW: Se la transazione principale fallisce (es. errore missione),
    // il log del tentativo DEVE essere salvato comunque. Sicurezza prima di tutto.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actor, String action, String details, String ip) {
        AuditLog log = new AuditLog(actor, action, details, ip);
        repository.save(log);
    }

    public List<AuditLog> getRecentLogs() {
        return repository.findTop50ByOrderByTimestampDesc();
    }
}