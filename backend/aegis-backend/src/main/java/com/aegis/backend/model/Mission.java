package com.aegis.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

@Entity
@Data
@Table(name = "missions")
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // [cite: 30] Descrizione
    @Column(columnDefinition = "TEXT")
    private String description;

    // [cite: 29] Zona Geografica
    private String geographicZone;

    // [cite: 27] Livello Clearance: da 0 a 3
    @Column(nullable = false)
    private Integer clearanceLevel;

    //  Stato con indicatore visivo (gestito poi nel frontend)
    @Enumerated(EnumType.STRING)
    private MissionStatus status;

    // [cite: 38] Creatore (Owner) - Memorizziamo l'ID Keycloak
    private String ownerId;

    private String attachmentFilename;

    // [cite: 38] Utenti assegnati alla missione (Lista di ID Keycloak)
    @ElementCollection
    @CollectionTable(name = "mission_agents", joinColumns = @JoinColumn(name = "mission_id"))
    @Column(name = "agent_id")
    private Set<String> assignedAgentIds = new HashSet<>();
}