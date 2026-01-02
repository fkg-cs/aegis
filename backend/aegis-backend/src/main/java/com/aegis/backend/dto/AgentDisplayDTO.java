package com.aegis.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// JsonInclude.Include.NON_NULL significa: se il campo è null, non mandarlo proprio nel JSON
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentDisplayDTO(
        String codeName,
        String role,
        String email,

        // Questi saranno NULL se chi guarda non è un Supervisor
        String fullName,
        String phone,
        String office,
        String department
) {}