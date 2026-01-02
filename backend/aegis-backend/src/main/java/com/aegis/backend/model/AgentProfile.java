package com.aegis.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "agent_profiles")
@AllArgsConstructor
@NoArgsConstructor
public class AgentProfile {
    @Id
    private String username;
    private String codeName;
    private String matricola;
    private Integer clearanceLevel;
    private String email;

    // --- NUOVI CAMPI ESTESI ---
    private String fullName;
    private String phone;
    private String office;
    private String department;
}