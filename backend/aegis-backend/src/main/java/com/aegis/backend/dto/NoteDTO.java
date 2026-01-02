package com.aegis.backend.dto;

import java.time.LocalDateTime;

public record NoteDTO(
        String id,
        String content,
        String authorCodeName, // Qui metteremo "Recluta", non "analyst-doe"
        LocalDateTime timestamp
) {}