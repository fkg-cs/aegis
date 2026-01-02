package com.aegis.backend.repository;

import com.aegis.backend.model.AgentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileRepository extends JpaRepository<AgentProfile, String> {
}