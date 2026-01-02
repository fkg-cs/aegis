package com.aegis.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "mission_notes")
public class MissionNote {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String authorId; // Username reale (es. analyst-doe)
    private LocalDateTime timestamp;
    private UUID missionId;  // Collegamento alla missione

    public MissionNote(String content, String authorId, UUID missionId) {
        this.content = content;
        this.authorId = authorId;
        this.missionId = missionId;
        this.timestamp = LocalDateTime.now();
    }
}