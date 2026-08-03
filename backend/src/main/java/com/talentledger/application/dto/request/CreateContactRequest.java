package com.talentledger.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateContactRequest(
    @NotBlank String name,
    @NotBlank String email,
    String phone,
    String linkedinUrl,
    String secondaryEmail,
    String title,
    String department,
    String seniorityLevel,
    String location,
    String timezone,
    String language,
    String notes,
    java.util.List<String> tags,
    UUID primaryDumpId,
    UUID companyId
) {}
