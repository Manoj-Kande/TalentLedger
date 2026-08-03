package com.talentledger.infrastructure.web.controller;

import com.talentledger.application.port.inbound.CompanyUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyUseCase companyUseCase;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = companyUseCase.listCompanies(userId);
        return toResponse(result);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String q,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = companyUseCase.searchCompanies(userId, q != null ? q : "");
        return toResponse(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = companyUseCase.getCompany(id, userId);
        return toResponse(result);
    }

    @GetMapping("/{id}/contacts")
    public ResponseEntity<Map<String, Object>> getContacts(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = companyUseCase.getCompanyContacts(id, userId, cursor, size);
        if (result.isSuccess()) {
            var page = result.getValue();
            return ResponseEntity.ok(Map.of("success", true, "data", page.contacts(), "meta",
                    Map.of("nextCursor", page.nextCursor() != null ? page.nextCursor() : "", "hasMore", page.hasMore())));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    private ResponseEntity<Map<String, Object>> toResponse(com.talentledger.domain.shared.Result<?, String> result) {
        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of("success", true, "data", result.getValue()));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    private UUID getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "No authenticated user on request (SessionAuthFilter should have rejected this earlier)");
        }
        return UUID.fromString(userId.toString());
    }
}
