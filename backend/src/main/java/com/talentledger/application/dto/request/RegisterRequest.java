package com.talentledger.application.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 12, message = "Password must be at least 12 characters")
    String password,

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    String name,

    @AssertTrue(message = "You must accept the Terms of Service and Privacy Policy")
    boolean acceptedTerms
) {}
