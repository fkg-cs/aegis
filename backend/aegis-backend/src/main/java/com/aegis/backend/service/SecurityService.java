package com.aegis.backend.service;

import com.aegis.backend.model.Mission;
import com.aegis.backend.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final MissionRepository repository;

    // Logger per Audit Trail (Non-Repudiation)
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT_LOG");

    /**
     * Verifica se l'utente ha il diritto di accedere a una specifica missione.
     * Regola [Mallardi Pt. 4]: "Visibile solo al creatore (Owner) e agli utenti aggiunti."
     */
    public boolean canAccessMission(UUID missionId, Authentication authentication) {
        Mission mission = repository.findById(missionId).orElse(null);

        // Se la missione non esiste, lasciamo passare (ritorniamo true).
        // Sarà il Service/Controller a lanciare l'eccezione 404 Not Found.
        // Questo evita di rivelare l'esistenza di ID validi tramite errori 403.
        if (mission == null) return true;

        String currentUserId = authentication.getName(); // Questo è l'ID univoco (es. UUID Keycloak o username)

        // 1. Controllo Owner (Il Supervisor che l'ha creata)
        boolean isOwner = currentUserId.equals(mission.getOwnerId());

        // 2. Controllo Assegnazione (Se l'utente è nella lista dei partecipanti)
        boolean isAssigned = mission.getAssignedAgentIds() != null &&
                mission.getAssignedAgentIds().contains(currentUserId);

        // --- DECISIONE & AUDIT ---

        if (isOwner || isAssigned) {
            // ✅ AUDIT: ACCESSO CONSENTITO
            auditLogger.info("EVENT=ACCESS_GRANTED | USER={} | RESOURCE_ID={} | ROLE={}",
                    currentUserId, missionId, isOwner ? "OWNER" : "ASSIGNED_AGENT");
            return true;
        } else {
            // 🚨 AUDIT: ACCESSO NEGATO (Violazione Need-to-Know)
            auditLogger.warn("EVENT=ACCESS_DENIED | USER={} | RESOURCE_ID={} | REASON=NOT_AUTHORIZED_FOR_MISSION",
                    currentUserId, missionId);
            return false;
        }
    }
}