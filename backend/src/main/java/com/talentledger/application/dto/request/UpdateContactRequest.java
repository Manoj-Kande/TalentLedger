package com.talentledger.application.dto.request;

public record UpdateContactRequest(
    String name,
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
    java.util.Map<String, Object> customFields
) {}
