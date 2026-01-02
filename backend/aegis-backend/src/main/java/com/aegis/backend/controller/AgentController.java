package com.aegis.backend.controller;

import com.aegis.backend.model.AgentProfile;
import com.aegis.backend.repository.AgentProfileRepository;
import com.aegis.backend.service.MissionService; // <--- NUOVO IMPORT
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/intel/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentProfileRepository repository;
    private final MissionService missionService; // <--- AGGIUNTO per la logica di salvataggio

    /**
     * METODO DI SINCRONIZZAZIONE (VERSIONE DIAGNOSTICA)
     * Stampa in console i dati grezzi del token per debuggare il problema del LVL-0.
     */
    private void syncUser(JwtAuthenticationToken auth) {
        if (auth == null) return;

        // --- 🔍 INIZIO DIAGNOSTICA ---
        System.out.println("\n--- 🕵️‍♂️ DIAGNOSTICA TOKEN PER: " + auth.getName() + " ---");

        // Stampiamo TUTTI i dati grezzi che arrivano da Keycloak
        java.util.Map<String, Object> claims = auth.getToken().getClaims();
        claims.forEach((k, v) -> System.out.println("   ➤ " + k + ": " + v));

        Object rawClearance = claims.get("clearance_level");
        System.out.println("   🎯 VALORE GREZZO clearance_level: " + (rawClearance == null ? "NULL" : rawClearance.toString() + " (Tipo: " + rawClearance.getClass().getSimpleName() + ")"));
        // --- 🔍 FINE DIAGNOSTICA ---

        String username = auth.getName();

        // Recupero attributi
        String codeName = auth.getToken().getClaimAsString("code_name");
        String matricola = auth.getToken().getClaimAsString("matricola");
        String email = auth.getToken().getClaimAsString("email");
        String fullName = auth.getToken().getClaimAsString("full_name");
        String phone = auth.getToken().getClaimAsString("phone");
        String office = auth.getToken().getClaimAsString("office");
        String department = auth.getToken().getClaimAsString("department");

        // Fallback CodeName (Cruciale per il SuperUser)
        if (codeName == null || codeName.isEmpty()) {
            System.out.println("   ⚠️ CodeName mancante! Uso username come fallback.");
            codeName = username;
        }

        // Parsing Clearance "Blindato"
        int clearance = 0;
        if (rawClearance != null) {
            try {
                // Tenta di convertire qualsiasi cosa arrivi (Stringa, Intero, ecc.) in int
                String valStr = String.valueOf(rawClearance).trim();
                // Rimuove eventuali parentesi quadre se arriva come array [3]
                valStr = valStr.replace("[", "").replace("]", "");
                clearance = Integer.parseInt(valStr);
                System.out.println("   ✅ Clearance parsata con successo: " + clearance);
            } catch (NumberFormatException e) {
                System.err.println("   ❌ ERRORE PARSING CLEARANCE: " + e.getMessage());
                clearance = 0;
            }
        } else {
            System.err.println("   ❌ ATTENZIONE: Il campo 'clearance_level' non esiste nel token!");
        }

        // Salvataggio
        missionService.syncAgentProfile(username, codeName, matricola, clearance, email, fullName, phone, office, department);
    }

    // 1. RECUPERO PROFILO "LIVE"
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public AgentProfile getMyProfile(JwtAuthenticationToken auth) {
        // --- STEP 1: SINCRONIZZA (Scrive nel DB i dati aggiornati dal Token) ---
        syncUser(auth);

        // --- STEP 2: LEGGE (Restituisce il profilo aggiornato) ---
        // (Il tuo codice originale rimane qui intatto)
        return repository.findById(auth.getName())
                .orElseThrow(() -> new RuntimeException("Profilo non trovato nel registro locale."));
    }

    // 2. RICERCA AGENTI (Codice originale intatto)
    @GetMapping("/search")
    @PreAuthorize("hasRole('SUPERVISOR') or hasRole('SUPER_SUPERVISOR')")
    public List<AgentProfile> searchAgents(@RequestParam String query, JwtAuthenticationToken auth) {
        String q = query.toLowerCase();
        String myUsername = auth.getName();

        return repository.findAll().stream()
                .filter(a -> (a.getUsername().toLowerCase().contains(q) ||
                        a.getCodeName().toLowerCase().contains(q)) &&
                        !a.getUsername().equals(myUsername))
                .collect(Collectors.toList());
    }
}