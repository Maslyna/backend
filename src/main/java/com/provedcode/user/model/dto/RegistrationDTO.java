package com.provedcode.user.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

@Builder
public record RegistrationDTO(
        @NotEmpty String login,
        @NotEmpty String password,
        @JsonProperty("first_name")
        @NotEmpty
        String firstName,
        @JsonProperty("last_name")
        @NotEmpty
        String lastName,
        @NotEmpty String specialization
) {
}
