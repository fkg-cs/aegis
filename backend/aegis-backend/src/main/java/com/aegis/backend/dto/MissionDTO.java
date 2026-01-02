package com.aegis.backend.dto;

import com.aegis.backend.model.MissionStatus;
import com.aegis.backend.validation.NoLinks;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MissionDTO(
        UUID id,

        @NotBlank(message = "La descrizione è obbligatoria")
        @NoLinks(message = "VIOLAZIONE SICUREZZA: Non è consentito inserire link o URL nelle note/descrizioni")
        String description,

        @NotBlank(message = "La zona geografica è obbligatoria")
        @NoLinks(message = "Non è consentito inserire link nella zona geografica")
        String geographicZone,

        @NotNull(message = "Il livello di clearance è obbligatorio")
        @Min(value = 0, message = "Il livello minimo è 0 (Riservato)")
        @Max(value = 3, message = "Il livello massimo è 3 (Segretissimo)")
        Integer clearanceLevel,

        // Lo stato (ISTRUTTORIA, IN_CORSO, ecc.)
        MissionStatus status,

        // Nome del file PDF allegato (se presente)
        String attachmentFilename,

        // Set di ID degli agenti assegnati (usato per l'assegnazione lato Supervisor)
        Set<String> assignedAgentIds,

        // Dettagli privacy-safe (Code Name) per la visualizzazione
        List<AgentDisplayDTO> assignedAgentsDetails,

        // NUOVO CAMPO: Lista delle note operative (Chat/Log)
        List<NoteDTO> notes
) {}