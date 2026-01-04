package com.aegis.backend.service;

import com.aegis.backend.model.Mission;
import com.aegis.backend.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final MissionRepository repository;

    // Logger per Audit Trail (Non-Repudiation)
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT_LOG");

    /**
     * Verifica se l'utente ha il diritto di accedere a una specifica missione.
     * Regola [Mallardi Pt. 4]: "Visibile solo al creatore (Owner) e agli utenti aggiunti."
     * + AGGIORNAMENTO: "Verifica dinamica Clearance Level (Need-to-Know persistente)"
     */
    public boolean canAccessMission(UUID missionId, Authentication authentication) {
        Mission mission = repository.findById(missionId).orElse(null);

        // Se la missione non esiste, lasciamo passare (ritorniamo true) per delegare il 404.
        if (mission == null) return true;

        String currentUserId = authentication.getName(); // Questo è l'ID univoco (es. UUID Keycloak o username)

        // 1. Controllo Owner (Il Supervisor che l'ha creata ha sempre accesso)
        boolean isOwner = currentUserId.equals(mission.getOwnerId());
        if (isOwner) {
            auditLogger.info("EVENT=ACCESS_GRANTED | USER={} | RESOURCE_ID={} | ROLE=OWNER", currentUserId, missionId);
            return true;
        }

        // 2. Verifica Assegnazione
        boolean isAssigned = mission.getAssignedAgentIds() != null &&
                mission.getAssignedAgentIds().contains(currentUserId);

        if (!isAssigned) {
            auditLogger.warn("EVENT=ACCESS_DENIED | USER={} | RESOURCE_ID={} | REASON=NOT_ASSIGNED", currentUserId, missionId);
            return false;
        }

        // 3. Verifica Dinamica della Clearance (Anti-Demotion Check)
        // Estraiamo il livello attuale dal Token JWT
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Object claim = jwtAuth.getToken().getClaims().get("clearance_level");
            int userClearance = 0;
            if (claim != null) {
                try {
                    userClearance = Integer.parseInt(String.valueOf(claim));
                } catch (NumberFormatException e) {
                    userClearance = 0;
                }
            }

            if (userClearance < mission.getClearanceLevel()) {
                auditLogger.warn("EVENT=ACCESS_DENIED | USER={} | RESOURCE_ID={} | REASON=INSUFFICIENT_CLEARANCE (Req: {}, Has: {})",
                        currentUserId, missionId, mission.getClearanceLevel(), userClearance);
                return false;
            }
        }

        // ✅ SE ASSEGNATO E CLEARANCE SUFFICIENTE -> OK
        auditLogger.info("EVENT=ACCESS_GRANTED | USER={} | RESOURCE_ID={} | ROLE=ASSIGNED_AGENT", currentUserId, missionId);
        return true;
    }
}