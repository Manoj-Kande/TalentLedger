package com.talentledger.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
    @NotBlank
    String token,

    @NotBlank
    @Size(min = 12)
    String newPassword
) {}
