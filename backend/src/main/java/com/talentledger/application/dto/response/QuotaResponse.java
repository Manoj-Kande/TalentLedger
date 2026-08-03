package com.talentledger.application.dto.response;

import java.util.Map;
import java.util.UUID;

public record QuotaResponse(
    int activeDumpsCount,
    int activeDumpsLimit,
    int contactsStoredCount,
    int contactsStoredLimit,
    int uploadsThisMonthCount,
    int uploadsMonthlyLimit,
    int aiCreditsUsed,
    int aiCreditsLimit,
    long storageBytesUsed,
    long storageBytesLimit
) {}
