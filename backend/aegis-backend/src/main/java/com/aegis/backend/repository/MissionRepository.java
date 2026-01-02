package com.aegis.backend.repository;

import com.aegis.backend.model.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MissionRepository extends JpaRepository<Mission, UUID> {
    // Qui aggiungeremo query custom se servono
}