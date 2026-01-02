package com.aegis.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue
    private UUID id;

    @CreationTimestamp
    private LocalDateTime timestamp;

    private String actor;       // Chi ha fatto l'azione
    private String action;      // Cosa ha fatto
    private String details;     // Dettagli
    private String ipAddress;   // Indirizzo IP

    public AuditLog(String actor, String action, String details, String ipAddress) {
        this.actor = actor;
        this.action = action;
        this.details = details;
        this.ipAddress = ipAddress;
    }
}