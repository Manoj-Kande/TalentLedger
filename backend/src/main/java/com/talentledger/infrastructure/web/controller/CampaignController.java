package com.talentledger.infrastructure.web.controller;

import com.talentledger.application.port.inbound.CampaignUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignUseCase campaignUseCase;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = campaignUseCase.listCampaigns(userId);
        return toResponse(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = campaignUseCase.getCampaign(id, userId);
        return toResponse(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CampaignUseCase.CreateCampaignCommand command,
                                                       HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = campaignUseCase.createCampaign(userId, command);
        return toResponse(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID id,
            @RequestBody CampaignUseCase.UpdateCampaignCommand command,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = campaignUseCase.updateCampaign(id, userId, command);
        return toResponse(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = campaignUseCase.deleteCampaign(id, userId);
        return toResponse(result);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> transitionStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        String action = body.getOrDefault("action", "");
        var result = campaignUseCase.transitionStatus(id, userId, action);
        return toResponse(result);
    }

    @PostMapping("/{id}/contacts")
    public ResponseEntity<Map<String, Object>> addContacts(@PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        @SuppressWarnings("unchecked")
        java.util.List<UUID> contactIds = ((java.util.List<String>) body.getOrDefault("contactIds", java.util.List.of()))
                .stream().map(UUID::fromString).toList();
        var result = campaignUseCase.addContacts(id, userId, contactIds);
        return toResponse(result);
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<Map<String, Object>> removeContact(@PathVariable UUID id,
            @PathVariable UUID contactId,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = campaignUseCase.removeContact(id, userId, contactId);
        return toResponse(result);
    }

    @GetMapping("/{id}/contacts")
    public ResponseEntity<Map<String, Object>> getContacts(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = campaignUseCase.getCampaignContacts(id, userId, cursor, size);
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
