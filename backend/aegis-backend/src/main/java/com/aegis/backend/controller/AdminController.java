package com.aegis.backend.controller;

import com.aegis.backend.model.AgentProfile;
import com.aegis.backend.repository.AgentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/intel/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AgentProfileRepository agentRepository;

    // 1. LISTA COMPLETA AGENTI (Escluso me stesso)
    // Utilizza controlli multipli per gestire diverse configurazioni dei ruoli (con/senza prefisso ROLE_)
    @GetMapping("/agents")
    @PreAuthorize("hasAuthority('SUPER_SUPERVISOR') or hasRole('SUPER_SUPERVISOR') or hasAuthority('ROLE_SUPER_SUPERVISOR')")
    public List<AgentProfile> getAllAgents(Authentication auth) {
        String myUsername = auth.getName();

        return agentRepository.findAll().stream()
                // Filtro di sicurezza: escludi te stesso dalla lista per evitare auto-modifiche distruttive
                .filter(agent -> !agent.getUsername().equals(myUsername))
                .collect(Collectors.toList());
    }

    // 2. MODIFICA CLEARANCE (Potere Assoluto)
    @PatchMapping("/agents/{username}/clearance")
    @PreAuthorize("hasAuthority('SUPER_SUPERVISOR') or hasRole('SUPER_SUPERVISOR') or hasAuthority('ROLE_SUPER_SUPERVISOR')")
    public ResponseEntity<AgentProfile> updateClearance(@PathVariable String username, @RequestParam int newLevel) {
        AgentProfile agent = agentRepository.findById(username)
                .orElseThrow(() -> new RuntimeException("Agente non trovato"));

        agent.setClearanceLevel(newLevel);
        return ResponseEntity.ok(agentRepository.save(agent));
    }
}