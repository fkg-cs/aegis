package com.aegis.backend.repository;

import com.aegis.backend.model.MissionNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MissionNoteRepository extends JpaRepository<MissionNote, UUID> {
    // Trova tutte le note di una missione ordinate per data (dal più vecchio al più recente)
    List<MissionNote> findByMissionIdOrderByTimestampAsc(UUID missionId);
}