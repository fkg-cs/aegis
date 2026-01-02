package com.aegis.backend.controller;

import com.aegis.backend.dto.MissionDTO;
import com.aegis.backend.model.MissionStatus;
import com.aegis.backend.service.AuditService;
import com.aegis.backend.service.MissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/intel/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService service;
    private final AuditService auditService;

    /**
     * Sincronizza i dati dell'agente dal token JWT al database locale.
     */
    private void syncUser(JwtAuthenticationToken auth) {
        if (auth == null) return;

        String username = auth.getName();
        String codeName = auth.getToken().getClaimAsString("code_name");
        String matricola = auth.getToken().getClaimAsString("matricola");
        String email = auth.getToken().getClaimAsString("email");

        String fullName = auth.getToken().getClaimAsString("full_name");
        String phone = auth.getToken().getClaimAsString("phone");
        String office = auth.getToken().getClaimAsString("office");
        String department = auth.getToken().getClaimAsString("department");

        Object clearanceObj = auth.getToken().getClaims().get("clearance_level");
        int clearance = 0;
        if (clearanceObj != null) {
            try {
                clearance = Integer.parseInt(String.valueOf(clearanceObj));
            } catch (NumberFormatException e) {
                clearance = 0;
            }
        }

        if (codeName != null) {
            service.syncAgentProfile(username, codeName, matricola, clearance, email, fullName, phone, office, department);
        }
    }

    private boolean isSupervisor(JwtAuthenticationToken auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.contains("SUPERVISOR"));
    }

    // 1. Creazione Missione
    @PostMapping
    @PreAuthorize("hasRole('SUPERVISOR') or hasRole('SUPER_SUPERVISOR')")
    public ResponseEntity<MissionDTO> createMission(
            @RequestBody @Valid MissionDTO dto,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {

        syncUser(auth);

        int userClearance = service.getAgentClearance(auth.getName());

        if (dto.clearanceLevel() > userClearance) {
            auditService.log(auth.getName(), "CREATE_MISSION_DENIED", "Tentativo creazione livello superiore", request.getRemoteAddr());
            throw new AccessDeniedException("VIOLAZIONE GERARCHIA: Non puoi creare missioni con clearance superiore alla tua.");
        }

        String ownerId = auth.getName();
        MissionDTO created = service.createMission(dto, ownerId);

        auditService.log(auth.getName(), "CREATE_MISSION", "Nuova missione creata: " + created.id(), request.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // 2. Lettura Singola Missione
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_SUPERVISOR') or hasRole('SUPER_SUPERVISOR') or @securityService.canAccessMission(#id, authentication)")
    public ResponseEntity<MissionDTO> getMission(@PathVariable UUID id, JwtAuthenticationToken auth) {
        syncUser(auth);
        boolean supervisor = isSupervisor(auth);
        return ResponseEntity.ok(service.getMission(id, supervisor));
    }

    // 3. Lista Tutte
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MissionDTO>> listMissions(JwtAuthenticationToken auth) {
        syncUser(auth);
        boolean supervisor = isSupervisor(auth);
        return ResponseEntity.ok(service.getAllMissions(supervisor));
    }

    // 4. Modifica Stato (AGGIORNATO: Logica permessi robusta + Debug)
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('SUPER_SUPERVISOR', 'ROLE_SUPER_SUPERVISOR') or (hasAnyAuthority('SUPERVISOR', 'ROLE_SUPERVISOR') and @securityService.canAccessMission(#id, authentication))")
    public ResponseEntity<MissionDTO> updateStatus(
            @PathVariable UUID id,
            @RequestParam MissionStatus status,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {

        // DEBUG: Stampa cosa vede Spring Security per diagnosticare errori 403
        System.out.println(">>> DEBUG PERMESSI UTENTE: " + auth.getName() + " | RUOLI: " + auth.getAuthorities());

        syncUser(auth);
        MissionDTO updated = service.updateStatus(id, status);

        auditService.log(auth.getName(), "UPDATE_STATUS", "Missione " + id + " -> " + status, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    // 5. Assegna Agente (AGGIORNATO: Logica permessi robusta)
    @PostMapping("/{id}/agents")
    @PreAuthorize("hasAnyAuthority('SUPER_SUPERVISOR', 'ROLE_SUPER_SUPERVISOR') or (hasAnyAuthority('SUPERVISOR', 'ROLE_SUPERVISOR') and @securityService.canAccessMission(#id, authentication))")
    public ResponseEntity<MissionDTO> addAgent(
            @PathVariable UUID id,
            @RequestParam String agentId,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {

        syncUser(auth);
        MissionDTO updated = service.addAgentToMission(id, agentId);

        auditService.log(auth.getName(), "ADD_AGENT", "Agente " + agentId + " aggiunto a " + id, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    // 6. Upload Allegato
    @PostMapping("/{id}/attachment")
    @PreAuthorize("@securityService.canAccessMission(#id, authentication) and hasRole('SUPERVISOR')")
    public ResponseEntity<MissionDTO> uploadAttachment(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {

        syncUser(auth);
        MissionDTO updated = service.uploadAttachment(id, file);

        auditService.log(auth.getName(), "UPLOAD_FILE", "File caricato: " + file.getOriginalFilename() + " su " + id, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    // 7. Download con Watermark
    @GetMapping("/{id}/attachment")
    @PreAuthorize("hasAuthority('SUPER_SUPERVISOR') or hasRole('SUPER_SUPERVISOR') or @securityService.canAccessMission(#id, authentication)")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable UUID id,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {

        syncUser(auth);
        MissionDTO mission = service.getMission(id, false);

        if (mission.attachmentFilename() == null) {
            return ResponseEntity.notFound().build();
        }

        String matricola = auth.getToken().getClaimAsString("matricola");
        String identity = (matricola != null) ? matricola : auth.getName();

        Resource resource = service.loadFileWithWatermark(mission.attachmentFilename(), identity);

        auditService.log(auth.getName(), "DOWNLOAD_FILE", "Download sicuro file: " + mission.attachmentFilename(), request.getRemoteAddr());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mission.attachmentFilename() + "\"")
                .body(resource);
    }

    // 8. Aggiunta Nota 
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyAuthority('SUPER_SUPERVISOR', 'ROLE_SUPER_SUPERVISOR') or (hasAnyAuthority('SUPERVISOR', 'ROLE_SUPERVISOR') and @securityService.canAccessMission(#id, authentication))")
    public ResponseEntity<MissionDTO> addNote(
            @PathVariable UUID id,
            @RequestBody String content,
            JwtAuthenticationToken auth,
            HttpServletRequest request) {

        syncUser(auth);
        String cleanContent = content.replaceAll("^\"|\"$", "").replace("\\n", "\n");
        MissionDTO updated = service.addNote(id, cleanContent, auth.getName());

        auditService.log(auth.getName(), "ADD_NOTE", "Nota operativa aggiunta a " + id, request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }
}